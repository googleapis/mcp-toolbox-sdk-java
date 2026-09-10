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

import com.google.cloud.mcp.ProtocolVersion;
import com.google.cloud.mcp.auth.CredentialsProvider;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/** Default HTTP transport implementation routing requests to version-specific handlers. */
public final class HttpMcpTransport implements Transport {

  private final Transport delegate;

  /**
   * Constructs a new HttpMcpTransport with a base URL.
   *
   * @param baseUrl The base URL of the remote service.
   */
  public HttpMcpTransport(final String baseUrl) {
    this(baseUrl, Map.of(), (CredentialsProvider) null);
  }

  /**
   * Constructs a new HttpMcpTransport with base URL and default headers.
   *
   * @param baseUrl The base URL of the remote service.
   * @param clientHeaders Default HTTP headers to include in every request.
   */
  public HttpMcpTransport(final String baseUrl, final Map<String, String> clientHeaders) {
    this(baseUrl, clientHeaders, (CredentialsProvider) null);
  }

  /**
   * Constructs a new HttpMcpTransport with base URL, default headers and credentials provider.
   *
   * @param baseUrl The base URL of the remote service.
   * @param clientHeaders Default HTTP headers to include in every request.
   * @param credentialsProvider Provider for retrieving authorization credentials.
   */
  public HttpMcpTransport(
      final String baseUrl,
      final Map<String, String> clientHeaders,
      final CredentialsProvider credentialsProvider) {
    this(baseUrl, clientHeaders, credentialsProvider, null, null, null);
  }

  /**
   * Constructs a HttpMcpTransport.
   *
   * @param baseUrl The base URL of the remote service.
   * @param clientHeaders Default HTTP headers to include in every request.
   * @param preferredProtocolVersion Preferred MCP protocol version.
   * @param httpClient Custom HTTP Client.
   * @param executor Optional Executor for handling async requests.
   */
  public HttpMcpTransport(
      final String baseUrl,
      final Map<String, String> clientHeaders,
      final ProtocolVersion preferredProtocolVersion,
      final HttpClient httpClient,
      final java.util.concurrent.Executor executor) {
    this(baseUrl, clientHeaders, null, preferredProtocolVersion, httpClient, executor);
  }

  /**
   * Primary constructor for HttpMcpTransport.
   *
   * @param baseUrl The base URL of the remote service.
   * @param clientHeaders Default HTTP headers to include in every request.
   * @param credentialsProvider Provider for retrieving authorization credentials.
   * @param preferredProtocolVersion Preferred MCP protocol version.
   * @param httpClient Custom HTTP Client.
   * @param executor Optional Executor for handling async requests.
   */
  public HttpMcpTransport(
      final String baseUrl,
      final Map<String, String> clientHeaders,
      final CredentialsProvider credentialsProvider,
      final ProtocolVersion preferredProtocolVersion,
      final HttpClient httpClient,
      final java.util.concurrent.Executor executor) {
    this(
        baseUrl,
        clientHeaders,
        credentialsProvider,
        preferredProtocolVersion,
        httpClient,
        executor,
        null,
        null,
        null);
  }

  /**
   * Constructs a new HttpMcpTransport with full configuration.
   *
   * @param baseUrl The base URL of the MCP service.
   * @param clientHeaders Optional headers to include in every request.
   * @param credentialsProvider Optional provider for auth credentials.
   * @param preferredProtocolVersion Optional preferred protocol version.
   * @param httpClient Optional HttpClient instance.
   * @param executor Optional Executor for handling async requests.
   * @param connectTimeout Optional connection timeout.
   * @param requestTimeout Optional request timeout.
   * @param logger Optional Logger instance.
   */
  public HttpMcpTransport(
      final String baseUrl,
      final Map<String, String> clientHeaders,
      final CredentialsProvider credentialsProvider,
      final ProtocolVersion preferredProtocolVersion,
      final HttpClient httpClient,
      final java.util.concurrent.Executor executor,
      final Duration connectTimeout,
      final Duration requestTimeout,
      final Logger logger) {
    final ProtocolVersion version =
        preferredProtocolVersion != null
            ? preferredProtocolVersion
            : ProtocolVersion.VERSION_2025_11_25;

    switch (version) {
      case VERSION_2025_11_25:
        this.delegate =
            new HttpMcpTransportV20251125(
                baseUrl,
                clientHeaders,
                credentialsProvider,
                httpClient,
                executor,
                connectTimeout,
                requestTimeout,
                logger);
        break;
      case VERSION_2025_06_18:
        this.delegate =
            new HttpMcpTransportV20250618(
                baseUrl,
                clientHeaders,
                credentialsProvider,
                httpClient,
                executor,
                connectTimeout,
                requestTimeout,
                logger);
        break;
      case VERSION_2025_03_26:
        this.delegate =
            new HttpMcpTransportV20250326(
                baseUrl,
                clientHeaders,
                credentialsProvider,
                httpClient,
                executor,
                connectTimeout,
                requestTimeout,
                logger);
        break;
      case VERSION_2024_11_05:
        this.delegate =
            new HttpMcpTransportV20241105(
                baseUrl,
                clientHeaders,
                credentialsProvider,
                httpClient,
                executor,
                connectTimeout,
                requestTimeout,
                logger);
        break;
      default:
        throw new IllegalArgumentException("Unsupported protocol version: " + version);
    }
  }

  /**
   * Internal constructor for testing purposes.
   *
   * @param baseUrl The base URL.
   * @param httpClient The mock HttpClient.
   */
  public HttpMcpTransport(final String baseUrl, final HttpClient httpClient) {
    this(baseUrl, Map.of(), null, null, httpClient, null);
  }

  /**
   * Internal constructor for testing purposes.
   *
   * @param baseUrl The base URL.
   * @param clientHeaders The client headers.
   * @param httpClient The mock HttpClient.
   */
  public HttpMcpTransport(
      final String baseUrl, final Map<String, String> clientHeaders, final HttpClient httpClient) {
    this(baseUrl, clientHeaders, null, null, httpClient, null);
  }

  HttpMcpTransport(
      final String baseUrl,
      final Map<String, String> clientHeaders,
      final CredentialsProvider credentialsProvider,
      final HttpClient httpClient) {
    this(baseUrl, clientHeaders, credentialsProvider, null, httpClient, null);
  }

  @Override
  public String getBaseUrl() {
    return delegate.getBaseUrl();
  }

  @Override
  public CompletableFuture<TransportManifest> listTools(
      final String toolsetName, final Map<String, String> metadata) {
    return delegate.listTools(toolsetName, metadata);
  }

  @Override
  public CompletableFuture<TransportResponse> invokeTool(
      final String toolName,
      final Map<String, Object> arguments,
      final Map<String, String> metadata) {
    return delegate.invokeTool(toolName, arguments, metadata);
  }

  @Override
  public void close() {
    delegate.close();
  }
}
