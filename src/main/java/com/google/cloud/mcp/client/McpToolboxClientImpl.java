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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.mcp.McpToolboxClient;
import com.google.cloud.mcp.auth.AuthTokenGetter;
import com.google.cloud.mcp.auth.CredentialsProvider;
import com.google.cloud.mcp.tool.Tool;
import com.google.cloud.mcp.tool.ToolDefinition;
import com.google.cloud.mcp.tool.ToolPostProcessor;
import com.google.cloud.mcp.tool.ToolPreProcessor;
import com.google.cloud.mcp.tool.ToolResult;
import com.google.cloud.mcp.transport.HttpMcpTransport;
import com.google.cloud.mcp.transport.Transport;
import com.google.cloud.mcp.transport.TransportManifest;
import com.google.cloud.mcp.transport.TransportResponse;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

  private final List<ToolPreProcessor> preProcessors;
  private final List<ToolPostProcessor> postProcessors;

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
   * @param transport The underlying MCP transport layer.
   * @param headers Fallback headers for deprecated constructor compatibility.
   * @param credentialsProvider Fallback provider for deprecated constructor compatibility.
   */
  @Deprecated
  public McpToolboxClientImpl(
      Transport transport, Map<String, String> headers, CredentialsProvider credentialsProvider) {
    this(transport, headers, credentialsProvider, null, null);
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

  /**
   * Primary constructor for McpToolboxClientImpl.
   *
   * @param transport The underlying MCP transport layer.
   * @param headers Default HTTP headers.
   * @param credentialsProvider Provider for credentials.
   * @param preProcessors List of pre-processors.
   * @param postProcessors List of post-processors.
   */
  public McpToolboxClientImpl(
      Transport transport,
      Map<String, String> headers,
      CredentialsProvider credentialsProvider,
      List<ToolPreProcessor> preProcessors,
      List<ToolPostProcessor> postProcessors) {
    this.transport = transport;
    this.headers =
        headers != null
            ? java.util.Collections.unmodifiableMap(new java.util.HashMap<>(headers))
            : java.util.Collections.emptyMap();
    this.credentialsProvider = credentialsProvider;
    this.preProcessors = preProcessors != null ? List.copyOf(preProcessors) : List.of();
    this.postProcessors = postProcessors != null ? List.copyOf(postProcessors) : List.of();
    this.objectMapper = new ObjectMapper();
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
              for (Map.Entry<String, Object> bind : paramBinds.get(toolName).entrySet()) {
                tool = tool.bindParam(bind.getKey(), bind.getValue());
              }
            }
            if (authBinds != null && authBinds.containsKey(toolName)) {
              for (Map.Entry<String, AuthTokenGetter> bind : authBinds.get(toolName).entrySet()) {
                tool = tool.addAuthTokenGetter(bind.getKey(), bind.getValue());
              }
            }
            for (ToolPreProcessor preProcessor : this.preProcessors) {
              tool = tool.addPreProcessor(preProcessor);
            }
            for (ToolPostProcessor postProcessor : this.postProcessors) {
              tool = tool.addPostProcessor(postProcessor);
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
                for (Map.Entry<String, AuthTokenGetter> entry : authTokenGetters.entrySet()) {
                  tool = tool.addAuthTokenGetter(entry.getKey(), entry.getValue());
                }
              }
              for (ToolPreProcessor preProcessor : this.preProcessors) {
                tool = tool.addPreProcessor(preProcessor);
              }
              for (ToolPostProcessor postProcessor : this.postProcessors) {
                tool = tool.addPostProcessor(postProcessor);
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
