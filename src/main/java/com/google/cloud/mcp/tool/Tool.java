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

package com.google.cloud.mcp.tool;

import com.google.cloud.mcp.McpToolboxClient;
import com.google.cloud.mcp.auth.AuthResolver;
import com.google.cloud.mcp.auth.AuthTokenGetter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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

  private final Map<String, Object> boundParameters;
  private final Map<String, AuthTokenGetter> authGetters;
  private final List<ToolPreProcessor> preProcessors;
  private final List<ToolPostProcessor> postProcessors;

  /**
   * Constructs a new Tool.
   *
   * @param toolName The name of the tool.
   * @param toolDefinition The definition of the tool.
   * @param toolboxClient The client used to invoke the tool.
   */
  public Tool(
      final String toolName,
      final ToolDefinition toolDefinition,
      final McpToolboxClient toolboxClient) {
    this.name = toolName;
    this.definition = toolDefinition;
    this.client = toolboxClient;
    this.boundParameters = Collections.emptyMap();
    this.authGetters = Collections.emptyMap();
    this.preProcessors = Collections.emptyList();
    this.postProcessors = Collections.emptyList();
  }

  private Tool(
      final String toolName,
      final ToolDefinition toolDefinition,
      final McpToolboxClient toolboxClient,
      final Map<String, Object> initialBoundParameters,
      final Map<String, AuthTokenGetter> initialAuthGetters,
      final List<ToolPreProcessor> initialPreProcessors,
      final List<ToolPostProcessor> initialPostProcessors) {
    this.name = toolName;
    this.definition = toolDefinition;
    this.client = toolboxClient;
    this.boundParameters = Collections.unmodifiableMap(initialBoundParameters);
    this.authGetters = Collections.unmodifiableMap(initialAuthGetters);
    this.preProcessors = Collections.unmodifiableList(initialPreProcessors);
    this.postProcessors = Collections.unmodifiableList(initialPostProcessors);
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
   * @return A new tool instance with the parameter bound and pruned from definition.
   */
  public Tool bindParam(final String key, final Object value) {
    Map<String, Object> newBound = new HashMap<>(this.boundParameters);
    newBound.put(key, value);
    ToolDefinition newDef = pruneParameter(this.definition, key);
    return new Tool(
        this.name,
        newDef,
        this.client,
        newBound,
        this.authGetters,
        this.preProcessors,
        this.postProcessors);
  }

  /**
   * Binds a dynamic value supplier to a parameter.
   *
   * @param key The parameter name.
   * @param valueSupplier The supplier that provides the value at execution time.
   * @return A new tool instance with the parameter bound and pruned from definition.
   */
  public Tool bindParam(final String key, final Supplier<Object> valueSupplier) {
    Map<String, Object> newBound = new HashMap<>(this.boundParameters);
    newBound.put(key, valueSupplier);
    ToolDefinition newDef = pruneParameter(this.definition, key);
    return new Tool(
        this.name,
        newDef,
        this.client,
        newBound,
        this.authGetters,
        this.preProcessors,
        this.postProcessors);
  }

  /**
   * Registers an authentication token getter for a specific service.
   *
   * @param serviceName The name of the service.
   * @param getter The token getter.
   * @return A new tool instance with the token getter registered.
   */
  public Tool addAuthTokenGetter(final String serviceName, final AuthTokenGetter getter) {
    Map<String, AuthTokenGetter> newAuth = new HashMap<>(this.authGetters);
    newAuth.put(serviceName, getter);
    return new Tool(
        this.name,
        this.definition,
        this.client,
        this.boundParameters,
        newAuth,
        this.preProcessors,
        this.postProcessors);
  }

  private static ToolDefinition pruneParameter(
      final ToolDefinition original, final String paramName) {
    if (original.parameters() == null) {
      return original;
    }
    List<ToolDefinition.Parameter> newParams = new ArrayList<>();
    for (ToolDefinition.Parameter param : original.parameters()) {
      if (!param.name().equals(paramName)) {
        newParams.add(param);
      }
    }
    return new ToolDefinition(
        original.description(),
        newParams,
        original.authRequired(),
        original.readOnlyHint(),
        original.destructiveHint());
  }

  /**
   * Adds a pre-processor to the tool.
   *
   * @param processor The pre-processor to add.
   * @return The tool instance.
   */
  public Tool addPreProcessor(final ToolPreProcessor processor) {
    List<ToolPreProcessor> newPre = new ArrayList<>(this.preProcessors);
    newPre.add(processor);
    return new Tool(
        this.name,
        this.definition,
        this.client,
        this.boundParameters,
        this.authGetters,
        newPre,
        this.postProcessors);
  }

  /**
   * Adds a post-processor to the tool.
   *
   * @param processor The post-processor to add.
   * @return A new tool instance with the post-processor added.
   */
  public Tool addPostProcessor(final ToolPostProcessor processor) {
    List<ToolPostProcessor> newPost = new ArrayList<>(this.postProcessors);
    newPost.add(processor);
    return new Tool(
        this.name,
        this.definition,
        this.client,
        this.boundParameters,
        this.authGetters,
        this.preProcessors,
        newPost);
  }

  /**
   * Executes the tool with the provided arguments, applying any bound parameters and resolving
   * authentication tokens.
   *
   * @param args The arguments for the tool invocation.
   * @return A CompletableFuture containing the result of the tool execution.
   */
  public CompletableFuture<ToolResult> execute(final Map<String, Object> args) {
    CompletableFuture<Map<String, Object>> argsFuture =
        CompletableFuture.completedFuture(new HashMap<>(args));

    for (ToolPreProcessor preProcessor : preProcessors) {
      argsFuture = argsFuture.thenCompose(currentArgs -> preProcessor.process(name, currentArgs));
    }

    CompletableFuture<ToolResult> resultFuture =
        argsFuture.thenCompose(
            processedArgs -> {
              Map<String, Object> finalArgs =
                  java.util.Collections.synchronizedMap(new HashMap<>(processedArgs));
              Map<String, String> extraHeaders =
                  java.util.Collections.synchronizedMap(new HashMap<>());

              // 1. Apply Bound Parameters
              for (Map.Entry<String, Object> entry : boundParameters.entrySet()) {
                Object val = entry.getValue();
                if (val instanceof Supplier) {
                  finalArgs.put(entry.getKey(), ((Supplier<?>) val).get());
                } else {
                  finalArgs.put(entry.getKey(), val);
                }
              }

              // 2. Resolve Auth & Execute
              return AuthResolver.resolve(authGetters)
                  .thenCompose(
                      resolvedAuth -> {
                        try {
                          // Apply credential parameter bindings and extra
                          // headers
                          resolvedAuth.applyTo(finalArgs, extraHeaders, definition);

                          // Validation & Cleanup
                          validateAndSanitizeArgs(finalArgs);
                          return client.invokeTool(name, finalArgs, extraHeaders);
                        } catch (Exception e) {
                          return CompletableFuture.failedFuture(e);
                        }
                      });
            });

    for (ToolPostProcessor postProcessor : postProcessors) {
      resultFuture = resultFuture.thenCompose(res -> postProcessor.process(name, res));
    }

    return resultFuture;
  }

  /**
   * Validates arguments against the tool definition and removes null values.
   *
   * @param args The arguments to validate.
   */
  private void validateAndSanitizeArgs(final Map<String, Object> args) {
    // Remove nulls first (filtering none values)
    args.values().removeIf(Objects::isNull);

    if (definition.parameters() == null) {
      return;
    }

    for (ToolDefinition.Parameter param : definition.parameters()) {
      Object value = args.get(param.name());

      if (value == null && param.defaultValue() != null) {
        value = deepCopy(param.defaultValue());
        args.put(param.name(), value);
      }

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

  private Object deepCopy(final Object value) {
    if (value instanceof Map) {
      Map<?, ?> map = (Map<?, ?>) value;
      Map<Object, Object> copy = new HashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        copy.put(deepCopy(entry.getKey()), deepCopy(entry.getValue()));
      }
      return copy;
    } else if (value instanceof List) {
      List<?> list = (List<?>) value;
      List<Object> copy = new ArrayList<>();
      for (Object item : list) {
        copy.add(deepCopy(item));
      }
      return copy;
    }
    return value;
  }

  private boolean isTypeMatch(final Object value, final String type) {
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
