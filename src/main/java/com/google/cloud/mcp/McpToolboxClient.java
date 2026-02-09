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

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** The core client for interacting with an MCP Toolbox Server. */
public interface McpToolboxClient {

  /**
   * Connects to the MCP Server and retrieves the list of all available tools.
   *
   * @return A CompletableFuture containing the map of Tool definitions (Key: Tool Name).
   */
  CompletableFuture<Map<String, ToolDefinition>> listTools();

  /**
   * Loads the toolset (all available tools) from the MCP Server. Alias for {@link #listTools()}.
   */
  default CompletableFuture<Map<String, ToolDefinition>> loadToolset() {
    return listTools();
  }

  /** Loads a specific toolset by name (if supported by server). */
  CompletableFuture<Map<String, ToolDefinition>> loadToolset(String toolsetName);

  /**
   * Loads a toolset (or all tools if toolsetName is null) and applies bindings. Returns a map of
   * configured Tool objects rather than just definitions.
   *
   * @param toolsetName The name of the toolset to load (or null for all).
   * @param paramBinds A map of Tool Name -> (Parameter Name -> Value) to pre-bind.
   * @param authBinds A map of Tool Name -> (Service Name -> Token Getter) to pre-bind.
   * @param strict If true, throws an exception if bindings refer to tools that do not exist in the
   *     fetched toolset.
   * @return A CompletableFuture containing a Map of ready-to-use Tool objects.
   */
  CompletableFuture<Map<String, Tool>> loadToolset(
      String toolsetName,
      Map<String, Map<String, Object>> paramBinds,
      Map<String, Map<String, AuthTokenGetter>> authBinds,
      boolean strict);

  /** Loads a specific tool definition and returns a smart Tool object. */
  CompletableFuture<Tool> loadTool(String toolName);

  /**
   * Loads a specific tool and registers authentication getters immediately.
   *
   * @param toolName The name of the tool.
   * @param authTokenGetters A map of Service Name -> Token Getter Function.
   */
  CompletableFuture<Tool> loadTool(String toolName, Map<String, AuthTokenGetter> authTokenGetters);

  /** Low-level invocation method. */
  CompletableFuture<ToolResult> invokeTool(String toolName, Map<String, Object> arguments);

  /** Low-level invocation method with explicit headers. */
  CompletableFuture<ToolResult> invokeTool(
      String toolName, Map<String, Object> arguments, Map<String, String> extraHeaders);

  /** Builder pattern for creating client instances. */
  static Builder builder() {
    return new McpToolboxClientBuilder();
  }

  interface Builder {
    Builder baseUrl(String baseUrl);

    Builder apiKey(String apiKey);

    McpToolboxClient build();
  }
}
