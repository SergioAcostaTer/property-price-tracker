package dev.propprice.co.schema;

import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

public final class SchemaValidator {
  private static final ObjectMapper OM = new ObjectMapper();
  private static final JsonSchemaFactory FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

  private SchemaValidator() {
  }

  public static JsonSchema load(String classpath) {
    try (InputStream is = SchemaValidator.class.getClassLoader().getResourceAsStream(classpath)) {
      if (is == null)
        throw new IllegalArgumentException("Schema not found on classpath: " + classpath);
      JsonNode schemaNode = OM.readTree(is);
      return FACTORY.getSchema(schemaNode);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static void validate(JsonSchema schema, JsonNode instance) {
    Set<ValidationMessage> errors = schema.validate(instance);
    if (!errors.isEmpty()) {
      String msg = errors.stream().map(ValidationMessage::getMessage).collect(Collectors.joining("; "));
      throw new IllegalArgumentException("JSON schema validation failed: " + msg);
    }
  }
}
