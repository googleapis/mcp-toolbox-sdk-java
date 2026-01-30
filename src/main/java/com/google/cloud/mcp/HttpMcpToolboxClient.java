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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenProvider;

/**
 * Default implementation using Java 11 HttpClient.
 */
public class HttpMcpToolboxClient implements McpToolboxClient {

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HttpMcpToolboxClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public CompletableFuture<Map<String, ToolDefinition>> listTools() {
        return CompletableFuture.supplyAsync(this::getAuthorizationHeader)
            .thenCompose(authHeader -> sendGetRequest("/api/toolset", authHeader));
    }

    @Override
    public CompletableFuture<Map<String, ToolDefinition>> loadToolset(String toolsetName) {
        return CompletableFuture.supplyAsync(this::getAuthorizationHeader)
            .thenCompose(authHeader -> sendGetRequest("/api/toolset/" + toolsetName, authHeader));
    }

    @Override
    public CompletableFuture<Map<String, Tool>> loadToolset(
            String toolsetName,
            Map<String, Map<String, Object>> paramBinds,
            Map<String, Map<String, AuthTokenGetter>> authBinds,
            boolean strict) {

        // 1. Determine which fetch method to use
        CompletableFuture<Map<String, ToolDefinition>> definitionsFuture = (toolsetName == null
                || toolsetName.isEmpty())
                        ? listTools()
                        : loadToolset(toolsetName);

        return definitionsFuture.thenApply(defs -> {
            // 2. Strict Mode Validation
            if (strict) {
                Set<String> unknownTools = new HashSet<>();
                if (paramBinds != null)
                    unknownTools.addAll(paramBinds.keySet());
                if (authBinds != null)
                    unknownTools.addAll(authBinds.keySet());

                // Remove all valid tools from the set of keys we are trying to bind to
                unknownTools.removeAll(defs.keySet());

                if (!unknownTools.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Strict mode error: Bindings provided for unknown tools: " + unknownTools);
                }
            }

            // 3. Build Tool Objects & Apply Bindings
            Map<String, Tool> tools = new HashMap<>();
            for (Map.Entry<String, ToolDefinition> entry : defs.entrySet()) {
                String toolName = entry.getKey();
                Tool tool = new Tool(toolName, entry.getValue(), this);

                // Apply Parameter Bindings
                if (paramBinds != null && paramBinds.containsKey(toolName)) {
                    paramBinds.get(toolName).forEach(tool::bindParam);
                }

                // Apply Auth Bindings
                if (authBinds != null && authBinds.containsKey(toolName)) {
                    authBinds.get(toolName).forEach(tool::addAuthTokenGetter);
                }

                tools.put(toolName, tool);
            }
            return tools;
        });
    }

    private CompletableFuture<Map<String, ToolDefinition>> sendGetRequest(String path, String authHeader) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .GET();
        if (authHeader != null) requestBuilder.header("Authorization", authHeader);
        
        return httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(this::handleListToolsResponse);
    }

    @Override
    public CompletableFuture<Tool> loadTool(String toolName) {
        return loadTool(toolName, Collections.emptyMap());
    }

    @Override
    public CompletableFuture<Tool> loadTool(String toolName, Map<String, AuthTokenGetter> authTokenGetters) {
        return CompletableFuture.supplyAsync(this::getAuthorizationHeader)
            .thenCompose(authHeader -> {
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/tool/" + toolName))
                        .GET();
                if (authHeader != null) requestBuilder.header("Authorization", authHeader);

                return httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                        .thenApply(res -> {
                            ToolDefinition def = handleLoadToolResponse(res, toolName);
                            Tool tool = new Tool(toolName, def, this);
                            authTokenGetters.forEach(tool::addAuthTokenGetter);
                            return tool;
                        });
            });
    }

    @Override
    public CompletableFuture<ToolResult> invokeTool(String toolName, Map<String, Object> arguments) {
        return invokeTool(toolName, arguments, Collections.emptyMap());
    }

    @Override
    public CompletableFuture<ToolResult> invokeTool(String toolName, Map<String, Object> arguments, Map<String, String> extraHeaders) {
        return CompletableFuture.supplyAsync(this::getAuthorizationHeader)
            .thenCompose(adcHeader -> {
                try {
                    String requestBody = objectMapper.writeValueAsString(arguments);
                    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl + "/api/tool/" + toolName + "/invoke")) 
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(requestBody));

                    // Priority Logic: If tool provides 'Authorization', use it. Else use ADC.
                    if (extraHeaders.containsKey("Authorization")) {
                        // Tool specific auth wins
                    } else if (adcHeader != null) {
                        requestBuilder.header("Authorization", adcHeader);
                    }
                    extraHeaders.forEach(requestBuilder::header);

                    return httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                            .thenApply(response -> handleInvokeResponse(response, toolName));

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
                // If we can get a token for the base URL, we use it for global auth
                return "Bearer " + ((IdTokenProvider) credentials).idTokenWithAudience(this.baseUrl, java.util.List.of()).getTokenValue();
            }
        } catch (Exception e) {
            // ADC not available or not OIDC-compatible. Proceed without global auth.
        }
        return null;
    }

    private Map<String, ToolDefinition> handleListToolsResponse(HttpResponse<String> response) {
        if (response.statusCode() != 200) throw new RuntimeException("Failed to list tools. Status: " + response.statusCode());
        try {
            JsonNode root = objectMapper.readTree(response.body());
            return objectMapper.convertValue(root.get("tools"), new TypeReference<Map<String, ToolDefinition>>() {});
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private ToolDefinition handleLoadToolResponse(HttpResponse<String> response, String toolName) {
        Map<String, ToolDefinition> tools = handleListToolsResponse(response);
        if (tools.containsKey(toolName)) return tools.get(toolName);
        throw new RuntimeException("Tool not found: " + toolName);
    }

    private ToolResult handleInvokeResponse(HttpResponse<String> response, String toolName) {
        String body = response.body();
        if (response.statusCode() != 200) {
             return new ToolResult(java.util.List.of(new ToolResult.Content("text", "Error " + response.statusCode() + ": " + body)), true);
        }
        try {
            ToolResult result = objectMapper.readValue(body, ToolResult.class);
            // Robust check: if content is null (schema mismatch), wrap body as text
            if (result.content() == null) {
                return new ToolResult(java.util.List.of(new ToolResult.Content("text", body)), result.isError());
            }
            return result;
        } catch (Exception e) {
            // Parsing failed, return raw body
            return new ToolResult(java.util.List.of(new ToolResult.Content("text", body)), false);
        }
    }
}
