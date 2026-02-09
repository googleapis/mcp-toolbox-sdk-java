package com.google.cloud.mcp.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.cloud.mcp.McpToolboxClient;
import com.google.cloud.mcp.Tool;
import com.google.cloud.mcp.ToolDefinition;
import com.google.cloud.mcp.ToolResult;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class McpToolboxClientE2ETest {

  @RegisterExtension static ToolboxE2ESetup server = new ToolboxE2ESetup();

  private McpToolboxClient client;

  @BeforeEach
  void setUp() {
    client = McpToolboxClient.builder().baseUrl(server.getBaseUrl()).build();
  }

  // --- TestBasicE2E ---

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
  }

  @Test
  void testRunTool() {
    Tool tool = client.loadTool("get-n-rows").join();
    ToolResult result = tool.execute(Map.of("num_rows", "2")).join();

    assertFalse(result.isError());
    String output = result.content().get(0).text();
    assertTrue(output.contains("row1"));
    assertTrue(output.contains("row2"));
    assertFalse(output.contains("row3"));
  }

  @Test
  void testRunToolMissingParams() {
    Tool tool = client.loadTool("get-n-rows").join();
    assertThrows(IllegalArgumentException.class, () -> tool.execute(Map.of()));
  }

  @Test
  void testRunToolWrongParamType() {
    Tool tool = client.loadTool("get-n-rows").join();
    assertThrows(IllegalArgumentException.class, () -> tool.execute(Map.of("num_rows", 2)));
  }

  // --- TestBindParams ---

  @Test
  void testBindParams() {
    Tool tool = client.loadTool("get-n-rows").join();
    Tool boundTool = tool.bindParam("num_rows", "3");

    ToolResult result = boundTool.execute(Map.of()).join();
    String output = result.content().get(0).text();

    assertTrue(output.contains("row1"));
    assertTrue(output.contains("row2"));
    assertTrue(output.contains("row3"));
    assertFalse(output.contains("row4"));
  }

  @Test
  void testBindParamsCallable() {
    Tool tool = client.loadTool("get-n-rows").join();
    Tool boundTool = tool.bindParam("num_rows", () -> "3");

    ToolResult result = boundTool.execute(Map.of()).join();
    String output = result.content().get(0).text();

    assertTrue(output.contains("row1"));
    assertTrue(output.contains("row2"));
    assertTrue(output.contains("row3"));
    assertFalse(output.contains("row4"));
  }

  // --- TestAuth ---

  @Test
  void testRunToolAuth() {
    Tool tool = client.loadTool("get-row-by-id-auth").join();
    tool.addAuthTokenGetter(
        "my-test-auth", () -> CompletableFuture.completedFuture(server.getAuthToken1()));

    ToolResult result = tool.execute(Map.of("id", "2")).join();
    String output = result.content().get(0).text();
    assertTrue(output.contains("row2"));
  }

  @Test
  void testRunToolNoAuth() {
    Tool tool = client.loadTool("get-row-by-id-auth").join();

    ExecutionException ex =
        assertThrows(ExecutionException.class, () -> tool.execute(Map.of("id", "2")).join());
  }

  @Test
  void testRunToolWrongAuth() {
    Tool tool = client.loadTool("get-row-by-id-auth").join();

    tool.addAuthTokenGetter(
        "my-test-auth", () -> CompletableFuture.completedFuture(server.getAuthToken2()));

    ExecutionException ex =
        assertThrows(ExecutionException.class, () -> tool.execute(Map.of("id", "2")).join());
  }

  // --- TestOptionalParams ---

  @Test
  void testRunToolWithOptionalParamsOmitted() {
    Tool tool = client.loadTool("search-rows").join();
    ToolResult result = tool.execute(Map.of("email", "twishabansal@google.com")).join();
    String output = result.content().get(0).text();
    assertTrue(output.contains("twishabansal@google.com"));
    assertTrue(output.contains("row2"));
  }

  @Test
  void testRunToolWithOptionalDataProvided() {
    Tool tool = client.loadTool("search-rows").join();
    ToolResult result =
        tool.execute(Map.of("email", "twishabansal@google.com", "data", "row3")).join();
    String output = result.content().get(0).text();
    assertTrue(output.contains("row3"));
  }

  // --- TestMapParams ---

  @Test
  void testRunToolWithMapParams() {
    Tool tool = client.loadTool("process-data").join();
    ToolResult result =
        tool.execute(
                Map.of(
                    "execution_context", Map.of("env", "prod", "id", 1234, "user", 1234.5),
                    "user_scores", Map.of("user1", 100, "user2", 200),
                    "feature_flags", Map.of("new_feature", true)))
            .join();

    String output = result.content().get(0).text();
    assertTrue(output.contains("\"env\":\"prod\""));
    assertTrue(output.contains("\"user1\":100"));
    assertTrue(output.contains("\"new_feature\":true"));
  }
}
