package dev.propprice.co.domain.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

import dev.propprice.co.domain.enums.JobStatus;
import dev.propprice.co.domain.enums.Segment;
import dev.propprice.co.domain.enums.TaskType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "job", schema = "ing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Job {

  @jakarta.persistence.Id
  @Column(name = "job_id", nullable = false, updatable = false)
  @Builder.Default
  private UUID jobId = UUID.randomUUID();

  @Column(name = "portal", nullable = false)
  private String portal;

  @Enumerated(EnumType.STRING)
  @Column(name = "task_type", nullable = false, columnDefinition = "ing_task_type")
  private TaskType taskType;

  @Enumerated(EnumType.STRING)
  @Column(name = "segment", nullable = false, columnDefinition = "ing_segment")
  @Builder.Default
  private Segment segment = Segment.unknown;

  @Column(name = "url_hash", length = 32, nullable = false)
  private String urlHash;

  @Column(name = "url", nullable = false)
  private String url;

  @Column(name = "attempt", nullable = false)
  @ColumnDefault("1")
  @Builder.Default
  private int attempt = 1;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, columnDefinition = "ing_job_status")
  @Builder.Default
  private JobStatus status = JobStatus.dispatched;

  @Column(name = "scheduled_at", nullable = false, columnDefinition = "timestamptz")
  private OffsetDateTime scheduledAt;

  @Column(name = "last_update_at", nullable = false, columnDefinition = "timestamptz")
  @ColumnDefault("now()")
  @Builder.Default
  private OffsetDateTime lastUpdateAt = OffsetDateTime.now();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "hints", columnDefinition = "jsonb", nullable = false)
  @Builder.Default
  private JsonNode hints = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

  @PrePersist
  public void prePersist() {
    if (jobId == null)
      jobId = UUID.randomUUID();
    if (scheduledAt == null)
      scheduledAt = OffsetDateTime.now();
    if (hints == null)
      hints = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    lastUpdateAt = OffsetDateTime.now();
  }

  @PreUpdate
  public void preUpdate() {
    lastUpdateAt = OffsetDateTime.now();
  }
}