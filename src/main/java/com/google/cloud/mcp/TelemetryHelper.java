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
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Helper class for OpenTelemetry metrics and tracing instrumentation. */
public final class TelemetryHelper {
  /** Bucket boundary 0.01. */
  private static final double B_0_01 = 0.01;

  /** Bucket boundary 0.02. */
  private static final double B_0_02 = 0.02;

  /** Bucket boundary 0.05. */
  private static final double B_0_05 = 0.05;

  /** Bucket boundary 0.1. */
  private static final double B_0_1 = 0.1;

  /** Bucket boundary 0.2. */
  private static final double B_0_2 = 0.2;

  /** Bucket boundary 0.5. */
  private static final double B_0_5 = 0.5;

  /** Bucket boundary 1.0. */
  private static final double B_1 = 1.0;

  /** Bucket boundary 2.0. */
  private static final double B_2 = 2.0;

  /** Bucket boundary 5.0. */
  private static final double B_5 = 5.0;

  /** Bucket boundary 10.0. */
  private static final double B_10 = 10.0;

  /** Bucket boundary 30.0. */
  private static final double B_30 = 30.0;

  /** Bucket boundary 60.0. */
  private static final double B_60 = 60.0;

  /** Bucket boundary 120.0. */
  private static final double B_120 = 120.0;

  /** Bucket boundary 300.0. */
  private static final double B_300 = 300.0;

  /** Conversion factor from nanoseconds to seconds. */
  static final double NANOS_IN_SECOND = 1e9;

  /** Name of the instrumentation library. */
  private static final String INSTRUMENTATION_NAME = "toolbox.mcp.sdk";

  // Dynamic / lazy OpenTelemetry binding cache
  private static io.opentelemetry.api.OpenTelemetry lastOtel = null;
  private static DoubleHistogram cachedOperationDuration = null;
  private static DoubleHistogram cachedSessionDuration = null;

  private static synchronized void checkRebind() {
    io.opentelemetry.api.OpenTelemetry currentOtel = GlobalOpenTelemetry.get();
    if (currentOtel != lastOtel) {
      lastOtel = currentOtel;
      Meter meter = currentOtel.getMeter(INSTRUMENTATION_NAME);
      cachedOperationDuration =
          meter
              .histogramBuilder("mcp.client.operation.duration")
              .setUnit("s")
              .setDescription(
                  "Duration of MCP client operations (requests/notifications) from the time it was"
                      + " sent until the response or ack is received.")
              .setExplicitBucketBoundariesAdvice(
                  Arrays.asList(
                      B_0_01, B_0_02, B_0_05, B_0_1, B_0_2, B_0_5, B_1, B_2, B_5, B_10, B_30, B_60,
                      B_120, B_300))
              .build();
      cachedSessionDuration =
          meter
              .histogramBuilder("mcp.client.session.duration")
              .setUnit("s")
              .setDescription("Total duration of MCP client sessions")
              .setExplicitBucketBoundariesAdvice(
                  Arrays.asList(
                      B_0_01, B_0_02, B_0_05, B_0_1, B_0_2, B_0_5, B_1, B_2, B_5, B_10, B_30, B_60,
                      B_120, B_300))
              .build();
    }
  }

  private static DoubleHistogram operationDuration() {
    checkRebind();
    return cachedOperationDuration;
  }

  private static DoubleHistogram sessionDuration() {
    checkRebind();
    return cachedSessionDuration;
  }

  private static Tracer tracer() {
    return GlobalOpenTelemetry.getTracer(INSTRUMENTATION_NAME);
  }

  private static TextMapPropagator propagator() {
    return GlobalOpenTelemetry.getPropagators().getTextMapPropagator();
  }

  private TelemetryHelper() {}

  /**
   * Helper record to extract ServerInfo.
   *
   * @param address The server host address.
   * @param port The server port.
   * @param protocol The network protocol (e.g. http).
   */
  record ServerInfo(String address, Integer port, String protocol) {}

