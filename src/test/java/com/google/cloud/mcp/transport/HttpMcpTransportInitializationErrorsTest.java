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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.mcp.ProtocolVersion;
import com.google.cloud.mcp.exception.McpException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 5, unit = java.util.concurrent.TimeUnit.SECONDS)
class HttpMcpTransportInitializationErrorsTest {

  private Object getDelegate(HttpMcpTransport transport) throws Exception {
    java.lang.reflect.Field field = HttpMcpTransport.class.getDeclaredField("delegate");
    field.setAccessible(true);
    return field.get(transport);
  }

  private void setMockObjectMapper(BaseMcpTransport transport, ObjectMapper mockMapper)
      throws Exception {
    java.lang.reflect.Field field = BaseMcpTransport.class.getDeclaredField("objectMapper");
    field.setAccessible(true);
    field.set(transport, mockMapper);
  }

  @Test
  @SuppressWarnings("unchecked")
  void testInitialize_ServerReturnsErrorJsonRpcResponse() throws Exception {
    List<ProtocolVersion> versions =
        List.of(
            ProtocolVersion.VERSION_2025_11_25,
            ProtocolVersion.VERSION_2025_06_18,
            ProtocolVersion.VERSION_2025_03_26,
            ProtocolVersion.VERSION_2024_11_05);
    for (ProtocolVersion version : versions) {
      HttpClient localMockClient = mock(HttpClient.class);
      HttpMcpTransport versionedTransport =
          new HttpMcpTransport(
              "https://test-mcp-service.com",
              Collections.emptyMap(),
              null,
              version,
              localMockClient,
              null);

      HttpResponse<String> mockInitResponse = mock(HttpResponse.class);
      when(mockInitResponse.statusCode()).thenReturn(200);
      when(mockInitResponse.body())
          .thenReturn(
              "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"error\":{\"code\":-32603,\"message\":\"Internal"
                  + " error\"}}");

      when(localMockClient.<String>sendAsync(
              any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
          .thenReturn(CompletableFuture.completedFuture(mockInitResponse));

      CompletableFuture<TransportManifest> future =
          versionedTransport.listTools("", Collections.emptyMap());
      java.util.concurrent.ExecutionException ex =
          org.junit.jupiter.api.Assertions.assertThrows(
              java.util.concurrent.ExecutionException.class, future::get);
      assertTrue(ex.getCause() instanceof McpException);
      assertTrue(ex.getCause().getMessage().contains("MCP Error"));
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void testListTools_Non200Response_ThrowsException() {
    List<ProtocolVersion> versions =
        List.of(
            ProtocolVersion.VERSION_2025_11_25,
            ProtocolVersion.VERSION_2025_06_18,
            ProtocolVersion.VERSION_2025_03_26,
            ProtocolVersion.VERSION_2024_11_05);
    for (ProtocolVersion version : versions) {
      HttpClient localMockClient = mock(HttpClient.class);
      HttpMcpTransport versionedTransport =
          new HttpMcpTransport(
              "https://test-mcp-service.com",
              Collections.emptyMap(),
              null,
              version,
              localMockClient,
              null);

      HttpResponse<String> mockInitResponse = mock(HttpResponse.class);
      when(mockInitResponse.statusCode()).thenReturn(200);
      when(mockInitResponse.body())
          .thenReturn(
              "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"protocolVersion\":\""
                  + version.getValue()
                  + "\"}}");
      java.net.http.HttpHeaders mockHeaders =
          java.net.http.HttpHeaders.of(
              Map.of("Mcp-Session-Id", List.of("test-session-123")), (k, v) -> true);
      when(mockInitResponse.headers()).thenReturn(mockHeaders);

      HttpResponse<String> mockInitializedResponse = mock(HttpResponse.class);
      when(mockInitializedResponse.statusCode()).thenReturn(200);
      when(mockInitializedResponse.body()).thenReturn("");

      HttpResponse<String> mockErrorResponse = mock(HttpResponse.class);
      when(mockErrorResponse.statusCode()).thenReturn(500);
      when(mockErrorResponse.body()).thenReturn("Internal Server Error");

      when(localMockClient.<String>sendAsync(
              any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
          .thenReturn(CompletableFuture.completedFuture(mockInitResponse))
          .thenReturn(CompletableFuture.completedFuture(mockInitializedResponse))
          .thenReturn(CompletableFuture.completedFuture(mockErrorResponse));

      Exception ex =
          org.junit.jupiter.api.Assertions.assertThrows(
              Exception.class,
              () -> versionedTransport.listTools("", Collections.emptyMap()).get());
      assertTrue(ex.getCause().getMessage().contains("Status: 500"));
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void testListTools_JsonRpcError_ThrowsException() {
    List<ProtocolVersion> versions =
        List.of(
            ProtocolVersion.VERSION_2025_11_25,
            ProtocolVersion.VERSION_2025_06_18,
            ProtocolVersion.VERSION_2025_03_26,
            ProtocolVersion.VERSION_2024_11_05);
    for (ProtocolVersion version : versions) {
      HttpClient localMockClient = mock(HttpClient.class);
      HttpMcpTransport versionedTransport =
          new HttpMcpTransport(
              "https://test-mcp-service.com",
              Collections.emptyMap(),
              null,
              version,
              localMockClient,
              null);

      HttpResponse<String> mockInitResponse = mock(HttpResponse.class);
      when(mockInitResponse.statusCode()).thenReturn(200);
      when(mockInitResponse.body())
          .thenReturn(
              "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"protocolVersion\":\""
                  + version.getValue()
                  + "\"}}");
      java.net.http.HttpHeaders mockHeaders =
          java.net.http.HttpHeaders.of(
              Map.of("Mcp-Session-Id", List.of("test-session-123")), (k, v) -> true);
      when(mockInitResponse.headers()).thenReturn(mockHeaders);

      HttpResponse<String> mockInitializedResponse = mock(HttpResponse.class);
      when(mockInitializedResponse.statusCode()).thenReturn(200);
      when(mockInitializedResponse.body()).thenReturn("");

      HttpResponse<String> mockErrorResponse = mock(HttpResponse.class);
      when(mockErrorResponse.statusCode()).thenReturn(200);
      when(mockErrorResponse.body())
          .thenReturn(
              "{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"error\":{\"code\":-1,\"message\":\"Custom"
                  + " error\"}}");

      when(localMockClient.<String>sendAsync(
              any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
          .thenReturn(CompletableFuture.completedFuture(mockInitResponse))
          .thenReturn(CompletableFuture.completedFuture(mockInitializedResponse))
          .thenReturn(CompletableFuture.completedFuture(mockErrorResponse));

      Exception ex =
          org.junit.jupiter.api.Assertions.assertThrows(
              Exception.class,
              () -> versionedTransport.listTools("", Collections.emptyMap()).get());
      assertTrue(ex.getCause().getMessage().contains("Custom error"));
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void testInitialize_VersionMismatch_ThrowsException() {
    List<ProtocolVersion> versions =
        List.of(
            ProtocolVersion.VERSION_2025_11_25,
            ProtocolVersion.VERSION_2025_06_18,
            ProtocolVersion.VERSION_2025_03_26,
            ProtocolVersion.VERSION_2024_11_05);
    for (ProtocolVersion version : versions) {
      HttpClient localMockClient = mock(HttpClient.class);
      HttpMcpTransport versionedTransport =
          new HttpMcpTransport(
              "https://test-mcp-service.com",
              Collections.emptyMap(),
              null,
              version,
              localMockClient,
              null);

      HttpResponse<String> mockInitResponse = mock(HttpResponse.class);
      when(mockInitResponse.statusCode()).thenReturn(200);
      when(mockInitResponse.body())
          .thenReturn(
              "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"protocolVersion\":\"2000-01-01\"}}");

      when(localMockClient.<String>sendAsync(
              any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
          .thenReturn(CompletableFuture.completedFuture(mockInitResponse));

      Exception ex =
          org.junit.jupiter.api.Assertions.assertThrows(
              Exception.class,
              () -> versionedTransport.listTools("", Collections.emptyMap()).get());
      assertTrue(ex.getCause().getMessage().contains("version mismatch"));
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void testInitialize_MissingSessionIdHeader_ThrowsException() {
    List<ProtocolVersion> sessionVer = List.of(ProtocolVersion.VERSION_2025_03_26);
    for (ProtocolVersion version : sessionVer) {
      HttpClient localMockClient = mock(HttpClient.class);
      HttpMcpTransport versionedTransport =
          new HttpMcpTransport(
              "https://test-mcp-service.com",
              Collections.emptyMap(),
              null,
              version,
              localMockClient,
              null);

      HttpResponse<String> mockInitResponse = mock(HttpResponse.class);
      when(mockInitResponse.statusCode()).thenReturn(200);
      when(mockInitResponse.body())
          .thenReturn(
              "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"protocolVersion\":\""
                  + version.getValue()
                  + "\"}}");
      java.net.http.HttpHeaders mockHeaders =
          java.net.http.HttpHeaders.of(Map.of(), (k, v) -> true);
      when(mockInitResponse.headers()).thenReturn(mockHeaders);

      when(localMockClient.<String>sendAsync(
              any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
          .thenReturn(CompletableFuture.completedFuture(mockInitResponse));

      Exception ex =
          org.junit.jupiter.api.Assertions.assertThrows(
              Exception.class,
              () -> versionedTransport.listTools("", Collections.emptyMap()).get());
      assertTrue(
          ex.getCause().getMessage().contains("Server did not return a Mcp-Session-Id header"));
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void testInitialize_Non200Response_ThrowsException() {
    List<ProtocolVersion> versions =
        List.of(
            ProtocolVersion.VERSION_2025_11_25,
            ProtocolVersion.VERSION_2025_06_18,
            ProtocolVersion.VERSION_2025_03_26,
            ProtocolVersion.VERSION_2024_11_05);
    for (ProtocolVersion version : versions) {
      HttpClient localMockClient = mock(HttpClient.class);
      HttpMcpTransport versionedTransport =
          new HttpMcpTransport(
              "https://test-mcp-service.com",
              Collections.emptyMap(),
              null,
              version,
              localMockClient,
              null);

      HttpResponse<String> mockInitResponse = mock(HttpResponse.class);
      when(mockInitResponse.statusCode()).thenReturn(500);
      when(mockInitResponse.body()).thenReturn("Init Server Error");

      when(localMockClient.<String>sendAsync(
              any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
          .thenReturn(CompletableFuture.completedFuture(mockInitResponse));

      Exception ex =
          org.junit.jupiter.api.Assertions.assertThrows(
              Exception.class,
              () -> versionedTransport.listTools("", Collections.emptyMap()).get());
      assertTrue(ex.getCause().getMessage().contains("Init failed: 500"));
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void testInitialize_MissingProtocolVersionInResult_FallbackToDefault() throws Exception {
    List<ProtocolVersion> versions =
        List.of(
            ProtocolVersion.VERSION_2025_11_25,
            ProtocolVersion.VERSION_2025_06_18,
            ProtocolVersion.VERSION_2025_03_26,
            ProtocolVersion.VERSION_2024_11_05);
    for (ProtocolVersion version : versions) {
      HttpClient localMockClient = mock(HttpClient.class);
      HttpMcpTransport versionedTransport =
          new HttpMcpTransport(
              "https://test-mcp-service.com",
              Collections.emptyMap(),
              null,
              version,
              localMockClient,
              null);

      HttpResponse<String> mockInitResponse = mock(HttpResponse.class);
      when(mockInitResponse.statusCode()).thenReturn(200);
      when(mockInitResponse.body()).thenReturn("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{}}");
      java.net.http.HttpHeaders mockHeaders =
          java.net.http.HttpHeaders.of(
              Map.of("Mcp-Session-Id", List.of("test-session-123")), (k, v) -> true);
      when(mockInitResponse.headers()).thenReturn(mockHeaders);

      HttpResponse<String> mockInitializedResponse = mock(HttpResponse.class);
      when(mockInitializedResponse.statusCode()).thenReturn(200);
      when(mockInitializedResponse.body()).thenReturn("");

      HttpResponse<String> mockListResponse = mock(HttpResponse.class);
      when(mockListResponse.statusCode()).thenReturn(200);
      when(mockListResponse.body())
          .thenReturn("{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"result\":{\"tools\":[]}}");

      when(localMockClient.<String>sendAsync(
              any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
          .thenReturn(CompletableFuture.completedFuture(mockInitResponse))
          .thenReturn(CompletableFuture.completedFuture(mockInitializedResponse))
          .thenReturn(CompletableFuture.completedFuture(mockListResponse));

      TransportManifest manifest = versionedTransport.listTools("", Collections.emptyMap()).get();
      assertNotNull(manifest);
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void testInitialize_NullResult_FallbackToDefault() throws Exception {
    List<ProtocolVersion> versions =
        List.of(
            ProtocolVersion.VERSION_2025_11_25,
            ProtocolVersion.VERSION_2025_06_18,
            ProtocolVersion.VERSION_2025_03_26,
            ProtocolVersion.VERSION_2024_11_05);
    for (ProtocolVersion version : versions) {
      HttpClient localMockClient = mock(HttpClient.class);
      HttpMcpTransport versionedTransport =
          new HttpMcpTransport(
              "https://test-mcp-service.com",
              Collections.emptyMap(),
              null,
              version,
              localMockClient,
              null);

      HttpResponse<String> mockInitResponse = mock(HttpResponse.class);
      when(mockInitResponse.statusCode()).thenReturn(200);
      when(mockInitResponse.body())
          .thenReturn("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":null}");
      java.net.http.HttpHeaders mockHeaders =
          java.net.http.HttpHeaders.of(
              Map.of("Mcp-Session-Id", List.of("test-session-123")), (k, v) -> true);
      when(mockInitResponse.headers()).thenReturn(mockHeaders);

      HttpResponse<String> mockInitializedResponse = mock(HttpResponse.class);
      when(mockInitializedResponse.statusCode()).thenReturn(200);
      when(mockInitializedResponse.body()).thenReturn("");

      HttpResponse<String> mockListResponse = mock(HttpResponse.class);
      when(mockListResponse.statusCode()).thenReturn(200);
      when(mockListResponse.body())
          .thenReturn("{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"result\":{\"tools\":[]}}");

      when(localMockClient.<String>sendAsync(
              any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
          .thenReturn(CompletableFuture.completedFuture(mockInitResponse))
          .thenReturn(CompletableFuture.completedFuture(mockInitializedResponse))
          .thenReturn(CompletableFuture.completedFuture(mockListResponse));

      TransportManifest manifest = versionedTransport.listTools("", Collections.emptyMap()).get();
      assertNotNull(manifest);
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void testInitialize_InitializedNotificationThrowsException() {
    List<ProtocolVersion> versions =
        List.of(
            ProtocolVersion.VERSION_2025_11_25,
            ProtocolVersion.VERSION_2025_06_18,
            ProtocolVersion.VERSION_2025_03_26,
            ProtocolVersion.VERSION_2024_11_05);
    for (ProtocolVersion version : versions) {
      HttpClient localMockClient = mock(HttpClient.class);
      HttpMcpTransport versionedTransport =
          new HttpMcpTransport(
              "https://test-mcp-service.com",
              Collections.emptyMap(),
              null,
              version,
              localMockClient,
              null);

      HttpResponse<String> mockInitResponse = mock(HttpResponse.class);
      when(mockInitResponse.statusCode()).thenReturn(200);
      when(mockInitResponse.body())
          .thenReturn(
              "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"protocolVersion\":\""
                  + version.getValue()
                  + "\"}}");
      java.net.http.HttpHeaders mockHeaders =
          java.net.http.HttpHeaders.of(
              Map.of("Mcp-Session-Id", List.of("test-session-123")), (k, v) -> true);
      when(mockInitResponse.headers()).thenReturn(mockHeaders);

      when(localMockClient.<String>sendAsync(
              any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
          .thenReturn(CompletableFuture.completedFuture(mockInitResponse))
          .thenReturn(CompletableFuture.failedFuture(new java.io.IOException("Connection reset")));

      Exception ex =
          org.junit.jupiter.api.Assertions.assertThrows(
              Exception.class,
              () -> versionedTransport.listTools("", Collections.emptyMap()).get());
      assertTrue(ex.getCause().getMessage().contains("Connection reset"));
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void testInitialize_JsonProcessingExceptionDuringRequestSerialization_ThrowsException()
      throws Exception {
    List<ProtocolVersion> versions =
        List.of(
            ProtocolVersion.VERSION_2025_11_25,
            ProtocolVersion.VERSION_2025_06_18,
            ProtocolVersion.VERSION_2025_03_26,
            ProtocolVersion.VERSION_2024_11_05);
    for (ProtocolVersion version : versions) {
      HttpClient localMockClient = mock(HttpClient.class);
      HttpMcpTransport versionedTransport =
          new HttpMcpTransport(
              "https://test-mcp-service.com",
              Collections.emptyMap(),
              null,
              version,
              localMockClient,
              null);

      BaseMcpTransport delegate = (BaseMcpTransport) getDelegate(versionedTransport);

      ObjectMapper mockMapper = mock(ObjectMapper.class);
      when(mockMapper.writeValueAsString(any()))
          .thenThrow(new com.fasterxml.jackson.core.JsonParseException(null, "Mock JSON Error"));

      setMockObjectMapper(delegate, mockMapper);

      Exception ex =
          org.junit.jupiter.api.Assertions.assertThrows(
              Exception.class,
              () -> versionedTransport.listTools("", Collections.emptyMap()).get());
      assertTrue(ex.getCause().getMessage().contains("Mock JSON Error"));
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void testInitialize_JsonProcessingExceptionDuringNotificationSerialization_ThrowsException()
      throws Exception {
    List<ProtocolVersion> versions =
        List.of(
            ProtocolVersion.VERSION_2025_11_25,
            ProtocolVersion.VERSION_2025_06_18,
            ProtocolVersion.VERSION_2025_03_26,
            ProtocolVersion.VERSION_2024_11_05);
    for (ProtocolVersion version : versions) {
      HttpClient localMockClient = mock(HttpClient.class);
      HttpMcpTransport versionedTransport =
          new HttpMcpTransport(
              "https://test-mcp-service.com",
              Collections.emptyMap(),
              null,
              version,
              localMockClient,
              null);

      BaseMcpTransport delegate = (BaseMcpTransport) getDelegate(versionedTransport);

      HttpResponse<String> mockInitResponse = mock(HttpResponse.class);
      when(mockInitResponse.statusCode()).thenReturn(200);
      when(mockInitResponse.body())
          .thenReturn(
              "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"protocolVersion\":\""
                  + version.getValue()
                  + "\"}}");
      java.net.http.HttpHeaders mockHeaders =
          java.net.http.HttpHeaders.of(
              Map.of("Mcp-Session-Id", List.of("test-session-123")), (k, v) -> true);
      when(mockInitResponse.headers()).thenReturn(mockHeaders);

      ObjectMapper mockMapper = mock(ObjectMapper.class);
      when(mockMapper.writeValueAsString(any()))
          .thenReturn("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"initialize\"}")
          .thenThrow(new com.fasterxml.jackson.core.JsonParseException(null, "Mock JSON Error 2"));

      com.fasterxml.jackson.databind.JsonNode mockNode =
          new ObjectMapper()
              .readTree(
                  "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"protocolVersion\":\""
                      + version.getValue()
                      + "\"}}");
      when(mockMapper.readTree(anyString())).thenReturn(mockNode);

      setMockObjectMapper(delegate, mockMapper);

      when(localMockClient.<String>sendAsync(
              any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
          .thenReturn(CompletableFuture.completedFuture(mockInitResponse));

      Exception ex =
          org.junit.jupiter.api.Assertions.assertThrows(
              Exception.class,
              () -> versionedTransport.listTools("", Collections.emptyMap()).get());
      assertTrue(ex.getCause().getMessage().contains("Mock JSON Error 2"));
    }
  }

  @Test
  void testHttpMcpTransportV20250326_SessionIdNull_HeaderNotAdded() throws Exception {
    com.google.cloud.mcp.transport.v20250326.HttpMcpTransportV20250326 transportV20250326 =
        new com.google.cloud.mcp.transport.v20250326.HttpMcpTransportV20250326(
            "https://test.com", Map.of(), null, mock(HttpClient.class), null);

    java.lang.reflect.Method method =
        com.google.cloud.mcp.transport.v20250326.HttpMcpTransportV20250326.class.getDeclaredMethod(
            "applyProtocolHeaders", HttpRequest.Builder.class);
    method.setAccessible(true);

    HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create("https://test.com"));
    method.invoke(transportV20250326, builder);

    HttpRequest req = builder.build();
    assertFalse(req.headers().firstValue("Mcp-Session-Id").isPresent());
  }

  @Test
  @SuppressWarnings("unchecked")
  void testInitialize_HttpUrlWithCredentialsProvider_LogsWarning() throws Exception {
    List<ProtocolVersion> versions =
        List.of(
            ProtocolVersion.VERSION_2025_11_25,
            ProtocolVersion.VERSION_2025_06_18,
            ProtocolVersion.VERSION_2025_03_26,
            ProtocolVersion.VERSION_2024_11_05);
    for (ProtocolVersion version : versions) {
      HttpClient localMockClient = mock(HttpClient.class);
      com.google.cloud.mcp.auth.CredentialsProvider mockProvider =
          () -> CompletableFuture.completedFuture("Bearer test-token");

      HttpMcpTransport versionedTransport =
          new HttpMcpTransport(
              "http://test-mcp-service.com",
              Collections.emptyMap(),
              mockProvider,
              version,
              localMockClient,
              null);

      HttpResponse<String> mockInitResponse = mock(HttpResponse.class);
      when(mockInitResponse.statusCode()).thenReturn(200);
      when(mockInitResponse.body())
          .thenReturn(
              "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"protocolVersion\":\""
                  + version.getValue()
                  + "\"}}");
      java.net.http.HttpHeaders mockHeaders =
          java.net.http.HttpHeaders.of(
              Map.of("Mcp-Session-Id", List.of("test-session-123")), (k, v) -> true);
      when(mockInitResponse.headers()).thenReturn(mockHeaders);

      HttpResponse<String> mockInitializedResponse = mock(HttpResponse.class);
      when(mockInitializedResponse.statusCode()).thenReturn(200);
      when(mockInitializedResponse.body()).thenReturn("");

      HttpResponse<String> mockListResponse = mock(HttpResponse.class);
      when(mockListResponse.statusCode()).thenReturn(200);
      when(mockListResponse.body())
          .thenReturn("{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"result\":{\"tools\":[]}}");

      when(localMockClient.<String>sendAsync(
              any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
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
        versionedTransport.listTools("", Collections.emptyMap()).get();
      } finally {
        transportLogger.removeHandler(logHandler);
      }

      assertFalse(logRecords.isEmpty());
      boolean hasWarning =
          logRecords.stream()
              .anyMatch(r -> r.getMessage().contains("This connection is using HTTP"));
      assertTrue(hasWarning);
    }
  }

  private static String anyString() {
    return org.mockito.ArgumentMatchers.anyString();
  }
}
