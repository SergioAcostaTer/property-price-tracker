package dev.propprice.co.app;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.propprice.co.config.KafkaTopics;
import dev.propprice.co.domain.enums.JobStatus;
import dev.propprice.co.domain.enums.Segment;
import dev.propprice.co.domain.enums.TaskType;
import dev.propprice.co.util.Hashing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PageResultListener {

  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper om = new ObjectMapper();

  @KafkaListener(topics = "acq.raw.page", groupId = "co-result-handler")
  @Transactional
  public void onResult(@Header(name = "ce_id", required = false) String ceId, String value) {
    try {
      JsonNode evt = om.readTree(value);
      int schemaVersion = evt.path("schema_version").asInt(1);
      if (schemaVersion != 1) {
        log.info("Unexpected schema_version={}, treating as v1-compatible", schemaVersion);
      }

      UUID eventId = UUID.fromString(
          (ceId != null && !ceId.isBlank()) ? ceId : evt.path("event_id").asText(UUID.randomUUID().toString()));
      if (alreadyProcessed(eventId))
        return;
      markProcessed(eventId, "acq.raw.page");

      JsonNode job = evt.path("job");
      JsonNode req = evt.path("request");
      JsonNode http = evt.path("http");
      JsonNode parser = evt.path("parser");
      JsonNode discovered = evt.path("discovered");

      UUID jobId = UUID.fromString(job.path("job_id").asText());
      String portal = job.path("portal").asText();
      TaskType task = TaskType.valueOf(job.path("task_type").asText("detail"));
      Segment jobSeg = Segment.valueOf(job.path("segment").asText("unknown"));
      String urlHash = req.path("url_hash").asText();

      int status = http.path("status").asInt(200);
      boolean ok = status >= 200 && status < 300;

      // 1) Update job
      jdbc.update("""
            update ing.job
            set status = :new_status::ing.ing_job_status,
                last_update_at = now(),
                hints = jsonb_set(hints,'{last_status}', to_jsonb(:last_status::int), true)
            where job_id = :job_id
          """, new MapSqlParameterSource()
          .addValue("job_id", jobId)
          .addValue("new_status", ok ? JobStatus.succeeded.name() : JobStatus.retry.name())
          .addValue("last_status", status));

      // 2) Touch frontier (no next_run_at scheduling)
      jdbc.update("""
            update ing.frontier f
            set last_run_at = now(),
                last_result_status = :status,
                last_success_at = case when :status between 200 and 299 then now() else f.last_success_at end,
                consecutive_failures = case when :status between 200 and 299 then 0 else f.consecutive_failures + 1 end,
                lease_until = null
            where f.portal=:portal and f.task_type=:task::ing.ing_task_type and f.url_hash=:h
          """, new MapSqlParameterSource()
          .addValue("portal", portal)
          .addValue("task", task.name())
          .addValue("h", urlHash)
          .addValue("status", status));

      // 3) Upsert discovered links (from search pages). Required array; for detail
      // it's empty.
      if (discovered.isArray() && discovered.size() > 0) {
        List<MapSqlParameterSource> batch = new ArrayList<>(discovered.size());
        for (JsonNode d : discovered) {
          String dUrl = d.path("url").asText();
          String dHash = Hashing.md5(dUrl);
          TaskType dTask = TaskType.valueOf(d.path("task_type").asText("detail"));
          Segment dSeg = Segment.valueOf(d.path("segment").asText(jobSeg.name()));
          int dPrio = d.path("priority").asInt(5);

          batch.add(new MapSqlParameterSource()
              .addValue("portal", portal)
              .addValue("task_type", dTask.name())
              .addValue("url", dUrl)
              .addValue("url_hash", dHash)
              .addValue("segment", dSeg.name())
              .addValue("priority", dPrio)
              .addValue("dedupe_key", null));
        }

        jdbc.batchUpdate("""
              insert into ing.frontier (portal, task_type, url, url_hash, segment, priority,
                                        status, dedupe_key, first_seen_at, scope, meta)
              values (:portal, :task_type::ing.ing_task_type, :url, :url_hash,
                      :segment::ing.ing_segment, :priority,
                      'active'::ing.ing_frontier_status, :dedupe_key, now(), '{}'::jsonb, '{}'::jsonb)
              on conflict (portal, task_type, url_hash) do update set
                priority = excluded.priority,
                status   = 'active'::ing.ing_frontier_status
            """, batch.toArray(MapSqlParameterSource[]::new));
      }

      // 4) Emit ACK (for observability) via outbox
      var ack = om.createObjectNode();
      ack.put("schema_version", 1);
      ack.put("event_id", UUID.randomUUID().toString());
      ack.put("occurred_at", java.time.Instant.now().toString());
      var aJob = ack.putObject("job");
      aJob.put("job_id", jobId.toString());
      aJob.put("portal", portal);
      aJob.put("task_type", task.name());
      ack.put("result_status", status);
      ack.put("processed_at", OffsetDateTime.now().toString());
      ack.put("discovered_count", discovered.isArray() ? discovered.size() : 0);

      jdbc.update("""
            insert into ing.outbox(topic, k, v, headers, created_at)
            values (:topic, :k, cast(:v as jsonb), '{}'::jsonb, now())
          """, new MapSqlParameterSource()
          .addValue("topic", KafkaTopics.PAGE_RESULT_ACK)
          .addValue("k", jobId.toString().getBytes(StandardCharsets.UTF_8))
          .addValue("v", ack.toString()));

    } catch (Exception e) {
      log.warn("PageResultListener error (rolled back). value={}", value, e);
      throw new RuntimeException(e);
    }
  }

  private boolean alreadyProcessed(UUID eventId) {
    Integer n = jdbc.queryForObject("select count(*) from ing.event_log where event_id=:e",
        Map.of("e", eventId), Integer.class);
    return n != null && n > 0;
  }

  private void markProcessed(UUID eventId, String topic) {
    jdbc.update("insert into ing.event_log(event_id, topic) values (:e,:t)",
        new MapSqlParameterSource().addValue("e", eventId).addValue("t", topic));
  }
}
