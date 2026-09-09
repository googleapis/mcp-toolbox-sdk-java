/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.mcp.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.mcp.McpToolboxClient;
import com.google.cloud.mcp.ProtocolVersion;
import com.google.cloud.mcp.tool.Tool;
import com.google.cloud.mcp.tool.ToolDefinition;
import com.google.cloud.mcp.tool.ToolResult;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@Timeout(value = 60, unit = TimeUnit.SECONDS)
class McpToolboxClientE2ETest {

  @RegisterExtension static ToolboxE2ESetup server = new ToolboxE2ESetup();

  private McpToolboxClient client;

  @BeforeEach
  void setUp() {
    client = McpToolboxClient.builder().baseUrl(server.getBaseUrl()).build();
  }

  // --- Toolset Loading & Error Tests ---

  @Test
  void testLoadToolsetSpecific() {
    Map<String, ToolDefinition> tools1 = client.loadToolset("my-toolset").join();
    assertEquals(1, tools1.size());
    assertTrue(tools1.containsKey("get-row-by-id"));

    Map<String, ToolDefinition> tools2 = client.loadToolset("my-toolset-2").join();
    assertEquals(2, tools2.size());
    assertTrue(tools2.containsKey("get-n-rows"));
    assertTrue(tools2.containsKey("get-row-by-id"));
  }

  @Test
  void testLoadToolsetDefault() {
    Map<String, ToolDefinition> tools = client.loadToolset().join();
    assertEquals(7, tools.size());
    assertTrue(tools.containsKey("get-row-by-content-auth"));
    assertTrue(tools.containsKey("get-row-by-email-auth"));
    assertTrue(tools.containsKey("get-row-by-id-auth"));
    assertTrue(tools.containsKey("get-row-by-id"));
    assertTrue(tools.containsKey("get-n-rows"));
    assertTrue(tools.containsKey("search-rows"));
    assertTrue(tools.containsKey("process-data"));
  }

  @Test
  void testLoadNonExistentToolset() {
    CompletionException ex =
        assertThrows(
            CompletionException.class,
            () -> {
              client.loadToolset("non-existent-toolset").join();
            });
    assertNotNull(ex.getCause());
    assertTrue(
        ex.getCause().getMessage().contains("toolset does not exist")
            || ex.getCause().getMessage().contains("non-existent-toolset")
            || ex.getCause().getMessage().contains("Toolset not found"),
        "Unexpected cause: " + ex.getCause().getMessage());
  }

  @Test
  void testLoadNonExistentTool() {
    CompletionException ex =
        assertThrows(
            CompletionException.class,
            () -> {
              client.loadTool("non-existent-tool").join();
            });
    assertNotNull(ex.getCause());
    assertTrue(
        ex.getCause().getMessage().contains("Tool not found: non-existent-tool"),
        "Unexpected cause: " + ex.getCause().getMessage());
  }

  // --- Tool Invocation & Argument Validations ---

  @Test
  void testRunTool() {
    Tool tool = client.loadTool("get-n-rows").join();
    ToolResult result = tool.execute(Map.of("num_rows", "2")).join();

    assertFalse(result.isError(), "Expected successful result, but got error: " + result.text());
    String output = result.text();
    assertTrue(output.contains("row1"), "Output didn't contain row1. Actual output: " + output);
    assertTrue(output.contains("row2"));
    assertFalse(output.contains("row3"));
  }

  @Test
  void testRunToolMissingRequiredParams() {
    Tool tool = client.loadTool("get-n-rows").join();
    CompletionException ex =
        assertThrows(
            CompletionException.class,
            () -> {
              tool.execute(Map.of()).join();
            });
    assertNotNull(ex.getCause());
    assertTrue(
        ex.getCause() instanceof IllegalArgumentException,
        "Expected IllegalArgumentException but got: " + ex.getCause().getClass().getName());
    assertTrue(
        ex.getCause().getMessage().contains("Missing required parameter 'num_rows'"),
        "Unexpected message: " + ex.getCause().getMessage());
  }

  @Test
  void testRunToolWrongParamType() {
    Tool tool = client.loadTool("get-n-rows").join();
    CompletionException ex =
        assertThrows(
            CompletionException.class,
            () -> {
              tool.execute(Map.of("num_rows", 2)).join();
            });
    assertNotNull(ex.getCause());
    assertTrue(
        ex.getCause() instanceof IllegalArgumentException,
        "Expected IllegalArgumentException but got: " + ex.getCause().getClass().getName());
    assertTrue(
        ex.getCause().getMessage().contains("expected type 'string'"),
        "Unexpected message: " + ex.getCause().getMessage());
  }

