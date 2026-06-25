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
import com.google.cloud.mcp.auth.CredentialsProvider;
import com.google.cloud.mcp.client.McpToolboxClientBuilder;
import com.google.cloud.mcp.tool.Tool;
import com.google.cloud.mcp.tool.ToolDefinition;
import com.google.cloud.mcp.tool.ToolPostProcessor;
import com.google.cloud.mcp.tool.ToolPreProcessor;
import com.google.cloud.mcp.tool.ToolResult;
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
   *
   * @return A CompletableFuture containing the map of Tool definitions (Key: Tool Name).
   */
  default CompletableFuture<Map<String, ToolDefinition>> loadToolset() {
    return listTools();
  }

  /**
   * Loads a specific toolset by name (if supported by server).
   *
   * @param toolsetName The name of the toolset to load.
   * @return A CompletableFuture containing the map of Tool definitions (Key: Tool Name).
   */
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

  /**
   * Loads a specific tool definition and returns a smart Tool object.
   *
   * @param toolName The name of the tool to load.
   * @return A CompletableFuture containing the Tool object.
   */
  CompletableFuture<Tool> loadTool(String toolName);

  /**
   * Loads a specific tool and registers authentication getters immediately.
   *
   * @param toolName The name of the tool.
   * @param authTokenGetters A map of Service Name -> Token Getter Function.
   * @return A CompletableFuture containing the Tool object.
   */
  CompletableFuture<Tool> loadTool(String toolName, Map<String, AuthTokenGetter> authTokenGetters);

  /**
   * Low-level invocation method.
   *
   * @param toolName The name of the tool to invoke.
   * @param arguments The arguments to pass to the tool.
   * @return A CompletableFuture containing the result of the tool invocation.
   */
  CompletableFuture<ToolResult> invokeTool(String toolName, Map<String, Object> arguments);

  /**
   * Low-level invocation method with explicit headers.
   *
   * @param toolName The name of the tool to invoke.
   * @param arguments The arguments to pass to the tool.
   * @param extraHeaders Additional HTTP headers to include in the request.
   * @return A CompletableFuture containing the result of the tool invocation.
   */
  CompletableFuture<ToolResult> invokeTool(
      String toolName, Map<String, Object> arguments, Map<String, String> extraHeaders);

  /**
   * Builder pattern for creating client instances.
   *
   * @return A new Builder instance.
   */
  static Builder builder() {
    return new McpToolboxClientBuilder();
  }

  /** Builder for creating {@link McpToolboxClient} instances. */
  interface Builder {
    /**
     * Sets the base URL of the MCP Toolbox Server.
     *
     * @param baseUrl The base URL.
     * @return The builder instance.
     */
    Builder baseUrl(String baseUrl);

    /**
     * Sets the API key for authentication with the MCP Toolbox Server.
     *
     * @param apiKey The API key.
     * @return The builder instance.
     */
    Builder apiKey(String apiKey);

    /**
     * Sets additional HTTP headers to be included in all requests to the MCP Toolbox Server.
     *
     * @param headers The HTTP headers.
     * @return The builder instance.
     */
    Builder headers(Map<String, String> headers);

    /**
     * Sets the credentials provider for dynamic authorization header resolution.
     *
     * @param credentialsProvider The credentials provider.
     * @return The builder instance.
     */
    Builder credentialsProvider(CredentialsProvider credentialsProvider);

    /**
     * Adds a global pre-processor that will be applied to all tools loaded by this client.
     *
     * @param preProcessor The pre-processor to add.
     * @return The builder instance.
     */
    Builder preProcessor(ToolPreProcessor preProcessor);

    /**
     * Adds a global post-processor that will be applied to all tools loaded by this client.
     *
     * @param postProcessor The post-processor to add.
     * @return The builder instance.
     */
    Builder postProcessor(ToolPostProcessor postProcessor);

    /**
     * Sets the preferred protocol version of the client.
     *
     * @param protocolVersion The protocol version (e.g. ProtocolVersion.VERSION_2025_11_25).
     * @return The builder instance.
     */
    Builder protocolVersion(ProtocolVersion protocolVersion);

    /**
     * Sets a custom {@link java.net.http.HttpClient} for connection management.
     *
     * @param httpClient The custom HttpClient.
     * @return The builder instance.
     */
    Builder httpClient(java.net.http.HttpClient httpClient);

    /**
     * Sets a custom {@link java.util.concurrent.Executor} for running asynchronous tasks.
     *
     * @param executor The custom Executor.
     * @return The builder instance.
     */
    Builder executor(java.util.concurrent.Executor executor);

    /**
     * Builds and returns a new {@link McpToolboxClient} instance.
     *
     * @return The new client instance.
     */
    McpToolboxClient build();
  }
}
