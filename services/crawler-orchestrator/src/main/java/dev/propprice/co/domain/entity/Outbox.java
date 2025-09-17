package dev.propprice.co.domain.entity;

import java.time.OffsetDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "outbox", schema = "ing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Outbox {

  @jakarta.persistence.Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @Column(name = "topic", nullable = false)
  private String topic;

  @Column(name = "k")
  private byte[] key;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "v", columnDefinition = "jsonb", nullable = false)
  @Builder.Default
  private JsonNode value = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "headers", columnDefinition = "jsonb", nullable = false)
  @Builder.Default
  private JsonNode headers = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

  @Column(name = "created_at", columnDefinition = "timestamptz")
  private OffsetDateTime createdAt;

  @Column(name = "sent_at", columnDefinition = "timestamptz")
  private OffsetDateTime sentAt;

  @Column(name = "attempts", nullable = false)
  @Builder.Default
  private int attempts = 0;

  @Column(name = "last_error")
  private String lastError;

  @PrePersist
  void prePersist() {
    if (value == null)
      value = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    if (headers == null)
      headers = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    if (createdAt == null)
      createdAt = OffsetDateTime.now();
  }
}