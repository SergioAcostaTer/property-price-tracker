package dev.propprice.co.app;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import dev.propprice.co.config.KafkaTopics;
import dev.propprice.co.domain.entity.PortalPolicy;
import dev.propprice.co.domain.enums.Segment;
import dev.propprice.co.domain.enums.TaskType;
import dev.propprice.co.schema.SchemaValidator;
import dev.propprice.co.schema.Schemas;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class FrontierDispatcher {
  private final NamedParameterJdbcTemplate jdbc;
  private final PolicyService policyService;
  private final RedisTokenBucket bucket;
  private final ObjectMapper om = new ObjectMapper();
  private final RedisLeaderElector leader;

  // every second, lightweight
  @Scheduled(fixedDelay = 1000)
  public void tick() {
    // Dispatch per portal known in portal_policy
    if (!leader.isLeader())
      return;
    List<String> portals = jdbc.query("select portal from ing.portal_policy", (rs, i) -> rs.getString(1));
    for (String portal : portals) {
      try {
        dispatchForPortal(portal);
      } catch (Exception e) {
        log.warn("dispatch error portal={}", portal, e);
      }
    }
  }

  @Transactional
  void dispatchForPortal(String portal) {
    PortalPolicy pol = policyService.getOrDefault(portal);

    // 1) respect max_concurrency (dispatched jobs)
    Integer inflightObj = jdbc.queryForObject(
        "select count(*) from ing.job where portal=:p and status='dispatched'::ing.ing_job_status",
        Map.of("p", portal), Integer.class);
    int inflight = java.util.Objects.requireNonNullElse(inflightObj, 0);

    int capacity = Math.max(0, pol.getMaxConcurrency() - inflight);
    if (capacity <= 0)
      return;

    // 2) token bucket (rate-limit)
    String bucketKey = "portal:" + portal;
    if (!bucket.allow(bucketKey, pol.getTargetQps().doubleValue(), pol.getBucketSize())) {
      return;
    }

    // 3) claim rows atomically & create job + outbox (in this TX)
    int limit = Math.min(capacity, pol.getBucketSize()); // small batch
    List<Claimed> claimed = claimDueRows(portal, limit);
    if (claimed.isEmpty())
      return;

    for (Claimed c : claimed) {
      UUID jobId = UUID.randomUUID();
      OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
      // job insert
      var pJob = new MapSqlParameterSource()
          .addValue("job_id", jobId)
          .addValue("portal", portal)
          .addValue("task_type", c.taskType.name())
          .addValue("segment", c.segment.name())
          .addValue("url_hash", c.urlHash)
          .addValue("url", c.url)
          .addValue("scheduled_at", nowUtc);
      jdbc.update(
          """
              insert into ing.job(job_id, portal, task_type, segment, url_hash, url, attempt, status, scheduled_at, hints)
              values (:job_id, :portal, :task_type::ing.ing_task_type, :segment::ing.ing_segment, :url_hash, :url, 1, 'dispatched'::ing.ing_job_status, :scheduled_at, '{}'::jsonb)
              """,
          pJob);

      // outbox produce (event as JSON)
      ObjectNode evt = om.createObjectNode();
      evt.put("schema_version", 1);
      evt.put("event_id", UUID.randomUUID().toString());
      evt.put("occurred_at", Instant.now().toString());

      var job = evt.putObject("job");
      job.put("job_id", jobId.toString());
      job.put("portal", portal);
      job.put("task_type", c.taskType.name());
      job.put("segment", c.segment.name());
      job.put("priority", c.taskType == TaskType.search_page ? 2 : 5);

      var req = evt.putObject("request");
      req.put("url", c.url);
      req.put("url_hash", c.urlHash);
      req.put("attempt", 1);

      SchemaValidator.validate(Schemas.JOB_DISPATCHED_V1, evt);

      var headersJson = om.createObjectNode();
      headersJson.put("content-type", "application/json");
      headersJson.put("schema", "acq.job.dispatched@v1");
      headersJson.put("ce_type", "acq.job.dispatched");
      headersJson.put("ce_id", evt.get("event_id").asText());
      headersJson.put("ce_source", "co");

      var pOut = new MapSqlParameterSource()
          .addValue("topic", KafkaTopics.JOB_DISPATCHED)
          .addValue("k", c.urlHash.getBytes())
          .addValue("v", evt.toString())
          .addValue("headers", headersJson.toString());
      jdbc.update(
          """
              insert into ing.outbox(topic, k, v, headers, created_at) values (:topic, :k, cast(:v as jsonb), cast(:headers as jsonb), now())
              """,
          pOut);
    }
  }

  // claim N rows: lease for 2 minutes
  private List<Claimed> claimDueRows(String portal, int limit) {
    String sql = """
          with cte as (
            select portal, task_type, url_hash
            from ing.frontier
            where portal = :portal
              and status = 'active'::ing.ing_frontier_status
              and (lease_until is null or lease_until <= now())
              and (
                last_run_at is null
                or last_run_at <= now() - (interval '1 day' * :min_days_between_runs)
              )
            order by priority asc, coalesce(last_run_at, 'epoch') asc, first_seen_at asc
            for update skip locked
            limit :lim
          )
          update ing.frontier f
          set lease_until = now() + interval '2 minutes',
              last_dispatched_at = now()
          from cte
          where f.portal = cte.portal and f.task_type = cte.task_type and f.url_hash = cte.url_hash
          returning f.task_type::text, f.segment::text, f.url_hash, f.url
        """;
    int minDays = policyService.getOrDefault(portal).getMinDaysBetweenRuns();
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("portal", portal)
        .addValue("lim", limit)
        .addValue("min_days_between_runs", minDays);

    return jdbc.query(sql, p, (rs, i) -> new Claimed(
        TaskType.valueOf(rs.getString(1)),
        Segment.valueOf(rs.getString(2)),
        rs.getString(3),
        rs.getString(4)));
  }

  record Claimed(TaskType taskType, Segment segment, String urlHash, String url) {
  }
}
