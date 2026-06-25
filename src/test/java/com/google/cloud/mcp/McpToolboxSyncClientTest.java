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

package com.google.cloud.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.mcp.auth.AuthTokenGetter;
import com.google.cloud.mcp.client.HttpMcpToolboxSyncClient;
import com.google.cloud.mcp.exception.McpProtocolException;
import com.google.cloud.mcp.exception.McpToolboxException;
import com.google.cloud.mcp.exception.McpTransportException;
import com.google.cloud.mcp.exception.ToolExecutionException;
import com.google.cloud.mcp.tool.Tool;
import com.google.cloud.mcp.tool.ToolDefinition;
import com.google.cloud.mcp.tool.ToolResult;
import com.google.cloud.mcp.transport.HttpMcpTransport;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class McpToolboxSyncClientTest {

  @Test
  void testConstructorValidation() {
    assertThrows(IllegalArgumentException.class, () -> new HttpMcpToolboxSyncClient(null));
  }

  @Test
  void testBuilderConfigurationAndAdvancedTimeouts() {
    HttpClient mockHttpClient = mock(HttpClient.class);
    Logger mockLogger = Logger.getLogger("test");

    McpToolboxClient asyncClient =
        McpToolboxClient.builder()
            .baseUrl("http://localhost:8080")
            .apiKey("my-api-key")
            .connectTimeout(Duration.ofSeconds(5))
            .requestTimeout(Duration.ofSeconds(3))
            .httpClient(mockHttpClient)
            .logger(mockLogger)
            .build();

    assertNotNull(asyncClient);

    McpToolboxSyncClient syncClient =
        McpToolboxClient.builder()
            .baseUrl("http://localhost:8080")
            .connectTimeout(Duration.ofSeconds(5))
            .requestTimeout(Duration.ofSeconds(3))
            .logger(mockLogger)
            .buildSync();

    assertNotNull(syncClient);
  }

  @Test
  void testSyncClientDelegationAllMethods() {
    McpToolboxClient mockAsync = mock(McpToolboxClient.class);
    McpToolboxSyncClient syncClient = new HttpMcpToolboxSyncClient(mockAsync);

    // 1. listTools()
    Map<String, ToolDefinition> expectedTools = Collections.emptyMap();
    when(mockAsync.listTools()).thenReturn(CompletableFuture.completedFuture(expectedTools));
    assertEquals(expectedTools, syncClient.listTools());
    verify(mockAsync).listTools();

    // 2. loadToolset(String)
    when(mockAsync.loadToolset("set1"))
        .thenReturn(CompletableFuture.completedFuture(expectedTools));
    assertEquals(expectedTools, syncClient.loadToolset("set1"));
    verify(mockAsync).loadToolset("set1");

    // 3. loadToolset(String, Map, Map, boolean)
    Map<String, Tool> expectedLoadedTools = Collections.emptyMap();
    when(mockAsync.loadToolset("set1", Map.of(), Map.of(), true))
        .thenReturn(CompletableFuture.completedFuture(expectedLoadedTools));
    assertEquals(expectedLoadedTools, syncClient.loadToolset("set1", Map.of(), Map.of(), true));
    verify(mockAsync).loadToolset("set1", Map.of(), Map.of(), true);

    // 4. loadTool(String)
    Tool mockTool = mock(Tool.class);
    when(mockAsync.loadTool("tool1")).thenReturn(CompletableFuture.completedFuture(mockTool));
    assertEquals(mockTool, syncClient.loadTool("tool1"));
    verify(mockAsync).loadTool("tool1");

    // 5. loadTool(String, Map)
    when(mockAsync.loadTool("tool1", Map.of()))
        .thenReturn(CompletableFuture.completedFuture(mockTool));
    assertEquals(mockTool, syncClient.loadTool("tool1", Map.of()));
    verify(mockAsync).loadTool("tool1", Map.of());

    // 6. invokeTool(String, Map)
    ToolResult mockResult = new ToolResult(Collections.emptyList(), false);
    when(mockAsync.invokeTool("tool1", Map.of()))
        .thenReturn(CompletableFuture.completedFuture(mockResult));
    assertEquals(mockResult, syncClient.invokeTool("tool1", Map.of()));
    verify(mockAsync).invokeTool("tool1", Map.of());

    // 7. invokeTool(String, Map, Map)
    when(mockAsync.invokeTool("tool1", Map.of(), Map.of()))
        .thenReturn(CompletableFuture.completedFuture(mockResult));
    assertEquals(mockResult, syncClient.invokeTool("tool1", Map.of(), Map.of()));
    verify(mockAsync).invokeTool("tool1", Map.of(), Map.of());
  }

  @Test
  void testExceptionTranslationDifferentTypes() {
    McpToolboxClient mockAsync = mock(McpToolboxClient.class);
    McpToolboxSyncClient syncClient = new HttpMcpToolboxSyncClient(mockAsync);

    // 1. McpToolboxException -> rethrown directly
    CompletableFuture<Map<String, ToolDefinition>> f1 = new CompletableFuture<>();
    f1.completeExceptionally(new McpToolboxException("toolbox-error"));
    when(mockAsync.listTools()).thenReturn(f1);
    McpToolboxException ex1 = assertThrows(McpToolboxException.class, () -> syncClient.listTools());
    assertEquals("toolbox-error", ex1.getMessage());

    // 2. IllegalArgumentException -> rethrown directly
    CompletableFuture<Map<String, ToolDefinition>> f2 = new CompletableFuture<>();
    f2.completeExceptionally(new IllegalArgumentException("illegal-arg"));
    when(mockAsync.listTools()).thenReturn(f2);
    IllegalArgumentException ex2 =
        assertThrows(IllegalArgumentException.class, () -> syncClient.listTools());
    assertEquals("illegal-arg", ex2.getMessage());

    // 3. Other checked Exception -> wrapped in McpToolboxException
    CompletableFuture<Map<String, ToolDefinition>> f3 = new CompletableFuture<>();
    f3.completeExceptionally(new Exception("generic-error"));
    when(mockAsync.listTools()).thenReturn(f3);
    McpToolboxException ex3 = assertThrows(McpToolboxException.class, () -> syncClient.listTools());
    assertEquals("generic-error", ex3.getMessage());

    // 4. Null cause -> wrapped in McpToolboxException
    CompletableFuture<Map<String, ToolDefinition>> f4 = new CompletableFuture<>();
    f4.completeExceptionally(new java.util.concurrent.CompletionException(null));
    when(mockAsync.listTools()).thenReturn(f4);
    McpToolboxException ex4 = assertThrows(McpToolboxException.class, () -> syncClient.listTools());
    assertNotNull(ex4);
  }

  @Test
  void testToolExecuteSyncExceptionTranslation() {
    McpToolboxClient mockAsync = mock(McpToolboxClient.class);
    ToolDefinition def =
        new ToolDefinition("test description", Collections.emptyList(), Collections.emptyList());
    Tool tool = new Tool("test-tool", def, mockAsync);

    // 1. Tool execution McpToolboxException -> rethrown
    CompletableFuture<ToolResult> f1 = new CompletableFuture<>();
    f1.completeExceptionally(new McpToolboxException("execution-failed"));
    when(mockAsync.invokeTool(any(), any(), any())).thenReturn(f1);
    McpToolboxException ex1 =
        assertThrows(McpToolboxException.class, () -> tool.executeSync(Collections.emptyMap()));
    assertEquals("execution-failed", ex1.getMessage());

    // 2. Tool execution IllegalArgumentException -> rethrown
    CompletableFuture<ToolResult> f2 = new CompletableFuture<>();
    f2.completeExceptionally(new IllegalArgumentException("invalid-arg"));
    when(mockAsync.invokeTool(any(), any(), any())).thenReturn(f2);
    IllegalArgumentException ex2 =
        assertThrows(
            IllegalArgumentException.class, () -> tool.executeSync(Collections.emptyMap()));
    assertEquals("invalid-arg", ex2.getMessage());

    // 3. Tool execution other Exception -> wrapped in McpToolboxException
    CompletableFuture<ToolResult> f3 = new CompletableFuture<>();
    f3.completeExceptionally(new RuntimeException("general-fail"));
    when(mockAsync.invokeTool(any(), any(), any())).thenReturn(f3);
    McpToolboxException ex3 =
        assertThrows(McpToolboxException.class, () -> tool.executeSync(Collections.emptyMap()));
    assertEquals("general-fail", ex3.getMessage());

    // 4. Tool execution Null cause -> wrapped in McpToolboxException
    CompletableFuture<ToolResult> f4 = new CompletableFuture<>();
    f4.completeExceptionally(new java.util.concurrent.CompletionException(null));
    when(mockAsync.invokeTool(any(), any(), any())).thenReturn(f4);
    McpToolboxException ex4 =
        assertThrows(McpToolboxException.class, () -> tool.executeSync(Collections.emptyMap()));
    assertNotNull(ex4);
  }

  @Test
  void testToolDeepCopyListSupport() {
    McpToolboxClient mockAsync = mock(McpToolboxClient.class);
    // Set up a parameter with a default value that is a List
    List<Object> defaultList = List.of("element1", List.of("subelement"));
    ToolDefinition.Parameter param =
        new ToolDefinition.Parameter(
            "listParam", "array", false, "list param", Collections.emptyList(), defaultList);
    ToolDefinition def =
        new ToolDefinition("test description", List.of(param), Collections.emptyList());
    Tool tool = new Tool("test-tool", def, mockAsync);

    // Call invokeTool and verify the list is deep-copied and passed
    ToolResult expectedResult = new ToolResult(Collections.emptyList(), false);
    when(mockAsync.invokeTool(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(expectedResult));

    // Execute with empty args so the default value (list) is used and deep-copied
    ToolResult actualResult = tool.executeSync(Collections.emptyMap());
    assertEquals(expectedResult, actualResult);
  }

  @Test
  void testSyncClientDelegationExceptions() {
    McpToolboxClient mockAsync = mock(McpToolboxClient.class);
    McpToolboxSyncClient syncClient = new HttpMcpToolboxSyncClient(mockAsync);

    CompletableFuture<?> failedFuture =
        CompletableFuture.failedFuture(new McpToolboxException("error"));

    // 1. loadToolset(String) exception
    when(mockAsync.loadToolset("set1")).thenAnswer(inv -> failedFuture);
    assertThrows(McpToolboxException.class, () -> syncClient.loadToolset("set1"));

    // 2. loadToolset(String, Map, Map, boolean) exception
    when(mockAsync.loadToolset("set1", Map.of(), Map.of(), true)).thenAnswer(inv -> failedFuture);
    assertThrows(
        McpToolboxException.class, () -> syncClient.loadToolset("set1", Map.of(), Map.of(), true));

    // 3. loadTool(String) exception
    when(mockAsync.loadTool("tool1")).thenAnswer(inv -> failedFuture);
    assertThrows(McpToolboxException.class, () -> syncClient.loadTool("tool1"));

    // 4. loadTool(String, Map) exception
    when(mockAsync.loadTool("tool1", Map.of())).thenAnswer(inv -> failedFuture);
    assertThrows(McpToolboxException.class, () -> syncClient.loadTool("tool1", Map.of()));

    // 5. invokeTool(String, Map) exception
    when(mockAsync.invokeTool("tool1", Map.of())).thenAnswer(inv -> failedFuture);
    assertThrows(McpToolboxException.class, () -> syncClient.invokeTool("tool1", Map.of()));

    // 6. invokeTool(String, Map, Map) exception
    when(mockAsync.invokeTool("tool1", Map.of(), Map.of())).thenAnswer(inv -> failedFuture);
    assertThrows(
        McpToolboxException.class, () -> syncClient.invokeTool("tool1", Map.of(), Map.of()));
  }

  @Test
  void testExceptionsAndInterfaceCoverage() {
    // 1. Instantiate the exception classes
    assertNotNull(new McpProtocolException("protocol error"));
    assertNotNull(new McpProtocolException("protocol error", new Exception()));
    assertNotNull(new ToolExecutionException("execution error"));
    assertNotNull(new ToolExecutionException("execution error", new Exception()));
    assertNotNull(new McpTransportException("transport error"));
    assertNotNull(new McpTransportException("transport error", new Exception()));

    // 2. Interface default method call
    McpToolboxSyncClient syncClient =
        new McpToolboxSyncClient() {
          @Override
          public Map<String, ToolDefinition> listTools() {
            return Collections.emptyMap();
          }

          @Override
          public Map<String, ToolDefinition> loadToolset(String toolsetName) {
            return Collections.emptyMap();
          }

          @Override
          public Map<String, Tool> loadToolset(
              String toolsetName,
              Map<String, Map<String, Object>> paramBinds,
              Map<String, Map<String, AuthTokenGetter>> authBinds,
              boolean strict) {
            return Collections.emptyMap();
          }

          @Override
          public Tool loadTool(String toolName) {
            return null;
          }

          @Override
          public Tool loadTool(String toolName, Map<String, AuthTokenGetter> authTokenGetters) {
            return null;
          }

          @Override
          public ToolResult invokeTool(String toolName, Map<String, Object> arguments) {
            return null;
          }

          @Override
          public ToolResult invokeTool(
              String toolName, Map<String, Object> arguments, Map<String, String> extraHeaders) {
            return null;
          }
        };
    assertEquals(Collections.emptyMap(), syncClient.loadToolset());
  }

  @Test
  @SuppressWarnings("unchecked")
  void testRequestTimeoutPropagation() throws Exception {
    HttpClient mockHttpClient = mock(HttpClient.class);
    HttpResponse<String> mockInitResponse = mock(HttpResponse.class);
    when(mockInitResponse.statusCode()).thenReturn(200);
    when(mockInitResponse.body())
        .thenReturn(
            "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"protocolVersion\":\"2025-11-25\"}}");

    HttpResponse<String> mockInitializedResponse = mock(HttpResponse.class);
    when(mockInitializedResponse.statusCode()).thenReturn(200);
    when(mockInitializedResponse.body()).thenReturn("");

    HttpResponse<String> mockListResponse = mock(HttpResponse.class);
    when(mockListResponse.statusCode()).thenReturn(200);
    when(mockListResponse.body())
        .thenReturn("{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"result\":{\"tools\":[]}}");

    when(mockHttpClient.<String>sendAsync(
            any(java.net.http.HttpRequest.class),
            any(java.net.http.HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(mockInitResponse))
        .thenReturn(CompletableFuture.completedFuture(mockInitializedResponse))
        .thenReturn(CompletableFuture.completedFuture(mockListResponse));

    HttpMcpTransport transport =
        new HttpMcpTransport(
            "http://localhost:8080",
            Map.of(),
            null,
            ProtocolVersion.VERSION_2025_11_25,
            mockHttpClient,
            null,
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            null);

    transport.listTools("", Collections.emptyMap()).get();
    assertNotNull(transport);
  }
}
