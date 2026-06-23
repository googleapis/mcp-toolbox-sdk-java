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

/** Default HTTP transport implementation using Java 11 HttpClient. */
public final class HttpMcpTransport implements Transport {

  private static final Logger logger = Logger.getLogger(HttpMcpTransport.class.getName());
  private static final String HTTP_WARNING =
      "This connection is using HTTP. To prevent credential exposure, please ensure all"
          + " communication is sent over HTTPS.";

  private final String baseUrl;
  private final Map<String, String> clientHeaders;
  private final CredentialsProvider credentialsProvider;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final String protocolVersion = "2025-11-25";
  private boolean initialized = false;

  /**
   * Constructs a new HttpMcpTransport with a base URL.
   *
   * @param baseUrl The base URL of the remote service.
   */
  public HttpMcpTransport(String baseUrl) {
    this(baseUrl, Map.of(), (CredentialsProvider) null);
  }

  /**
   * Constructs a new HttpMcpTransport with a base URL and client-level headers.
   *
   * @param baseUrl The base URL of the remote service.
   * @param clientHeaders The client-level headers.
   */
  public HttpMcpTransport(String baseUrl, Map<String, String> clientHeaders) {
    this(baseUrl, clientHeaders, (CredentialsProvider) null);
  }

  /**
   * Constructs a new HttpMcpTransport with a base URL, client-level headers, and credentials
   * provider.
   *
   * @param baseUrl The base URL of the remote service.
   * @param clientHeaders The client-level headers.
   * @param credentialsProvider The credentials provider.
   */
  public HttpMcpTransport(
      String baseUrl, Map<String, String> clientHeaders, CredentialsProvider credentialsProvider) {
    this(
        baseUrl,
        clientHeaders,
        credentialsProvider,
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
  }

  /** Package-private constructor for unit testing. */
  HttpMcpTransport(String baseUrl, HttpClient httpClient) {
    this(baseUrl, Map.of(), (CredentialsProvider) null, httpClient);
  }

  /** Package-private constructor for unit testing. */
  HttpMcpTransport(String baseUrl, Map<String, String> clientHeaders, HttpClient httpClient) {
    this(baseUrl, clientHeaders, (CredentialsProvider) null, httpClient);
  }

  /** Package-private constructor for unit testing. */
  HttpMcpTransport(
      String baseUrl,
      Map<String, String> clientHeaders,
      CredentialsProvider credentialsProvider,
      HttpClient httpClient) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.clientHeaders =
        clientHeaders != null
            ? java.util.Collections.unmodifiableMap(new java.util.HashMap<>(clientHeaders))
            : java.util.Collections.emptyMap();
    this.credentialsProvider = credentialsProvider;
    this.httpClient = httpClient;
    this.objectMapper = new ObjectMapper();
  }

  @Override
  public String getBaseUrl() {
    return this.baseUrl;
  }

  private CompletableFuture<Map<String, String>> mergeHeaders(Map<String, String> extraMetadata) {
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

  private synchronized CompletableFuture<Void> ensureInitialized(
      Map<String, String> extraMetadata) {
    if (initialized) return CompletableFuture.completedFuture(null);
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
    return mergeHeaders(handshakeMetadata)
        .thenCompose(
            handshakeHeaders -> {
              try {
                String authHeader = handshakeHeaders.get("Authorization");
                if (this.baseUrl.toLowerCase(java.util.Locale.ROOT).startsWith("http://")
                    && authHeader != null) {
                  logger.warning(HTTP_WARNING);
                }
                JsonRpc.Request initReq =
                    new JsonRpc.Request(
                        "initialize",
                        new JsonRpc.InitializeParams(protocolVersion, "mcp-toolbox-sdk-java"));
                String body = objectMapper.writeValueAsString(initReq);
                HttpRequest.Builder req =
                    HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body));
                handshakeHeaders.forEach(req::setHeader);

                return httpClient
                    .sendAsync(req.build(), HttpResponse.BodyHandlers.ofString())
                    .thenCompose(
                        res -> {
                          if (res.statusCode() != 200) {
                            return CompletableFuture.failedFuture(
                                new RuntimeException(
                                    "Init failed: " + res.statusCode() + " " + res.body()));
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
                            handshakeHeaders.forEach(nReq::setHeader);

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
            });
  }

  @Override
  public CompletableFuture<TransportManifest> listTools(
      String toolsetName, Map<String, String> metadata) {
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
                JsonRpc.Request listReq = new JsonRpc.Request("tools/list", Map.of());
                String body = objectMapper.writeValueAsString(listReq);
                HttpRequest.Builder req =
                    HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .header("MCP-Protocol-Version", protocolVersion)
                        .POST(HttpRequest.BodyPublishers.ofString(body));
                mergedHeaders.forEach(req::setHeader);

                return httpClient
                    .sendAsync(req.build(), HttpResponse.BodyHandlers.ofString())
                    .thenApply(this::handleListToolsResponse);
              } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
              }
            });
  }

  @Override
  public CompletableFuture<TransportResponse> invokeTool(
      String toolName, Map<String, Object> arguments, Map<String, String> metadata) {
    if (this.baseUrl.toLowerCase(java.util.Locale.ROOT).startsWith("http://")
        && !metadata.isEmpty()) {
      logger.warning(HTTP_WARNING);
    }
    return ensureInitialized(metadata)
        .thenCompose(v -> mergeHeaders(metadata))
        .thenCompose(
            mergedHeaders -> {
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

                mergedHeaders.forEach(requestBuilder::setHeader);

                return httpClient
                    .sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                    .thenApply(res -> new TransportResponse(res.statusCode(), res.body()));
              } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
              }
            });
  }

  @Override
  public void close() {
    // No-op for HttpClient in Java 11
  }

  private TransportManifest handleListToolsResponse(HttpResponse<String> response) {
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

              params.add(
                  new ToolDefinition.Parameter(
                      paramName,
                      paramType,
                      requiredSet.contains(paramName),
                      paramDesc,
                      authSources));
            }
          }

          toolsMap.put(name, new ToolDefinition(description, params, authRequired));
        }
      }
      return new TransportManifest(toolsMap);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
