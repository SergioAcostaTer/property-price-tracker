package dev.propprice.co.app;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import dev.propprice.co.api.dto.FrontierBatchUpsertRequest;
import dev.propprice.co.domain.enums.Segment;
import dev.propprice.co.domain.enums.TaskType;
import dev.propprice.co.util.Hashing;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FrontierService {
  private final NamedParameterJdbcTemplate jdbc;

  public int batchUpsert(FrontierBatchUpsertRequest req) {
    int total = 0;
    for (var r : req.getResources()) {
      total += upsertOne(req.getPortal(), r.getTask_type(), r.getSegment(), r.getUrl(),
          r.getPriority() != null ? r.getPriority() : 5,
          r.getNext_run_in_sec() != null ? Duration.ofSeconds(r.getNext_run_in_sec()) : Duration.ZERO,
          r.getDedupe_key());
    }
    return total;
  }

  private int upsertOne(String portal, TaskType taskType, Segment segment, String url, int priority,
      Duration delay, String dedupeKey) {
    String urlHash = Hashing.md5(url);
    OffsetDateTime nextRun = OffsetDateTime.now().plus(delay);
    String sql = """
        insert into ing.frontier (portal, task_type, url, url_hash, segment, priority, next_run_at, status, dedupe_key, first_seen_at, scope, meta)
        values (:portal, :task_type::ing.ing_task_type, :url, :url_hash, :segment::ing.ing_segment, :priority, :next_run_at, 'active'::ing.ing_frontier_status, :dedupe_key, now(), '{}'::jsonb, '{}'::jsonb)
        on conflict (portal, task_type, url_hash) do update set
          priority = excluded.priority,
          next_run_at = excluded.next_run_at,
          status = 'active'::ing.ing_frontier_status,
          dedupe_key = coalesce(excluded.dedupe_key, ing.frontier.dedupe_key)
        """;
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("portal", portal)
        .addValue("task_type", taskType.name())
        .addValue("url", url)
        .addValue("url_hash", urlHash)
        .addValue("segment", segment.name())
        .addValue("priority", priority)
        .addValue("next_run_at", nextRun)
        .addValue("dedupe_key", dedupeKey);
    return jdbc.update(sql, p);
  }
}
