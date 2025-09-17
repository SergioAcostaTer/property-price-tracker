package dev.propprice.co.app;

import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class Watchdog {
  private final NamedParameterJdbcTemplate jdbc;

  // every minute: release stuck leases (conservative)
  @Scheduled(fixedDelay = 60_000)
  public void releaseExpiredLeases() {
    int n = jdbc.update("""
        update ing.frontier
        set lease_until = null
        where lease_until is not null and lease_until < now()
        """, Map.of());
    if (n > 0)
      log.info("watchdog released {} leases", n);
  }
}
