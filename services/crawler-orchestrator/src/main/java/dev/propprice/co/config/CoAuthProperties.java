package dev.propprice.co.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "co.auth")
public class CoAuthProperties {
  /** Auth token sent by clients (keep as String, not boolean) */
  private String token;

  /** Optional flag to turn auth on/off */
  private boolean enabled = true;
}
