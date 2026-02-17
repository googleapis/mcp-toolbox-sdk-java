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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Represents a loaded tool ready to be invoked. Handles parameter binding, authentication token
 * resolution, and input validation.
 */
public class Tool {
  private final String name;
  private final ToolDefinition definition;
  private final McpToolboxClient client;

  private final Map<String, Object> boundParameters = new HashMap<>();
  private final Map<String, AuthTokenGetter> authGetters = new HashMap<>();

  /**
   * Constructs a new Tool.
   *
   * @param name The name of the tool.
   * @param definition The definition of the tool.
   * @param client The client used to invoke the tool.
   */
  public Tool(String name, ToolDefinition definition, McpToolboxClient client) {
    this.name = name;
    this.definition = definition;
    this.client = client;
  }

  /**
   * Returns the name of the tool.
   *
   * @return The tool name.
   */
  public String name() {
    return name;
  }

  /**
   * Returns the definition of the tool.
   *
   * @return The tool definition.
   */
  public ToolDefinition definition() {
    return definition;
  }

  /**
   * Binds a static value to a parameter.
   *
   * @param key The parameter name.
   * @param value The value to bind.
   * @return The tool instance.
   */
  public Tool bindParam(String key, Object value) {
    this.boundParameters.put(key, value);
    return this;
  }

  /**
   * Binds a dynamic value supplier to a parameter.
   *
   * @param key The parameter name.
   * @param valueSupplier The supplier that provides the value at execution time.
   * @return The tool instance.
   */
  public Tool bindParam(String key, Supplier<Object> valueSupplier) {
    this.boundParameters.put(key, valueSupplier);
    return this;
  }

  /**
   * Registers an authentication token getter for a specific service.
   *
   * @param serviceName The name of the service.
   * @param getter The token getter.
   * @return The tool instance.
   */
  public Tool addAuthTokenGetter(String serviceName, AuthTokenGetter getter) {
    this.authGetters.put(serviceName, getter);
    return this;
  }

  /**
   * Executes the tool with the provided arguments, applying any bound parameters and resolving
   * authentication tokens.
   *
   * @param args The arguments for the tool invocation.
   * @return A CompletableFuture containing the result of the tool execution.
   */
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
                .map(
                    entry -> {
                      String serviceName = entry.getKey();
                      return entry
                          .getValue()
                          .getToken()
                          .thenAccept(
                              token -> {
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
                .toArray(CompletableFuture[]::new))
        .thenCompose(
            v -> {
              try {
                // 3. Validation & Cleanup
                validateAndSanitizeArgs(finalArgs);
                return client.invokeTool(name, finalArgs, extraHeaders);
              } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
              }
            });
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

  /** Validates arguments against the tool definition and removes null values. */
  private void validateAndSanitizeArgs(Map<String, Object> args) {
    // Remove nulls first (filtering none values)
    args.values().removeIf(Objects::isNull);

    if (definition.parameters() == null) return;

    for (ToolDefinition.Parameter param : definition.parameters()) {
      Object value = args.get(param.name());

      // A. Check Required Parameters
      if (param.required() && value == null) {
        throw new IllegalArgumentException(
            String.format(
                "Missing required parameter '%s' for tool '%s'.", param.name(), this.name));
      }

      // B. Check Parameter Types (only if value is present)
      if (value != null && param.type() != null) {
        if (!isTypeMatch(value, param.type())) {
          throw new IllegalArgumentException(
              String.format(
                  "Parameter '%s' expected type '%s' but got '%s'.",
                  param.name(), param.type(), value.getClass().getSimpleName()));
        }
      }
    }
  }

  private boolean isTypeMatch(Object value, String type) {
    switch (type.toLowerCase()) {
      case "string":
        return value instanceof String;
      case "integer":
        return value instanceof Integer || value instanceof Long;
      case "number":
        return value instanceof Number; // Covers Integer, Long, Float, Double
      case "boolean":
        return value instanceof Boolean;
      case "array":
        return value instanceof java.util.List || value.getClass().isArray();
      case "object":
        return value instanceof Map;
      default:
        return true;
    }
  }
}
