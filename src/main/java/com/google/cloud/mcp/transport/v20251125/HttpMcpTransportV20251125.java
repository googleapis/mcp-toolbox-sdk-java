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

package com.google.cloud.mcp.transport.v20251125;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.cloud.mcp.JsonRpc;
import com.google.cloud.mcp.ProtocolVersion;
import com.google.cloud.mcp.auth.CredentialsProvider;
import com.google.cloud.mcp.exception.McpException;
import com.google.cloud.mcp.transport.BaseMcpTransport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class HttpMcpTransportV20251125 extends BaseMcpTransport {

  public HttpMcpTransportV20251125(
      final String baseUrl,
      final Map<String, String> clientHeaders,
      final CredentialsProvider credentialsProvider,
      final HttpClient httpClient,
      final java.util.concurrent.Executor executor) {
    super(
        baseUrl,
        clientHeaders,
        credentialsProvider,
        ProtocolVersion.VERSION_2025_11_25,
        httpClient,
        executor);
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
                  ProtocolVersion.VERSION_2025_11_25.getValue(), "mcp-toolbox-sdk-java"));
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
                    serverVersion = ProtocolVersion.VERSION_2025_11_25.getValue();
                  }

                  if (!ProtocolVersion.VERSION_2025_11_25.getValue().equals(serverVersion)) {
                    return CompletableFuture.failedFuture(
                        new McpException(
                            "MCP version mismatch: client ("
                                + ProtocolVersion.VERSION_2025_11_25.getValue()
                                + ") != server ("
                                + serverVersion
                                + ")"));
                  }

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
    builder.header("MCP-Protocol-Version", ProtocolVersion.VERSION_2025_11_25.getValue());
  }
}
