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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.mcp.McpToolboxClient;
import com.google.cloud.mcp.auth.CredentialsProvider;
import com.google.cloud.mcp.transport.BaseMcpTransport;
import com.google.cloud.mcp.transport.HttpMcpTransport;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;

@Timeout(value = 5, unit = java.util.concurrent.TimeUnit.SECONDS)
class McpToolboxClientImplHeadersTest {

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
  void testCustomHeadersPopulatedInAllRequests() throws Exception {
    McpToolboxClient client =
        new McpToolboxClientBuilder()
            .baseUrl("http://localhost:8080")
            .apiKey("client-api-key")
            .headers(Map.of("X-Client-Header", "client-value", "X-Common-Header", "client-common"))
            .build();

    HttpClient mockHttpClient = mock(HttpClient.class);
    Field transportField = McpToolboxClientImpl.class.getDeclaredField("transport");
    transportField.setAccessible(true);
    HttpMcpTransport transport = (HttpMcpTransport) transportField.get(client);
    Field delegateField = HttpMcpTransport.class.getDeclaredField("delegate");
    delegateField.setAccessible(true);
    Object delegate = delegateField.get(transport);
    Field httpClientField = BaseMcpTransport.class.getDeclaredField("httpClient");
    httpClientField.setAccessible(true);
    httpClientField.set(delegate, mockHttpClient);

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
    Field transportField = McpToolboxClientImpl.class.getDeclaredField("transport");
    transportField.setAccessible(true);
    HttpMcpTransport transport = (HttpMcpTransport) transportField.get(client);
    Field delegateField = HttpMcpTransport.class.getDeclaredField("delegate");
    delegateField.setAccessible(true);
    Object delegate = delegateField.get(transport);
    Field httpClientField = BaseMcpTransport.class.getDeclaredField("httpClient");
    httpClientField.setAccessible(true);
    httpClientField.set(delegate, mockHttpClient);

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
    Field transportField = McpToolboxClientImpl.class.getDeclaredField("transport");
    transportField.setAccessible(true);
    HttpMcpTransport transport = (HttpMcpTransport) transportField.get(client);
    Field delegateField = HttpMcpTransport.class.getDeclaredField("delegate");
    delegateField.setAccessible(true);
    Object delegate = delegateField.get(transport);
    Field httpClientField = BaseMcpTransport.class.getDeclaredField("httpClient");
    httpClientField.setAccessible(true);
    httpClientField.set(delegate, mockHttpClient);

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
