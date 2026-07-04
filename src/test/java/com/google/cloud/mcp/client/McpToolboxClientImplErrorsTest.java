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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.mcp.auth.CredentialsProvider;
import com.google.cloud.mcp.tool.ToolDefinition;
import com.google.cloud.mcp.tool.ToolResult;
import com.google.cloud.mcp.transport.HttpMcpTransport;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 5, unit = java.util.concurrent.TimeUnit.SECONDS)
class McpToolboxClientImplErrorsTest {

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
