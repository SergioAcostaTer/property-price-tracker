package dev.propprice.co.config;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public final class KafkaTopics {
  public static final String JOB_DISPATCHED = "acq.job.dispatched";
  public static final String PAGE_RESULT_ACK = "acq.page.ack";
  public static final String PAGE_RESULT_EVENT = "acq.page.event";
}
