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

import com.google.cloud.mcp.McpToolboxClient;
import com.google.cloud.mcp.McpToolboxSyncClient;
import com.google.cloud.mcp.ProtocolVersion;
import com.google.cloud.mcp.auth.CredentialsProvider;
import com.google.cloud.mcp.tool.ToolPostProcessor;
import com.google.cloud.mcp.tool.ToolPreProcessor;
import com.google.cloud.mcp.transport.HttpMcpTransport;
import com.google.cloud.mcp.transport.Transport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Implementation of the {@link McpToolboxClient.Builder} interface. */
public final class McpToolboxClientBuilder implements McpToolboxClient.Builder {
  private String baseUrl;
  private String apiKey;
  private Map<String, String> headers = new HashMap<>();
  private CredentialsProvider credentialsProvider;
  private final List<ToolPreProcessor> preProcessors = new ArrayList<>();
  private final List<ToolPostProcessor> postProcessors = new ArrayList<>();
  private ProtocolVersion protocolVersion;
  private java.time.Duration connectTimeout;
  private java.time.Duration requestTimeout;
  private java.net.http.HttpClient httpClient;
  private java.util.concurrent.Executor executor;
  private java.util.logging.Logger logger;

  /** Constructs a new McpToolboxClientBuilder. */
  public McpToolboxClientBuilder() {}

  @Override
  public McpToolboxClient.Builder baseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
    return this;
  }

  @Override
  public McpToolboxClient.Builder apiKey(String apiKey) {
    this.apiKey = apiKey;
    return this;
  }

  @Override
  public McpToolboxClient.Builder headers(Map<String, String> headers) {
    if (headers != null) {
      this.headers.putAll(headers);
    }
    return this;
  }

  @Override
  public McpToolboxClient.Builder credentialsProvider(CredentialsProvider credentialsProvider) {
    this.credentialsProvider = credentialsProvider;
    return this;
  }

  @Override
  public McpToolboxClient.Builder preProcessor(ToolPreProcessor preProcessor) {
    if (preProcessor != null) {
      this.preProcessors.add(preProcessor);
    }
    return this;
  }

  @Override
  public McpToolboxClient.Builder postProcessor(ToolPostProcessor postProcessor) {
    if (postProcessor != null) {
      this.postProcessors.add(postProcessor);
    }
    return this;
  }

  @Override
  public McpToolboxClient.Builder protocolVersion(ProtocolVersion protocolVersion) {
    this.protocolVersion = protocolVersion;
    return this;
  }

  @Override
  public McpToolboxClient.Builder connectTimeout(java.time.Duration connectTimeout) {
    this.connectTimeout = connectTimeout;
    return this;
  }

  @Override
  public McpToolboxClient.Builder requestTimeout(java.time.Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public McpToolboxClient.Builder httpClient(java.net.http.HttpClient httpClient) {
    this.httpClient = httpClient;
    return this;
  }

  @Override
  public McpToolboxClient.Builder executor(java.util.concurrent.Executor executor) {
    this.executor = executor;
    return this;
  }

  @Override
  public McpToolboxClient.Builder logger(java.util.logging.Logger logger) {
    this.logger = logger;
    return this;
  }

  @Override
  public McpToolboxClient build() {
    if (baseUrl == null || baseUrl.isEmpty()) {
      throw new IllegalArgumentException("Base URL must be provided");
    }
    // Normalize URL: remove trailing slash if present
    if (baseUrl.endsWith("/")) {
      baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
    }

    CredentialsProvider resolvedProvider = this.credentialsProvider;
    boolean hasStaticAuth = false;
    for (String key : this.headers.keySet()) {
      if ("Authorization".equalsIgnoreCase(key)) {
        hasStaticAuth = true;
        break;
      }
    }
    if (resolvedProvider == null
        && !hasStaticAuth
        && this.apiKey != null
        && !this.apiKey.isEmpty()) {
      String bearerKey = this.apiKey.startsWith("Bearer ") ? this.apiKey : "Bearer " + this.apiKey;
      resolvedProvider = () -> CompletableFuture.completedFuture(bearerKey);
    }

    Transport transport =
        new HttpMcpTransport(
            baseUrl,
            this.headers,
            resolvedProvider,
            this.protocolVersion,
            this.httpClient,
            this.executor,
            this.connectTimeout,
            this.requestTimeout,
            this.logger);
    return new McpToolboxClientImpl(
        transport, this.headers, resolvedProvider, preProcessors, postProcessors);
  }

  @Override
  public McpToolboxSyncClient buildSync() {
    return new HttpMcpToolboxSyncClient(build());
  }
}
