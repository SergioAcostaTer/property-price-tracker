package dev.propprice.co.config;

public final class KafkaTopics {
  private KafkaTopics() {
  }

  public static final String JOB_DISPATCHED = "acq.job.dispatched";
  public static final String RAW_PAGE = "acq.raw.page";
  public static final String RAW_PAGE_DLT = "acq.raw.page.DLT";
}
