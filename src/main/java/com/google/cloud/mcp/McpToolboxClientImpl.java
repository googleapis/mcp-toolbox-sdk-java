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
public class McpToolboxClientImpl implements McpToolboxClient {

  private static final Logger logger = Logger.getLogger(McpToolboxClientImpl.class.getName());
  private static final String HTTP_WARNING =
      "This connection is using HTTP. To prevent credential exposure, please ensure all"
          + " communication is sent over HTTPS.";
  private final Transport transport;
  private final Map<String, String> headers;
  private final CredentialsProvider credentialsProvider;
  private final ObjectMapper objectMapper;
  private final List<ToolPreProcessor> preProcessors;
  private final List<ToolPostProcessor> postProcessors;

  /**
   * Constructs a new McpToolboxClientImpl.
   *
   * @param transport The underlying MCP transport layer.
   * @param credentialsProvider The provider for authentication headers (optional).
   */
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
  public McpToolboxClientImpl(String baseUrl, String apiKey) {
    this(new HttpMcpTransport(baseUrl), Collections.emptyMap(), apiKeyToProvider(apiKey));
  }

  /**
   * Constructs a new McpToolboxClientImpl with generic headers.
   *
   * @param baseUrl The base URL of the MCP Toolbox Server.
   * @param headers The HTTP headers to include in requests.
   */
  @Deprecated
  public McpToolboxClientImpl(String baseUrl, Map<String, String> headers) {
    this(new HttpMcpTransport(baseUrl), headers, null);
  }

  /**
   * Constructs a new McpToolboxClientImpl.
   *
   * @param baseUrl The base URL of the MCP Toolbox Server.
   * @param headers The HTTP headers to include in requests.
   * @param credentialsProvider The provider for authentication headers (optional).
   */
  @Deprecated
  public McpToolboxClientImpl(
      String baseUrl, Map<String, String> headers, CredentialsProvider credentialsProvider) {
    this(new HttpMcpTransport(baseUrl), headers, credentialsProvider);
  }

  /** Deprecated constructor. Use the constructor accepting {@link CredentialsProvider} instead. */
  @Deprecated
  public McpToolboxClientImpl(String baseUrl, CredentialsProvider credentialsProvider) {
    this(new HttpMcpTransport(baseUrl), Collections.emptyMap(), credentialsProvider);
  }

  /** Deprecated constructor. Use the constructor accepting {@link Transport} instead. */
  @Deprecated
  public McpToolboxClientImpl(Transport transport, CredentialsProvider credentialsProvider) {
    this(transport, Collections.emptyMap(), credentialsProvider);
  }

  private static CredentialsProvider apiKeyToProvider(String apiKey) {
    if (apiKey == null || apiKey.isEmpty()) {
      return null;
    }
    String bearerKey = apiKey.startsWith("Bearer ") ? apiKey : "Bearer " + apiKey;
    return () -> CompletableFuture.completedFuture(bearerKey);
  }

  /** Primary constructor for McpToolboxClientImpl. */
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

  @Override
  public CompletableFuture<Map<String, ToolDefinition>> listTools() {
    return loadToolset("");
  }

  @Override
  public CompletableFuture<Map<String, ToolDefinition>> loadToolset(String toolsetName) {
    return getAuthorizationHeader()
        .thenCompose(
            authHeader -> {
              Map<String, String> mergedHeaders = new HashMap<>(this.headers);
              if (authHeader != null) {
                mergedHeaders.put("Authorization", authHeader);
              }
              return transport
                  .listTools(toolsetName, mergedHeaders)
                  .thenApply(TransportManifest::getTools);
            });
  }

  @Override
  public CompletableFuture<Map<String, Tool>> loadToolset(
      String toolsetName,
      Map<String, Map<String, Object>> paramBinds,
      Map<String, Map<String, AuthTokenGetter>> authBinds,
      boolean strict) {

    if (this.transport.getBaseUrl().toLowerCase(java.util.Locale.ROOT).startsWith("http://")
        && authBinds != null
        && !authBinds.isEmpty()) {
      logger.warning(HTTP_WARNING);
    }

    CompletableFuture<Map<String, ToolDefinition>> definitionsFuture = loadToolset(toolsetName);

    return definitionsFuture.thenApply(
        defs -> {
          if (strict) {
            Set<String> unknownTools = new HashSet<>();
            if (paramBinds != null) unknownTools.addAll(paramBinds.keySet());
            if (authBinds != null) unknownTools.addAll(authBinds.keySet());
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
            for (ToolPreProcessor preProcessor : this.preProcessors) {
              tool.addPreProcessor(preProcessor);
            }
            for (ToolPostProcessor postProcessor : this.postProcessors) {
              tool.addPostProcessor(postProcessor);
            }
            tools.put(toolName, tool);
          }
          return tools;
        });
  }

  @Override
  public CompletableFuture<Tool> loadTool(String toolName) {
    return loadTool(toolName, Collections.emptyMap());
  }

  @Override
  public CompletableFuture<Tool> loadTool(
      String toolName, Map<String, AuthTokenGetter> authTokenGetters) {
    if (this.transport.getBaseUrl().toLowerCase(java.util.Locale.ROOT).startsWith("http://")
        && authTokenGetters != null
        && !authTokenGetters.isEmpty()) {
      logger.warning(HTTP_WARNING);
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
              for (ToolPreProcessor preProcessor : this.preProcessors) {
                tool.addPreProcessor(preProcessor);
              }
              for (ToolPostProcessor postProcessor : this.postProcessors) {
                tool.addPostProcessor(postProcessor);
              }
              return tool;
            });
  }

  @Override
  public CompletableFuture<ToolResult> invokeTool(String toolName, Map<String, Object> arguments) {
    return invokeTool(toolName, arguments, Collections.emptyMap());
  }

  @Override
  public CompletableFuture<ToolResult> invokeTool(
      String toolName, Map<String, Object> arguments, Map<String, String> extraHeaders) {
    if (this.transport.getBaseUrl().toLowerCase(java.util.Locale.ROOT).startsWith("http://")
        && extraHeaders != null
        && !extraHeaders.isEmpty()) {
      logger.warning(HTTP_WARNING);
    }
    return getAuthorizationHeader()
        .thenCompose(
            adcHeader -> {
              try {
                // Determine priority Auth header before init so init requests can use it if
                // needed
                String finalAuthHeader = null;
                String authKeyInExtra =
                    extraHeaders.keySet().stream()
                        .filter(k -> "Authorization".equalsIgnoreCase(k))
                        .findFirst()
                        .orElse(null);

                if (authKeyInExtra != null) {
                  finalAuthHeader = extraHeaders.get(authKeyInExtra);
                } else if (adcHeader != null) {
                  finalAuthHeader = adcHeader;
                }

                Map<String, String> mergedHeaders = new HashMap<>(this.headers);
                extraHeaders.forEach(
                    (k, val) -> {
                      if (!"Authorization".equalsIgnoreCase(k)) {
                        mergedHeaders.put(k, val);
                      }
                    });
                if (finalAuthHeader != null) {
                  mergedHeaders.put("Authorization", finalAuthHeader);
                }

                return transport
                    .invokeTool(toolName, arguments, mergedHeaders)
                    .thenApply(response -> handleInvokeResponse(response, toolName));

              } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
              }
            });
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

  private ToolResult handleInvokeResponse(TransportResponse response, String toolName) {
    String body = response.getBody();
    if (response.getStatusCode() != 200) {
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
