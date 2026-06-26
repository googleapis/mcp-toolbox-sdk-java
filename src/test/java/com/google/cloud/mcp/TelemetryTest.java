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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.mcp.tool.ToolDefinition;
import com.google.cloud.mcp.tool.ToolResult;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

@Timeout(value = 15, unit = TimeUnit.SECONDS)
public class TelemetryTest {

  @RegisterExtension
  static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

  private HttpServer server;
  private String serverUrl;
  private final List<JsonNode> receivedRequests = Collections.synchronizedList(new ArrayList<>());
  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  public void setUp() throws Exception {
    receivedRequests.clear();
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/mcp",
        exchange -> {
          try {
            byte[] reqBytes = exchange.getRequestBody().readAllBytes();
            JsonNode reqNode = mapper.readTree(reqBytes);
            receivedRequests.add(reqNode);

            String method = reqNode.has("method") ? reqNode.get("method").asText() : "";
            String responseBody = "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{}}";

            if ("tools/list".equals(method)) {
              responseBody =
                  "{\n"
                      + "  \"jsonrpc\": \"2.0\",\n"
                      + "  \"id\": \"1\",\n"
                      + "  \"result\": {\n"
                      + "    \"tools\": [\n"
                      + "      {\n"
                      + "        \"name\": \"test-tool\",\n"
                      + "        \"description\": \"A test tool\",\n"
                      + "        \"inputSchema\": {\n"
                      + "          \"type\": \"object\",\n"
                      + "          \"properties\": {}\n"
                      + "        }\n"
                      + "      }\n"
                      + "    ]\n"
                      + "  }\n"
                      + "}";
            } else if ("tools/call".equals(method)) {
              responseBody =
                  "{\n"
                      + "  \"jsonrpc\": \"2.0\",\n"
                      + "  \"id\": \"1\",\n"
                      + "  \"result\": {\n"
                      + "    \"content\": [\n"
                      + "      {\n"
                      + "        \"type\": \"text\",\n"
                      + "        \"text\": \"Success\"\n"
                      + "      }\n"
                      + "    ],\n"
                      + "    \"isError\": false\n"
                      + "  }\n"
                      + "}";
            }

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] responseBytes = responseBody.getBytes();
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
              os.write(responseBytes);
            }
          } catch (Exception e) {
            exchange.sendResponseHeaders(500, 0);
            exchange.close();
          }
        });
    server.start();
    int port = server.getAddress().getPort();
    serverUrl = "http://localhost:" + port + "/mcp";
  }

  @AfterEach
  public void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  public void testTelemetrySpansAndContextPropagation() throws Exception {
    try (McpToolboxClient client = McpToolboxClient.builder().baseUrl(serverUrl).build()) {
      // 1. Load toolset (triggers initialize and tools/list)
      Map<String, ToolDefinition> tools = client.loadToolset().get();
      assertNotNull(tools);
      assertTrue(tools.containsKey("test-tool"));

      // 2. Invoke tool
      ToolResult result = client.invokeTool("test-tool", Map.of()).get();
      assertNotNull(result);
      assertFalse(result.isError());
    }

    // Verify Spans were created
    List<SpanData> spans = otelTesting.getSpans();

    // Spans should be: "initialize", "tools/list", "tools/call test-tool"
    assertTrue(spans.stream().anyMatch(s -> "initialize".equals(s.getName())));
    assertTrue(spans.stream().anyMatch(s -> "tools/list".equals(s.getName())));
    assertTrue(spans.stream().anyMatch(s -> "tools/call test-tool".equals(s.getName())));

    SpanData initSpan =
        spans.stream().filter(s -> "initialize".equals(s.getName())).findFirst().orElseThrow();
    SpanData listSpan =
        spans.stream().filter(s -> "tools/list".equals(s.getName())).findFirst().orElseThrow();
    SpanData callSpan =
        spans.stream()
            .filter(s -> "tools/call test-tool".equals(s.getName()))
            .findFirst()
            .orElseThrow();

    // Verify Span attributes
    assertEquals(
        "initialize", initSpan.getAttributes().get(AttributeKey.stringKey("mcp.method.name")));
    assertEquals(
        "tools/list", listSpan.getAttributes().get(AttributeKey.stringKey("mcp.method.name")));
    assertEquals(
        "tools/call", callSpan.getAttributes().get(AttributeKey.stringKey("mcp.method.name")));
    assertEquals(
        "test-tool", callSpan.getAttributes().get(AttributeKey.stringKey("gen_ai.tool.name")));

    // Verify context propagation in JSON-RPC metadata
    // Note: invokeTool does not trigger initialization again since it was already initialized
    // So invokeTool adds tools/call request, making it 4 requests total.
    // Wait, let's verify if the list size is 4.
    // index 0: initialize (Request)
    // index 1: notifications/initialized (Notification)
    // index 2: tools/list (Request)
    // index 3: tools/call (Request)
    assertEquals(4, receivedRequests.size());

    JsonNode initReq = receivedRequests.get(0);
    JsonNode listReq = receivedRequests.get(2);
    JsonNode callReq = receivedRequests.get(3);

    // Verify traceparent in requests matches the span's traceId
    String initTraceParent = initReq.get("params").get("_meta").get("traceparent").asText();
    assertNotNull(initTraceParent);
    assertTrue(initTraceParent.contains(initSpan.getTraceId()));

    String listTraceParent = listReq.get("params").get("_meta").get("traceparent").asText();
    assertNotNull(listTraceParent);
    assertTrue(listTraceParent.contains(listSpan.getTraceId()));

    String callTraceParent = callReq.get("params").get("_meta").get("traceparent").asText();
    assertNotNull(callTraceParent);
    assertTrue(callTraceParent.contains(callSpan.getTraceId()));
  }

  @Test
  public void testTelemetryHelperEdgeCases() {
    // 1. Test ServerInfo record methods (equals, hashCode, toString, and accessors)
    TelemetryHelper.ServerInfo info1 = new TelemetryHelper.ServerInfo("localhost", 8080, "http");
    TelemetryHelper.ServerInfo info2 = new TelemetryHelper.ServerInfo("localhost", 8080, "http");
    TelemetryHelper.ServerInfo info3 = new TelemetryHelper.ServerInfo("example.com", 9090, "https");

    assertEquals(info1, info2);
    assertNotEquals(info1, info3);
    assertEquals(info1.hashCode(), info2.hashCode());
    assertNotNull(info1.toString());
    assertEquals("localhost", info1.address());
    assertEquals(8080, info1.port());
    assertEquals("http", info1.protocol());

    // 2. Test extractServerInfo with various edge-case URLs
    TelemetryHelper.ServerInfo invalid = TelemetryHelper.extractServerInfo(":::");
    assertEquals("", invalid.address());
    assertNull(invalid.port());
    assertEquals("http", invalid.protocol());

    TelemetryHelper.ServerInfo noHost = TelemetryHelper.extractServerInfo("http:///mcp");
    assertEquals("", noHost.address());
    assertNull(noHost.port());

    TelemetryHelper.ServerInfo noHostWithPort =
        TelemetryHelper.extractServerInfo("http://my_server:8080");
    assertEquals("my_server", noHostWithPort.address());
    assertEquals(8080, noHostWithPort.port());

    TelemetryHelper.ServerInfo invalidPort =
        TelemetryHelper.extractServerInfo("http://my_server:invalidport");
    assertEquals("my_server", invalidPort.address());
    assertNull(invalidPort.port());

    TelemetryHelper.ServerInfo noProtocol = TelemetryHelper.extractServerInfo("//localhost:8080");
    assertEquals("localhost", noProtocol.address());
    assertEquals(8080, noProtocol.port());
    assertEquals("http", noProtocol.protocol());

    // 3. Test recordSessionDuration with error
    TelemetryHelper.recordSessionDuration(
        5.5, "2025-11-25", "http://localhost:8080", new RuntimeException("session error"));
  }
}
