package dev.propprice.co.app;

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

  @Scheduled(fixedDelay = 500)
  @Transactional
  public void drain() {
    List<Outbox> batch = repo.fetchUnsentOrdered(100);
    for (Outbox o : batch) {

      try {
        String topic = o.getTopic();
        String key = o.getKey() != null ? new String(o.getKey()) : null;
        String value = o.getValue().toString();

        var headers = new RecordHeaders();
        if (o.getHeaders() != null && !o.getHeaders().isEmpty()) {
          var fields = o.getHeaders().fields();
          while (fields.hasNext()) {
            var entry = fields.next();
            String hKey = entry.getKey();
            String hVal = String.valueOf(entry.getValue().asText());
            headers.add(hKey, hVal.getBytes());
          }
        }

        ProducerRecord<String, String> record = new ProducerRecord<>(topic, null, key, value, headers);
        kafka.send(record).get();

        o.setAttempts(o.getAttempts() + 1);
        o.setSentAt(java.time.OffsetDateTime.now());
        repo.save(o);
      } catch (Exception e) {
        o.setAttempts(o.getAttempts() + 1);
        o.setLastError(e.getMessage());
        repo.save(o);
        log.warn("outbox publish failed id={}", o.getId(), e);
      }
    }
  }
}
