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
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Default implementation using Java 11 HttpClient. */
public class HttpMcpToolboxClient implements McpToolboxClient {

  private final String baseUrl;
  private final String apiKey;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private boolean initialized = false;
  private final String protocolVersion = "2025-11-25";

  /**
   * Constructs a new HttpMcpToolboxClient.
   *
   * @param baseUrl The base URL of the MCP Toolbox Server.
   * @param apiKey The API key for authentication (optional).
   */
  public HttpMcpToolboxClient(String baseUrl, String apiKey) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.apiKey = apiKey;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    this.objectMapper = new ObjectMapper();
  }

  private synchronized CompletableFuture<Void> ensureInitialized(String authHeader) {
    if (initialized) return CompletableFuture.completedFuture(null);
    try {
      JsonRpc.Request initReq =
          new JsonRpc.Request(
              "initialize", new JsonRpc.InitializeParams(protocolVersion, "mcp-toolbox-sdk-java"));
      String body = objectMapper.writeValueAsString(initReq);
      HttpRequest.Builder req =
          HttpRequest.newBuilder()
              .uri(URI.create(baseUrl))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body));
      if (authHeader != null) req.header("Authorization", authHeader);

      return httpClient
          .sendAsync(req.build(), HttpResponse.BodyHandlers.ofString())
          .thenCompose(
              res -> {
                if (res.statusCode() != 200) {
                  return CompletableFuture.failedFuture(
                      new RuntimeException("Init failed: " + res.statusCode() + " " + res.body()));
                }
                try {
                  JsonRpc.Notification notif =
                      new JsonRpc.Notification("notifications/initialized", Map.of());
                  String notifBody = objectMapper.writeValueAsString(notif);
                  HttpRequest.Builder nReq =
                      HttpRequest.newBuilder()
                          .uri(URI.create(baseUrl))
                          .header("Content-Type", "application/json")
                          .header("MCP-Protocol-Version", protocolVersion)
                          .POST(HttpRequest.BodyPublishers.ofString(notifBody));
                  if (authHeader != null) nReq.header("Authorization", authHeader);

                  return httpClient
                      .sendAsync(nReq.build(), HttpResponse.BodyHandlers.ofString())
                      .thenAccept(
                          nRes -> {
                            initialized = true;
                          });
                } catch (Exception e) {
                  return CompletableFuture.failedFuture(e);
                }
              });
    } catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @Override
  public CompletableFuture<Map<String, ToolDefinition>> listTools() {
    return loadToolset("");
  }

  @Override
  public CompletableFuture<Map<String, ToolDefinition>> loadToolset(String toolsetName) {
    return CompletableFuture.supplyAsync(this::getAuthorizationHeader)
        .thenCompose(
            authHeader ->
                ensureInitialized(authHeader)
                    .thenCompose(
                        v -> {
                          String path =
                              toolsetName != null && !toolsetName.isEmpty()
                                  ? "/" + toolsetName
                                  : "";
                          String url = baseUrl + path;
                          try {
                            JsonRpc.Request listReq = new JsonRpc.Request("tools/list", Map.of());
                            String body = objectMapper.writeValueAsString(listReq);
                            HttpRequest.Builder req =
                                HttpRequest.newBuilder()
                                    .uri(URI.create(url))
                                    .header("Content-Type", "application/json")
                                    .header("MCP-Protocol-Version", protocolVersion)
                                    .POST(HttpRequest.BodyPublishers.ofString(body));
                            if (authHeader != null) req.header("Authorization", authHeader);

                            return httpClient
                                .sendAsync(req.build(), HttpResponse.BodyHandlers.ofString())
                                .thenApply(this::handleListToolsResponse);
                          } catch (Exception e) {
                            return CompletableFuture.failedFuture(e);
                          }
                        }));
  }

  @Override
  public CompletableFuture<Map<String, Tool>> loadToolset(
      String toolsetName,
      Map<String, Map<String, Object>> paramBinds,
      Map<String, Map<String, AuthTokenGetter>> authBinds,
      boolean strict) {

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
  public CompletableFuture<ToolResult> invokeTool(String toolName, Map<String, Object> arguments) {
    return invokeTool(toolName, arguments, Collections.emptyMap());
  }

  @Override
  public CompletableFuture<ToolResult> invokeTool(
      String toolName, Map<String, Object> arguments, Map<String, String> extraHeaders) {
    return CompletableFuture.supplyAsync(this::getAuthorizationHeader)
        .thenCompose(
            adcHeader -> {
              try {
                // Determine priority Auth header before init so init requests can use it if
                // needed
                String finalAuthHeader = null;
                if (extraHeaders.containsKey("Authorization")) {
                  finalAuthHeader = extraHeaders.get("Authorization");
                } else if (adcHeader != null) {
                  finalAuthHeader = adcHeader;
                }

                final String reqAuth = finalAuthHeader;

                return ensureInitialized(reqAuth)
                    .thenCompose(
                        v -> {
                          try {
                            JsonRpc.Request invokeReq =
                                new JsonRpc.Request(
                                    "tools/call", new JsonRpc.CallToolParams(toolName, arguments));
                            String requestBody = objectMapper.writeValueAsString(invokeReq);

                            HttpRequest.Builder requestBuilder =
                                HttpRequest.newBuilder()
                                    .uri(URI.create(baseUrl))
                                    .header("Content-Type", "application/json")
                                    .header("MCP-Protocol-Version", protocolVersion)
                                    .POST(HttpRequest.BodyPublishers.ofString(requestBody));

                            if (reqAuth != null) {
                              requestBuilder.header("Authorization", reqAuth);
                            }
                            extraHeaders.forEach(requestBuilder::header);

                            return httpClient
                                .sendAsync(
                                    requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                                .thenApply(response -> handleInvokeResponse(response, toolName));
                          } catch (Exception e) {
                            return CompletableFuture.failedFuture(e);
                          }
                        });

              } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
              }
            });
  }

  private String getAuthorizationHeader() {
    if (this.apiKey != null && !this.apiKey.isEmpty()) {
      return this.apiKey.startsWith("Bearer ") ? this.apiKey : "Bearer " + this.apiKey;
    }
    try {
      GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
      credentials.refreshIfExpired();
      if (credentials instanceof IdTokenProvider) {
        return "Bearer "
            + ((IdTokenProvider) credentials)
                .idTokenWithAudience(this.baseUrl, java.util.List.of())
                .getTokenValue();
      }
    } catch (Exception e) {
      // ADC not available or not OIDC-compatible. Proceed without global auth.
    }
    return null;
  }

  private Map<String, ToolDefinition> handleListToolsResponse(HttpResponse<String> response) {
    if (response.statusCode() != 200)
      throw new RuntimeException(
          "Failed to list tools. Status: " + response.statusCode() + " " + response.body());
    try {
      JsonNode root = objectMapper.readTree(response.body());
      if (root.has("error")) {
        throw new RuntimeException("MCP Error: " + root.get("error").toString());
      }
      JsonNode result = root.get("result");
      JsonNode toolsNode = result.get("tools");

      Map<String, ToolDefinition> toolsMap = new HashMap<>();
      if (toolsNode != null && toolsNode.isArray()) {
        for (JsonNode toolNode : toolsNode) {
          String name = toolNode.get("name").asText();
          String description =
              toolNode.has("description") ? toolNode.get("description").asText() : "";

          List<ToolDefinition.Parameter> params = new ArrayList<>();
          JsonNode inputSchema = toolNode.get("inputSchema");
          JsonNode requiredNode = inputSchema != null ? inputSchema.get("required") : null;
          Set<String> requiredSet = new HashSet<>();
          if (requiredNode != null && requiredNode.isArray()) {
            for (JsonNode req : requiredNode) {
              requiredSet.add(req.asText());
            }
          }

          JsonNode propertiesNode = inputSchema != null ? inputSchema.get("properties") : null;
          if (propertiesNode != null && propertiesNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = propertiesNode.fields();
            while (fields.hasNext()) {
              Map.Entry<String, JsonNode> entry = fields.next();
              String paramName = entry.getKey();
              JsonNode propNode = entry.getValue();

              String paramType = propNode.has("type") ? propNode.get("type").asText() : "string";
              String paramDesc =
                  propNode.has("description") ? propNode.get("description").asText() : "";

              List<String> authSources = new ArrayList<>();
              // Extract from _meta if exists
              JsonNode metaNode = toolNode.get("_meta");
              if (metaNode != null && metaNode.has("toolbox/authParam")) {
                JsonNode paramAuthNode = metaNode.get("toolbox/authParam").get(paramName);
                if (paramAuthNode != null && paramAuthNode.isArray()) {
                  for (JsonNode src : paramAuthNode) {
                    authSources.add(src.asText());
                  }
                }
              }

              params.add(
                  new ToolDefinition.Parameter(
                      paramName,
                      paramType,
                      requiredSet.contains(paramName),
                      paramDesc,
                      authSources));
            }
          }

          toolsMap.put(name, new ToolDefinition(description, params));
        }
      }
      return toolsMap;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private ToolResult handleInvokeResponse(HttpResponse<String> response, String toolName) {
    String body = response.body();
    if (response.statusCode() != 200) {
      return new ToolResult(
          java.util.List.of(
              new ToolResult.Content("text", "Error " + response.statusCode() + ": " + body)),
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