  // --- Parameter Binding & Schema Pruning ---

  @Test
  void testBindParams() {
    Tool tool = client.loadTool("get-n-rows").join();
    Tool boundTool = tool.bindParam("num_rows", "3");

    ToolResult result = boundTool.execute(Map.of()).join();
    String output = result.text();

    assertTrue(output.contains("row1"), "Actual output: " + output);
    assertTrue(output.contains("row2"));
    assertTrue(output.contains("row3"));
    assertFalse(output.contains("row4"));
  }

  @Test
  void testBindParamsCallable() {
    Tool tool = client.loadTool("get-n-rows").join();
    Tool boundTool = tool.bindParam("num_rows", () -> "3");

    ToolResult result = boundTool.execute(Map.of()).join();
    String output = result.text();

    assertTrue(output.contains("row1"), "Actual output: " + output);
    assertTrue(output.contains("row2"));
    assertTrue(output.contains("row3"));
    assertFalse(output.contains("row4"));
  }

  @Test
  void testBoundParamPruningSchema() {
    Tool tool = client.loadTool("get-n-rows").join();
    boolean hadParam =
        tool.definition().parameters() != null
            && tool.definition().parameters().stream().anyMatch(p -> "num_rows".equals(p.name()));
    assertTrue(hadParam, "Original tool definition should have 'num_rows' parameter");

    Tool boundTool = tool.bindParam("num_rows", "3");
    boolean hasParamAfter =
        boundTool.definition().parameters() != null
            && boundTool.definition().parameters().stream()
                .anyMatch(p -> "num_rows".equals(p.name()));
    assertFalse(hasParamAfter, "Bound parameter 'num_rows' must be pruned from definition schema");

    boolean originalStillHasParam =
        tool.definition().parameters() != null
            && tool.definition().parameters().stream().anyMatch(p -> "num_rows".equals(p.name()));
    assertTrue(
        originalStillHasParam,
        "Original tool definition must still contain 'num_rows' to ensure immutability");
  }

  // --- Authentication & Claim Injections ---

  @Test
  void testRunToolAuth() {
    Tool tool =
        client
            .loadTool("get-row-by-id-auth")
            .join()
            .addAuthTokenGetter(
                "my-test-auth", () -> CompletableFuture.completedFuture(server.getAuthToken1()));

    ToolResult result = tool.execute(Map.of("id", "2")).join();
    assertFalse(result.isError());
    String output = result.text();
    assertTrue(output.contains("row2"));
  }

  @Test
  void testRunToolWrongAuth() {
    Tool tool =
        client
            .loadTool("get-row-by-id-auth")
            .join()
            .addAuthTokenGetter(
                "my-test-auth", () -> CompletableFuture.completedFuture(server.getAuthToken2()));

    ToolResult result = tool.execute(Map.of("id", "2")).join();
    assertTrue(result.isError(), "Expected error for wrong auth. Actual output: " + result.text());
    assertTrue(
        result.text().toLowerCase().contains("unauthorized"), "Actual output: " + result.text());
  }

  @Test
  void testRunToolAuthWithoutProvidingAuth() {
    Tool tool = client.loadTool("get-row-by-id-auth").join();
    // Running authenticated tool without adding auth token getter
    ToolResult result = tool.execute(Map.of("id", "2")).join();
    assertTrue(
        result.isError(),
        "Expected error when invoking tool without auth token. Output: " + result.text());
    assertTrue(
        result.text().toLowerCase().contains("unauthorized") || result.text().contains("401"),
        "Expected unauthorized/401 error message. Actual output: " + result.text());
  }

  @Test
  void testRunToolParamAuth() {
    Tool tool =
        client
            .loadTool("get-row-by-email-auth")
            .join()
            .addAuthTokenGetter(
                "my-test-auth", () -> CompletableFuture.completedFuture(server.getAuthToken1()));

    ToolResult result = tool.execute(Map.of()).join();
    assertFalse(result.isError(), "Expected success but got error: " + result.text());
    String output = result.text();
    assertTrue(output.contains("row4"), "Actual output: " + output);
    assertTrue(output.contains("row5"));
    assertTrue(output.contains("row6"));
  }

