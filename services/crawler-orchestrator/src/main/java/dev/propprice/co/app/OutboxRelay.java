package dev.propprice.co.app;

import java.util.List;

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
        String key = o.getKey() == null ? "" : new String(o.getKey());
        String value = o.getValue().toString();
        kafka.send(o.getTopic(), key, value).get();
        o.setAttempts(o.getAttempts() + 1);
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
