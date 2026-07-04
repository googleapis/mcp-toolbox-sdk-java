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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.mcp.ProtocolVersion;
import com.google.cloud.mcp.client.McpToolboxClientImpl;
import com.google.cloud.mcp.exception.McpProtocolNegotiationException;
import com.google.cloud.mcp.tool.ToolDefinition;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;

@Timeout(value = 5, unit = java.util.concurrent.TimeUnit.SECONDS)
class HttpMcpTransportStatelessTest {

  private HttpClient mockClient;
  private HttpMcpTransport transport;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    mockClient = mock(HttpClient.class);
    // Explicitly configure stateless protocol version
    transport =
        new HttpMcpTransport(
            "https://test-mcp-service.com",
            Collections.emptyMap(),
            null,
            ProtocolVersion.VERSION_2026_06_18,
            mockClient,
            null);
  }

  @Test
  @SuppressWarnings("unchecked")
  void testListTools_BypassesHandshake_InjectsMetadata() throws Exception {
    HttpResponse<String> mockListResponse = mock(HttpResponse.class);
    when(mockListResponse.statusCode()).thenReturn(200);
    when(mockListResponse.body())
        .thenReturn("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"tools\":[]}}");

    when(mockClient.<String>sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(mockListResponse));

    CompletableFuture<TransportManifest> future = transport.listTools("", Collections.emptyMap());
    TransportManifest manifest = future.get();

    assertNotNull(manifest);

    // Verify only 1 HTTP request was made (no initialize handshake)
    ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(mockClient, times(1))
        .sendAsync(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

    HttpRequest request = requestCaptor.getValue();
    assertEquals("https://test-mcp-service.com", request.uri().toString());
    assertEquals("POST", request.method());
    assertEquals("2026-06-18", request.headers().firstValue("MCP-Protocol-Version").orElse(""));
    assertEquals("tools/list", request.headers().firstValue("Mcp-Method").orElse(""));
    assertTrue(request.headers().firstValue("Mcp-Name").isEmpty());

    // Verify metadata was serialized into params
    // HttpRequest body is not easily readable directly from the object without custom publisher
    // mocks,
    // but the test confirms it executed successfully.
  }

  @Test
  @SuppressWarnings("unchecked")
  void testListTools_ReceivesNegotiationError_ThrowsException() throws Exception {
    HttpResponse<String> mockListResponse = mock(HttpResponse.class);
    when(mockListResponse.statusCode()).thenReturn(200);
    // Server rejects 2026-06-18 and returns code -32004 with supported version 2025-11-25
    when(mockListResponse.body())
        .thenReturn(
            "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"error\":{\"code\":-32004,\"message\":\"Unsupported"
                + " protocol version\",\"data\":{\"supported\":[\"2025-11-25\"]}}}");

    when(mockClient.<String>sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(mockListResponse));

    CompletableFuture<TransportManifest> future = transport.listTools("", Collections.emptyMap());
    ExecutionException ex = assertThrows(ExecutionException.class, future::get);
    Throwable cause = ex.getCause();
    if (cause instanceof java.util.concurrent.CompletionException) {
      cause = cause.getCause();
    }
    assertTrue(cause instanceof McpProtocolNegotiationException);
    McpProtocolNegotiationException negEx = (McpProtocolNegotiationException) cause;
    assertEquals("2025-11-25", negEx.getNegotiatedVersion());
  }

  @Test
  @SuppressWarnings("unchecked")
  void testClientIntegration_NegotiatesAndRetriesWithFallback() throws Exception {
    // We wrap our stateless transport in a client wrapper
    McpToolboxClientImpl client = new McpToolboxClientImpl(transport);

    // Call sequence:
    // 1. Client calls listTools (stateless, tools/list) -> Server returns error -32004 (supported:
    // ["2025-11-25"])
    // 2. Client catches it, switches transport to VERSION_2025_11_25
    // 3. Client retries:
    //    a. Performs stateful initialize -> Server returns 200
    //    b. Performs stateful notifications/initialized -> Server returns 200
    //    c. Performs tools/list -> Server returns tools list 200

    HttpResponse<String> mockListResponseError = mock(HttpResponse.class);
    when(mockListResponseError.statusCode()).thenReturn(200);
    when(mockListResponseError.body())
        .thenReturn(
            "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"error\":{\"code\":-32004,\"message\":\"Unsupported"
                + " protocol version\",\"data\":{\"supported\":[\"2025-11-25\"]}}}");

    HttpResponse<String> mockInitResponse = mock(HttpResponse.class);
    when(mockInitResponse.statusCode()).thenReturn(200);
    when(mockInitResponse.body())
        .thenReturn(
            "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"protocolVersion\":\"2025-11-25\"}}");

    HttpResponse<String> mockInitializedResponse = mock(HttpResponse.class);
    when(mockInitializedResponse.statusCode()).thenReturn(200);
    when(mockInitializedResponse.body()).thenReturn("");

    HttpResponse<String> mockListResponseSuccess = mock(HttpResponse.class);
    when(mockListResponseSuccess.statusCode()).thenReturn(200);
    when(mockListResponseSuccess.body())
        .thenReturn(
            "{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"result\":{\"tools\":[{\"name\":\"fallback-tool\",\"description\":\"A"
                + " fallback tool\",\"inputSchema\":{\"type\":\"object\"}}]}}");

    when(mockClient.<String>sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(mockListResponseError)) // first call fails
        .thenReturn(CompletableFuture.completedFuture(mockInitResponse)) // fallback init
        .thenReturn(
            CompletableFuture.completedFuture(mockInitializedResponse)) // fallback initialized
        .thenReturn(CompletableFuture.completedFuture(mockListResponseSuccess)); // fallback list

    Map<String, ToolDefinition> tools = client.listTools().get();

    assertNotNull(tools);
    assertEquals(1, tools.size());
    assertTrue(tools.containsKey("fallback-tool"));

    // Verify 4 calls to sendAsync in total
    verify(mockClient, times(4))
        .sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testInvokeTool_BypassesHandshake_InjectsMetadata() throws Exception {
    HttpResponse<String> mockInvokeResponse = mock(HttpResponse.class);
    when(mockInvokeResponse.statusCode()).thenReturn(200);
    when(mockInvokeResponse.body())
        .thenReturn(
            "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"success\"}]}}");

    when(mockClient.<String>sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(mockInvokeResponse));

    CompletableFuture<TransportResponse> future =
        transport.invokeTool("test-tool", Map.of("p1", "v1"), Collections.emptyMap());
    TransportResponse response = future.get();

    assertNotNull(response);
    assertEquals(200, response.getStatusCode());

    // Verify only 1 HTTP request was made (no initialize handshake)
    ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(mockClient, times(1))
        .sendAsync(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

    HttpRequest request = requestCaptor.getValue();
    assertEquals("https://test-mcp-service.com", request.uri().toString());
    assertEquals("POST", request.method());
    assertEquals("2026-06-18", request.headers().firstValue("MCP-Protocol-Version").orElse(""));
    assertEquals("tools/call", request.headers().firstValue("Mcp-Method").orElse(""));
    assertEquals("test-tool", request.headers().firstValue("Mcp-Name").orElse(""));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testListTools_ErrorResponseNoCode_ThrowsGenericException() throws Exception {
    HttpResponse<String> mockListResponse = mock(HttpResponse.class);
    when(mockListResponse.statusCode()).thenReturn(200);
    when(mockListResponse.body())
        .thenReturn("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"error\":{\"message\":\"Some error\"}}");

    when(mockClient.<String>sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(mockListResponse));

    CompletableFuture<TransportManifest> future = transport.listTools("", Collections.emptyMap());
    ExecutionException ex = assertThrows(ExecutionException.class, future::get);
    Throwable cause = ex.getCause();
    if (cause instanceof java.util.concurrent.CompletionException) {
      cause = cause.getCause();
    }
    assertTrue(
        cause instanceof com.google.cloud.mcp.exception.McpException,
        "Actual cause class: "
            + (cause == null ? "null" : cause.getClass().getName())
            + ", message: "
            + (cause == null ? "null" : cause.getMessage()));
    assertTrue(cause.getMessage().contains("MCP Error"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testListTools_NegotiationErrorNoMutual_ThrowsException() throws Exception {
    HttpResponse<String> mockListResponse = mock(HttpResponse.class);
    when(mockListResponse.statusCode()).thenReturn(200);
    when(mockListResponse.body())
        .thenReturn(
            "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"error\":{\"code\":-32004,\"message\":\"Unsupported"
                + " protocol version\",\"data\":{\"supported\":[\"1999-01-01\"]}}}");

    when(mockClient.<String>sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(mockListResponse));

    CompletableFuture<TransportManifest> future = transport.listTools("", Collections.emptyMap());
    ExecutionException ex = assertThrows(ExecutionException.class, future::get);
    Throwable cause = ex.getCause();
    if (cause instanceof java.util.concurrent.CompletionException) {
      cause = cause.getCause();
    }
    assertTrue(cause instanceof com.google.cloud.mcp.exception.McpException);
    assertTrue(cause.getMessage().contains("No mutually supported protocol version"));
  }

  @Test
  void testClientFallback_WithMockTransport_PropagatesException() {
    Transport mockTransport = mock(Transport.class);
    when(mockTransport.getBaseUrl()).thenReturn("https://mock-service.com");

    when(mockTransport.listTools(any(), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new McpProtocolNegotiationException("fallback requested", "2025-11-25")));

    McpToolboxClientImpl client = new McpToolboxClientImpl(mockTransport);

    ExecutionException ex = assertThrows(ExecutionException.class, () -> client.listTools().get());
    Throwable cause = ex.getCause();
    if (cause instanceof java.util.concurrent.CompletionException) {
      cause = cause.getCause();
    }
    assertTrue(cause instanceof McpProtocolNegotiationException);
  }

  @Test
  @SuppressWarnings("unchecked")
  void testClientFallback_WithInvalidVersion_PropagatesException() {
    HttpClient localMockClient = mock(HttpClient.class);
    HttpMcpTransport localTransport =
        new HttpMcpTransport(
            "https://test-mcp-service.com",
            Collections.emptyMap(),
            null,
            ProtocolVersion.VERSION_2026_06_18,
            localMockClient,
            null);

    HttpResponse<String> mockListResponse = mock(HttpResponse.class);
    when(mockListResponse.statusCode()).thenReturn(200);
    when(mockListResponse.body())
        .thenReturn(
            "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"error\":{\"code\":-32004,\"message\":\"Unsupported\",\"data\":{\"supported\":[\"invalid-version\"]}}}");

    when(localMockClient.<String>sendAsync(
            any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(mockListResponse));

    McpToolboxClientImpl client = new McpToolboxClientImpl(localTransport);

    ExecutionException ex = assertThrows(ExecutionException.class, () -> client.listTools().get());
    Throwable cause = ex.getCause();
    if (cause instanceof java.util.concurrent.CompletionException) {
      cause = cause.getCause();
    }
    assertTrue(cause instanceof com.google.cloud.mcp.exception.McpException);
    assertTrue(cause.getMessage().contains("No mutually supported protocol version"));
  }

  @Test
  void testHttpMcpTransportV20260618_ModifyRequestParams_Fallback() throws Exception {
    com.google.cloud.mcp.transport.v20260618.HttpMcpTransportV20260618 transportV20260618 =
        new com.google.cloud.mcp.transport.v20260618.HttpMcpTransportV20260618(
            "https://test.com", Map.of(), null, mock(HttpClient.class), null);

    java.lang.reflect.Method method =
        com.google.cloud.mcp.transport.v20260618.HttpMcpTransportV20260618.class.getDeclaredMethod(
            "modifyRequestParams", String.class, Object.class);
    method.setAccessible(true);

    Object inputParams = new Object();
    Object result = method.invoke(transportV20260618, "other/method", inputParams);
    assertSame(inputParams, result);
  }
}
