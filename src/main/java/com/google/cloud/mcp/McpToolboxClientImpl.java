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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/** Default implementation using Java 11 HttpClient. */
public final class McpToolboxClientImpl implements McpToolboxClient {

  /** Logger for logging messages. */
  private static final Logger LOGGER = Logger.getLogger(McpToolboxClientImpl.class.getName());

  /** Warning message for non-HTTPS URL usage. */
  private static final String HTTP_WARNING =
      "This connection is using HTTP. To prevent credential exposure, "
          + "please ensure all communication is sent over HTTPS.";

  /** The transport layer. */
  private final Transport transport;

  /** Client headers. */
  private final Map<String, String> headers;

  /** Credentials provider. */
  private final CredentialsProvider credentialsProvider;

  /** Jackson ObjectMapper for JSON parsing. */
  private final ObjectMapper objectMapper;

  /**
   * Constructs a new McpToolboxClientImpl.
   *
   * @param clientTransport The underlying MCP transport layer.
   */
  public McpToolboxClientImpl(final Transport clientTransport) {
    this(clientTransport, java.util.Collections.emptyMap(), null);
  }

  /**
   * Constructs a new McpToolboxClientImpl.
   *
   * @param clientTransport The underlying MCP transport layer.
   * @param clientHeaders Fallback headers for deprecated constructor compatibility.
   * @param provider Fallback provider for deprecated constructor compatibility.
   */
  @Deprecated
  public McpToolboxClientImpl(
      final Transport clientTransport,
      final Map<String, String> clientHeaders,
      final CredentialsProvider provider) {
    this.transport = clientTransport;
    this.headers =
        clientHeaders != null
            ? java.util.Collections.unmodifiableMap(new java.util.HashMap<>(clientHeaders))
            : java.util.Collections.emptyMap();
    this.credentialsProvider = provider;
    this.objectMapper = new ObjectMapper();
  }

  /**
   * Deprecated constructor. Use the constructor accepting {@link CredentialsProvider} instead.
   *
   * @param baseUrl The base URL.
   * @param apiKey The static API key.
   */
  @Deprecated
  public McpToolboxClientImpl(final String baseUrl, final String apiKey) {
    this(
        new HttpMcpTransport(baseUrl, Collections.emptyMap(), apiKeyToProvider(apiKey)),
        Collections.emptyMap(),
        apiKeyToProvider(apiKey));
  }

  /**
   * Constructs a new McpToolboxClientImpl with generic headers.
   *
   * @param baseUrl The base URL of the MCP Toolbox Server.
   * @param clientHeaders The HTTP headers to include in requests.
   */
  @Deprecated
  public McpToolboxClientImpl(final String baseUrl, final Map<String, String> clientHeaders) {
    this(new HttpMcpTransport(baseUrl, clientHeaders), clientHeaders, null);
  }

  /**
   * Constructs a new McpToolboxClientImpl.
   *
   * @param baseUrl The base URL of the MCP Toolbox Server.
   * @param clientHeaders The HTTP headers to include in requests.
   * @param provider The provider for authentication headers (optional).
   */
  @Deprecated
  public McpToolboxClientImpl(
      final String baseUrl,
      final Map<String, String> clientHeaders,
      final CredentialsProvider provider) {
    this(new HttpMcpTransport(baseUrl, clientHeaders, provider), clientHeaders, provider);
  }

  /**
   * Deprecated constructor. Use the constructor accepting {@link CredentialsProvider} instead.
   *
   * @param baseUrl The base URL.
   * @param provider The provider for auth headers.
   */
  @Deprecated
  public McpToolboxClientImpl(final String baseUrl, final CredentialsProvider provider) {
    this(
        new HttpMcpTransport(baseUrl, Collections.emptyMap(), provider),
        Collections.emptyMap(),
        provider);
  }

