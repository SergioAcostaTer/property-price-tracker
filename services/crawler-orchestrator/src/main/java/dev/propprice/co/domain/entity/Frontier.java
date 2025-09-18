package dev.propprice.co.domain.entity;

import java.time.OffsetDateTime;
import java.util.Map;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import dev.propprice.co.domain.enums.FrontierStatus;
import dev.propprice.co.domain.enums.Segment;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "frontier", schema = "ing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Frontier {

  @EmbeddedId
  private FrontierId id;

  @Column(nullable = false)
  private String url;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "ing_segment")
  @ColumnDefault("'unknown'")
  @Builder.Default
  private Segment segment = Segment.unknown;

  @Column(nullable = false)
  @ColumnDefault("5")
  @Builder.Default
  private Integer priority = 5;

  @Column(name = "last_run_at", columnDefinition = "timestamptz")
  private OffsetDateTime lastRunAt;

  @Column(name = "last_result_status")
  private Integer lastResultStatus;

  @Column(name = "lease_until")
  private OffsetDateTime leaseUntil;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "ing_frontier_status")
  @ColumnDefault("'active'")
  @Builder.Default
  private FrontierStatus status = FrontierStatus.active;

  @Column(name = "dedupe_key")
  private String dedupeKey;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  @Builder.Default
  private Map<String, Object> scope = Map.of();

  @Column(name = "first_seen_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
  @ColumnDefault("now()")
  @Builder.Default
  private OffsetDateTime firstSeenAt = OffsetDateTime.now();

  @Column(name = "last_dispatched_at", columnDefinition = "timestamptz")
  private OffsetDateTime lastDispatchedAt;

  @Column(name = "last_success_at", columnDefinition = "timestamptz")
  private OffsetDateTime lastSuccessAt;

  @Column(name = "consecutive_failures", nullable = false)
  @ColumnDefault("0")
  @Builder.Default
  private Integer consecutiveFailures = 0;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  @Builder.Default
  private Map<String, Object> meta = Map.of();
}