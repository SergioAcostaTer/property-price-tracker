package dev.propprice.co.api.dto;

import java.util.List;

import dev.propprice.co.domain.enums.Segment;
import dev.propprice.co.domain.enums.TaskType;
import lombok.Data;

@Data
public class FrontierBatchUpsertRequest {
  private String portal;
  private List<Resource> resources;

  @Data
  public static class Resource {
    private TaskType task_type;
    private Segment segment = Segment.unknown;
    private String url;
    private Integer priority; // optional; default 5
    private String dedupe_key; // optional
  }
}
