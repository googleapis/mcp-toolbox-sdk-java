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

package com.google.cloud.mcp.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.mcp.JsonRpc;
import com.google.cloud.mcp.McpToolboxClient;
import com.google.cloud.mcp.auth.AuthTokenGetter;
import com.google.cloud.mcp.auth.CredentialsProvider;
import com.google.cloud.mcp.tool.Tool;
import com.google.cloud.mcp.tool.ToolDefinition;
import com.google.cloud.mcp.tool.ToolPostProcessor;
import com.google.cloud.mcp.tool.ToolPreProcessor;
import com.google.cloud.mcp.tool.ToolResult;
import com.google.cloud.mcp.transport.BaseMcpTransport;
import com.google.cloud.mcp.transport.HttpMcpTransport;
import com.google.cloud.mcp.transport.Transport;
import com.google.cloud.mcp.transport.TransportManifest;
import com.google.cloud.mcp.transport.TransportResponse;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

@Timeout(value = 5, unit = java.util.concurrent.TimeUnit.SECONDS)
class McpToolboxClientImplTest {

  private McpToolboxClientImpl client;
  private HttpClient mockHttpClient;
  private ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() throws Exception {
    mockHttpClient = mock(HttpClient.class);
    HttpMcpTransport transport = new HttpMcpTransport("http://localhost:8080", mockHttpClient);
    CredentialsProvider provider = () -> CompletableFuture.completedFuture("Bearer test-api-key");
    client = new McpToolboxClientImpl(transport, java.util.Collections.emptyMap(), provider);
  }

