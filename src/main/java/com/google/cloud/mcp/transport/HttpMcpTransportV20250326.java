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

import com.fasterxml.jackson.databind.JsonNode;
import com.google.cloud.mcp.ProtocolVersion;
import com.google.cloud.mcp.auth.CredentialsProvider;
import com.google.cloud.mcp.exception.McpException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/** HTTP transport implementation for protocol version 2025-03-26. */
public final class HttpMcpTransportV20250326 extends BaseMcpTransport {

  private volatile String sessionId;

  /**
   * Constructs a new HttpMcpTransportV20250326.
   *
   * @param baseUrl The base URL.
   * @param clientHeaders The client headers.
   * @param credentialsProvider The credentials provider.
   * @param httpClient The HTTP client.
   * @param executor The executor.
   */
  public HttpMcpTransportV20250326(
      final String baseUrl,
      final Map<String, String> clientHeaders,
      final CredentialsProvider credentialsProvider,
      final HttpClient httpClient,
      final java.util.concurrent.Executor executor) {
    super(
        baseUrl,
        clientHeaders,
        credentialsProvider,
        ProtocolVersion.VERSION_2025_03_26,
        httpClient,
        executor);
  }

  /**
   * Constructs a new HttpMcpTransportV20250326 with timeouts and logger.
   *
   * @param baseUrl The base URL.
   * @param clientHeaders The client headers.
   * @param credentialsProvider The credentials provider.
   * @param httpClient The HTTP client.
   * @param executor The executor.
   * @param connectTimeout The connection timeout.
   * @param requestTimeout The request timeout.
   * @param logger The logger.
   */
  public HttpMcpTransportV20250326(
      final String baseUrl,
      final Map<String, String> clientHeaders,
      final CredentialsProvider credentialsProvider,
      final HttpClient httpClient,
      final java.util.concurrent.Executor executor,
      final Duration connectTimeout,
      final Duration requestTimeout,
      final Logger logger) {
    super(
        baseUrl,
        clientHeaders,
        credentialsProvider,
        ProtocolVersion.VERSION_2025_03_26,
        httpClient,
        executor,
        connectTimeout,
        requestTimeout,
        logger);
  }

  @Override
  protected CompletableFuture<Void> performInitialization(
      final String authHeader, final Map<String, String> handshakeHeaders) {
    try {
      if (this.baseUrl.toLowerCase(java.util.Locale.ROOT).startsWith("http://")
          && authHeader != null) {
        logger.warning(HTTP_WARNING);
      }
      JsonRpc.Request initReq =
          new JsonRpc.Request(
              "initialize",
              new JsonRpc.InitializeParams(
                  ProtocolVersion.VERSION_2025_03_26.getValue(), "mcp-toolbox-sdk-java"));
      String body = objectMapper.writeValueAsString(initReq);
      HttpRequest.Builder req =
          HttpRequest.newBuilder()
              .uri(URI.create(baseUrl))
              .POST(HttpRequest.BodyPublishers.ofString(body));

      handshakeHeaders.forEach(req::setHeader);
      applyProtocolHeaders(req);

      return httpClient
          .sendAsync(req.build(), HttpResponse.BodyHandlers.ofString())
          .thenCompose(
              res -> {
                if (res.statusCode() != 200) {
                  return CompletableFuture.failedFuture(
                      new McpException("Init failed: " + res.statusCode() + " " + res.body()));
                }
                try {
                  JsonNode responseJson = objectMapper.readTree(res.body());
                  if (responseJson.has("error")) {
                    return CompletableFuture.failedFuture(
                        new McpException("MCP Error: " + responseJson.get("error").toString()));
                  }
                  JsonNode result = responseJson.get("result");
                  String serverVersion;
                  if (result != null && result.has("protocolVersion")) {
                    serverVersion = result.get("protocolVersion").asText();
                  } else {
                    serverVersion = ProtocolVersion.VERSION_2025_03_26.getValue();
                  }

                  if (!ProtocolVersion.VERSION_2025_03_26.getValue().equals(serverVersion)) {
                    return CompletableFuture.failedFuture(
                        new McpException(
                            "MCP version mismatch: client ("
                                + ProtocolVersion.VERSION_2025_03_26.getValue()
                                + ") != server ("
                                + serverVersion
                                + ")"));
                  }

                  Optional<String> sessionIdOpt = res.headers().firstValue("Mcp-Session-Id");
                  if (sessionIdOpt.isEmpty()) {
                    return CompletableFuture.failedFuture(
                        new McpException(
                            "Server did not return a Mcp-Session-Id header during"
                                + " initialization."));
                  }
                  this.sessionId = sessionIdOpt.get();

                  JsonRpc.Notification notif =
                      new JsonRpc.Notification("notifications/initialized", Map.of());
                  String notifBody = objectMapper.writeValueAsString(notif);
                  HttpRequest.Builder nReq =
                      HttpRequest.newBuilder()
                          .uri(URI.create(baseUrl))
                          .POST(HttpRequest.BodyPublishers.ofString(notifBody));

                  handshakeHeaders.forEach(nReq::setHeader);
                  applyProtocolHeaders(nReq);

                  return httpClient
                      .sendAsync(nReq.build(), HttpResponse.BodyHandlers.ofString())
                      .thenAccept(nRes -> {});
                } catch (Exception e) {
                  return CompletableFuture.failedFuture(e);
                }
              });
    } catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @Override
  protected void applyProtocolHeaders(final HttpRequest.Builder builder) {
    builder.header("Content-Type", "application/json");
    builder.header("Accept", "application/json");
    if (sessionId != null) {
      builder.header("Mcp-Session-Id", sessionId);
    }
  }
}
