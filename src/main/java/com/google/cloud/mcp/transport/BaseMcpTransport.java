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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.mcp.JsonRpc;
import com.google.cloud.mcp.ProtocolVersion;
import com.google.cloud.mcp.auth.CredentialsProvider;
import com.google.cloud.mcp.tool.ToolDefinition;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public abstract class BaseMcpTransport implements Transport {

  protected static final Logger logger = Logger.getLogger(BaseMcpTransport.class.getName());
  protected static final String HTTP_WARNING =
      "This connection is using HTTP. To prevent credential exposure, please ensure all"
          + " communication is sent over HTTPS.";

  protected final String baseUrl;
  protected final Map<String, String> clientHeaders;
  protected final CredentialsProvider credentialsProvider;
  protected final HttpClient httpClient;
  protected final ObjectMapper objectMapper;
  protected final ProtocolVersion preferredProtocolVersion;
  protected final Object initLock = new Object();
  protected CompletableFuture<Void> initFuture;

  protected BaseMcpTransport(
      final String baseUrl,
      final Map<String, String> clientHeaders,
      final CredentialsProvider credentialsProvider,
      final ProtocolVersion preferredProtocolVersion,
      final HttpClient httpClient,
      final java.util.concurrent.Executor executor) {
    if (baseUrl == null || baseUrl.isEmpty()) {
      throw new IllegalArgumentException("Base URL must be provided");
    }
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.clientHeaders =
        clientHeaders != null
            ? java.util.Collections.unmodifiableMap(new java.util.HashMap<>(clientHeaders))
            : java.util.Collections.emptyMap();
    this.credentialsProvider = credentialsProvider;
    this.preferredProtocolVersion =
        preferredProtocolVersion != null
            ? preferredProtocolVersion
            : ProtocolVersion.VERSION_2025_11_25;
    if (httpClient != null) {
      this.httpClient = httpClient;
    } else {
      HttpClient.Builder builder =
          HttpClient.newBuilder()
              .cookieHandler(new java.net.CookieManager())
              .connectTimeout(Duration.ofSeconds(10));
      if (executor != null) {
        builder.executor(executor);
      }
      this.httpClient = builder.build();
    }
    this.objectMapper = new ObjectMapper();
  }

  @Override
  public final String getBaseUrl() {
    return this.baseUrl;
  }

  final CompletableFuture<Map<String, String>> mergeHeaders(
      final Map<String, String> extraMetadata) {
    CompletableFuture<String> authFuture =
        this.credentialsProvider != null
            ? this.credentialsProvider.getAuthorizationHeader()
            : CompletableFuture.completedFuture(null);

    return authFuture.thenApply(
        providerAuth -> {
          Map<String, String> merged = new HashMap<>();

          // 1. Find dynamic or static Authorization header
          String finalAuthHeader = null;

          // A. Check extraMetadata first
          if (extraMetadata != null) {
            String authKeyInExtra =
                extraMetadata.keySet().stream()
                    .filter(k -> "Authorization".equalsIgnoreCase(k))
                    .findFirst()
                    .orElse(null);
            if (authKeyInExtra != null) {
              finalAuthHeader = extraMetadata.get(authKeyInExtra);
            }
          }

          // B. If not in extraMetadata, check credentialsProvider
          if (finalAuthHeader == null) {
            finalAuthHeader = providerAuth;
          }

          // C. If still null, check clientHeaders
          if (finalAuthHeader == null) {
            for (Map.Entry<String, String> entry : this.clientHeaders.entrySet()) {
              if ("Authorization".equalsIgnoreCase(entry.getKey())) {
                finalAuthHeader = entry.getValue();
                break;
              }
            }
          }

          // 2. Put all client-level headers except Authorization
          this.clientHeaders.forEach(
              (k, v) -> {
                if (!"Authorization".equalsIgnoreCase(k)) {
                  merged.put(k, v);
                }
              });

          // 3. Put all extra/call-level metadata except Authorization
          if (extraMetadata != null) {
            extraMetadata.forEach(
                (k, v) -> {
                  if (!"Authorization".equalsIgnoreCase(k)) {
                    merged.put(k, v);
                  }
                });
          }

          // 4. Put the final Authorization header if found
          if (finalAuthHeader != null) {
            merged.put("Authorization", finalAuthHeader);
          }

          return merged;
        });
  }

  final CompletableFuture<Void> ensureInitialized(final Map<String, String> extraMetadata) {
    synchronized (initLock) {
      if (initFuture == null) {
        Map<String, String> handshakeMetadata = new HashMap<>();
        if (extraMetadata != null) {
          String authKey =
              extraMetadata.keySet().stream()
                  .filter(k -> "Authorization".equalsIgnoreCase(k))
                  .findFirst()
                  .orElse(null);
          if (authKey != null) {
            handshakeMetadata.put("Authorization", extraMetadata.get(authKey));
          }
        }
        initFuture =
            mergeHeaders(handshakeMetadata)
                .thenCompose(
                    handshakeHeaders -> {
                      String authHeader = handshakeHeaders.get("Authorization");
                      return performInitialization(authHeader, handshakeHeaders);
                    });
      }
      return initFuture;
    }
  }

  protected abstract CompletableFuture<Void> performInitialization(
      final String authHeader, final Map<String, String> handshakeHeaders);

  protected abstract void applyProtocolHeaders(final HttpRequest.Builder builder);

  protected void applyProtocolHeaders(
      final HttpRequest.Builder builder, final String method, final String name) {
    applyProtocolHeaders(builder);
  }

  protected Object modifyRequestParams(final String method, final Object params) {
    return params;
  }

  @Override
  public final CompletableFuture<TransportManifest> listTools(
      final String toolsetName, final Map<String, String> metadata) {
    if (this.baseUrl.toLowerCase(java.util.Locale.ROOT).startsWith("http://")
        && !metadata.isEmpty()) {
      logger.warning(HTTP_WARNING);
    }
    return ensureInitialized(metadata)
        .thenCompose(v -> mergeHeaders(metadata))
        .thenCompose(
            mergedHeaders -> {
              String path = toolsetName != null && !toolsetName.isEmpty() ? "/" + toolsetName : "";
              String url = baseUrl + path;
              try {
                Object finalParams = modifyRequestParams("tools/list", Map.of());
                JsonRpc.Request listReq = new JsonRpc.Request("tools/list", finalParams);
                String body = objectMapper.writeValueAsString(listReq);
                HttpRequest.Builder req =
                    HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .POST(HttpRequest.BodyPublishers.ofString(body));
                mergedHeaders.forEach(req::setHeader);
                applyProtocolHeaders(req, "tools/list", null);

                return httpClient
                    .sendAsync(req.build(), HttpResponse.BodyHandlers.ofString())
                    .thenApply(this::handleListToolsResponse);
              } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
              }
            });
  }

  @Override
  public final CompletableFuture<TransportResponse> invokeTool(
      final String toolName,
      final Map<String, Object> arguments,
      final Map<String, String> metadata) {
    if (this.baseUrl.toLowerCase(java.util.Locale.ROOT).startsWith("http://")
        && !metadata.isEmpty()) {
      logger.warning(HTTP_WARNING);
    }
    return ensureInitialized(metadata)
        .thenCompose(v -> mergeHeaders(metadata))
        .thenCompose(
            mergedHeaders -> {
              try {
                Object finalParams =
                    modifyRequestParams(
                        "tools/call", new JsonRpc.CallToolParams(toolName, arguments));
                JsonRpc.Request invokeReq = new JsonRpc.Request("tools/call", finalParams);
                String requestBody = objectMapper.writeValueAsString(invokeReq);

                HttpRequest.Builder requestBuilder =
                    HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl))
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody));

                mergedHeaders.forEach(requestBuilder::setHeader);
                applyProtocolHeaders(requestBuilder, "tools/call", toolName);

                return httpClient
                    .sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                    .thenApply(
                        res -> {
                          checkResponseForNegotiationError(res);
                          return new TransportResponse(res.statusCode(), res.body());
                        });
              } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
              }
            });
  }

  @Override
  public void close() {
    // No-op for HttpClient in Java 11
  }

  private TransportManifest handleListToolsResponse(final HttpResponse<String> response) {
    checkResponseForNegotiationError(response);
    if (response.statusCode() != 200) {
      throw new RuntimeException(
          "Failed to list tools. Status: " + response.statusCode() + " " + response.body());
    }
    try {
      JsonNode root = objectMapper.readTree(response.body());
      if (root.has("error")) {
        throw new com.google.cloud.mcp.exception.McpException(
            "MCP Error: " + root.get("error").toString());
      }
      JsonNode result = root.get("result");
      JsonNode toolsNode = result.get("tools");

      Map<String, ToolDefinition> toolsMap = new HashMap<>();
      if (toolsNode != null && toolsNode.isArray()) {
        for (JsonNode toolNode : toolsNode) {
          String name = toolNode.get("name").asText();
          String description =
              toolNode.has("description") ? toolNode.get("description").asText() : "";

          List<String> authRequired = new ArrayList<>();
          JsonNode metaNode = toolNode.get("_meta");
          if (metaNode != null && metaNode.has("toolbox/authInvoke")) {
            JsonNode invokeAuthNode = metaNode.get("toolbox/authInvoke");
            if (invokeAuthNode != null && invokeAuthNode.isArray()) {
              for (JsonNode src : invokeAuthNode) {
                authRequired.add(src.asText());
              }
            }
          }

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
              if (metaNode != null && metaNode.has("toolbox/authParam")) {
                JsonNode paramAuthNode = metaNode.get("toolbox/authParam").get(paramName);
                if (paramAuthNode != null && paramAuthNode.isArray()) {
                  for (JsonNode src : paramAuthNode) {
                    authSources.add(src.asText());
                  }
                }
              }

              Object defaultValue = null;
              if (propNode.has("default")) {
                JsonNode defNode = propNode.get("default");
                defaultValue = objectMapper.treeToValue(defNode, Object.class);
              }

              params.add(
                  new ToolDefinition.Parameter(
                      paramName,
                      paramType,
                      requiredSet.contains(paramName),
                      paramDesc,
                      authSources,
                      defaultValue));
            }
          }

          Boolean readOnlyHint =
              toolNode.has("readOnlyHint") ? toolNode.get("readOnlyHint").asBoolean() : null;
          Boolean destructiveHint =
              toolNode.has("destructiveHint") ? toolNode.get("destructiveHint").asBoolean() : null;

          toolsMap.put(
              name,
              new ToolDefinition(description, params, authRequired, readOnlyHint, destructiveHint));
        }
      }
      return new TransportManifest(toolsMap);
    } catch (com.google.cloud.mcp.exception.McpException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void checkResponseForNegotiationError(final HttpResponse<String> response) {
    if (response == null || response.body() == null || response.body().isEmpty()) {
      return;
    }
    try {
      JsonNode root = objectMapper.readTree(response.body());
      if (root.has("error")) {
        JsonNode errorNode = root.get("error");
        if (errorNode.has("code")) {
          int code = errorNode.get("code").asInt();
          if (code == -32004 || code == -32001) {
            JsonNode dataNode = errorNode.get("data");
            if (dataNode != null && dataNode.has("supported")) {
              JsonNode supportedNode = dataNode.get("supported");
              if (supportedNode.isArray()) {
                java.util.List<String> serverSupported = new java.util.ArrayList<>();
                for (JsonNode versionNode : supportedNode) {
                  serverSupported.add(versionNode.asText());
                }

                String negotiated = findHighestCommonVersion(serverSupported);
                if (negotiated != null) {
                  throw new com.google.cloud.mcp.exception.McpProtocolNegotiationException(
                      "Protocol version fallback requested by server to: " + negotiated,
                      negotiated);
                } else {
                  throw new com.google.cloud.mcp.exception.McpException(
                      "No mutually supported protocol version. Client supports: [2026-06-18,"
                          + " 2025-11-25, 2025-06-18, 2025-03-26, 2024-11-05], Server supports: "
                          + serverSupported);
                }
              }
            }
          }
        }
      }
    } catch (com.google.cloud.mcp.exception.McpProtocolNegotiationException e) {
      throw e;
    } catch (com.google.cloud.mcp.exception.McpException e) {
      throw e;
    } catch (Exception e) {
      // Ignore JSON parsing exceptions here, they will be caught by caller
    }
  }

  private String findHighestCommonVersion(java.util.List<String> serverSupported) {
    java.util.List<String> clientPreference =
        java.util.List.of(
            ProtocolVersion.VERSION_2026_06_18.getValue(),
            ProtocolVersion.VERSION_2025_11_25.getValue(),
            ProtocolVersion.VERSION_2025_06_18.getValue(),
            ProtocolVersion.VERSION_2025_03_26.getValue(),
            ProtocolVersion.VERSION_2024_11_05.getValue());
    for (String preferred : clientPreference) {
      if (serverSupported.contains(preferred)) {
        return preferred;
      }
    }
    return null;
  }
}