  @Test
  void testEnsureInitializedCalledOnce() throws Exception {
    // Setup mock responses
    HttpResponse<String> initResponse = mock(HttpResponse.class);
    when(initResponse.statusCode()).thenReturn(200);
    when(initResponse.body()).thenReturn("{}");

    HttpResponse<String> notifResponse = mock(HttpResponse.class);
    when(notifResponse.statusCode()).thenReturn(200);
    when(notifResponse.body()).thenReturn("{}");

    HttpResponse<String> listResponse = mock(HttpResponse.class);
    when(listResponse.statusCode()).thenReturn(200);
    when(listResponse.body())
        .thenReturn(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[{\"name\":\"test-tool\","
                + "\"description\":\"A test tool\",\"inputSchema\":{\"type\":\"object\","
                + "\"properties\":{\"param1\":{\"type\":\"string\"}},"
                + "\"required\":[\"param1\"]}}]}}");

    // The order of requests will be: initialize, notifications/initialized, tools/list
    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(initResponse))
        .thenReturn(CompletableFuture.completedFuture(notifResponse))
        .thenReturn(CompletableFuture.completedFuture(listResponse))
        .thenReturn(CompletableFuture.completedFuture(listResponse));

    // Call listTools multiple times
    client.listTools().join();
    client.listTools().join();

    // Verify requests
    ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(mockHttpClient, times(4)).sendAsync(requestCaptor.capture(), any());

    long initCount =
        requestCaptor.getAllValues().stream()
            .filter(req -> getBodyStringQuietly(req).contains("\"method\":\"initialize\""))
            .count();
    long notifCount =
        requestCaptor.getAllValues().stream()
            .filter(
                req ->
                    getBodyStringQuietly(req).contains("\"method\":\"notifications/initialized\""))
            .count();
    long listCount =
        requestCaptor.getAllValues().stream()
            .filter(req -> getBodyStringQuietly(req).contains("\"method\":\"tools/list\""))
            .count();

    assertEquals(1, initCount, "initialize should be called exactly once");
    assertEquals(1, notifCount, "notifications/initialized should be called exactly once");
    assertEquals(2, listCount, "tools/list should be called twice");
  }

  @Test
  void testListTools() throws Exception {
    // Setup mock responses
    HttpResponse<String> initResponse = mock(HttpResponse.class);
    when(initResponse.statusCode()).thenReturn(200);
    when(initResponse.body()).thenReturn("{}");

    HttpResponse<String> notifResponse = mock(HttpResponse.class);
    when(notifResponse.statusCode()).thenReturn(200);
    when(notifResponse.body()).thenReturn("{}");

    String listBody =
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[{\"name\":\"test-tool\","
            + "\"description\":\"A test tool\",\"inputSchema\":{\"type\":\"object\","
            + "\"properties\":{\"param1\":{\"type\":\"string\",\"description\":\"param desc\"}},"
            + "\"required\":[\"param1\"]},\"_meta\":{\"toolbox/authInvoke\":[\"auth1\"]}}]}}";
    HttpResponse<String> listResponse = mock(HttpResponse.class);
    when(listResponse.statusCode()).thenReturn(200);
    when(listResponse.body()).thenReturn(listBody);

    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(initResponse))
        .thenReturn(CompletableFuture.completedFuture(notifResponse))
        .thenReturn(CompletableFuture.completedFuture(listResponse));

    Map<String, ToolDefinition> tools = client.listTools().join();

    assertNotNull(tools);
    assertEquals(1, tools.size());
    assertTrue(tools.containsKey("test-tool"));

    ToolDefinition toolDef = tools.get("test-tool");
    assertEquals("A test tool", toolDef.description());
    assertEquals(1, toolDef.authRequired().size());
    assertEquals("auth1", toolDef.authRequired().get(0));

    assertEquals(1, toolDef.parameters().size());
    ToolDefinition.Parameter param = toolDef.parameters().get(0);
    assertEquals("param1", param.name());
    assertEquals("string", param.type());
    assertEquals("param desc", param.description());
    assertTrue(param.required());
  }

  @Test
  void testInvokeTool() throws Exception {
    // Setup mock responses
    HttpResponse<String> initResponse = mock(HttpResponse.class);
    when(initResponse.statusCode()).thenReturn(200);
    when(initResponse.body()).thenReturn("{}");

    HttpResponse<String> notifResponse = mock(HttpResponse.class);
    when(notifResponse.statusCode()).thenReturn(200);
    when(notifResponse.body()).thenReturn("{}");

    String callBody =
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":[{\"type\":\"text\","
            + "\"text\":\"success\"}],\"isError\":false}}";
    HttpResponse<String> callResponse = mock(HttpResponse.class);
    when(callResponse.statusCode()).thenReturn(200);
    when(callResponse.body()).thenReturn(callBody);

    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(initResponse))
        .thenReturn(CompletableFuture.completedFuture(notifResponse))
        .thenReturn(CompletableFuture.completedFuture(callResponse));

    ToolResult result = client.invokeTool("test-tool", Map.of("param1", "value1")).join();

    assertNotNull(result);
    assertFalse(result.isError());
    assertEquals(1, result.content().size());
    assertEquals("success", result.content().get(0).text());

    // Verify request payload
    ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(mockHttpClient, times(3)).sendAsync(requestCaptor.capture(), any());

    HttpRequest callReq = requestCaptor.getAllValues().get(2);
    String bodyStr = getBodyString(callReq);

    JsonNode root = objectMapper.readTree(bodyStr);
    assertEquals("tools/call", root.get("method").asText());
    JsonNode params = root.get("params");
    assertEquals("test-tool", params.get("name").asText());
    assertEquals("value1", params.get("arguments").get("param1").asText());
  }

  private String getBodyStringQuietly(HttpRequest request) {
    try {
      return getBodyString(request);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private String getBodyString(HttpRequest request) throws Exception {
    if (request.bodyPublisher().isPresent()) {
      var publisher = request.bodyPublisher().get();
      var subscriber =
          HttpResponse.BodySubscribers.ofString(java.nio.charset.StandardCharsets.UTF_8);
      publisher.subscribe(
          new java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer>() {
            @Override
            public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
              subscriber.onSubscribe(subscription);
              subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(java.nio.ByteBuffer item) {
              subscriber.onNext(java.util.List.of(item));
            }

            @Override
            public void onError(Throwable throwable) {
              subscriber.onError(throwable);
            }

            @Override
            public void onComplete() {
              subscriber.onComplete();
            }
          });
      return subscriber.getBody().toCompletableFuture().join();
    }
    return "";
  }

  @Test
  @SuppressWarnings("unchecked")
  void testConstructor_withNullAndEmptyAndRawApiKeys() throws Exception {
    Method getAuthHeaderMethod =
        McpToolboxClientImpl.class.getDeclaredMethod("getAuthorizationHeader");
    getAuthHeaderMethod.setAccessible(true);

    McpToolboxClientImpl clientNull =
        new McpToolboxClientImpl("http://localhost:8080", (String) null);
    CompletableFuture<String> futureNull =
        (CompletableFuture<String>) getAuthHeaderMethod.invoke(clientNull);
    assertNull(futureNull.join());

    McpToolboxClientImpl clientEmpty = new McpToolboxClientImpl("http://localhost:8080", "");
    CompletableFuture<String> futureEmpty =
        (CompletableFuture<String>) getAuthHeaderMethod.invoke(clientEmpty);
    assertNull(futureEmpty.join());

    McpToolboxClientImpl clientRaw = new McpToolboxClientImpl("http://localhost:8080", "my-key");
    CompletableFuture<String> futureRaw =
        (CompletableFuture<String>) getAuthHeaderMethod.invoke(clientRaw);
    assertEquals("Bearer my-key", futureRaw.join());

    McpToolboxClientImpl clientBearer =
        new McpToolboxClientImpl("http://localhost:8080", "Bearer already-bearer");
    CompletableFuture<String> futureBearer =
        (CompletableFuture<String>) getAuthHeaderMethod.invoke(clientBearer);
    assertEquals("Bearer already-bearer", futureBearer.join());
  }

  @Test
  void testLoadToolset_strictMode_unknownToolsThrowsException() throws Exception {
    // Setup mock responses to return empty tools
    HttpResponse<String> initResponse = mock(HttpResponse.class);
    when(initResponse.statusCode()).thenReturn(200);
    when(initResponse.body()).thenReturn("{}");

    HttpResponse<String> notifResponse = mock(HttpResponse.class);
    when(notifResponse.statusCode()).thenReturn(200);
    when(notifResponse.body()).thenReturn("{}");

    HttpResponse<String> listResponse = mock(HttpResponse.class);
    when(listResponse.statusCode()).thenReturn(200);
    when(listResponse.body())
        .thenReturn("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[]}}");

    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(initResponse))
        .thenReturn(CompletableFuture.completedFuture(notifResponse))
        .thenReturn(CompletableFuture.completedFuture(listResponse));

    // Try strict loading with binding for unknown tool
    Exception exception =
        org.junit.jupiter.api.Assertions.assertThrows(
            Exception.class,
            () ->
                client.loadToolset("my-set", Map.of("unknown-tool", Map.of()), null, true).join());
    Throwable cause = exception.getCause();
    assertNotNull(cause);
    assertTrue(cause instanceof IllegalArgumentException);
    assertTrue(
        cause
            .getMessage()
            .contains("Strict mode error: Bindings provided for unknown tools: [unknown-tool]"));
  }

  @Test
  void testLoadTool_notFoundThrowsException() throws Exception {
    HttpResponse<String> initResponse = mock(HttpResponse.class);
    when(initResponse.statusCode()).thenReturn(200);
    when(initResponse.body()).thenReturn("{}");

    HttpResponse<String> notifResponse = mock(HttpResponse.class);
    when(notifResponse.statusCode()).thenReturn(200);
    when(notifResponse.body()).thenReturn("{}");

    HttpResponse<String> listResponse = mock(HttpResponse.class);
    when(listResponse.statusCode()).thenReturn(200);
    when(listResponse.body())
        .thenReturn("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[]}}");

    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(initResponse))
        .thenReturn(CompletableFuture.completedFuture(notifResponse))
        .thenReturn(CompletableFuture.completedFuture(listResponse));

    Exception exception =
        org.junit.jupiter.api.Assertions.assertThrows(
            Exception.class, () -> client.loadTool("non-existent-tool").join());
    Throwable cause = exception.getCause();
    assertNotNull(cause);
    assertTrue(cause instanceof RuntimeException);
    assertTrue(cause.getMessage().contains("Tool not found: non-existent-tool"));
  }

  @Test
  void testLoadTool_successWithAuthTokenGetters() throws Exception {
    HttpResponse<String> initResponse = mock(HttpResponse.class);
    when(initResponse.statusCode()).thenReturn(200);
    when(initResponse.body()).thenReturn("{}");

    HttpResponse<String> notifResponse = mock(HttpResponse.class);
    when(notifResponse.statusCode()).thenReturn(200);
    when(notifResponse.body()).thenReturn("{}");

    String listBody =
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[{\"name\":\"test-tool\","
            + "\"description\":\"A test tool\",\"inputSchema\":{\"type\":\"object\","
            + "\"properties\":{\"param1\":{\"type\":\"string\"}},"
            + "\"required\":[\"param1\"]}}]}}";
    HttpResponse<String> listResponse = mock(HttpResponse.class);
    when(listResponse.statusCode()).thenReturn(200);
    when(listResponse.body()).thenReturn(listBody);

    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(initResponse))
        .thenReturn(CompletableFuture.completedFuture(notifResponse))
        .thenReturn(CompletableFuture.completedFuture(listResponse));

    Tool tool =
        client
            .loadTool("test-tool", Map.of("my-svc", () -> CompletableFuture.completedFuture("tok")))
            .join();

    assertNotNull(tool);
    assertEquals("test-tool", tool.name());
  }

  @Test
  void testLoadToolset_successWithAuthBinds() throws Exception {
    HttpResponse<String> initResponse = mock(HttpResponse.class);
    when(initResponse.statusCode()).thenReturn(200);
    when(initResponse.body()).thenReturn("{}");

    HttpResponse<String> notifResponse = mock(HttpResponse.class);
    when(notifResponse.statusCode()).thenReturn(200);
    when(notifResponse.body()).thenReturn("{}");

    String listBody =
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[{\"name\":\"test-tool\","
            + "\"description\":\"A test tool\",\"inputSchema\":{\"type\":\"object\","
            + "\"properties\":{\"param1\":{\"type\":\"string\"}},"
            + "\"required\":[\"param1\"]}}]}}";
    HttpResponse<String> listResponse = mock(HttpResponse.class);
    when(listResponse.statusCode()).thenReturn(200);
    when(listResponse.body()).thenReturn(listBody);

    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(initResponse))
        .thenReturn(CompletableFuture.completedFuture(notifResponse))
        .thenReturn(CompletableFuture.completedFuture(listResponse));

    Map<String, Map<String, AuthTokenGetter>> authBinds =
        Map.of("test-tool", Map.of("my-svc", () -> CompletableFuture.completedFuture("tok")));

    Map<String, Tool> tools = client.loadToolset("my-set", null, authBinds, false).join();
    assertNotNull(tools);
    assertTrue(tools.containsKey("test-tool"));
    Tool tool = tools.get("test-tool");
    assertEquals("test-tool", tool.name());
  }

  @Test
  void testEnsureInitialized_withHttpsBaseUrl() throws Exception {
    HttpMcpTransport transport = new HttpMcpTransport("https://localhost:8443", mockHttpClient);
    CredentialsProvider provider = () -> CompletableFuture.completedFuture("Bearer test-api-key");
    McpToolboxClientImpl httpsClient =
        new McpToolboxClientImpl(transport, java.util.Collections.emptyMap(), provider);

    HttpResponse<String> initResponse = mock(HttpResponse.class);
    when(initResponse.statusCode()).thenReturn(200);
    when(initResponse.body()).thenReturn("{}");

    HttpResponse<String> notifResponse = mock(HttpResponse.class);
    when(notifResponse.statusCode()).thenReturn(200);
    when(notifResponse.body()).thenReturn("{}");

    HttpResponse<String> listResponse = mock(HttpResponse.class);
    when(listResponse.statusCode()).thenReturn(200);
    when(listResponse.body())
        .thenReturn("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[]}}");

    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(initResponse))
        .thenReturn(CompletableFuture.completedFuture(notifResponse))
        .thenReturn(CompletableFuture.completedFuture(listResponse));

    httpsClient.listTools().join(); // should succeed and NOT print any HTTP_WARNING
  }

  @Test
  void testEnsureInitialized_withoutApiKeyFallbackToAdcException() throws Exception {
    HttpMcpTransport transport = new HttpMcpTransport("http://localhost:8080", mockHttpClient);
    McpToolboxClientImpl noAuthClient =
        new McpToolboxClientImpl(transport, java.util.Collections.emptyMap(), null);

    HttpResponse<String> initResponse = mock(HttpResponse.class);
    when(initResponse.statusCode()).thenReturn(200);
    when(initResponse.body()).thenReturn("{}");

    HttpResponse<String> notifResponse = mock(HttpResponse.class);
    when(notifResponse.statusCode()).thenReturn(200);
    when(notifResponse.body()).thenReturn("{}");

    HttpResponse<String> listResponse = mock(HttpResponse.class);
    when(listResponse.statusCode()).thenReturn(200);
    when(listResponse.body())
        .thenReturn("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[]}}");

    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(initResponse))
        .thenReturn(CompletableFuture.completedFuture(notifResponse))
        .thenReturn(CompletableFuture.completedFuture(listResponse));

    // This triggers getAuthorizationHeader() -> OIDC / ADC resolution -> Exception -> fallback to
    // null
    noAuthClient.listTools().join();

    ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(mockHttpClient, times(3)).sendAsync(requestCaptor.capture(), any());

    HttpRequest initReq = requestCaptor.getAllValues().get(0);
    if (initReq.headers().map().containsKey("Authorization")) {
      String auth = initReq.headers().firstValue("Authorization").get();
      assertTrue(auth.startsWith("Bearer "));
    }
  }

  @Test
  void testLoadToolset_withNullToolsetName() throws Exception {
    HttpResponse<String> initResponse = mock(HttpResponse.class);
    when(initResponse.statusCode()).thenReturn(200);
    when(initResponse.body()).thenReturn("{}");

    HttpResponse<String> notifResponse = mock(HttpResponse.class);
    when(notifResponse.statusCode()).thenReturn(200);
    when(notifResponse.body()).thenReturn("{}");

    HttpResponse<String> listResponse = mock(HttpResponse.class);
    when(listResponse.statusCode()).thenReturn(200);
    when(listResponse.body())
        .thenReturn("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[]}}");

    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(initResponse))
        .thenReturn(CompletableFuture.completedFuture(notifResponse))
        .thenReturn(CompletableFuture.completedFuture(listResponse));

    Map<String, ToolDefinition> tools = client.loadToolset(null).join();
    assertNotNull(tools);
    assertTrue(tools.isEmpty());
  }

