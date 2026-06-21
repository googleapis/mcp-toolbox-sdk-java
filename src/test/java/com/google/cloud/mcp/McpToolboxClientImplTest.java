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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    client = new McpToolboxClientImpl("http://localhost:8080", "test-api-key");
    mockHttpClient = mock(HttpClient.class);

    // Inject mock HttpClient using reflection
    Field httpClientField = McpToolboxClientImpl.class.getDeclaredField("httpClient");
    httpClientField.setAccessible(true);
    httpClientField.set(client, mockHttpClient);
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
  void testConstructor_withNullAndEmptyAndRawApiKeys() throws Exception {
    McpToolboxClientImpl clientNull =
        new McpToolboxClientImpl("http://localhost:8080", (String) null);
    Field headersField = McpToolboxClientImpl.class.getDeclaredField("headers");
    headersField.setAccessible(true);
    Map<String, String> headersNull = (Map<String, String>) headersField.get(clientNull);
    assertTrue(headersNull.isEmpty());

    McpToolboxClientImpl clientEmpty = new McpToolboxClientImpl("http://localhost:8080", "");
    Map<String, String> headersEmpty = (Map<String, String>) headersField.get(clientEmpty);
    assertTrue(headersEmpty.isEmpty());

    McpToolboxClientImpl clientRaw = new McpToolboxClientImpl("http://localhost:8080", "my-key");
    Map<String, String> headersRaw = (Map<String, String>) headersField.get(clientRaw);
    assertEquals("Bearer my-key", headersRaw.get("Authorization"));

    McpToolboxClientImpl clientBearer =
        new McpToolboxClientImpl("http://localhost:8080", "Bearer already-bearer");
    Map<String, String> headersBearer = (Map<String, String>) headersField.get(clientBearer);
    assertEquals("Bearer already-bearer", headersBearer.get("Authorization"));
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
    McpToolboxClientImpl httpsClient =
        new McpToolboxClientImpl("https://localhost:8443", "test-api-key");
    Field httpClientField = McpToolboxClientImpl.class.getDeclaredField("httpClient");
    httpClientField.setAccessible(true);
    httpClientField.set(httpsClient, mockHttpClient);

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
    McpToolboxClientImpl noAuthClient =
        new McpToolboxClientImpl("http://localhost:8080", (String) null);
    Field httpClientField = McpToolboxClientImpl.class.getDeclaredField("httpClient");
    httpClientField.setAccessible(true);
    httpClientField.set(noAuthClient, mockHttpClient);

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
    McpToolboxClientImpl badClient = new McpToolboxClientImpl("http://invalid uri", (String) null);
    Field httpClientField = null;
    try {
      httpClientField = McpToolboxClientImpl.class.getDeclaredField("httpClient");
      httpClientField.setAccessible(true);
      httpClientField.set(badClient, mockHttpClient);
    } catch (Exception e) {
      org.junit.jupiter.api.Assertions.fail(e);
    }

    Exception exception =
        org.junit.jupiter.api.Assertions.assertThrows(
            Exception.class, () -> badClient.listTools().join());
    assertNotNull(exception.getCause());
    assertTrue(exception.getCause() instanceof IllegalArgumentException);
  }

  @Test
  void testInvokeTool_withInvalidUriThrowsException() throws Exception {
    McpToolboxClientImpl badClient = new McpToolboxClientImpl("http://invalid uri", (String) null);
    Field httpClientField = McpToolboxClientImpl.class.getDeclaredField("httpClient");
    httpClientField.setAccessible(true);
    httpClientField.set(badClient, mockHttpClient);

    Field initField = McpToolboxClientImpl.class.getDeclaredField("initialized");
    initField.setAccessible(true);
    initField.set(badClient, true); // bypass initialization

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
  void testGetAuthorizationHeader_withAdcException() throws Exception {
    McpToolboxClientImpl noAuthClient =
        new McpToolboxClientImpl("http://localhost:8080", (String) null);
    Method method = McpToolboxClientImpl.class.getDeclaredMethod("getAuthorizationHeader");
    method.setAccessible(true);

    try (MockedStatic<GoogleCredentials> mockedCredentials = mockStatic(GoogleCredentials.class)) {
      mockedCredentials
          .when(GoogleCredentials::getApplicationDefault)
          .thenThrow(new IOException("Simulated ADC exception"));

      String header = (String) method.invoke(noAuthClient);
      org.junit.jupiter.api.Assertions.assertNull(header);
    }
  }

  @Test
  void testEnsureInitialized_withNullAuthHeader() throws Exception {
    McpToolboxClientImpl noAuthClient =
        new McpToolboxClientImpl("http://localhost:8080", (String) null);
    Field httpClientField = McpToolboxClientImpl.class.getDeclaredField("httpClient");
    httpClientField.setAccessible(true);
    httpClientField.set(noAuthClient, mockHttpClient);

    HttpResponse<String> initResponse = mock(HttpResponse.class);
    when(initResponse.statusCode()).thenReturn(200);
    when(initResponse.body()).thenReturn("{}");

    HttpResponse<String> notifResponse = mock(HttpResponse.class);
    when(notifResponse.statusCode()).thenReturn(200);
    when(notifResponse.body()).thenReturn("{}");

    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(initResponse))
        .thenReturn(CompletableFuture.completedFuture(notifResponse));

    Method initMethod =
        McpToolboxClientImpl.class.getDeclaredMethod("ensureInitialized", String.class);
    initMethod.setAccessible(true);

    CompletableFuture<Void> future =
        (CompletableFuture<Void>) initMethod.invoke(noAuthClient, (String) null);
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

    Field baseUrlField = McpToolboxClientImpl.class.getDeclaredField("baseUrl");
    baseUrlField.setAccessible(true);
    assertEquals("http://localhost:8080", baseUrlField.get(clientWithSlash));

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
    McpToolboxClientImpl customClient =
        new McpToolboxClientImpl("http://localhost:8080", customHeaders);
    Field httpClientField = McpToolboxClientImpl.class.getDeclaredField("httpClient");
    httpClientField.setAccessible(true);
    httpClientField.set(customClient, mockHttpClient);

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
}
