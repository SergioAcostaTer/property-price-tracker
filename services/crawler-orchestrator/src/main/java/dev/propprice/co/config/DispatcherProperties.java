package dev.propprice.co.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "co.dispatcher")
public class DispatcherProperties {
  /** Interval between dispatcher ticks in milliseconds */
  private long tickInterval = 1000;

  /** Maximum consecutive failures before quarantining a URL */
  private int maxConsecutiveFailures = 5;

  /** Lease duration for claimed URLs in minutes */
  private int leaseDurationMinutes = 2;

  /** Maximum batch size per portal per tick */
  private int maxBatchSize = 50;

  /** Enable/disable dispatcher entirely */
  private boolean enabled = true;
}
