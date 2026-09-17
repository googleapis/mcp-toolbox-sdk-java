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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.mcp.McpToolboxClient;
import com.google.cloud.mcp.ProtocolVersion;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 5, unit = java.util.concurrent.TimeUnit.SECONDS)
class HttpMcpToolboxClientTest {

  private HttpServer server;
  private int port;
  private MockMcpHandler handler;
  private ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    port = server.getAddress().getPort();
    handler = new MockMcpHandler();
    server.createContext("/", handler);
    server.start();
  }

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  private String getBaseUrl() {
    return "http://localhost:" + port;
  }

  @Test
  void testVersionNegotiation_default_2025_11_25() throws Exception {
    handler.serverProtocolVersion = "2025-11-25";

    McpToolboxClient client = McpToolboxClient.builder().baseUrl(getBaseUrl()).build();

    // Trigger loadToolset which runs initialization
    client.loadToolset().get();

    // Verify initialization requests
    assertTrue(handler.requestsReceived.size() >= 2);

    // First request: initialize
    MockRequest initReq = handler.requestsReceived.get(0);
    assertEquals("initialize", initReq.method);
    assertEquals("2025-11-25", initReq.params.get("protocolVersion").asText());

    // Second request: notifications/initialized
    MockRequest notifReq = handler.requestsReceived.get(1);
    assertEquals("notifications/initialized", notifReq.method);
    assertEquals("2025-11-25", notifReq.headers.get("mcp-protocol-version"));
    assertEquals("application/json", notifReq.headers.get("accept"));

    // Third request: tools/list
    MockRequest listReq = handler.requestsReceived.get(2);
    assertEquals("tools/list", listReq.method);
    assertEquals("2025-11-25", listReq.headers.get("mcp-protocol-version"));
    assertEquals("application/json", listReq.headers.get("accept"));
  }

  @Test
  void testVersionNegotiation_mismatch_throws() {
    // Client proposes 2025-11-25 by default, server returns 2025-03-26
    handler.serverProtocolVersion = "2025-03-26";
    handler.serverSessionId = "sess-12345";

    McpToolboxClient client = McpToolboxClient.builder().baseUrl(getBaseUrl()).build();

    ExecutionException exception =
        assertThrows(
            ExecutionException.class,
            () -> {
              client.loadToolset().get();
            });

    assertTrue(exception.getCause().getMessage().contains("MCP version mismatch"));
  }

  @Test
  void testVersionNegotiation_success_2025_03_26() throws Exception {
    handler.serverProtocolVersion = "2025-03-26";
    handler.serverSessionId = "sess-12345";

    McpToolboxClient client =
        McpToolboxClient.builder()
            .baseUrl(getBaseUrl())
            .protocolVersion(ProtocolVersion.VERSION_2025_03_26)
            .build();

    client.loadToolset().get();

    assertTrue(handler.requestsReceived.size() >= 2);

    // Initial check: proposed version is 2025-03-26
    MockRequest initReq = handler.requestsReceived.get(0);
    assertEquals("initialize", initReq.method);
    assertEquals("2025-03-26", initReq.params.get("protocolVersion").asText());

    // Check initialized notification uses negotiated session ID and no version header
    MockRequest notifReq = handler.requestsReceived.get(1);
    assertEquals("notifications/initialized", notifReq.method);
    assertEquals("sess-12345", notifReq.headers.get("mcp-session-id"));
    assertTrue(!notifReq.headers.containsKey("mcp-protocol-version"));
    assertEquals("application/json", notifReq.headers.get("accept"));

    // Check subsequent tools/list uses negotiated session ID
    MockRequest listReq = handler.requestsReceived.get(2);
    assertEquals("tools/list", listReq.method);
    assertEquals("sess-12345", listReq.headers.get("mcp-session-id"));
    assertTrue(!listReq.headers.containsKey("mcp-protocol-version"));
    assertEquals("application/json", listReq.headers.get("accept"));
  }

  @Test
  void testVersionNegotiation_success_2024_11_05() throws Exception {
    handler.serverProtocolVersion = "2024-11-05";

    McpToolboxClient client =
        McpToolboxClient.builder()
            .baseUrl(getBaseUrl())
            .protocolVersion(ProtocolVersion.VERSION_2024_11_05)
            .build();

    client.loadToolset().get();

    assertTrue(handler.requestsReceived.size() >= 2);

    MockRequest notifReq = handler.requestsReceived.get(1);
    assertEquals("notifications/initialized", notifReq.method);
    assertTrue(!notifReq.headers.containsKey("mcp-protocol-version"));
    assertTrue(!notifReq.headers.containsKey("mcp-session-id"));
    assertTrue(!notifReq.headers.containsKey("accept"));

    MockRequest listReq = handler.requestsReceived.get(2);
    assertEquals("tools/list", listReq.method);
    assertTrue(!listReq.headers.containsKey("mcp-protocol-version"));
    assertTrue(!listReq.headers.containsKey("mcp-session-id"));
    assertTrue(!listReq.headers.containsKey("accept"));
  }

  @Test
  void testVersionNegotiation_unsupportedVersion() {
    handler.serverProtocolVersion = "2023-01-01"; // Unsupported version

    McpToolboxClient client = McpToolboxClient.builder().baseUrl(getBaseUrl()).build();

    ExecutionException exception =
        assertThrows(
            ExecutionException.class,
            () -> {
              client.loadToolset().get();
            });

    assertTrue(exception.getCause().getMessage().contains("MCP version mismatch"));
  }

  @Test
  void testVersionNegotiation_missingSessionId_for_2025_03_26() {
    handler.serverProtocolVersion = "2025-03-26";
    handler.serverSessionId = null; // Missing session ID

    McpToolboxClient client =
        McpToolboxClient.builder()
            .baseUrl(getBaseUrl())
            .protocolVersion(ProtocolVersion.VERSION_2025_03_26)
            .build();

    ExecutionException exception =
        assertThrows(
            ExecutionException.class,
            () -> {
              client.loadToolset().get();
            });

    assertTrue(exception.getCause().getMessage().contains("Mcp-Session-Id"));
  }

  @Test
  void testBuilderPreferredVersion() throws Exception {
    handler.serverProtocolVersion = "2025-03-26";
    handler.serverSessionId = "sess-prefer";

    // Build client with preferred version
    McpToolboxClient client =
        McpToolboxClient.builder()
            .baseUrl(getBaseUrl())
            .protocolVersion(ProtocolVersion.VERSION_2025_03_26)
            .build();

    client.loadToolset().get();

    assertTrue(handler.requestsReceived.size() >= 2);

    // Initial check: client proposed preferred version
    MockRequest initReq = handler.requestsReceived.get(0);
    assertEquals("initialize", initReq.method);
    assertEquals("2025-03-26", initReq.params.get("protocolVersion").asText());
  }

  private static class MockRequest {
    String method;
    JsonNode params;
    Map<String, String> headers;

    MockRequest(String method, JsonNode params, Map<String, String> headers) {
      this.method = method;
      this.params = params;
      this.headers = headers;
    }
  }

  private class MockMcpHandler implements HttpHandler {
    String serverProtocolVersion = "2025-11-25";
    String serverSessionId = null;
    List<MockRequest> requestsReceived = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void handle(HttpExchange exchange) throws IOException {
      if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
        exchange.sendResponseHeaders(405, -1);
        return;
      }

      String bodyStr = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      JsonNode jsonReq;
      try {
        jsonReq = objectMapper.readTree(bodyStr);
      } catch (Exception e) {
        exchange.sendResponseHeaders(400, -1);
        return;
      }

      String method = jsonReq.get("method").asText();
      JsonNode params = jsonReq.get("params");

      // Extract headers case-insensitively for checking (lowercase keys)
      java.util.Map<String, String> requestHeaders = new java.util.HashMap<>();
      exchange
          .getRequestHeaders()
          .forEach(
              (k, v) -> {
                if (v != null && !v.isEmpty()) {
                  requestHeaders.put(k.toLowerCase(java.util.Locale.ROOT), v.get(0));
                }
              });

      requestsReceived.add(new MockRequest(method, params, requestHeaders));

      String responseBody = "";
      int statusCode = 200;

      if ("initialize".equals(method)) {
        if (serverSessionId != null) {
          exchange.getResponseHeaders().set("Mcp-Session-Id", serverSessionId);
        }
        responseBody =
            "{\n"
                + "  \"jsonrpc\": \"2.0\",\n"
                + "  \"id\": \""
                + jsonReq.get("id").asText()
                + "\",\n"
                + "  \"result\": {\n"
                + "    \"protocolVersion\": \""
                + serverProtocolVersion
                + "\",\n"
                + "    \"capabilities\": {\n"
                + "      \"tools\": {}\n"
                + "    },\n"
                + "    \"serverInfo\": {\n"
                + "      \"name\": \"mock-server\",\n"
                + "      \"version\": \"1.0.0\"\n"
                + "    }\n"
                + "  }\n"
                + "}";
      } else if ("notifications/initialized".equals(method)) {
        statusCode = 204;
      } else if ("tools/list".equals(method)) {
        responseBody =
            "{\n"
                + "  \"jsonrpc\": \"2.0\",\n"
                + "  \"id\": \""
                + jsonReq.get("id").asText()
                + "\",\n"
                + "  \"result\": {\n"
                + "    \"tools\": []\n"
                + "  }\n"
                + "}";
      }

      byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
      if (statusCode == 204 || bytes.length == 0) {
        exchange.sendResponseHeaders(statusCode, -1);
      } else {
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
          os.write(bytes);
        }
      }
    }
  }
}
