package dev.propprice.co.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

  @Bean
  public NewTopic jobDispatched() {
    return TopicBuilder.name(KafkaTopics.JOB_DISPATCHED)
        .partitions(3) // allow parallel scrapers; key by url_hash
        .replicas(1) // dev; use 3 in prod
        .config("retention.ms", String.valueOf(7L * 24 * 60 * 60 * 1000)) // 7d
        .config("cleanup.policy", "delete")
        .build();
  }

  @Bean
  public NewTopic rawPage() {
    return TopicBuilder.name(KafkaTopics.RAW_PAGE)
        .partitions(3) // key by url_hash or job_id
        .replicas(1)
        .config("retention.ms", String.valueOf(7L * 24 * 60 * 60 * 1000))
        .config("cleanup.policy", "delete")
        .build();
  }

  @Bean
  public NewTopic rawPageDlt() {
    return TopicBuilder.name(KafkaTopics.RAW_PAGE_DLT)
        .partitions(3)
        .replicas(1)
        .config("retention.ms", String.valueOf(14L * 24 * 60 * 60 * 1000)) // 14d for debugging
        .build();
  }
}
