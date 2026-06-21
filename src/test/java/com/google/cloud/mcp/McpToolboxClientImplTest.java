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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
  void testEnsureInitializedFailsWith500() {
    HttpResponse<String> initResponse = mock(HttpResponse.class);
    when(initResponse.statusCode()).thenReturn(500);
    when(initResponse.body()).thenReturn("Internal Server Error");

    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(initResponse));

    CompletableFuture<Map<String, ToolDefinition>> future = client.listTools();

    Exception exception =
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, future::join);

    Throwable cause = exception.getCause();
    assertNotNull(cause);
    assertTrue(cause instanceof RuntimeException);
    assertTrue(cause.getMessage().contains("Init failed: 500"));
    assertTrue(cause.getMessage().contains("Internal Server Error"));
  }

  @Test
  void testEnsureInitializedFailsWith401() {
    HttpResponse<String> initResponse = mock(HttpResponse.class);
    when(initResponse.statusCode()).thenReturn(401);
    when(initResponse.body()).thenReturn("Unauthorized");

    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(initResponse));

    CompletableFuture<Map<String, ToolDefinition>> future = client.listTools();

    Exception exception =
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, future::join);

    Throwable cause = exception.getCause();
    assertNotNull(cause);
    assertTrue(cause instanceof RuntimeException);
    assertTrue(cause.getMessage().contains("Init failed: 401"));
    assertTrue(cause.getMessage().contains("Unauthorized"));
  }

  @Test
  void testInvokeToolFailsDuringInitializationWith403() {
    HttpResponse<String> initResponse = mock(HttpResponse.class);
    when(initResponse.statusCode()).thenReturn(403);
    when(initResponse.body()).thenReturn("Forbidden");

    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(initResponse));

    CompletableFuture<ToolResult> future = client.invokeTool("test-tool", Map.of());

    Exception exception =
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, future::join);

    Throwable cause = exception.getCause();
    assertNotNull(cause);
    assertTrue(cause instanceof RuntimeException);
    assertTrue(cause.getMessage().contains("Init failed: 403"));
    assertTrue(cause.getMessage().contains("Forbidden"));
  }

  @Test
  void testListToolsFailsWith500AfterInit() {
    HttpResponse<String> initResponse = mock(HttpResponse.class);
    when(initResponse.statusCode()).thenReturn(200);
    when(initResponse.body()).thenReturn("{}");

    HttpResponse<String> notifResponse = mock(HttpResponse.class);
    when(notifResponse.statusCode()).thenReturn(200);
    when(notifResponse.body()).thenReturn("{}");

    HttpResponse<String> listResponse = mock(HttpResponse.class);
    when(listResponse.statusCode()).thenReturn(500);
    when(listResponse.body()).thenReturn("Internal Server Error");

    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(initResponse))
        .thenReturn(CompletableFuture.completedFuture(notifResponse))
        .thenReturn(CompletableFuture.completedFuture(listResponse));

    CompletableFuture<Map<String, ToolDefinition>> future = client.listTools();

    Exception exception =
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, future::join);

    Throwable cause = exception.getCause();
    assertNotNull(cause);
    assertTrue(cause instanceof RuntimeException);
    assertTrue(cause.getMessage().contains("Failed to list tools. Status: 500"));
    assertTrue(cause.getMessage().contains("Internal Server Error"));
  }

  @Test
  void testInvokeToolReturnsErrorOnNon200Response() {
    HttpResponse<String> initResponse = mock(HttpResponse.class);
    when(initResponse.statusCode()).thenReturn(200);
    when(initResponse.body()).thenReturn("{}");

    HttpResponse<String> notifResponse = mock(HttpResponse.class);
    when(notifResponse.statusCode()).thenReturn(200);
    when(notifResponse.body()).thenReturn("{}");

    HttpResponse<String> callResponse = mock(HttpResponse.class);
    when(callResponse.statusCode()).thenReturn(500);
    when(callResponse.body()).thenReturn("Internal Server Error");

    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(initResponse))
        .thenReturn(CompletableFuture.completedFuture(notifResponse))
        .thenReturn(CompletableFuture.completedFuture(callResponse));

    ToolResult result = client.invokeTool("test-tool", Map.of()).join();

    assertNotNull(result);
    assertTrue(result.isError());
    assertEquals(1, result.content().size());
    assertTrue(result.content().get(0).text().contains("Error 500"));
    assertTrue(result.content().get(0).text().contains("Internal Server Error"));
  }

  @Test
  void testListToolsThrowsIOExceptionOnSend() {
    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.failedFuture(new java.io.IOException("Connection reset")));

    CompletableFuture<Map<String, ToolDefinition>> future = client.listTools();

    Exception exception =
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, future::join);

    Throwable cause = exception.getCause();
    assertNotNull(cause);
    assertTrue(cause instanceof java.io.IOException);
    assertEquals("Connection reset", cause.getMessage());
  }

  @Test
  void testListToolsThrowsIOExceptionOnListRequest() {
    HttpResponse<String> initResponse = mock(HttpResponse.class);
    when(initResponse.statusCode()).thenReturn(200);
    when(initResponse.body()).thenReturn("{}");

    HttpResponse<String> notifResponse = mock(HttpResponse.class);
    when(notifResponse.statusCode()).thenReturn(200);
    when(notifResponse.body()).thenReturn("{}");

    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(initResponse))
        .thenReturn(CompletableFuture.completedFuture(notifResponse))
        .thenReturn(CompletableFuture.failedFuture(new java.io.IOException("Connection timeout")));

    CompletableFuture<Map<String, ToolDefinition>> future = client.listTools();

    Exception exception =
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, future::join);

    Throwable cause = exception.getCause();
    assertNotNull(cause);
    assertTrue(cause instanceof java.io.IOException);
    assertEquals("Connection timeout", cause.getMessage());
  }

  @Test
  void testInvokeToolThrowsIOExceptionOnSend() {
    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.failedFuture(new java.io.IOException("Timeout occurred")));

    CompletableFuture<ToolResult> future = client.invokeTool("test-tool", Map.of());

    Exception exception =
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, future::join);

    Throwable cause = exception.getCause();
    assertNotNull(cause);
    assertTrue(cause instanceof java.io.IOException);
    assertEquals("Timeout occurred", cause.getMessage());
  }

  @Test
  void testCustomHeadersPopulatedInAllRequests() throws Exception {
    McpToolboxClient client =
        new McpToolboxClientBuilder()
            .baseUrl("http://localhost:8080")
            .apiKey("client-api-key")
            .headers(Map.of("X-Client-Header", "client-value", "X-Common-Header", "client-common"))
            .build();

    HttpClient mockHttpClient = mock(HttpClient.class);
    Field httpClientField = McpToolboxClientImpl.class.getDeclaredField("httpClient");
    httpClientField.setAccessible(true);
    httpClientField.set(client, mockHttpClient);

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

    String callBody =
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":[{\"type\":\"text\","
            + "\"text\":\"success\"}],\"isError\":false}}";
    HttpResponse<String> callResponse = mock(HttpResponse.class);
    when(callResponse.statusCode()).thenReturn(200);
    when(callResponse.body()).thenReturn(callBody);

    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(initResponse))
        .thenReturn(CompletableFuture.completedFuture(notifResponse))
        .thenReturn(CompletableFuture.completedFuture(listResponse))
        .thenReturn(CompletableFuture.completedFuture(callResponse));

    // Call listTools (which initializes first)
    client.listTools().join();
    // Call invokeTool
    client.invokeTool("test-tool", Map.of("param1", "value1")).join();

    ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(mockHttpClient, times(4)).sendAsync(requestCaptor.capture(), any());

    // 1st request: initialize
    HttpRequest initReq = requestCaptor.getAllValues().get(0);
    assertEquals("client-value", initReq.headers().firstValue("X-Client-Header").orElse(null));
    assertEquals("client-common", initReq.headers().firstValue("X-Common-Header").orElse(null));
    assertEquals(
        "Bearer client-api-key", initReq.headers().firstValue("Authorization").orElse(null));

    // 2nd request: notifications/initialized
    HttpRequest notifReq = requestCaptor.getAllValues().get(1);
    assertEquals("client-value", notifReq.headers().firstValue("X-Client-Header").orElse(null));
    assertEquals("client-common", notifReq.headers().firstValue("X-Common-Header").orElse(null));
    assertEquals(
        "Bearer client-api-key", notifReq.headers().firstValue("Authorization").orElse(null));

    // 3rd request: tools/list
    HttpRequest listReq = requestCaptor.getAllValues().get(2);
    assertEquals("client-value", listReq.headers().firstValue("X-Client-Header").orElse(null));
    assertEquals("client-common", listReq.headers().firstValue("X-Common-Header").orElse(null));
    assertEquals(
        "Bearer client-api-key", listReq.headers().firstValue("Authorization").orElse(null));

    // 4th request: tools/call
    HttpRequest callReq = requestCaptor.getAllValues().get(3);
    assertEquals("client-value", callReq.headers().firstValue("X-Client-Header").orElse(null));
    assertEquals("client-common", callReq.headers().firstValue("X-Common-Header").orElse(null));
    assertEquals(
        "Bearer client-api-key", callReq.headers().firstValue("Authorization").orElse(null));
  }

  @Test
  void testExtraHeadersOverrideAndAuthPriority() throws Exception {
    McpToolboxClient client =
        new McpToolboxClientBuilder()
            .baseUrl("http://localhost:8080")
            .apiKey("client-api-key")
            .headers(Map.of("X-Client-Header", "client-value", "X-Common-Header", "client-common"))
            .build();

    HttpClient mockHttpClient = mock(HttpClient.class);
    Field httpClientField = McpToolboxClientImpl.class.getDeclaredField("httpClient");
    httpClientField.setAccessible(true);
    httpClientField.set(client, mockHttpClient);

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

    // Call invokeTool directly (which will initialize client first)
    // Pass extraHeaders containing X-Common-Header override and Authorization override
    Map<String, String> extraHeaders =
        Map.of("X-Common-Header", "override-common", "Authorization", "Bearer extra-auth-key");
    client.invokeTool("test-tool", Map.of("param1", "value1"), extraHeaders).join();

    ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(mockHttpClient, times(3)).sendAsync(requestCaptor.capture(), any());

    // 1st request: initialize
    HttpRequest initReq = requestCaptor.getAllValues().get(0);
    assertEquals("client-value", initReq.headers().firstValue("X-Client-Header").orElse(null));
    assertEquals("client-common", initReq.headers().firstValue("X-Common-Header").orElse(null));
    assertEquals(
        "Bearer extra-auth-key", initReq.headers().firstValue("Authorization").orElse(null));

    // 2nd request: notifications/initialized
    HttpRequest notifReq = requestCaptor.getAllValues().get(1);
    assertEquals("client-value", notifReq.headers().firstValue("X-Client-Header").orElse(null));
    assertEquals("client-common", notifReq.headers().firstValue("X-Common-Header").orElse(null));
    assertEquals(
        "Bearer extra-auth-key", notifReq.headers().firstValue("Authorization").orElse(null));

    // 3rd request: tools/call
    HttpRequest callReq = requestCaptor.getAllValues().get(2);
    assertEquals("client-value", callReq.headers().firstValue("X-Client-Header").orElse(null));
    assertEquals("override-common", callReq.headers().firstValue("X-Common-Header").orElse(null));
    assertEquals(
        "Bearer extra-auth-key", callReq.headers().firstValue("Authorization").orElse(null));
  }

  @Test
  void testNoDuplicateHeaders() throws Exception {
    Map<String, String> customHeaders = new HashMap<>();
    customHeaders.put("X-Test-Header", "value1");
    customHeaders.put("x-test-header", "value2");
    customHeaders.put("Authorization", "Bearer initial-token");
    customHeaders.put("authorization", "Bearer lowercase-token");

    McpToolboxClient client =
        new McpToolboxClientBuilder()
            .baseUrl("http://localhost:8080")
            .headers(customHeaders)
            .build();

    HttpClient mockHttpClient = mock(HttpClient.class);
    Field httpClientField = McpToolboxClientImpl.class.getDeclaredField("httpClient");
    httpClientField.setAccessible(true);
    httpClientField.set(client, mockHttpClient);

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

    client.listTools().join();

    ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(mockHttpClient, times(3)).sendAsync(requestCaptor.capture(), any());

    for (HttpRequest request : requestCaptor.getAllValues()) {
      java.net.http.HttpHeaders headers = request.headers();

      // Verify Authorization is not duplicated
      List<String> authHeaders = headers.allValues("Authorization");
      assertEquals(1, authHeaders.size(), "Authorization header should have exactly one value");

      // Verify X-Test-Header is not duplicated
      List<String> testHeaders = headers.allValues("X-Test-Header");
      assertEquals(1, testHeaders.size(), "X-Test-Header should have exactly one value");
    }
  }

  @Test
  void testListTools_ProtocolError() throws Exception {
    HttpResponse<String> errorResponse = mock(HttpResponse.class);
    when(errorResponse.statusCode()).thenReturn(200);
    when(errorResponse.body())
        .thenReturn(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32601,\"message\":\"Method not"
                + " found\"}}");

    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenAnswer(
            invocation -> {
              HttpRequest req = invocation.getArgument(0);
              if (getBodyString(req).contains("tools/list")) {
                return CompletableFuture.completedFuture(errorResponse);
              }
              HttpResponse<String> initRes = mock(HttpResponse.class);
              when(initRes.statusCode()).thenReturn(200);
              when(initRes.body()).thenReturn("{}");
              return CompletableFuture.completedFuture(initRes);
            });

    CompletionException exception =
        assertThrows(CompletionException.class, () -> client.listTools().join());
    assertTrue(exception.getCause() instanceof RuntimeException);
    assertTrue(exception.getCause().getMessage().contains("MCP Error:"));
    assertTrue(exception.getCause().getMessage().contains("Method not found"));
  }

  @Test
  void testInvokeTool_ProtocolError() throws Exception {
    HttpResponse<String> errorResponse = mock(HttpResponse.class);
    when(errorResponse.statusCode()).thenReturn(200);
    when(errorResponse.body())
        .thenReturn(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32602,\"message\":\"Invalid"
                + " params\"}}");

    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenAnswer(
            invocation -> {
              HttpRequest req = invocation.getArgument(0);
              if (getBodyString(req).contains("tools/call")) {
                return CompletableFuture.completedFuture(errorResponse);
              }
              HttpResponse<String> initRes = mock(HttpResponse.class);
              when(initRes.statusCode()).thenReturn(200);
              when(initRes.body()).thenReturn("{}");
              return CompletableFuture.completedFuture(initRes);
            });

    ToolResult result = client.invokeTool("test-tool", Collections.emptyMap()).join();
    assertNotNull(result);
    assertTrue(result.isError());
    assertEquals(1, result.content().size());
    assertTrue(result.content().get(0).text().contains("MCP Error:"));
    assertTrue(result.content().get(0).text().contains("Invalid params"));
  }

  @Test
  void testListTools_MalformedJson() throws Exception {
    HttpResponse<String> malformedResponse = mock(HttpResponse.class);
    when(malformedResponse.statusCode()).thenReturn(200);
    when(malformedResponse.body()).thenReturn("{\"invalid_json");

    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenAnswer(
            invocation -> {
              HttpRequest req = invocation.getArgument(0);
              if (getBodyString(req).contains("tools/list")) {
                return CompletableFuture.completedFuture(malformedResponse);
              }
              HttpResponse<String> initRes = mock(HttpResponse.class);
              when(initRes.statusCode()).thenReturn(200);
              when(initRes.body()).thenReturn("{}");
              return CompletableFuture.completedFuture(initRes);
            });

    CompletionException exception =
        assertThrows(CompletionException.class, () -> client.listTools().join());
    assertTrue(exception.getCause() instanceof RuntimeException);
    assertTrue(
        exception.getCause().getCause()
            instanceof com.fasterxml.jackson.core.JsonProcessingException);
  }

  @Test
  void testInvokeTool_MalformedJson() throws Exception {
    HttpResponse<String> malformedResponse = mock(HttpResponse.class);
    when(malformedResponse.statusCode()).thenReturn(200);
    when(malformedResponse.body()).thenReturn("{\"invalid_json");

    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenAnswer(
            invocation -> {
              HttpRequest req = invocation.getArgument(0);
              if (getBodyString(req).contains("tools/call")) {
                return CompletableFuture.completedFuture(malformedResponse);
              }
              HttpResponse<String> initRes = mock(HttpResponse.class);
              when(initRes.statusCode()).thenReturn(200);
              when(initRes.body()).thenReturn("{}");
              return CompletableFuture.completedFuture(initRes);
            });

    ToolResult result = client.invokeTool("test-tool", Collections.emptyMap()).join();
    assertNotNull(result);
    assertFalse(result.isError());
    assertEquals(1, result.content().size());
    assertEquals("{\"invalid_json", result.content().get(0).text());
  }

  @Test
  void testListTools_MissingResult() throws Exception {
    HttpResponse<String> missingResultResponse = mock(HttpResponse.class);
    when(missingResultResponse.statusCode()).thenReturn(200);
    when(missingResultResponse.body()).thenReturn("{\"jsonrpc\":\"2.0\",\"id\":1}");

    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenAnswer(
            invocation -> {
              HttpRequest req = invocation.getArgument(0);
              if (getBodyString(req).contains("tools/list")) {
                return CompletableFuture.completedFuture(missingResultResponse);
              }
              HttpResponse<String> initRes = mock(HttpResponse.class);
              when(initRes.statusCode()).thenReturn(200);
              when(initRes.body()).thenReturn("{}");
              return CompletableFuture.completedFuture(initRes);
            });

    CompletionException exception =
        assertThrows(CompletionException.class, () -> client.listTools().join());
    assertTrue(exception.getCause() instanceof RuntimeException);
    assertTrue(exception.getCause().getCause() instanceof NullPointerException);
  }

  @Test
  void testListTools_EmptyResult() throws Exception {
    HttpResponse<String> emptyResultResponse = mock(HttpResponse.class);
    when(emptyResultResponse.statusCode()).thenReturn(200);
    when(emptyResultResponse.body()).thenReturn("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}");

    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenAnswer(
            invocation -> {
              HttpRequest req = invocation.getArgument(0);
              if (getBodyString(req).contains("tools/list")) {
                return CompletableFuture.completedFuture(emptyResultResponse);
              }
              HttpResponse<String> initRes = mock(HttpResponse.class);
              when(initRes.statusCode()).thenReturn(200);
              when(initRes.body()).thenReturn("{}");
              return CompletableFuture.completedFuture(initRes);
            });

    Map<String, ToolDefinition> tools = client.listTools().join();
    assertNotNull(tools);
    assertTrue(tools.isEmpty());
  }

  @Test
  void testInvokeTool_MissingResult() throws Exception {
    HttpResponse<String> missingResultResponse = mock(HttpResponse.class);
    when(missingResultResponse.statusCode()).thenReturn(200);
    when(missingResultResponse.body()).thenReturn("{\"jsonrpc\":\"2.0\",\"id\":1}");

    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenAnswer(
            invocation -> {
              HttpRequest req = invocation.getArgument(0);
              if (getBodyString(req).contains("tools/call")) {
                return CompletableFuture.completedFuture(missingResultResponse);
              }
              HttpResponse<String> initRes = mock(HttpResponse.class);
              when(initRes.statusCode()).thenReturn(200);
              when(initRes.body()).thenReturn("{}");
              return CompletableFuture.completedFuture(initRes);
            });

    ToolResult result = client.invokeTool("test-tool", Collections.emptyMap()).join();
    assertNotNull(result);
    assertFalse(result.isError());
    assertEquals(1, result.content().size());
    assertEquals("{\"jsonrpc\":\"2.0\",\"id\":1}", result.content().get(0).text());
  }

  @Test
  void testInvokeTool_EmptyResult() throws Exception {
    HttpResponse<String> emptyResultResponse = mock(HttpResponse.class);
    when(emptyResultResponse.statusCode()).thenReturn(200);
    when(emptyResultResponse.body()).thenReturn("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}");

    when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenAnswer(
            invocation -> {
              HttpRequest req = invocation.getArgument(0);
              if (getBodyString(req).contains("tools/call")) {
                return CompletableFuture.completedFuture(emptyResultResponse);
              }
              HttpResponse<String> initRes = mock(HttpResponse.class);
              when(initRes.statusCode()).thenReturn(200);
              when(initRes.body()).thenReturn("{}");
              return CompletableFuture.completedFuture(initRes);
            });

    ToolResult result = client.invokeTool("test-tool", Collections.emptyMap()).join();
    assertNotNull(result);
    assertFalse(result.isError());
    assertEquals(1, result.content().size());
    assertEquals("", result.content().get(0).text());
  }
}
