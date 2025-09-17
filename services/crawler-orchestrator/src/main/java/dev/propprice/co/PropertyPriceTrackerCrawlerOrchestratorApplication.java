package dev.propprice.co;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PropertyPriceTrackerCrawlerOrchestratorApplication {

  public static void main(String[] args) {
    SpringApplication.run(PropertyPriceTrackerCrawlerOrchestratorApplication.class, args);
  }

}
