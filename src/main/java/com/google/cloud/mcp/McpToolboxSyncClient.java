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

import com.google.cloud.mcp.auth.AuthTokenGetter;
import com.google.cloud.mcp.exception.McpToolboxException;
import com.google.cloud.mcp.tool.Tool;
import com.google.cloud.mcp.tool.ToolDefinition;
import com.google.cloud.mcp.tool.ToolResult;
import java.util.Map;

/** The synchronous blocking client for interacting with MCP Toolbox. */
public interface McpToolboxSyncClient {

  /**
   * Connects to the MCP Server and retrieves the list of all available tools.
   *
   * @return The map of Tool definitions (Key: Tool Name).
   * @throws McpToolboxException if any error occurs.
   */
  Map<String, ToolDefinition> listTools();

  /**
   * Loads the toolset from the MCP Server. Alias for {@link #listTools()}.
   *
   * @return The map of Tool definitions (Key: Tool Name).
   * @throws McpToolboxException if any error occurs.
   */
  default Map<String, ToolDefinition> loadToolset() {
    return listTools();
  }

  /**
   * Loads a specific toolset by name (if supported by server).
   *
   * @param toolsetName The name of the toolset to load.
   * @return The map of Tool definitions (Key: Tool Name).
   * @throws McpToolboxException if any error occurs.
   */
  Map<String, ToolDefinition> loadToolset(String toolsetName);

  /**
   * Loads a toolset (or all tools if toolsetName is null) and applies bindings.
   *
   * @param toolsetName Name of the toolset to load (or null for all).
   * @param paramBinds Map of Tool Name -> (Parameter Name -> Value).
   * @param authBinds Map of Tool Name -> (Service -> Token Getter).
   * @param strict Throws exception if bindings refer to non-existent tools.
   * @return A Map of ready-to-use Tool objects.
   * @throws McpToolboxException if any error occurs.
   */
  Map<String, Tool> loadToolset(
      String toolsetName,
      Map<String, Map<String, Object>> paramBinds,
      Map<String, Map<String, AuthTokenGetter>> authBinds,
      boolean strict);

  /**
   * Loads a specific tool definition and returns a smart Tool object.
   *
   * @param toolName The name of the tool to load.
   * @return The Tool object.
   * @throws McpToolboxException if any error occurs.
   */
  Tool loadTool(String toolName);

  /**
   * Loads a specific tool and registers authentication getters immediately.
   *
   * @param toolName The name of the tool.
   * @param authTokenGetters A map of Service Name -> Token Getter Function.
   * @return The Tool object.
   * @throws McpToolboxException if any error occurs.
   */
  Tool loadTool(String toolName, Map<String, AuthTokenGetter> authTokenGetters);

  /**
   * Low-level invocation method.
   *
   * @param toolName The name of the tool to invoke.
   * @param arguments The arguments to pass to the tool.
   * @return The result of the tool invocation.
   * @throws McpToolboxException if any error occurs.
   */
  ToolResult invokeTool(String toolName, Map<String, Object> arguments);

  /**
   * Low-level invocation method with explicit headers.
   *
   * @param toolName The name of the tool to invoke.
   * @param arguments The arguments to pass to the tool.
   * @param extraHeaders Additional HTTP headers to include in the request.
   * @return The result of the tool invocation.
   * @throws McpToolboxException if any error occurs.
   */
  ToolResult invokeTool(
      String toolName, Map<String, Object> arguments, Map<String, String> extraHeaders);
}
