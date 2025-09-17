package dev.propprice.co.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FrontierBatchUpsertResponse {
  private int upserted;
}
