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

import dev.propprice.co.config.DispatcherProperties;
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
  private final DispatcherProperties config;
  private final LeaseService leaseService;

  @Scheduled(fixedDelayString = "${co.dispatcher.tick-interval:1000}")
  public void tick() {
    if (!config.isEnabled())
      return;
    if (!leader.isLeader())
      return;

    List<String> portals = getActivePortals();
    for (String portal : portals) {
      try {
        dispatchForPortal(portal);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        log.warn("Dispatcher interrupted for portal={}", portal);
        break;
      } catch (Exception e) {
        // transactional method already rolled back
        log.error("Dispatch error for portal={} (batch rolled back)", portal, e);
      }
    }
  }

  private List<String> getActivePortals() {
    return jdbc.query("select portal from ing.portal_policy where max_concurrency > 0",
        (rs, i) -> rs.getString(1));
  }

  @Transactional
  void dispatchForPortal(String portal) throws InterruptedException {
    PortalPolicy policy = policyService.getOrDefault(portal);
    if (policy.getMaxConcurrency() <= 0)
      return;

    int capacity = calculateCapacity(portal, policy);
    if (capacity <= 0)
      return;

    if (!checkRateLimit(portal, policy))
      return;

    int batchCap = Math.min(capacity, policy.getBucketSize());
    int batchSize = Math.min(batchCap, Math.max(1, config.getMaxBatchSize()));

    List<Claimed> claimed = claimDueRows(portal,
        batchSize,
        policy.getMinDaysBetweenRuns(),
        config.getLeaseDurationMinutes());

    if (claimed.isEmpty()) {
      log.debug("No due URLs found for portal={}", portal);
      return;
    }

    log.info("Dispatching {} jobs for portal={}", claimed.size(), portal);

    try {
      // All-or-nothing within this transaction
      for (Claimed c : claimed) {
        createJobAndOutboxEntry(portal, c);
      }
    } catch (Exception e) {
      // Release leases in a separate transaction so the release is not rolled back
      try {
        leaseService.releaseLeases(portal, claimed);
      } catch (Exception releaseEx) {
        log.warn("Failed to release leases for portal={} after rollback", portal, releaseEx);
      }
      throw e; // rethrow to rollback job/outbox inserts
    }
  }

  private int calculateCapacity(String portal, PortalPolicy policy) {
    Integer inflight = jdbc.queryForObject(
        "select count(*) from ing.job where portal=:p and status='dispatched'::ing.ing_job_status",
        Map.of("p", portal),
        Integer.class);
    int currentInflight = inflight != null ? inflight : 0;
    return Math.max(0, policy.getMaxConcurrency() - currentInflight);
  }

  private boolean checkRateLimit(String portal, PortalPolicy policy) {
    String bucketKey = "portal:" + portal;
    return bucket.allow(bucketKey, policy.getTargetQps().doubleValue(), policy.getBucketSize());
  }

  private void createJobAndOutboxEntry(String portal, Claimed claimed) {
    UUID jobId = UUID.randomUUID();
    OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);

    insertJob(jobId, portal, claimed, nowUtc);
    insertOutboxEntry(jobId, portal, claimed, nowUtc);
  }

  private void insertJob(UUID jobId, String portal, Claimed claimed, OffsetDateTime scheduledAt) {
    var params = new MapSqlParameterSource()
        .addValue("job_id", jobId)
        .addValue("portal", portal)
        .addValue("task_type", claimed.taskType().name())
        .addValue("segment", claimed.segment().name())
        .addValue("url_hash", claimed.urlHash())
        .addValue("url", claimed.url())
        .addValue("scheduled_at", scheduledAt);

    int updated = jdbc.update("""
        insert into ing.job(job_id, portal, task_type, segment, url_hash, url, attempt, status, scheduled_at, hints)
        values (:job_id, :portal, :task_type::ing.ing_task_type, :segment::ing.ing_segment,
                :url_hash, :url, 1, 'dispatched'::ing.ing_job_status, :scheduled_at, '{}'::jsonb)
        """, params);

    if (updated != 1) {
      throw new RuntimeException("Failed to insert job record");
    }
  }

  private void insertOutboxEntry(UUID jobId, String portal, Claimed claimed, OffsetDateTime occurredAt) {
    ObjectNode evt = createJobDispatchedEvent(jobId, portal, claimed, occurredAt);
    SchemaValidator.validate(Schemas.JOB_DISPATCHED_V1, evt);

    var headersJson = om.createObjectNode();
    headersJson.put("content-type", "application/json");
    headersJson.put("schema", "acq.job.dispatched@v1");
    headersJson.put("ce_type", "acq.job.dispatched");
    headersJson.put("ce_id", evt.get("event_id").asText());
    headersJson.put("ce_source", "co");

    var params = new MapSqlParameterSource()
        .addValue("topic", KafkaTopics.JOB_DISPATCHED)
        .addValue("k", claimed.urlHash().getBytes(java.nio.charset.StandardCharsets.UTF_8))
        .addValue("v", evt.toString())
        .addValue("headers", headersJson.toString());

    int updated = jdbc.update("""
        insert into ing.outbox(topic, k, v, headers, created_at)
        values (:topic, :k, cast(:v as jsonb), cast(:headers as jsonb), now())
        """, params);

    if (updated != 1) {
      throw new RuntimeException("Failed to insert outbox record");
    }
  }

  private ObjectNode createJobDispatchedEvent(UUID jobId, String portal, Claimed claimed, OffsetDateTime occurredAt) {
    ObjectNode evt = om.createObjectNode();
    evt.put("schema_version", 1);
    evt.put("event_id", UUID.randomUUID().toString());
    evt.put("occurred_at", Instant.now().toString());

    var job = evt.putObject("job");
    job.put("job_id", jobId.toString());
    job.put("portal", portal);
    job.put("task_type", claimed.taskType().name());
    job.put("segment", claimed.segment().name());
    job.put("priority", claimed.taskType() == TaskType.search_page ? 2 : 5);

    var req = evt.putObject("request");
    req.put("url", claimed.url());
    req.put("url_hash", claimed.urlHash());
    req.put("attempt", 1);

    return evt;
  }

  private List<Claimed> claimDueRows(String portal, int limit, int minDaysBetweenRuns, int leaseMinutes) {
    String sql = """
        with cte as (
            select portal, task_type, url_hash, url, segment
            from ing.frontier
            where portal = :portal
              and status = 'active'::ing.ing_frontier_status
              and (lease_until is null or lease_until <= now())
              and (last_run_at is null
                   or last_run_at <= now() - (interval '1 day' * :min_days_between_runs))
              and consecutive_failures < :max_failures
            order by priority asc, coalesce(last_run_at, 'epoch') asc, first_seen_at asc
            for update skip locked
            limit :lim
        )
        update ing.frontier f
        set lease_until = now() + (interval '1 minute' * :lease_minutes),
            last_dispatched_at = now()
        from cte
        where f.portal = cte.portal
          and f.task_type = cte.task_type
          and f.url_hash = cte.url_hash
        returning f.task_type::text, f.segment::text, f.url_hash, f.url
        """;

    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("portal", portal)
        .addValue("lim", limit)
        .addValue("min_days_between_runs", minDaysBetweenRuns)
        .addValue("max_failures", config.getMaxConsecutiveFailures())
        .addValue("lease_minutes", leaseMinutes);

    return jdbc.query(sql, params, (rs, i) -> new Claimed(
        TaskType.valueOf(rs.getString(1)),
        Segment.valueOf(rs.getString(2)),
        rs.getString(3),
        rs.getString(4)));
  }

  record Claimed(TaskType taskType, Segment segment, String urlHash, String url) {
  }
}