  static ServerInfo extractServerInfo(final String urlStr) {
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
      if (port == -1 && uri.getAuthority() != null && uri.getAuthority().contains(":")) {
        try {
          String auth = uri.getAuthority();
          port = Integer.parseInt(auth.substring(auth.indexOf(':') + 1));
        } catch (NumberFormatException e) {
          // ignore
        }
      }
      String protocol = uri.getScheme();
      if (protocol == null) {
        protocol = "http";
      }
      return new ServerInfo(host != null ? host : "", port != -1 ? port : null, protocol);
    } catch (Exception e) {
      return new ServerInfo("", null, "http");
    }
  }

  /** Wrapper for recording client operation metrics and tracing spans. */
  public static class OperationSpan implements AutoCloseable {
    /** The OpenTelemetry span. */
    private final Span span;

    /** The scope for the current span context. */
    private final Scope scope;

    /** Start time of the span in nanoseconds. */
    private final long startTimeNanos;

    /** Name of the MCP method. */
    private final String methodName;

    /** Protocol version of MCP. */
    private final String protocolVersion;

    /** Server base URL. */
    private final String serverUrl;

    /** Name of the tool. */
    private final String toolName;

    /** Class name of the error if an error occurred. */
    private String errorType = null;

    /**
     * Constructs a new OperationSpan.
     *
     * @param method The MCP method name.
     * @param version The protocol version.
     * @param url The server base URL.
     * @param tool The tool name, or null.
     */
    public OperationSpan(
        final String method, final String version, final String url, final String tool) {
      this.methodName = method;
      this.protocolVersion = version;
      this.serverUrl = url;
      this.toolName = tool;
      this.startTimeNanos = System.nanoTime();

      String spanName = tool != null ? method + " " + tool : method;
      this.span = tracer().spanBuilder(spanName).setSpanKind(SpanKind.CLIENT).startSpan();
      this.scope = span.makeCurrent();

      // Set standard span attributes
      span.setAttribute("mcp.method.name", method);
      span.setAttribute("mcp.protocol.version", version);
      ServerInfo info = extractServerInfo(url);
      span.setAttribute("server.address", info.address());
      span.setAttribute("network.protocol.name", info.protocol());
      span.setAttribute("network.transport", "tcp");
      if (info.port() != null) {
        span.setAttribute("server.port", (long) info.port());
      }
      if (tool != null) {
        span.setAttribute("gen_ai.tool.name", tool);
      }
      if ("tools/call".equals(method)) {
        span.setAttribute("gen_ai.operation.name", "execute_tool");
      }
    }

    /**
     * Gets W3C context headers to inject into the request.
     *
     * @return A map containing trace context headers.
     */
    public Map<String, String> getTraceContextHeaders() {
      Map<String, String> carrier = new HashMap<>();
      propagator().inject(Context.current(), carrier, Map::put);
      return carrier;
    }

    /**
     * Records a throwable error on the span.
     *
     * @param t The error thrown.
     */
    public void recordError(final Throwable t) {
      span.recordException(t);
      span.setStatus(StatusCode.ERROR, t.getMessage());
      this.errorType = t.getClass().getName();
      span.setAttribute("error.type", errorType);
    }

    /**
     * Records a JSON-RPC error on the span.
     *
     * @param code The JSON-RPC error code.
     * @param message The error message.
     */
    public void recordError(final int code, final String message) {
      span.setStatus(StatusCode.ERROR, message);
      this.errorType = "jsonrpc.error." + code;
      span.setAttribute("error.type", errorType);
    }

    @Override
    public void close() {
      scope.close();
      span.end();

      // Record operation duration metric
      double durationSeconds = (System.nanoTime() - startTimeNanos) / NANOS_IN_SECOND;
      AttributesBuilder attrs =
          Attributes.builder()
              .put("mcp.method.name", methodName)
              .put("mcp.protocol.version", protocolVersion);
      ServerInfo info = extractServerInfo(serverUrl);
      attrs.put("server.address", info.address());
      attrs.put("network.protocol.name", info.protocol());
      attrs.put("network.transport", "tcp");
      if (info.port() != null) {
        attrs.put("server.port", (long) info.port());
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

      operationDuration().record(durationSeconds, attrs.build());
    }
  }

  /**
   * Records the duration of an MCP session.
   *
   * @param durationSeconds The duration of the session in seconds.
   * @param protocolVersion The negotiated protocol version.
   * @param serverUrl The server base URL.
   * @param error The session error, or null if successful.
   */
  public static void recordSessionDuration(
      final double durationSeconds,
      final String protocolVersion,
      final String serverUrl,
      final Throwable error) {
    AttributesBuilder attrs = Attributes.builder().put("mcp.protocol.version", protocolVersion);
    ServerInfo info = extractServerInfo(serverUrl);
    attrs.put("server.address", info.address());
    attrs.put("network.protocol.name", info.protocol());
    attrs.put("network.transport", "tcp");
    if (info.port() != null) {
      attrs.put("server.port", (long) info.port());
    }
    if (error != null) {
      attrs.put("error.type", error.getClass().getName());
    }
    sessionDuration().record(durationSeconds, attrs.build());
  }
}
