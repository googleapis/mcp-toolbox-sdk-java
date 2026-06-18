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

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

class TelemetryHelper {
  private static final String INSTRUMENTATION_NAME = "toolbox.mcp.sdk";

  private static final Tracer TRACER = GlobalOpenTelemetry.getTracer(INSTRUMENTATION_NAME);
  private static final Meter METER = GlobalOpenTelemetry.getMeter(INSTRUMENTATION_NAME);
  private static final TextMapPropagator PROPAGATOR = W3CTraceContextPropagator.getInstance();

  private static final DoubleHistogram OPERATION_DURATION =
      METER
          .histogramBuilder("mcp.client.operation.duration")
          .setUnit("s")
          .setDescription(
              "Duration of MCP client operations (requests/notifications) from the time it was"
                  + " sent until the response or ack is received.")
          .setExplicitBucketBoundariesAdvice(
              Arrays.asList(
                  0.01, 0.02, 0.05, 0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 30.0, 60.0, 120.0, 300.0))
          .build();

  private static final DoubleHistogram SESSION_DURATION =
      METER
          .histogramBuilder("mcp.client.session.duration")
          .setUnit("s")
          .setDescription("Total duration of MCP client sessions")
          .setExplicitBucketBoundariesAdvice(
              Arrays.asList(
                  0.01, 0.02, 0.05, 0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 30.0, 60.0, 120.0, 300.0))
          .build();

  // Helper to extract ServerInfo
  static class ServerInfo {
    final String address;
    final Integer port;
    final String protocol;

    ServerInfo(String address, Integer port, String protocol) {
      this.address = address;
      this.port = port;
      this.protocol = protocol;
    }
  }

  static ServerInfo extractServerInfo(String urlStr) {
    try {
      URI uri = new URI(urlStr);
      String host = uri.getHost();
      if (host == null) {
        host = uri.getAuthority();
        if (host != null && host.contains(":")) {
          host = host.substring(0, host.indexOf(':'));
        }
      }
      int port = uri.getPort();
      String protocol = uri.getScheme();
      if (protocol == null) {
        protocol = "http";
      }
      return new ServerInfo(host != null ? host : "", port != -1 ? port : null, protocol);
    } catch (Exception e) {
      return new ServerInfo("", null, "http");
    }
  }

  // Operation execution span wrapper
  static class OperationSpan implements AutoCloseable {
    private final Span span;
    private final Scope scope;
    private final long startTimeNanos;
    private final String methodName;
    private final String protocolVersion;
    private final String serverUrl;
    private final String toolName;
    private String errorType = null;

    OperationSpan(String methodName, String protocolVersion, String serverUrl, String toolName) {
      this.methodName = methodName;
      this.protocolVersion = protocolVersion;
      this.serverUrl = serverUrl;
      this.toolName = toolName;
      this.startTimeNanos = System.nanoTime();

      String spanName = toolName != null ? methodName + " " + toolName : methodName;
      this.span = TRACER.spanBuilder(spanName).setSpanKind(SpanKind.CLIENT).startSpan();
      this.scope = span.makeCurrent();

      // Set standard span attributes
      span.setAttribute("mcp.method.name", methodName);
      span.setAttribute("mcp.protocol.version", protocolVersion);
      ServerInfo info = extractServerInfo(serverUrl);
      span.setAttribute("server.address", info.address);
      span.setAttribute("network.protocol.name", info.protocol);
      span.setAttribute("network.transport", "tcp");
      if (info.port != null) {
        span.setAttribute("server.port", (long) info.port);
      }
      if (toolName != null) {
        span.setAttribute("gen_ai.tool.name", toolName);
      }
      if ("tools/call".equals(methodName)) {
        span.setAttribute("gen_ai.operation.name", "execute_tool");
      }
    }

    public Map<String, String> getTraceContextHeaders() {
      Map<String, String> carrier = new HashMap<>();
      PROPAGATOR.inject(Context.current(), carrier, Map::put);
      return carrier;
    }

    public void recordError(Throwable t) {
      span.recordException(t);
      span.setStatus(StatusCode.ERROR, t.getMessage());
      this.errorType = t.getClass().getName();
      span.setAttribute("error.type", errorType);
    }

    public void recordError(int code, String message) {
      span.setStatus(StatusCode.ERROR, message);
      this.errorType = "jsonrpc.error." + code;
      span.setAttribute("error.type", errorType);
    }

    @Override
    public void close() {
      scope.close();
      span.end();

      // Record operation duration metric
      double durationSeconds = (System.nanoTime() - startTimeNanos) / 1e9;
      AttributesBuilder attrs =
          Attributes.builder()
              .put("mcp.method.name", methodName)
              .put("mcp.protocol.version", protocolVersion);
      ServerInfo info = extractServerInfo(serverUrl);
      attrs.put("server.address", info.address);
      attrs.put("network.protocol.name", info.protocol);
      attrs.put("network.transport", "tcp");
      if (info.port != null) {
        attrs.put("server.port", (long) info.port);
      }
      if (toolName != null) {
        attrs.put("gen_ai.tool.name", toolName);
      }
      if ("tools/call".equals(methodName)) {
        attrs.put("gen_ai.operation.name", "execute_tool");
      }
      if (errorType != null) {
        attrs.put("error.type", errorType);
      }

      OPERATION_DURATION.record(durationSeconds, attrs.build());
    }
  }

  static void recordSessionDuration(
      double durationSeconds, String protocolVersion, String serverUrl, Throwable error) {
    AttributesBuilder attrs = Attributes.builder().put("mcp.protocol.version", protocolVersion);
    ServerInfo info = extractServerInfo(serverUrl);
    attrs.put("server.address", info.address);
    attrs.put("network.protocol.name", info.protocol);
    attrs.put("network.transport", "tcp");
    if (info.port != null) {
      attrs.put("server.port", (long) info.port);
    }
    if (error != null) {
      attrs.put("error.type", error.getClass().getName());
    }
    SESSION_DURATION.record(durationSeconds, attrs.build());
  }
}