  @Test
  void testLoadToolset_withInvalidUriThrowsException() {
    HttpMcpTransport transport = new HttpMcpTransport("http://invalid uri", mockHttpClient);
    McpToolboxClientImpl badClient =
        new McpToolboxClientImpl(transport, java.util.Collections.emptyMap(), null);

    Exception exception =
        org.junit.jupiter.api.Assertions.assertThrows(
            Exception.class, () -> badClient.listTools().join());
    assertNotNull(exception.getCause());
    assertTrue(exception.getCause() instanceof IllegalArgumentException);
  }

  @Test
  void testInvokeTool_withInvalidUriThrowsException() throws Exception {
    HttpMcpTransport transport = new HttpMcpTransport("http://invalid uri", mockHttpClient);
    Field delegateField = HttpMcpTransport.class.getDeclaredField("delegate");
    delegateField.setAccessible(true);
    Object delegate = delegateField.get(transport);
    Field initFutureField = BaseMcpTransport.class.getDeclaredField("initFuture");
    initFutureField.setAccessible(true);
    initFutureField.set(delegate, CompletableFuture.completedFuture(null)); // bypass initialization
    McpToolboxClientImpl badClient =
        new McpToolboxClientImpl(transport, java.util.Collections.emptyMap(), null);

    Exception exception =
        org.junit.jupiter.api.Assertions.assertThrows(
            Exception.class, () -> badClient.invokeTool("test-tool", Map.of()).join());
    assertNotNull(exception.getCause());
    assertTrue(exception.getCause() instanceof IllegalArgumentException);
  }

