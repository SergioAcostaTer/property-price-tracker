package dev.propprice.co.domain.entity;

import java.math.BigDecimal;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "portal_policy", schema = "ing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortalPolicy {

  @jakarta.persistence.Id
  @Column(name = "portal", nullable = false)
  private String portal;

  @Column(name = "max_concurrency", nullable = false)
  @Builder.Default
  private int maxConcurrency = 4;

  @Column(name = "target_qps", nullable = false, precision = 5, scale = 2)
  @Builder.Default
  private BigDecimal targetQps = new BigDecimal("0.40");

  @Column(name = "bucket_size", nullable = false)
  @Builder.Default
  private int bucketSize = 6;

  @Column(name = "max_attempts", nullable = false)
  @Builder.Default
  private int maxAttempts = 4;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "backoff_sec", columnDefinition = "integer[]", nullable = false)
  @Builder.Default
  private Integer[] backoffSec = new Integer[] { 60, 300, 1800, 3600 };

  @Column(name = "min_days_between_runs", nullable = false)
  @Builder.Default
  private int minDaysBetweenRuns = 7;
}