  @Test
  void testRunToolParamAuthNoField() {
    Tool tool =
        client
            .loadTool("get-row-by-content-auth")
            .join()
            .addAuthTokenGetter(
                "my-test-auth", () -> CompletableFuture.completedFuture(server.getAuthToken1()));

    ToolResult result = tool.execute(Map.of()).join();
    assertTrue(result.isError());
    assertTrue(result.text().contains("no field named row_data"));
  }

  @Test
  void testRunToolWithFailingTokenSupplier() {
    Tool tool =
        client
            .loadTool("get-row-by-id-auth")
            .join()
            .addAuthTokenGetter(
                "my-test-auth",
                () -> CompletableFuture.failedFuture(new RuntimeException("Token unavailable")));

    CompletionException ex =
        assertThrows(
            CompletionException.class,
            () -> {
              tool.execute(Map.of("id", "2")).join();
            });
    assertNotNull(ex.getCause());
    assertTrue(
        ex.getCause().getMessage().contains("Token unavailable"),
        "Unexpected cause: " + ex.getCause().getMessage());
  }

  // =========================================================================
  // 5. Optional & Default Parameters Suite (search-rows)
  // =========================================================================

  @Test
  void testSearchRowsDefinitionSchema() {
    Tool tool = client.loadTool("search-rows").join();
    assertEquals("search-rows", tool.name());
    assertNotNull(tool.definition());

    boolean hasEmail = false;
    boolean hasData = false;
    boolean hasId = false;

    if (tool.definition().parameters() != null) {
      for (ToolDefinition.Parameter p : tool.definition().parameters()) {
        if ("email".equals(p.name())) {
          hasEmail = true;
          assertTrue(p.required(), "Parameter 'email' should be required");
          assertEquals("string", p.type());
        } else if ("data".equals(p.name())) {
          hasData = true;
          assertFalse(p.required(), "Parameter 'data' should be optional");
          assertEquals("string", p.type());
        } else if ("id".equals(p.name())) {
          hasId = true;
          assertFalse(p.required(), "Parameter 'id' should be optional");
          assertEquals("integer", p.type());
        }
      }
    }
    assertTrue(hasEmail, "Missing required parameter 'email' in definition");
    assertTrue(hasData, "Missing optional parameter 'data' in definition");
    assertTrue(hasId, "Missing optional parameter 'id' in definition");
  }

  @Test
  void testSearchRowsOmittingOptionals() {
    Tool tool = client.loadTool("search-rows").join();
    ToolResult result = tool.execute(Map.of("email", "twishabansal@google.com")).join();

    assertFalse(result.isError(), "Expected success: " + result.text());
    String output = result.text();
    assertTrue(output.contains("twishabansal@google.com"), "Output: " + output);
    assertTrue(output.contains("row2"), "Output: " + output);
    assertFalse(output.contains("row1"), "Output should not contain row1: " + output);
    assertFalse(output.contains("row3"), "Output should not contain row3: " + output);
  }

  @Test
  void testSearchRowsWithAllParamsProvided() {
    Tool tool = client.loadTool("search-rows").join();
    Map<String, Object> args = new HashMap<>();
    args.put("email", "twishabansal@google.com");
    args.put("data", "row3");
    args.put("id", 3L);

    ToolResult result = tool.execute(args).join();
    assertFalse(result.isError(), "Expected success: " + result.text());
    String output = result.text();
    assertTrue(output.contains("twishabansal@google.com"));
    assertTrue(output.contains("row3"));
    assertFalse(output.contains("row2"));
  }

  @Test
  void testSearchRowsWithNullOptionalParams() {
    Tool tool = client.loadTool("search-rows").join();
    Map<String, Object> args = new HashMap<>();
    args.put("email", "twishabansal@google.com");
    args.put("data", null);
    args.put("id", null);

    ToolResult result = tool.execute(args).join();
    assertFalse(result.isError(), "Expected success: " + result.text());
    String output = result.text();
    assertTrue(output.contains("twishabansal@google.com"));
    assertTrue(output.contains("row2"));
  }

  @Test
  void testSearchRowsWithNullRequiredParam() {
    Tool tool = client.loadTool("search-rows").join();
    Map<String, Object> args = new HashMap<>();
    args.put("email", null);
    args.put("data", "row3");

    CompletionException ex =
        assertThrows(
            CompletionException.class,
            () -> {
              tool.execute(args).join();
            });
    assertNotNull(ex.getCause());
    assertTrue(ex.getCause() instanceof IllegalArgumentException);
    assertTrue(ex.getCause().getMessage().contains("Missing required parameter 'email'"));
  }

