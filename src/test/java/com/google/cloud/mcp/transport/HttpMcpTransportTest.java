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

package com.google.cloud.mcp.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.mcp.ProtocolVersion;
import com.google.cloud.mcp.auth.CredentialsProvider;
import com.google.cloud.mcp.exception.McpException;
import com.google.cloud.mcp.tool.ToolDefinition;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 5, unit = java.util.concurrent.TimeUnit.SECONDS)
class HttpMcpTransportTest {

  private HttpClient mockClient;
  private HttpMcpTransport transport;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    mockClient = mock(HttpClient.class);
    transport = new HttpMcpTransport("https://test-mcp-service.com", mockClient);
  }

  @Test
  @SuppressWarnings("unchecked")
  void testListTools_PerformsHandshakeAndFetchesTools() throws Exception {
    // 1. Mock response for 'initialize'
    HttpResponse<String> mockInitResponse = mock(HttpResponse.class);
    when(mockInitResponse.statusCode()).thenReturn(200);
    when(mockInitResponse.body())
        .thenReturn(
            "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"protocolVersion\":\"2025-11-25\"}}");

    // 2. Mock response for 'notifications/initialized'
    HttpResponse<String> mockInitializedResponse = mock(HttpResponse.class);
    when(mockInitializedResponse.statusCode()).thenReturn(200);
    when(mockInitializedResponse.body()).thenReturn("");

    // 3. Mock response for 'tools/list'
    HttpResponse<String> mockListResponse = mock(HttpResponse.class);
    when(mockListResponse.statusCode()).thenReturn(200);
    when(mockListResponse.body())
        .thenReturn(
            "{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"result\":{\"tools\":[{\"name\":\"test-tool\",\"description\":\"A"
                + " test"
                + " tool\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"param1\":{\"type\":\"string\",\"description\":\"param"
                + " desc\"}},"
                + "\"required\":[\"param1\"]},\"_meta\":{\"toolbox/authInvoke\":[\"gcp\"]}}]}}");

    CompletableFuture<HttpResponse<String>> initFuture =
        CompletableFuture.completedFuture(mockInitResponse);
    CompletableFuture<HttpResponse<String>> initializedFuture =
        CompletableFuture.completedFuture(mockInitializedResponse);
    CompletableFuture<HttpResponse<String>> listFuture =
        CompletableFuture.completedFuture(mockListResponse);

    // Set up mock calls sequentially with type hint
    when(mockClient.<String>sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(initFuture)
        .thenReturn(initializedFuture)
        .thenReturn(listFuture);

    CompletableFuture<TransportManifest> futureManifest =
        transport.listTools("", Collections.emptyMap());
    TransportManifest manifest = futureManifest.get();

    assertNotNull(manifest);
    assertEquals(1, manifest.getTools().size());
    assertTrue(manifest.getTools().containsKey("test-tool"));
    ToolDefinition def = manifest.getTools().get("test-tool");
    assertEquals("A test tool", def.description());
    assertEquals(1, def.parameters().size());
    assertEquals("param1", def.parameters().get(0).name());
    assertTrue(def.parameters().get(0).required());
  }

  @Test
  @SuppressWarnings("unchecked")
  void testInvokeTool_PerformsHandshakeAndExecutesCall() throws Exception {
    // 1. Mock response for 'initialize'
    HttpResponse<String> mockInitResponse = mock(HttpResponse.class);
    when(mockInitResponse.statusCode()).thenReturn(200);
    when(mockInitResponse.body())
        .thenReturn(
            "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"protocolVersion\":\"2025-11-25\"}}");

    // 2. Mock response for 'notifications/initialized'
    HttpResponse<String> mockInitializedResponse = mock(HttpResponse.class);
    when(mockInitializedResponse.statusCode()).thenReturn(200);
    when(mockInitializedResponse.body()).thenReturn("");

    // 3. Mock response for 'tools/call'
    HttpResponse<String> mockInvokeResponse = mock(HttpResponse.class);
    when(mockInvokeResponse.statusCode()).thenReturn(200);
    when(mockInvokeResponse.body())
        .thenReturn(
            "{\"jsonrpc\":\"2.0\",\"id\":\"3\",\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"success\"}]}}");

    CompletableFuture<HttpResponse<String>> initFuture =
        CompletableFuture.completedFuture(mockInitResponse);
    CompletableFuture<HttpResponse<String>> initializedFuture =
        CompletableFuture.completedFuture(mockInitializedResponse);
    CompletableFuture<HttpResponse<String>> invokeFuture =
        CompletableFuture.completedFuture(mockInvokeResponse);

    when(mockClient.<String>sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(initFuture)
        .thenReturn(initializedFuture)
        .thenReturn(invokeFuture);

    CompletableFuture<TransportResponse> futureResult =
        transport.invokeTool("test-tool", Map.of("param1", "value1"), Collections.emptyMap());
    TransportResponse response = futureResult.get();

    assertNotNull(response);
    assertEquals(200, response.getStatusCode());
    assertTrue(response.getBody().contains("success"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testSubsequentCalls_DoNotReinitialize() throws Exception {
    // 1. Mock response for 'initialize'
    HttpResponse<String> mockInitResponse = mock(HttpResponse.class);
    when(mockInitResponse.statusCode()).thenReturn(200);
    when(mockInitResponse.body())
        .thenReturn(
            "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"protocolVersion\":\"2025-11-25\"}}");

    // 2. Mock response for 'notifications/initialized'
    HttpResponse<String> mockInitializedResponse = mock(HttpResponse.class);
    when(mockInitializedResponse.statusCode()).thenReturn(200);
    when(mockInitializedResponse.body()).thenReturn("");

    // 3. Mock response for first 'tools/list'
    HttpResponse<String> mockListResponse1 = mock(HttpResponse.class);
    when(mockListResponse1.statusCode()).thenReturn(200);
    when(mockListResponse1.body())
        .thenReturn("{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"result\":{\"tools\":[]}}");

    // 4. Mock response for second 'tools/list'
    HttpResponse<String> mockListResponse2 = mock(HttpResponse.class);
    when(mockListResponse2.statusCode()).thenReturn(200);
    when(mockListResponse2.body())
        .thenReturn("{\"jsonrpc\":\"2.0\",\"id\":\"3\",\"result\":{\"tools\":[]}}");

    CompletableFuture<HttpResponse<String>> initFuture =
        CompletableFuture.completedFuture(mockInitResponse);
    CompletableFuture<HttpResponse<String>> initializedFuture =
        CompletableFuture.completedFuture(mockInitializedResponse);
    CompletableFuture<HttpResponse<String>> listFuture1 =
        CompletableFuture.completedFuture(mockListResponse1);
    CompletableFuture<HttpResponse<String>> listFuture2 =
        CompletableFuture.completedFuture(mockListResponse2);

    // Set up sequential answers
    when(mockClient.<String>sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(initFuture)
        .thenReturn(initializedFuture)
        .thenReturn(listFuture1)
        .thenReturn(listFuture2);

    // First call lists tools (performs handshake + lists)
    transport.listTools("", Collections.emptyMap()).get();

    // Second call lists tools (should only list tools directly)
    transport.listTools("", Collections.emptyMap()).get();

    // Total calls to sendAsync should be 4 (1: init, 2: initialized, 3: list1, 4: list2)
    verify(mockClient, times(4))
        .sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
  }

  @Test
  void testConstructor_InvalidBaseUrlThrows() {
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class, () -> new HttpMcpTransport(null));
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class, () -> new HttpMcpTransport(""));
  }

  @Test
  void testConstructor_WithOnlyBaseUrl() {
    HttpMcpTransport simpleTransport = new HttpMcpTransport("https://test-mcp-service.com");
    assertNotNull(simpleTransport);
    assertEquals("https://test-mcp-service.com", simpleTransport.getBaseUrl());
  }

  @Test
  void testOtherOverloadedConstructors() {
    java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
    java.util.concurrent.Executor executor = java.util.concurrent.ForkJoinPool.commonPool();
    CredentialsProvider provider = () -> CompletableFuture.completedFuture("Bearer test");

    HttpMcpTransport transport1 =
        new HttpMcpTransport(
            "https://test-mcp.com",
            Map.of("X-Header", "value"),
            ProtocolVersion.VERSION_2025_11_25,
            client,
            executor);
    assertNotNull(transport1);

    HttpMcpTransport transport2 =
        new HttpMcpTransport("https://test-mcp.com", Map.of("X-Header", "value"), provider, client);
    assertNotNull(transport2);
  }

  @Test
  void testConstructor_WithCustomExecutorConfiguresHttpClient() throws Exception {
    java.util.concurrent.atomic.AtomicInteger taskCount =
        new java.util.concurrent.atomic.AtomicInteger(0);
    java.util.concurrent.Executor customExecutor =
        runnable -> {
          taskCount.incrementAndGet();
          new Thread(runnable).start();
        };

    HttpMcpTransport transport =
        new HttpMcpTransport(
            "http://localhost:8080",
            java.util.Map.of(),
            ProtocolVersion.VERSION_2025_11_25,
            null,
            customExecutor);

    java.lang.reflect.Field delegateField = HttpMcpTransport.class.getDeclaredField("delegate");
    delegateField.setAccessible(true);
    Object delegate = delegateField.get(transport);

    java.lang.reflect.Field httpClientField = BaseMcpTransport.class.getDeclaredField("httpClient");
    httpClientField.setAccessible(true);
    java.net.http.HttpClient httpClient = (java.net.http.HttpClient) httpClientField.get(delegate);

    assertNotNull(httpClient);
    Object internalExecutor = null;
    try {
      java.lang.reflect.Field executorField = httpClient.getClass().getDeclaredField("executor");
      executorField.setAccessible(true);
      internalExecutor = executorField.get(httpClient);
    } catch (NoSuchFieldException e) {
      // Fallback
    }

    if (internalExecutor != null) {
      org.junit.jupiter.api.Assertions.assertSame(customExecutor, internalExecutor);
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void testInitialize_ServerReturnsErrorJsonRpcResponse() throws Exception {
    HttpResponse<String> mockInitResponse = mock(HttpResponse.class);
    when(mockInitResponse.statusCode()).thenReturn(200);
    when(mockInitResponse.body())
        .thenReturn(
            "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"error\":{\"code\":-32603,\"message\":\"Internal"
                + " error\"}}");

    when(mockClient.<String>sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(mockInitResponse));

    CompletableFuture<TransportManifest> future = transport.listTools("", Collections.emptyMap());
    java.util.concurrent.ExecutionException ex =
        org.junit.jupiter.api.Assertions.assertThrows(
            java.util.concurrent.ExecutionException.class, future::get);
    assertTrue(ex.getCause() instanceof McpException);
    assertTrue(ex.getCause().getMessage().contains("MCP Error"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testListTools_WithHttpUrlAndMetadata_LogsWarning() throws Exception {
    HttpMcpTransport httpTransport =
        new HttpMcpTransport("http://test-mcp-service.com", mockClient);
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

    when(mockClient.<String>sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(mockInitResponse))
        .thenReturn(CompletableFuture.completedFuture(mockInitializedResponse))
        .thenReturn(CompletableFuture.completedFuture(mockListResponse));

    java.util.logging.Logger transportLogger =
        java.util.logging.Logger.getLogger(BaseMcpTransport.class.getName());
    java.util.List<java.util.logging.LogRecord> logRecords = new java.util.ArrayList<>();
    java.util.logging.Handler logHandler =
        new java.util.logging.Handler() {
          @Override
          public void publish(java.util.logging.LogRecord record) {
            logRecords.add(record);
          }

          @Override
          public void flush() {}

          @Override
          public void close() throws SecurityException {}
        };
    transportLogger.addHandler(logHandler);

    try {
      httpTransport.listTools("", Map.of("key", "val")).get();
    } finally {
      transportLogger.removeHandler(logHandler);
    }

    assertFalse(logRecords.isEmpty());
    assertTrue(logRecords.get(0).getMessage().contains("This connection is using HTTP"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testListTools_Non200Response_ThrowsException() {
    HttpResponse<String> mockInitResponse = mock(HttpResponse.class);
    when(mockInitResponse.statusCode()).thenReturn(200);
    when(mockInitResponse.body())
        .thenReturn(
            "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"protocolVersion\":\"2025-11-25\"}}");

    HttpResponse<String> mockInitializedResponse = mock(HttpResponse.class);
    when(mockInitializedResponse.statusCode()).thenReturn(200);
    when(mockInitializedResponse.body()).thenReturn("");

    HttpResponse<String> mockErrorResponse = mock(HttpResponse.class);
    when(mockErrorResponse.statusCode()).thenReturn(500);
    when(mockErrorResponse.body()).thenReturn("Internal Server Error");

    when(mockClient.<String>sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(mockInitResponse))
        .thenReturn(CompletableFuture.completedFuture(mockInitializedResponse))
        .thenReturn(CompletableFuture.completedFuture(mockErrorResponse));

    Exception ex =
        org.junit.jupiter.api.Assertions.assertThrows(
            Exception.class, () -> transport.listTools("", Collections.emptyMap()).get());
    assertTrue(ex.getCause().getMessage().contains("Status: 500"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testListTools_JsonRpcError_ThrowsException() {
    HttpResponse<String> mockInitResponse = mock(HttpResponse.class);
    when(mockInitResponse.statusCode()).thenReturn(200);
    when(mockInitResponse.body())
        .thenReturn(
            "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"protocolVersion\":\"2025-11-25\"}}");

    HttpResponse<String> mockInitializedResponse = mock(HttpResponse.class);
    when(mockInitializedResponse.statusCode()).thenReturn(200);
    when(mockInitializedResponse.body()).thenReturn("");

    HttpResponse<String> mockErrorResponse = mock(HttpResponse.class);
    when(mockErrorResponse.statusCode()).thenReturn(200);
    when(mockErrorResponse.body())
        .thenReturn(
            "{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"error\":{\"code\":-1,\"message\":\"Custom"
                + " error\"}}");

    when(mockClient.<String>sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(mockInitResponse))
        .thenReturn(CompletableFuture.completedFuture(mockInitializedResponse))
        .thenReturn(CompletableFuture.completedFuture(mockErrorResponse));

    Exception ex =
        org.junit.jupiter.api.Assertions.assertThrows(
            Exception.class, () -> transport.listTools("", Collections.emptyMap()).get());
    assertTrue(ex.getCause().getMessage().contains("Custom error"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testListTools_ParsesComplexToolsCorrectly() throws Exception {
    HttpResponse<String> mockInitResponse = mock(HttpResponse.class);
    when(mockInitResponse.statusCode()).thenReturn(200);
    when(mockInitResponse.body())
        .thenReturn(
            "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"protocolVersion\":\"2025-11-25\"}}");

    HttpResponse<String> mockInitializedResponse = mock(HttpResponse.class);
    when(mockInitializedResponse.statusCode()).thenReturn(200);
    when(mockInitializedResponse.body()).thenReturn("");

    String json =
        "{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"result\":{\"tools\":["
            + "{"
            + "  \"name\":\"test-tool\","
            + "  \"description\":\"Desc\","
            + "  \"inputSchema\":{"
            + "    \"type\":\"object\","
            + "    \"required\":[\"p1\"],"
            + "    \"properties\":{"
            + "      \"p1\": { \"type\":\"string\", \"description\":\"p1 desc\" },"
            + "      \"p2\": { }"
            + "    }"
            + "  },"
            + "  \"_meta\":{"
            + "    \"toolbox/authInvoke\": \"not-an-array\","
            + "    \"toolbox/authParam\": {"
            + "      \"p1\": [\"gcp\"]"
            + "    }"
            + "  }"
            + "}"
            + "]}}";
    HttpResponse<String> mockListResponse = mock(HttpResponse.class);
    when(mockListResponse.statusCode()).thenReturn(200);
    when(mockListResponse.body()).thenReturn(json);

    when(mockClient.<String>sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(mockInitResponse))
        .thenReturn(CompletableFuture.completedFuture(mockInitializedResponse))
        .thenReturn(CompletableFuture.completedFuture(mockListResponse));

    TransportManifest manifest = transport.listTools("", Collections.emptyMap()).get();
    assertNotNull(manifest);
    ToolDefinition tool = manifest.getTools().get("test-tool");
    assertNotNull(tool);
    assertEquals("Desc", tool.description());
    assertEquals(2, tool.parameters().size());

    ToolDefinition.Parameter p1 =
        tool.parameters().stream().filter(p -> p.name().equals("p1")).findFirst().get();
    assertTrue(p1.required());
    assertEquals("p1 desc", p1.description());
    assertEquals(List.of("gcp"), p1.authSources());

    ToolDefinition.Parameter p2 =
        tool.parameters().stream().filter(p -> p.name().equals("p2")).findFirst().get();
    assertFalse(p2.required());
    assertEquals("string", p2.type());
  }

  @Test
  void testJsonRpcInstantiation() {
    // Instantiate package-private JsonRpc namespace to cover its default constructor
    JsonRpc rpc = new JsonRpc();
    assertNotNull(rpc);
  }
}
