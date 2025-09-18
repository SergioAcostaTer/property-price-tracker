package dev.propprice.co.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, String> coKafkaListenerFactory(
      ConsumerFactory<String, String> cf,
      KafkaTemplate<String, String> template,
      @Value("${co.kafka.listener.concurrency:3}") int concurrency) {

    var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
    factory.setConsumerFactory(cf);
    factory.setConcurrency(concurrency);
    factory.setBatchListener(false);

    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
        (r, e) -> new TopicPartition(r.topic() + ".DLT", r.partition()));

    // retry 5 times, 500ms between attempts
    FixedBackOff backoff = new FixedBackOff(500L, 5L);

    factory.setCommonErrorHandler(new DefaultErrorHandler(recoverer, backoff));
    return factory;
  }
}
