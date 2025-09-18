package dev.propprice.co.app;

import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.propprice.co.domain.enums.TaskType;
import lombok.RequiredArgsConstructor;

/**
 * Separates lease release into REQUIRES_NEW transactions so releases are not
 * rolled back with the batch when a dispatch error occurs.
 */
@Service
@RequiredArgsConstructor
public class LeaseService {

  private final NamedParameterJdbcTemplate jdbc;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void releaseLeases(String portal, List<FrontierDispatcher.Claimed> claimed) {
    for (FrontierDispatcher.Claimed c : claimed) {
      releaseLease(portal, c.taskType(), c.urlHash());
    }
  }

  private void releaseLease(String portal, TaskType taskType, String urlHash) {
    jdbc.update("""
        update ing.frontier
        set lease_until = null
        where portal = :portal
          and task_type = :task_type::ing.ing_task_type
          and url_hash = :url_hash
        """,
        new MapSqlParameterSource()
            .addValue("portal", portal)
            .addValue("task_type", taskType.name())
            .addValue("url_hash", urlHash));
  }
}
