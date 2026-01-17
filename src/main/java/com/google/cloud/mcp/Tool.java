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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Represents a loaded tool ready to be invoked.
 * Handles parameter binding and authentication token resolution.
 */
public class Tool {
    private final String name;
    private final ToolDefinition definition;
    private final McpToolboxClient client;
    
    private final Map<String, Object> boundParameters = new HashMap<>();
    private final Map<String, AuthTokenGetter> authGetters = new HashMap<>();

    public Tool(String name, ToolDefinition definition, McpToolboxClient client) {
        this.name = name;
        this.definition = definition;
        this.client = client;
    }

    public String name() { return name; }
    public ToolDefinition definition() { return definition; }

    public Tool bindParam(String key, Object value) {
        this.boundParameters.put(key, value);
        return this;
    }

    public Tool bindParam(String key, Supplier<Object> valueSupplier) {
        this.boundParameters.put(key, valueSupplier);
        return this;
    }

    public Tool addAuthTokenGetter(String serviceName, AuthTokenGetter getter) {
        this.authGetters.put(serviceName, getter);
        return this;
    }

    public CompletableFuture<ToolResult> execute(Map<String, Object> args) {
        Map<String, Object> finalArgs = new HashMap<>(args);
        Map<String, String> extraHeaders = new HashMap<>();

        // 1. Apply Bound Parameters
        for (Map.Entry<String, Object> entry : boundParameters.entrySet()) {
            Object val = entry.getValue();
            if (val instanceof Supplier) {
                finalArgs.put(entry.getKey(), ((Supplier<?>) val).get());
            } else {
                finalArgs.put(entry.getKey(), val);
            }
        }

        // 2. Resolve Auth Tokens
        return CompletableFuture.allOf(
            authGetters.entrySet().stream()
                .map(entry -> {
                    String serviceName = entry.getKey();
                    return entry.getValue().getToken().thenAccept(token -> {
                        // A. Check if mapped to a Parameter (Authenticated Parameters)
                        String paramName = findParameterForService(serviceName);
                        if (paramName != null) {
                            finalArgs.put(paramName, token); 
                        } 
                        
                        // B. Always add to Headers to support Authorized Invocation
                        // 1. Standard OIDC Header (Cloud Run)
                        extraHeaders.put("Authorization", "Bearer " + token);
                        
                        // 2. SDK Convention Header (Framework Compatibility)
                        extraHeaders.put(serviceName + "_token", token);
                    });
                })
                .toArray(CompletableFuture[]::new)
        ).thenCompose(v -> client.invokeTool(name, finalArgs, extraHeaders));
    }

    private String findParameterForService(String serviceName) {
        if (definition.parameters() == null) return null;
        for (ToolDefinition.Parameter param : definition.parameters()) {
            if (param.authSources() != null && param.authSources().contains(serviceName)) {
                return param.name();
            }
        }
        return null;
    }
}