  @Test
  void testSearchRowsWithWrongParamType() {
    Tool tool = client.loadTool("search-rows").join();
    Map<String, Object> args = new HashMap<>();
    args.put("email", "twishabansal@google.com");
    args.put("id", "not-an-integer");

    CompletionException ex =
        assertThrows(
            CompletionException.class,
            () -> {
              tool.execute(args).join();
            });
    assertNotNull(ex.getCause());
    assertTrue(ex.getCause() instanceof IllegalArgumentException);
    assertTrue(ex.getCause().getMessage().contains("expected type 'integer' but got 'String'"));
  }

  @Test
  void testSearchRowsMissingRequiredParam() {
    Tool tool = client.loadTool("search-rows").join();
    CompletionException ex =
        assertThrows(
            CompletionException.class,
            () -> {
              tool.execute(Map.of("data", "row3")).join();
            });
    assertNotNull(ex.getCause());
    assertTrue(
        ex.getCause() instanceof IllegalArgumentException,
        "Expected IllegalArgumentException but got: " + ex.getCause().getClass().getName());
    assertTrue(
        ex.getCause().getMessage().contains("Missing required parameter 'email'"),
        "Unexpected message: " + ex.getCause().getMessage());
  }

  @Test
  void testSearchRowsNonMatchingData() {
    Tool tool = client.loadTool("search-rows").join();
    Map<String, Object> args = new HashMap<>();
    args.put("email", "twishabansal@google.com");
    args.put("id", 3L);
    args.put("data", "row4");

    ToolResult result = tool.execute(args).join();
    assertFalse(result.isError(), "Expected success: " + result.text());
    String output = result.text().trim();
    assertTrue(
        output.isEmpty() || "null".equals(output),
        "Expected empty or 'null' response for non-matching data, got: " + output);
    assertFalse(output.contains("row1"));
    assertFalse(output.contains("row2"));
    assertFalse(output.contains("row3"));
  }

  // =========================================================================
  // 6. Map & Structured Payloads Suite (process-data)
  // =========================================================================

  @Test
  void testProcessDataDefinitionSchema() {
    Tool tool = client.loadTool("process-data").join();
    assertEquals("process-data", tool.name());
    assertNotNull(tool.definition());

    boolean hasExecutionContext = false;
    boolean hasUserScores = false;
    boolean hasFeatureFlags = false;

    if (tool.definition().parameters() != null) {
      for (ToolDefinition.Parameter p : tool.definition().parameters()) {
        if ("execution_context".equals(p.name())) {
          hasExecutionContext = true;
          assertTrue(p.required(), "Parameter 'execution_context' should be required");
          assertNotNull(p.type());
          assertTrue(
              "object".equalsIgnoreCase(p.type()),
              "Parameter 'execution_context' type should be 'object', got: " + p.type());
        } else if ("user_scores".equals(p.name())) {
          hasUserScores = true;
          assertTrue(p.required(), "Parameter 'user_scores' should be required");
          assertNotNull(p.type());
          assertTrue(
              "object".equalsIgnoreCase(p.type()),
              "Parameter 'user_scores' type should be 'object', got: " + p.type());
        } else if ("feature_flags".equals(p.name())) {
          hasFeatureFlags = true;
          assertFalse(p.required(), "Parameter 'feature_flags' should be optional");
          assertNotNull(p.type());
          assertTrue(
              "object".equalsIgnoreCase(p.type()),
              "Parameter 'feature_flags' type should be 'object', got: " + p.type());
        }
      }
    }
    assertTrue(hasExecutionContext, "Missing required parameter 'execution_context' in definition");
    assertTrue(hasUserScores, "Missing required parameter 'user_scores' in definition");
    assertTrue(hasFeatureFlags, "Missing optional parameter 'feature_flags' in definition");
  }