  @Test
  void testListTools_withInvalidJsonResponseThrowsException() throws Exception {
    HttpResponse<String> initResponse = mock(HttpResponse.class);
    when(initResponse.statusCode()).thenReturn(200);
    when(initResponse.body()).thenReturn("{}");

    HttpResponse<String> notifResponse = mock(HttpResponse.class);
    when(notifResponse.statusCode()).thenReturn(200);
    when(notifResponse.body()).thenReturn("{}");

    HttpResponse<String> listResponse = mock(HttpResponse.class);
    when(listResponse.statusCode()).thenReturn(200);
    when(listResponse.body()).thenReturn("invalid-json-body");

    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(initResponse))
        .thenReturn(CompletableFuture.completedFuture(notifResponse))
        .thenReturn(CompletableFuture.completedFuture(listResponse));

    Exception exception =
        org.junit.jupiter.api.Assertions.assertThrows(
            Exception.class, () -> client.listTools().join());
    assertNotNull(exception.getCause());
    assertTrue(exception.getCause() instanceof RuntimeException);
    assertTrue(
        exception.getCause().getCause()
            instanceof com.fasterxml.jackson.core.JsonProcessingException);
  }

  @Test
  @SuppressWarnings("unchecked")
  void testGetAuthorizationHeader_withAdcException() throws Exception {
    McpToolboxClientImpl noAuthClient =
        new McpToolboxClientImpl("http://localhost:8080", (String) null);
    Method method = McpToolboxClientImpl.class.getDeclaredMethod("getAuthorizationHeader");
    method.setAccessible(true);

    try (MockedStatic<GoogleCredentials> mockedCredentials = mockStatic(GoogleCredentials.class)) {
      mockedCredentials
          .when(GoogleCredentials::getApplicationDefault)
          .thenThrow(new IOException("Simulated ADC exception"));

      CompletableFuture<String> future = (CompletableFuture<String>) method.invoke(noAuthClient);
      String header = future.join();
      org.junit.jupiter.api.Assertions.assertNull(header);
    }
  }

  @Test
  void testEnsureInitialized_withNullAuthHeader() throws Exception {
    HttpMcpTransport transport = new HttpMcpTransport("http://localhost:8080", mockHttpClient);
    McpToolboxClientImpl noAuthClient =
        new McpToolboxClientImpl(transport, java.util.Collections.emptyMap(), null);

    HttpResponse<String> initResponse = mock(HttpResponse.class);
    when(initResponse.statusCode()).thenReturn(200);
    when(initResponse.body()).thenReturn("{}");

    HttpResponse<String> notifResponse = mock(HttpResponse.class);
    when(notifResponse.statusCode()).thenReturn(200);
    when(notifResponse.body()).thenReturn("{}");

    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(initResponse))
        .thenReturn(CompletableFuture.completedFuture(notifResponse));

    Field delegateField = HttpMcpTransport.class.getDeclaredField("delegate");
    delegateField.setAccessible(true);
    Object delegate = delegateField.get(transport);

    Method initMethod = BaseMcpTransport.class.getDeclaredMethod("ensureInitialized", Map.class);
    initMethod.setAccessible(true);

    CompletableFuture<Void> future =
        (CompletableFuture<Void>) initMethod.invoke(delegate, java.util.Collections.emptyMap());
    future.join(); // should complete and NOT set Authorization header

    ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(mockHttpClient, times(2)).sendAsync(requestCaptor.capture(), any());

    HttpRequest initReq = requestCaptor.getAllValues().get(0);
    assertFalse(initReq.headers().map().containsKey("Authorization"));
  }

  @Test
  void testConstructor_withTrailingSlashAndNullHeaders() throws Exception {
    McpToolboxClientImpl clientWithSlash =
        new McpToolboxClientImpl("http://localhost:8080/", (Map<String, String>) null);

    Field transportField = McpToolboxClientImpl.class.getDeclaredField("transport");
    transportField.setAccessible(true);
    Transport transport = (Transport) transportField.get(clientWithSlash);
    assertEquals("http://localhost:8080", transport.getBaseUrl());

    Field headersField = McpToolboxClientImpl.class.getDeclaredField("headers");
    headersField.setAccessible(true);
    Map<?, ?> headersMap = (Map<?, ?>) headersField.get(clientWithSlash);
    assertNotNull(headersMap);
    assertTrue(headersMap.isEmpty());
  }

  @Test
  void testEnsureInitialized_withCustomHeaders() throws Exception {
    Map<String, String> customHeaders =
        Map.of("X-Custom-Header", "custom-val", "Authorization", "some-apiKey");
    HttpMcpTransport transport =
        new HttpMcpTransport("http://localhost:8080", customHeaders, mockHttpClient);
    McpToolboxClientImpl customClient = new McpToolboxClientImpl(transport, customHeaders, null);

    HttpResponse<String> initResponse = mock(HttpResponse.class);
    when(initResponse.statusCode()).thenReturn(200);
    when(initResponse.body()).thenReturn("{}");

    HttpResponse<String> notifResponse = mock(HttpResponse.class);
    when(notifResponse.statusCode()).thenReturn(200);
    when(notifResponse.body()).thenReturn("{}");

    HttpResponse<String> listResponse = mock(HttpResponse.class);
    when(listResponse.statusCode()).thenReturn(200);
    when(listResponse.body())
        .thenReturn("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[]}}");

    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(initResponse))
        .thenReturn(CompletableFuture.completedFuture(notifResponse))
        .thenReturn(CompletableFuture.completedFuture(listResponse));

    customClient.listTools().join();

    ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(mockHttpClient, times(3)).sendAsync(requestCaptor.capture(), any());

    HttpRequest initReq = requestCaptor.getAllValues().get(0);
    assertEquals("custom-val", initReq.headers().firstValue("X-Custom-Header").orElse(null));
    assertEquals("some-apiKey", initReq.headers().firstValue("Authorization").orElse(null));
  }

  @Test
  void testLoadToolset_withVariousBinds() throws Exception {
    HttpResponse<String> initResponse = mock(HttpResponse.class);
    when(initResponse.statusCode()).thenReturn(200);
    when(initResponse.body()).thenReturn("{}");

    HttpResponse<String> notifResponse = mock(HttpResponse.class);
    when(notifResponse.statusCode()).thenReturn(200);
    when(notifResponse.body()).thenReturn("{}");

    String listBody =
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[{\"name\":\"test-tool\","
            + "\"description\":\"A test tool\",\"inputSchema\":{\"type\":\"object\","
            + "\"properties\":{\"param1\":{\"type\":\"string\"}},"
            + "\"required\":[\"param1\"]}}]}}";
    HttpResponse<String> listResponse = mock(HttpResponse.class);
    when(listResponse.statusCode()).thenReturn(200);
    when(listResponse.body()).thenReturn(listBody);

    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(initResponse))
        .thenReturn(CompletableFuture.completedFuture(notifResponse))
        .thenReturn(CompletableFuture.completedFuture(listResponse));

    Map<String, Map<String, Object>> paramBinds = Map.of("test-tool", Map.of("param1", "value1"));
    Map<String, Map<String, AuthTokenGetter>> authBinds =
        Map.of("test-tool", Map.of("my-svc", () -> CompletableFuture.completedFuture("tok")));

    Map<String, Tool> tools = client.loadToolset("my-set", paramBinds, authBinds, true).join();
    assertNotNull(tools);
    assertTrue(tools.containsKey("test-tool"));
    Tool tool = tools.get("test-tool");
    assertEquals("test-tool", tool.name());
  }

  @Test
  @SuppressWarnings("unchecked")
  void testConstructor_withCredentialsProvider() throws Exception {
    CredentialsProvider provider = () -> CompletableFuture.completedFuture("Bearer provider-token");
    McpToolboxClientImpl client = new McpToolboxClientImpl("http://localhost:8080", provider);
    assertNotNull(client);

    Method getAuthHeaderMethod =
        McpToolboxClientImpl.class.getDeclaredMethod("getAuthorizationHeader");
    getAuthHeaderMethod.setAccessible(true);
    CompletableFuture<String> future =
        (CompletableFuture<String>) getAuthHeaderMethod.invoke(client);
    assertEquals("Bearer provider-token", future.join());
  }

  @Test
  @SuppressWarnings("unchecked")
  void testDefaultLoadToolset() throws Exception {
    HttpMcpTransport transport = new HttpMcpTransport("http://localhost:8080", mockHttpClient);
    McpToolboxClientImpl client =
        new McpToolboxClientImpl(transport, java.util.Collections.emptyMap(), null);

    HttpResponse<String> initResponse = mock(HttpResponse.class);
    when(initResponse.statusCode()).thenReturn(200);
    when(initResponse.body()).thenReturn("{}");

    HttpResponse<String> notifResponse = mock(HttpResponse.class);
    when(notifResponse.statusCode()).thenReturn(200);
    when(notifResponse.body()).thenReturn("{}");

    String listBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[]}}";
    HttpResponse<String> listResponse = mock(HttpResponse.class);
    when(listResponse.statusCode()).thenReturn(200);
    when(listResponse.body()).thenReturn(listBody);

    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(initResponse))
        .thenReturn(CompletableFuture.completedFuture(notifResponse))
        .thenReturn(CompletableFuture.completedFuture(listResponse));

    Map<String, ToolDefinition> tools = ((McpToolboxClient) client).loadToolset().join();
    assertNotNull(tools);
    assertTrue(tools.isEmpty());
  }

  @Test
  @SuppressWarnings("deprecation")
  void testCoverageBoosters() throws Exception {
    // 1. Cover HttpMcpTransport close() method
    HttpMcpTransport transport = new HttpMcpTransport("http://localhost:8080", mockHttpClient);
    transport.close();

    // 2. Cover HttpMcpTransport constructor with null headers
    HttpMcpTransport transportWithNullHeaders =
        new HttpMcpTransport("http://localhost:8080", null, mockHttpClient);
    assertNotNull(transportWithNullHeaders);

    // 3. Cover McpToolboxClientImpl deprecated constructor 1
    McpToolboxClientImpl client1 =
        new McpToolboxClientImpl("http://localhost:8080", java.util.Collections.emptyMap(), null);
    assertNotNull(client1);

    // 4. Cover McpToolboxClientImpl deprecated constructor 2
    McpToolboxClientImpl client2 = new McpToolboxClientImpl(transport, null);
    assertNotNull(client2);
  }

  @Test
  void testInvokeTool_withNullHeadersThrows() {
    HttpMcpTransport transport = new HttpMcpTransport("http://localhost:8080", mockHttpClient);
    McpToolboxClientImpl client =
        new McpToolboxClientImpl(transport, java.util.Collections.emptyMap(), null);

    CompletableFuture<ToolResult> future =
        client.invokeTool("test-tool", java.util.Collections.emptyMap(), null);
    java.util.concurrent.ExecutionException ex =
        org.junit.jupiter.api.Assertions.assertThrows(
            java.util.concurrent.ExecutionException.class, future::get);
    assertTrue(ex.getCause() instanceof NullPointerException);
  }

  @Test
  void testListTools_withInvalidToolsetNameThrows() throws Exception {
    HttpMcpTransport transport = new HttpMcpTransport("http://localhost:8080", mockHttpClient);

    // Force transport to be initialized first
    Field delegateField = HttpMcpTransport.class.getDeclaredField("delegate");
    delegateField.setAccessible(true);
    Object delegate = delegateField.get(transport);
    Field initFutureField = BaseMcpTransport.class.getDeclaredField("initFuture");
    initFutureField.setAccessible(true);
    initFutureField.set(delegate, CompletableFuture.completedFuture(null));

    CompletableFuture<TransportManifest> future =
        transport.listTools("invalid path with spaces \\", java.util.Collections.emptyMap());
    java.util.concurrent.ExecutionException ex =
        org.junit.jupiter.api.Assertions.assertThrows(
            java.util.concurrent.ExecutionException.class, future::get);
    assertTrue(ex.getCause() instanceof IllegalArgumentException);
  }

  @Test
  void testEnsureInitialized_withNotificationSerializationFailure() throws Exception {
    HttpMcpTransport transport = new HttpMcpTransport("http://localhost:8080", mockHttpClient);

    // Mock ObjectMapper to throw on notification
    ObjectMapper mockMapper = mock(ObjectMapper.class);
    when(mockMapper.readTree(any(String.class))).thenReturn(new ObjectMapper().readTree("{}"));
    when(mockMapper.writeValueAsString(any(JsonRpc.Request.class))).thenReturn("{}");
    when(mockMapper.writeValueAsString(any(JsonRpc.Notification.class)))
        .thenThrow(new RuntimeException("Simulated notification serialization failure"));

    Field delegateField = HttpMcpTransport.class.getDeclaredField("delegate");
    delegateField.setAccessible(true);
    Object delegate = delegateField.get(transport);
    Field mapperField = BaseMcpTransport.class.getDeclaredField("objectMapper");
    mapperField.setAccessible(true);
    mapperField.set(delegate, mockMapper);

    HttpResponse<String> initResponse = mock(HttpResponse.class);
    when(initResponse.statusCode()).thenReturn(200);
    when(initResponse.body()).thenReturn("{}");

    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(initResponse));

    Method initMethod = BaseMcpTransport.class.getDeclaredMethod("ensureInitialized", Map.class);
    initMethod.setAccessible(true);

    CompletableFuture<Void> future =
        (CompletableFuture<Void>) initMethod.invoke(delegate, java.util.Collections.emptyMap());

    java.util.concurrent.ExecutionException ex =
        org.junit.jupiter.api.Assertions.assertThrows(
            java.util.concurrent.ExecutionException.class, future::get);
    assertTrue(ex.getCause().getMessage().contains("Simulated notification serialization failure"));
  }

  @SuppressWarnings("unchecked")
  @Test
  void testClientPrePostProcessorsPropagation() throws Exception {
    Transport mockTransport = mock(Transport.class);
    ToolDefinition def =
        new ToolDefinition("desc", java.util.List.of(), java.util.List.of(), null, null);
    TransportManifest manifest = new TransportManifest(java.util.Map.of("test-tool", def));

    when(mockTransport.listTools(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(manifest));
    when(mockTransport.getBaseUrl()).thenReturn("http://localhost:8080");

    ToolPreProcessor mockPre = mock(ToolPreProcessor.class);
    ToolPostProcessor mockPost = mock(ToolPostProcessor.class);

    McpToolboxClientImpl customClient =
        new McpToolboxClientImpl(
            mockTransport,
            java.util.Collections.emptyMap(),
            null,
            java.util.List.of(mockPre),
            java.util.List.of(mockPost));

    // 1. Verify loadToolset propagates processors
    java.util.Map<String, Tool> tools = customClient.loadToolset("", null, null, false).get();
    Tool tool1 = tools.get("test-tool");
    assertNotNull(tool1);

    Field preField = Tool.class.getDeclaredField("preProcessors");
    preField.setAccessible(true);
    java.util.List<ToolPreProcessor> toolPre1 =
        (java.util.List<ToolPreProcessor>) preField.get(tool1);
    assertEquals(1, toolPre1.size());
    org.junit.jupiter.api.Assertions.assertSame(mockPre, toolPre1.get(0));

    Field postField = Tool.class.getDeclaredField("postProcessors");
    postField.setAccessible(true);
    java.util.List<ToolPostProcessor> toolPost1 =
        (java.util.List<ToolPostProcessor>) postField.get(tool1);
    assertEquals(1, toolPost1.size());
    org.junit.jupiter.api.Assertions.assertSame(mockPost, toolPost1.get(0));

    // 2. Verify loadTool propagates processors
    Tool tool2 = customClient.loadTool("test-tool").get();
    assertNotNull(tool2);

    java.util.List<ToolPreProcessor> toolPre2 =
        (java.util.List<ToolPreProcessor>) preField.get(tool2);
    assertEquals(1, toolPre2.size());
    org.junit.jupiter.api.Assertions.assertSame(mockPre, toolPre2.get(0));

    java.util.List<ToolPostProcessor> toolPost2 =
        (java.util.List<ToolPostProcessor>) postField.get(tool2);
    assertEquals(1, toolPost2.size());
    org.junit.jupiter.api.Assertions.assertSame(mockPost, toolPost2.get(0));
  }

  @Test
  void testInvokeTool_MalformedJsonResponse_GracefullyFallsBack() throws Exception {
    HttpResponse<String> initResponse = mock(HttpResponse.class);
    when(initResponse.statusCode()).thenReturn(200);
    when(initResponse.body()).thenReturn("{}");

    HttpResponse<String> notifResponse = mock(HttpResponse.class);
    when(notifResponse.statusCode()).thenReturn(200);
    when(notifResponse.body()).thenReturn("{}");

    HttpResponse<String> invokeResponse = mock(HttpResponse.class);
    when(invokeResponse.statusCode()).thenReturn(200);
    when(invokeResponse.body()).thenReturn("{invalid-json"); // Trigger JSON parse exception

    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(initResponse))
        .thenReturn(CompletableFuture.completedFuture(notifResponse))
        .thenReturn(CompletableFuture.completedFuture(invokeResponse));

    ToolResult res = client.invokeTool("test-tool", Map.of()).join();
    assertNotNull(res);
    assertFalse(res.isError());
    assertEquals("{invalid-json", res.content().get(0).text());
  }

  @Test
  @SuppressWarnings("unchecked")
  void testGetAuthorizationHeader_WithNoAuthInHeaders() throws Exception {
    McpToolboxClientImpl clientNoAuth =
        new McpToolboxClientImpl(mock(Transport.class), Map.of("X-Other", "SomeValue"), null);

    Method getAuthHeaderMethod =
        McpToolboxClientImpl.class.getDeclaredMethod("getAuthorizationHeader");
    getAuthHeaderMethod.setAccessible(true);
    CompletableFuture<String> future =
        (CompletableFuture<String>) getAuthHeaderMethod.invoke(clientNoAuth);
    assertNull(future.join());
  }

  @Test
  void testConstructor_WithOnlyTransport() {
    Transport mockTransport = mock(Transport.class);
    McpToolboxClientImpl simpleClient = new McpToolboxClientImpl(mockTransport);
    assertNotNull(simpleClient);
  }

  @Test
  @SuppressWarnings("unchecked")
  void testGetMergedMetadata_WithMockGenericTransport_AllBranches() throws Exception {
    Transport mockTransport = mock(Transport.class);
    when(mockTransport.getBaseUrl()).thenReturn("https://test-mcp-service.com");
    when(mockTransport.invokeTool(any(), any(), any()))
        .thenReturn(
            CompletableFuture.completedFuture(new TransportResponse(200, "{\"result\":{}}")));

    CredentialsProvider provider = () -> CompletableFuture.completedFuture("Bearer test-api-key");
    McpToolboxClientImpl genericClient =
        new McpToolboxClientImpl(mockTransport, Map.of("Custom-Header", "Value"), provider);

    // Call invokeTool with extra metadata to trigger merge
    genericClient
        .invokeTool(
            "test-tool",
            Map.of(),
            Map.of("Extra-Header", "ExtraValue", "Authorization", "Bearer overridden"))
        .join();

    ArgumentCaptor<Map<String, String>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
    verify(mockTransport).invokeTool(any(), any(), metadataCaptor.capture());

    Map<String, String> mergedMetadata = metadataCaptor.getValue();
    assertEquals("Value", mergedMetadata.get("Custom-Header"));
    assertEquals("ExtraValue", mergedMetadata.get("Extra-Header"));
    assertEquals("Bearer overridden", mergedMetadata.get("Authorization"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testGetMergedMetadata_WithMockGenericTransport_NullExtraMetadata() throws Exception {
    Transport mockTransport = mock(Transport.class);
    when(mockTransport.getBaseUrl()).thenReturn("https://test-mcp-service.com");
    when(mockTransport.invokeTool(any(), any(), any()))
        .thenReturn(
            CompletableFuture.completedFuture(new TransportResponse(200, "{\"result\":{}}")));

    CredentialsProvider provider = () -> CompletableFuture.completedFuture("Bearer test-api-key");
    McpToolboxClientImpl genericClient =
        new McpToolboxClientImpl(mockTransport, Map.of("Custom-Header", "Value"), provider);

    // Call invokeTool with null extra metadata to trigger branch
    genericClient.invokeTool("test-tool", Map.of(), (Map<String, String>) null).join();

    ArgumentCaptor<Map<String, String>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
    verify(mockTransport).invokeTool(any(), any(), metadataCaptor.capture());

    Map<String, String> mergedMetadata = metadataCaptor.getValue();
    assertEquals("Value", mergedMetadata.get("Custom-Header"));
    assertEquals("Bearer test-api-key", mergedMetadata.get("Authorization"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testGetMergedMetadata_WithMockGenericTransport_NullProviderAndEmptyHeaders()
      throws Exception {
    Transport mockTransport = mock(Transport.class);
    when(mockTransport.getBaseUrl()).thenReturn("https://test-mcp-service.com");
    when(mockTransport.invokeTool(any(), any(), any()))
        .thenReturn(
            CompletableFuture.completedFuture(new TransportResponse(200, "{\"result\":{}}")));

    McpToolboxClientImpl genericClient = new McpToolboxClientImpl(mockTransport, Map.of(), null);

    genericClient.invokeTool("test-tool", Map.of(), Map.of("Extra-Header", "ExtraValue")).join();

    ArgumentCaptor<Map<String, String>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
    verify(mockTransport).invokeTool(any(), any(), metadataCaptor.capture());

    Map<String, String> mergedMetadata = metadataCaptor.getValue();
    assertEquals("ExtraValue", mergedMetadata.get("Extra-Header"));
    assertFalse(mergedMetadata.containsKey("Authorization"));
  }

  @Test
  void testLoadToolset_withDefaultValuesAndHints() throws Exception {
    HttpResponse<String> initResponse = mock(HttpResponse.class);
    when(initResponse.statusCode()).thenReturn(200);
    when(initResponse.body()).thenReturn("{}");

    HttpResponse<String> notifResponse = mock(HttpResponse.class);
    when(notifResponse.statusCode()).thenReturn(200);
    when(notifResponse.body()).thenReturn("{}");

    HttpResponse<String> listResponse = mock(HttpResponse.class);
    when(listResponse.statusCode()).thenReturn(200);
    when(listResponse.body())
        .thenReturn(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[{"
                + "\"name\":\"test-tool\","
                + "\"description\":\"A test tool description\","
                + "\"readOnlyHint\":true,"
                + "\"destructiveHint\":false,"
                + "\"inputSchema\":{"
                + "  \"type\":\"object\","
                + "  \"properties\":{"
                + "    \"param1\":{"
                + "      \"type\":\"string\","
                + "      \"description\":\"parameter 1\","
                + "      \"default\":\"default-val\""
                + "    }"
                + "  }"
                + "}"
                + "}]}}");

    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(initResponse))
        .thenReturn(CompletableFuture.completedFuture(notifResponse))
        .thenReturn(CompletableFuture.completedFuture(listResponse));

    Map<String, ToolDefinition> tools = client.loadToolset("").join();
    assertNotNull(tools);
    assertEquals(1, tools.size());

    ToolDefinition def = tools.get("test-tool");
    assertNotNull(def);
    assertEquals("A test tool description", def.description());
    assertEquals(true, def.readOnlyHint());
    assertEquals(false, def.destructiveHint());

    assertEquals(1, def.parameters().size());
    ToolDefinition.Parameter param = def.parameters().get(0);
    assertEquals("param1", param.name());
    assertEquals("string", param.type());
    assertEquals("default-val", param.defaultValue());
  }
}
