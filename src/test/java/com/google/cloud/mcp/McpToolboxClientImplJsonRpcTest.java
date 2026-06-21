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
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 5, unit = java.util.concurrent.TimeUnit.SECONDS)
class McpToolboxClientImplJsonRpcTest {

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
}