  /**
   * Deprecated constructor. Use the constructor accepting {@link Transport} instead.
   *
   * @param clientTransport The underlying transport.
   * @param provider The provider for auth headers.
   */
  @Deprecated
  public McpToolboxClientImpl(final Transport clientTransport, final CredentialsProvider provider) {
    this(clientTransport, Collections.emptyMap(), provider);
  }

  private static CredentialsProvider apiKeyToProvider(final String apiKey) {
    if (apiKey == null || apiKey.isEmpty()) {
      return null;
    }
    String bearerKey = apiKey.startsWith("Bearer ") ? apiKey : "Bearer " + apiKey;
    return () -> CompletableFuture.completedFuture(bearerKey);
  }

  private CompletableFuture<Map<String, String>> getMergedMetadata(
      final Map<String, String> extraMetadata) {
    if (this.transport instanceof HttpMcpTransport) {
      return CompletableFuture.completedFuture(
          extraMetadata != null ? extraMetadata : java.util.Collections.emptyMap());
    }
    if (this.credentialsProvider == null && this.headers.isEmpty()) {
      return CompletableFuture.completedFuture(
          extraMetadata != null ? extraMetadata : java.util.Collections.emptyMap());
    }
    return getAuthorizationHeader()
        .thenApply(
            authHeader -> {
              Map<String, String> merged = new HashMap<>(this.headers);
              if (extraMetadata != null) {
                extraMetadata.forEach(
                    (k, v) -> {
                      if (!"Authorization".equalsIgnoreCase(k)) {
                        merged.put(k, v);
                      }
                    });
              }
              String finalAuthHeader = null;
              if (extraMetadata != null) {
                finalAuthHeader =
                    extraMetadata.keySet().stream()
                        .filter(k -> "Authorization".equalsIgnoreCase(k))
                        .findFirst()
                        .map(extraMetadata::get)
                        .orElse(null);
              }
              if (finalAuthHeader == null) {
                finalAuthHeader = authHeader;
              }
              if (finalAuthHeader != null) {
                merged.put("Authorization", finalAuthHeader);
              }
              return merged;
            });
  }

  @Override
  public CompletableFuture<Map<String, ToolDefinition>> listTools() {
    return loadToolset("");
  }

  @Override
  public CompletableFuture<Map<String, ToolDefinition>> loadToolset(final String toolsetName) {
    return getMergedMetadata(java.util.Collections.emptyMap())
        .thenCompose(
            mergedMetadata ->
                transport
                    .listTools(toolsetName, mergedMetadata)
                    .thenApply(TransportManifest::getTools));
  }

  @Override
  public CompletableFuture<Map<String, Tool>> loadToolset(
      final String toolsetName,
      final Map<String, Map<String, Object>> paramBinds,
      final Map<String, Map<String, AuthTokenGetter>> authBinds,
      final boolean strict) {

    if (this.transport.getBaseUrl().toLowerCase(java.util.Locale.ROOT).startsWith("http://")
        && authBinds != null
        && !authBinds.isEmpty()) {
      LOGGER.warning(HTTP_WARNING);
    }

    CompletableFuture<Map<String, ToolDefinition>> definitionsFuture = loadToolset(toolsetName);

    return definitionsFuture.thenApply(
        defs -> {
          if (strict) {
            Set<String> unknownTools = new HashSet<>();
            if (paramBinds != null) {
              unknownTools.addAll(paramBinds.keySet());
            }
            if (authBinds != null) {
              unknownTools.addAll(authBinds.keySet());
            }
            unknownTools.removeAll(defs.keySet());
            if (!unknownTools.isEmpty()) {
              throw new IllegalArgumentException(
                  "Strict mode error: Bindings provided for unknown tools: " + unknownTools);
            }
          }

          Map<String, Tool> tools = new HashMap<>();
          for (Map.Entry<String, ToolDefinition> entry : defs.entrySet()) {
            String toolName = entry.getKey();
            Tool tool = new Tool(toolName, entry.getValue(), this);
            if (paramBinds != null && paramBinds.containsKey(toolName)) {
              paramBinds.get(toolName).forEach(tool::bindParam);
            }
            if (authBinds != null && authBinds.containsKey(toolName)) {
              authBinds.get(toolName).forEach(tool::addAuthTokenGetter);
            }
            tools.put(toolName, tool);
          }
          return tools;
        });
  }

