package dev.propprice.co.schema;

import com.networknt.schema.JsonSchema;

public final class Schemas {
  private Schemas() {
  }

  public static final JsonSchema JOB_DISPATCHED_V1 = SchemaValidator.load("schemas/acq.job.dispatched.v1.schema.json");
  public static final JsonSchema RAW_PAGE_V1 = SchemaValidator.load("schemas/acq.raw.page.v1.schema.json");
}