  @Test
  void testProcessDataWithMapParams() throws JsonProcessingException {
    Tool tool = client.loadTool("process-data").join();
    Map<String, Object> execCtx = new LinkedHashMap<>();
    execCtx.put("env", "prod");
    execCtx.put("id", 1234);
    execCtx.put("user", 1234.5);

    Map<String, Object> userScores = new LinkedHashMap<>();
    userScores.put("user1", 100);
    userScores.put("user2", 200);

    Map<String, Object> featureFlags = new LinkedHashMap<>();
    featureFlags.put("new_feature", true);

    Map<String, Object> args = new LinkedHashMap<>();
    args.put("execution_context", execCtx);
    args.put("user_scores", userScores);
    args.put("feature_flags", featureFlags);

    ToolResult result = tool.execute(args).join();

    assertFalse(result.isError(), "Expected success: " + result.text());
    String output = result.text();
    JsonNode root = new ObjectMapper().readTree(output);
    JsonNode node = root.isArray() ? root.get(0) : root;
    JsonNode dataNode = node.has("jsonb_build_object") ? node.get("jsonb_build_object") : node;

    assertEquals("prod", dataNode.path("execution_context").path("env").asText());
    assertEquals(1234, dataNode.path("execution_context").path("id").asInt());
    assertEquals(1234.5, dataNode.path("execution_context").path("user").asDouble(), 0.001);
    assertEquals(100, dataNode.path("user_scores").path("user1").asInt());
    assertEquals(200, dataNode.path("user_scores").path("user2").asInt());
    assertTrue(dataNode.path("feature_flags").path("new_feature").asBoolean());
  }

  @Test
  void testProcessDataOmittingOptionalMap() throws JsonProcessingException {
    Tool tool = client.loadTool("process-data").join();
    Map<String, Object> execCtx = new LinkedHashMap<>();
    execCtx.put("env", "dev");

    Map<String, Object> userScores = new LinkedHashMap<>();
    userScores.put("user3", 300);

    Map<String, Object> args = new LinkedHashMap<>();
    args.put("execution_context", execCtx);
    args.put("user_scores", userScores);

    ToolResult result = tool.execute(args).join();

    assertFalse(result.isError(), "Expected success: " + result.text());
    String output = result.text();
    JsonNode root = new ObjectMapper().readTree(output);
    JsonNode node = root.isArray() ? root.get(0) : root;
    JsonNode dataNode = node.has("jsonb_build_object") ? node.get("jsonb_build_object") : node;

    assertEquals("dev", dataNode.path("execution_context").path("env").asText());
    assertEquals(300, dataNode.path("user_scores").path("user3").asInt());
    assertTrue(
        dataNode.path("feature_flags").isNull() || dataNode.path("feature_flags").isMissingNode(),
        "Expected null feature_flags: " + output);
  }

  @Test
  void testProcessDataWithWrongMapValueType() {
    Tool tool = client.loadTool("process-data").join();
    Map<String, Object> execCtx = new LinkedHashMap<>();
    execCtx.put("env", "staging");

    CompletionException ex =
        assertThrows(
            CompletionException.class,
            () -> {
              tool.execute(Map.of("execution_context", execCtx, "user_scores", "not-a-map")).join();
            });
    assertNotNull(ex.getCause());
    assertTrue(ex.getCause() instanceof IllegalArgumentException);
    assertTrue(ex.getCause().getMessage().contains("expected type 'object' but got 'String'"));
  }

  // =========================================================================
  // 7. Transport Headers & Protocol Suite
  // =========================================================================

  @Test
  void testClientWithCustomHeaders() {
    Map<String, String> customHeaders =
        Map.of("X-Custom-Client-Header", "SDK-Java-Client", "X-Integration-Source", "TestSuite");

    McpToolboxClient customHeaderClient =
        McpToolboxClient.builder().baseUrl(server.getBaseUrl()).headers(customHeaders).build();

    Tool tool = customHeaderClient.loadTool("get-n-rows").join();
    assertNotNull(tool);
    ToolResult result = tool.execute(Map.of("num_rows", "1")).join();
    assertFalse(result.isError(), "Execution failed: " + result.text());
    assertTrue(result.text().contains("row1"));
  }

  @ParameterizedTest
  @EnumSource(
      value = ProtocolVersion.class,
      names = {
        "VERSION_2024_11_05",
        "VERSION_2025_03_26",
        "VERSION_2025_06_18",
        "VERSION_2025_11_25"
      })
  void testClientWithExplicitProtocolVersions(ProtocolVersion version) {
    McpToolboxClient versionedClient =
        McpToolboxClient.builder().baseUrl(server.getBaseUrl()).protocolVersion(version).build();

    Tool tool = versionedClient.loadTool("get-n-rows").join();
    assertNotNull(tool);
    ToolResult result = tool.execute(Map.of("num_rows", "1")).join();
    assertFalse(
        result.isError(), "Execution failed for protocol " + version + ": " + result.text());
    assertTrue(result.text().contains("row1"), "Expected row1 for protocol " + version);
  }
}
