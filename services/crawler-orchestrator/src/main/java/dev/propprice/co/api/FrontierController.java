package dev.propprice.co.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.propprice.co.api.dto.FrontierBatchUpsertRequest;
import dev.propprice.co.api.dto.FrontierBatchUpsertResponse;
import dev.propprice.co.app.FrontierService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/frontier")
@RequiredArgsConstructor
public class FrontierController {
  private final FrontierService service;

  @PostMapping("/batch-upsert")
  public ResponseEntity<FrontierBatchUpsertResponse> upsert(@RequestBody FrontierBatchUpsertRequest req) {
    int n = service.batchUpsert(req);
    return ResponseEntity.ok(new FrontierBatchUpsertResponse(n));
  }
}
