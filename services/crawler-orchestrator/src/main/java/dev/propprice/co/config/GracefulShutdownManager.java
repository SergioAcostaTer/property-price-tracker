package dev.propprice.co.config;

import java.util.concurrent.TimeUnit;

import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

import dev.propprice.co.app.RedisLeaderElector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class GracefulShutdownManager implements ApplicationListener<ContextClosedEvent> {

  private final RedisLeaderElector leaderElector;
  private volatile boolean shutdownInProgress = false;

  @Override
  public void onApplicationEvent(ContextClosedEvent event) {
    log.info("Starting graceful shutdown...");
    shutdownInProgress = true;

    try {
      // Release leadership to allow other instances to take over quickly
      leaderElector.releaseLeadership();
      log.info("Released leadership");

      // Give some time for in-flight operations to complete
      log.info("Waiting for in-flight operations to complete...");
      Thread.sleep(TimeUnit.SECONDS.toMillis(2));

      log.info("Graceful shutdown completed");

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Graceful shutdown interrupted");
    } catch (Exception e) {
      log.error("Error during graceful shutdown", e);
    }
  }

  public boolean isShutdownInProgress() {
    return shutdownInProgress;
  }
}