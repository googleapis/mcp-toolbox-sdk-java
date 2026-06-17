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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        .thenReturn("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"protocolVersion\":\"2025-11-25\"}}");

    // 2. Mock response for 'notifications/initialized'
    HttpResponse<String> mockInitializedResponse = mock(HttpResponse.class);
    when(mockInitializedResponse.statusCode()).thenReturn(200);
    when(mockInitializedResponse.body()).thenReturn("");

    // 3. Mock response for 'tools/list'
    HttpResponse<String> mockListResponse = mock(HttpResponse.class);
    when(mockListResponse.statusCode()).thenReturn(200);
    when(mockListResponse.body())
        .thenReturn(
            "{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"result\":{\"tools\":[{\"name\":\"test-tool\","
                + "\"description\":\"A test tool\",\"inputSchema\":{\"type\":\"object\","
                + "\"properties\":{\"param1\":{\"type\":\"string\",\"description\":\"param desc\"}},"
                + "\"required\":[\"param1\"]},\"_meta\":{\"toolbox/authInvoke\":[\"gcp\"]}}]}}");

    CompletableFuture<HttpResponse<String>> initFuture = CompletableFuture.completedFuture(mockInitResponse);
    CompletableFuture<HttpResponse<String>> initializedFuture = CompletableFuture.completedFuture(mockInitializedResponse);
    CompletableFuture<HttpResponse<String>> listFuture = CompletableFuture.completedFuture(mockListResponse);

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
        .thenReturn("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"protocolVersion\":\"2025-11-25\"}}");

    // 2. Mock response for 'notifications/initialized'
    HttpResponse<String> mockInitializedResponse = mock(HttpResponse.class);
    when(mockInitializedResponse.statusCode()).thenReturn(200);
    when(mockInitializedResponse.body()).thenReturn("");

    // 3. Mock response for 'tools/call'
    HttpResponse<String> mockInvokeResponse = mock(HttpResponse.class);
    when(mockInvokeResponse.statusCode()).thenReturn(200);
    when(mockInvokeResponse.body())
        .thenReturn("{\"jsonrpc\":\"2.0\",\"id\":\"3\",\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"success\"}]}}");

    CompletableFuture<HttpResponse<String>> initFuture = CompletableFuture.completedFuture(mockInitResponse);
    CompletableFuture<HttpResponse<String>> initializedFuture = CompletableFuture.completedFuture(mockInitializedResponse);
    CompletableFuture<HttpResponse<String>> invokeFuture = CompletableFuture.completedFuture(mockInvokeResponse);

    when(mockClient.<String>sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(initFuture)
        .thenReturn(initializedFuture)
        .thenReturn(invokeFuture);

    CompletableFuture<String> futureResult =
        transport.invokeTool("test-tool", Map.of("param1", "value1"), Collections.emptyMap());
    String body = futureResult.get();

    assertNotNull(body);
    assertTrue(body.contains("success"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testSubsequentCalls_DoNotReinitialize() throws Exception {
    // 1. Mock response for 'initialize'
    HttpResponse<String> mockInitResponse = mock(HttpResponse.class);
    when(mockInitResponse.statusCode()).thenReturn(200);
    when(mockInitResponse.body())
        .thenReturn("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"protocolVersion\":\"2025-11-25\"}}");

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

    CompletableFuture<HttpResponse<String>> initFuture = CompletableFuture.completedFuture(mockInitResponse);
    CompletableFuture<HttpResponse<String>> initializedFuture = CompletableFuture.completedFuture(mockInitializedResponse);
    CompletableFuture<HttpResponse<String>> listFuture1 = CompletableFuture.completedFuture(mockListResponse1);
    CompletableFuture<HttpResponse<String>> listFuture2 = CompletableFuture.completedFuture(mockListResponse2);

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
    verify(mockClient, times(4)).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
  }
}
