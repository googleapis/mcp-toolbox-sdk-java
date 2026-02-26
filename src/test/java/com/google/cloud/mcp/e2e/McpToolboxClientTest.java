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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.cloud.mcp.McpToolboxClient;
import com.google.cloud.mcp.Tool;
import com.google.cloud.mcp.ToolDefinition;
import com.google.cloud.mcp.ToolResult;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
    assertTrue(tools.containsKey("get-row-by-content-auth"));
    assertTrue(tools.containsKey("get-row-by-email-auth"));
    assertTrue(tools.containsKey("get-row-by-id-auth"));
    assertTrue(tools.containsKey("get-row-by-id"));
    assertTrue(tools.containsKey("get-n-rows"));
    assertTrue(tools.containsKey("search-rows"));
    assertTrue(tools.containsKey("process-data"));
  }

  @Test
  void testRunTool() {
    Tool tool = client.loadTool("get-n-rows").join();
    ToolResult result = tool.execute(Map.of("num_rows", "2")).join();

    if (result.isError()) {
      System.out.println("ERROR OUTPUT: " + result.text());
    }

    assertFalse(result.isError(), "Expected successful result, but got error: " + result.text());
    String output = result.text();
    assertTrue(output.contains("row1"), "Output didn't contain row1. Actual output: " + output);
    assertTrue(output.contains("row2"));
    assertFalse(output.contains("row3"));
  }

  // --- TestBindParams ---

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

  // --- TestAuth ---

  @Test
  void testRunToolAuth() {
    Tool tool = client.loadTool("get-row-by-id-auth").join();
    tool.addAuthTokenGetter(
        "my-test-auth", () -> CompletableFuture.completedFuture(server.getAuthToken1()));

    ToolResult result = tool.execute(Map.of("id", "2")).join();
    assertFalse(result.isError());
    String output = result.text();
    assertTrue(output.contains("row2"));
  }

  @Test
  void testRunToolWrongAuth() {
    Tool tool = client.loadTool("get-row-by-id-auth").join();

    tool.addAuthTokenGetter(
        "my-test-auth", () -> CompletableFuture.completedFuture(server.getAuthToken2()));

    ToolResult result = tool.execute(Map.of("id", "2")).join();
    assertTrue(result.isError(), "Expected error for wrong auth. Actual output: " + result.text());
    assertTrue(result.text().contains("not authorized"));
  }

  @Test
  void testRunToolParamAuth() {
    Tool tool = client.loadTool("get-row-by-email-auth").join();
    tool.addAuthTokenGetter(
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
    Tool tool = client.loadTool("get-row-by-content-auth").join();
    tool.addAuthTokenGetter(
        "my-test-auth", () -> CompletableFuture.completedFuture(server.getAuthToken1()));

    ToolResult result = tool.execute(Map.of()).join();
    assertTrue(result.isError());
    assertTrue(result.text().contains("no field named row_data"));
  }
}
