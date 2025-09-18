package dev.propprice.co.app;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;

import dev.propprice.co.domain.entity.Outbox;
import dev.propprice.co.domain.repo.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {
  private final OutboxRepository repo;
  private final KafkaTemplate<String, String> kafka;

  // Exponential backoff intervals in minutes: 1, 5, 15, 60, 240, 480 (8 hours)
  private static final int[] BACKOFF_MINUTES = { 1, 5, 15, 60, 240, 480 };
  private static final int MAX_ATTEMPTS = 10;

  @Scheduled(fixedDelay = 500)
  @Transactional
  public void drain() {
    List<Outbox> batch = repo.fetchUnsentOrderedWithRetry(100);
    if (batch.isEmpty()) {
      return;
    }

    log.debug("Processing {} outbox messages", batch.size());

    for (Outbox o : batch) {
      try {
        if (!shouldAttempt(o)) {
          continue;
        }

        publishMessage(o);
        markSent(o);

      } catch (Exception e) {
        handleFailure(o, e);
      }
    }
  }

  // light housekeeping – keep table lean in dev
  @Scheduled(fixedDelay = 60_000)
  @Transactional
  public void cleanup() {
    repo.cleanupOldMessages();
  }

  private boolean shouldAttempt(Outbox o) {
    if (o.getAttempts() == 0)
      return true;

    if (o.getAttempts() >= MAX_ATTEMPTS) {
      if (o.getSentAt() == null) {
        markAsDead(o);
      }
      return false;
    }

    OffsetDateTime lastAttempt = o.getSentAt() != null ? o.getSentAt() : o.getCreatedAt();
    int backoffIndex = Math.min(o.getAttempts() - 1, BACKOFF_MINUTES.length - 1);
    OffsetDateTime nextRetry = lastAttempt.plusMinutes(BACKOFF_MINUTES[backoffIndex]);

    return OffsetDateTime.now().isAfter(nextRetry);
  }

  private void publishMessage(Outbox o) throws Exception {
    String topic = o.getTopic();
    String key = null;
    if (o.getKey() != null) {
      key = new String(o.getKey(), StandardCharsets.UTF_8);
    }
    String value = o.getValue().toString();

    var headers = buildHeaders(o.getHeaders());
    ProducerRecord<String, String> record = new ProducerRecord<>(topic, null, key, value, headers);

    kafka.send(record).get(); // sync send
    log.debug("Published message id={} to topic={}", o.getId(), topic);
  }

  private RecordHeaders buildHeaders(JsonNode node) {
    var headers = new RecordHeaders();
    var fields = node.properties();
    for (var entry : fields) {
      String hKey = entry.getKey();
      String hVal = entry.getValue().asText();
      headers.add(hKey, hVal.getBytes());
    }
    return headers;
  }

  private void markSent(Outbox o) {
    o.setAttempts(o.getAttempts() + 1);
    o.setSentAt(OffsetDateTime.now());
    o.setLastError(null);
    repo.save(o);
  }

  private void handleFailure(Outbox o, Exception e) {
    o.setAttempts(o.getAttempts() + 1);
    o.setLastError(truncateError(e.getMessage()));
    repo.save(o);

    if (o.getAttempts() >= MAX_ATTEMPTS) {
      log.error("Outbox message id={} failed permanently after {} attempts", o.getId(), o.getAttempts(), e);
    } else {
      log.warn("Outbox message id={} failed, attempt {}/{}", o.getId(), o.getAttempts(), MAX_ATTEMPTS, e);
    }
  }

  private void markAsDead(Outbox o) {
    log.error("Marking outbox message id={} as dead after {} attempts", o.getId(), o.getAttempts());
    o.setLastError("DEAD: Exceeded maximum retry attempts");
    repo.save(o);
  }

  private String truncateError(String error) {
    if (error == null)
      return null;
    return error.length() > 500 ? error.substring(0, 500) + "..." : error;
  }
}
