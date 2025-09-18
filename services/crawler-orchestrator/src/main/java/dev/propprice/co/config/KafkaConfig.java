package dev.propprice.co.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration
public class KafkaConfig {

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, String> coKafkaListenerFactory(
      ConsumerFactory<String, String> cf, KafkaTemplate<String, String> template,
      @Value("${co.kafka.listener.concurrency:3}") int concurrency) {

    var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
    factory.setConsumerFactory(cf);
    factory.setConcurrency(concurrency);
    factory.setBatchListener(false);

    // DLT recoverer: publish to <topic>.DLT, keep the original key and headers
    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template, (r, e) -> {
      String dltTopic = r.topic() + ".DLT";
      return new org.apache.kafka.common.TopicPartition(dltTopic, r.partition());
    });

    var backoff = new ExponentialBackOffWithMaxRetries(5);
    backoff.setInitialInterval(500L);
    backoff.setMultiplier(2.0);
    backoff.setMaxInterval(10_000L);

    factory.setCommonErrorHandler(new DefaultErrorHandler(recoverer, backoff));
    return factory;
  }
}