  @Override
  public CompletableFuture<Tool> loadTool(final String toolName) {
    return loadTool(toolName, Collections.emptyMap());
  }

  @Override
  public CompletableFuture<Tool> loadTool(
      final String toolName, final Map<String, AuthTokenGetter> authTokenGetters) {
    if (this.transport.getBaseUrl().toLowerCase(java.util.Locale.ROOT).startsWith("http://")
        && authTokenGetters != null
        && !authTokenGetters.isEmpty()) {
      LOGGER.warning(HTTP_WARNING);
    }
    return listTools()
        .thenApply(
            tools -> {
              if (!tools.containsKey(toolName)) {
                throw new RuntimeException("Tool not found: " + toolName);
              }
              Tool tool = new Tool(toolName, tools.get(toolName), this);
              if (authTokenGetters != null) {
                authTokenGetters.forEach(tool::addAuthTokenGetter);
              }
              return tool;
            });
  }

  @Override
  public CompletableFuture<ToolResult> invokeTool(
      final String toolName, final Map<String, Object> arguments) {
    return invokeTool(toolName, arguments, Collections.emptyMap());
  }

  @Override
  public CompletableFuture<ToolResult> invokeTool(
      final String toolName,
      final Map<String, Object> arguments,
      final Map<String, String> extraHeaders) {
    if (this.transport.getBaseUrl().toLowerCase(java.util.Locale.ROOT).startsWith("http://")
        && extraHeaders != null
        && !extraHeaders.isEmpty()) {
      LOGGER.warning(HTTP_WARNING);
    }
    return getMergedMetadata(extraHeaders)
        .thenCompose(
            mergedMetadata ->
                transport
                    .invokeTool(toolName, arguments, mergedMetadata)
                    .thenApply(res -> handleInvokeResponse(res, toolName)));
  }

  private CompletableFuture<String> getAuthorizationHeader() {
    if (this.credentialsProvider != null) {
      return this.credentialsProvider.getAuthorizationHeader();
    }
    for (Map.Entry<String, String> entry : this.headers.entrySet()) {
      if ("Authorization".equalsIgnoreCase(entry.getKey())) {
        return CompletableFuture.completedFuture(entry.getValue());
      }
    }
    return CompletableFuture.completedFuture(null);
  }

  private ToolResult handleInvokeResponse(final TransportResponse response, final String toolName) {
    String body = response.getBody();
    if (response.getStatusCode() != java.net.HttpURLConnection.HTTP_OK) {
      return new ToolResult(
          java.util.List.of(
              new ToolResult.Content("text", "Error " + response.getStatusCode() + ": " + body)),
          true);
    }
    try {
      JsonNode root = objectMapper.readTree(body);
      if (root.has("error")) {
        return new ToolResult(
            java.util.List.of(
                new ToolResult.Content("text", "MCP Error: " + root.get("error").toString())),
            true);
      }

      boolean isError = root.has("isError") && root.get("isError").asBoolean();

      JsonNode result = root.get("result");
      if (result != null) {
        ToolResult parsedResult = objectMapper.treeToValue(result, ToolResult.class);
        if (parsedResult.content() == null) {
          return new ToolResult(
              java.util.List.of(new ToolResult.Content("text", result.asText())),
              isError || parsedResult.isError());
        }
        return parsedResult;
      }

      return new ToolResult(java.util.List.of(new ToolResult.Content("text", body)), isError);
    } catch (Exception e) {
      return new ToolResult(java.util.List.of(new ToolResult.Content("text", body)), false);
    }
  }
}
