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

package com.google.cloud.mcp.transport.v20260618;

import com.google.cloud.mcp.JsonRpc;
import com.google.cloud.mcp.ProtocolVersion;
import com.google.cloud.mcp.auth.CredentialsProvider;
import com.google.cloud.mcp.transport.BaseMcpTransport;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class HttpMcpTransportV20260618 extends BaseMcpTransport {

  public HttpMcpTransportV20260618(
      final String baseUrl,
      final Map<String, String> clientHeaders,
      final CredentialsProvider credentialsProvider,
      final HttpClient httpClient,
      final java.util.concurrent.Executor executor) {
    super(
        baseUrl,
        clientHeaders,
        credentialsProvider,
        ProtocolVersion.VERSION_2026_06_18,
        httpClient,
        executor);
  }

  @Override
  protected CompletableFuture<Void> performInitialization(
      final String authHeader, final Map<String, String> handshakeHeaders) {
    return CompletableFuture.completedFuture(null);
  }

  @Override
  protected void applyProtocolHeaders(final HttpRequest.Builder builder) {
    builder.header("Content-Type", "application/json");
    builder.header("Accept", "application/json");
    builder.header("MCP-Protocol-Version", ProtocolVersion.VERSION_2026_06_18.getValue());
  }

  @Override
  protected void applyProtocolHeaders(
      final HttpRequest.Builder builder, final String method, final String name) {
    applyProtocolHeaders(builder);
    if (method != null) {
      builder.header("Mcp-Method", method);
    }
    if (name != null) {
      builder.header("Mcp-Name", name);
    }
  }

  @Override
  protected Object modifyRequestParams(final String method, final Object params) {
    Map<String, Object> meta = getRequestMeta();
    if ("tools/list".equals(method)) {
      return Map.of("_meta", meta);
    } else if ("tools/call".equals(method) && params instanceof JsonRpc.CallToolParams) {
      JsonRpc.CallToolParams callParams = (JsonRpc.CallToolParams) params;
      callParams._meta = meta;
      return callParams;
    }
    return params;
  }

  private Map<String, Object> getRequestMeta() {
    return Map.of(
        "io.modelcontextprotocol/protocolVersion", ProtocolVersion.VERSION_2026_06_18.getValue(),
        "io.modelcontextprotocol/clientInfo",
            Map.of("name", "mcp-toolbox-sdk-java", "version", "1.0.0"),
        "io.modelcontextprotocol/clientCapabilities", Map.of());
  }
}
