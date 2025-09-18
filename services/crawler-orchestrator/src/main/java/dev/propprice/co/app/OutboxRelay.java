package dev.propprice.co.app;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
          continue; // Skip messages not ready for retry
        }

        publishMessage(o);
        markSent(o);

      } catch (Exception e) {
        handleFailure(o, e);
      }
    }
  }

  private boolean shouldAttempt(Outbox o) {
    if (o.getAttempts() == 0) {
      return true; // First attempt
    }

    if (o.getAttempts() >= MAX_ATTEMPTS) {
      if (o.getSentAt() == null) { // Only mark as dead once
        markAsDead(o);
      }
      return false;
    }

    // Calculate next retry time using exponential backoff
    OffsetDateTime lastAttempt = o.getSentAt() != null ? o.getSentAt() : o.getCreatedAt();
    int backoffIndex = Math.min(o.getAttempts() - 1, BACKOFF_MINUTES.length - 1);
    OffsetDateTime nextRetry = lastAttempt.plusMinutes(BACKOFF_MINUTES[backoffIndex]);

    return OffsetDateTime.now().isAfter(nextRetry);
  }

  private void publishMessage(Outbox o) throws Exception {
    String topic = o.getTopic();
    String key = o.getKey() != null ? new String(o.getKey()) : null;
    String value = o.getValue().toString();

    var headers = buildHeaders(o);
    ProducerRecord<String, String> record = new ProducerRecord<>(topic, null, key, value, headers);

    // Synchronous send with timeout
    kafka.send(record).get();
    log.debug("Published message id={} to topic={}", o.getId(), topic);
  }

  private RecordHeaders buildHeaders(Outbox o) {
    var headers = new RecordHeaders();
    if (o.getHeaders() != null && !o.getHeaders().isEmpty()) {
      var fields = o.getHeaders().properties();
      for (var entry : fields) {
        String hKey = entry.getKey();
        String hVal = entry.getValue().asText();
        headers.add(hKey, hVal.getBytes());
      }
    }
    return headers;
  }

  private void markSent(Outbox o) {
    o.setAttempts(o.getAttempts() + 1);
    o.setSentAt(OffsetDateTime.now());
    o.setLastError(null); // Clear any previous error
    repo.save(o);
  }

  private void handleFailure(Outbox o, Exception e) {
    o.setAttempts(o.getAttempts() + 1);
    o.setLastError(truncateError(e.getMessage()));
    repo.save(o);

    if (o.getAttempts() >= MAX_ATTEMPTS) {
      log.error("Outbox message id={} failed permanently after {} attempts",
          o.getId(), o.getAttempts(), e);
    } else {
      log.warn("Outbox message id={} failed, attempt {}/{}",
          o.getId(), o.getAttempts(), MAX_ATTEMPTS, e);
    }
  }

  private void markAsDead(Outbox o) {
    log.error("Marking outbox message id={} as dead after {} attempts", o.getId(), o.getAttempts());
    // You could move to a dead letter table or set a flag
    // For now, we'll leave it with the error message
    o.setLastError("DEAD: Exceeded maximum retry attempts");
    repo.save(o);
  }

  private String truncateError(String error) {
    if (error == null)
      return null;
    return error.length() > 500 ? error.substring(0, 500) + "..." : error;
  }
}