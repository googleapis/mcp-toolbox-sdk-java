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

package com.google.cloud.mcp.client;

import com.google.cloud.mcp.McpToolboxClient;
import com.google.cloud.mcp.McpToolboxSyncClient;
import com.google.cloud.mcp.auth.AuthTokenGetter;
import com.google.cloud.mcp.exception.McpToolboxException;
import com.google.cloud.mcp.tool.Tool;
import com.google.cloud.mcp.tool.ToolDefinition;
import com.google.cloud.mcp.tool.ToolResult;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

/**
 * Synchronous client wrapper that delegates to an async {@link McpToolboxClient} and blocks the
 * calling thread, unwrapping exceptions.
 */
public final class HttpMcpToolboxSyncClient implements McpToolboxSyncClient {
  /** The underlying asynchronous client to delegate to. */
  private final McpToolboxClient delegate;

  /**
   * Constructs a new HttpMcpToolboxSyncClient wrapping the given async client.
   *
   * @param client The async client to delegate to.
   */
  public HttpMcpToolboxSyncClient(final McpToolboxClient client) {
    if (client == null) {
      throw new IllegalArgumentException("Delegate client cannot be null");
    }
    this.delegate = client;
  }

  @Override
  public Map<String, ToolDefinition> listTools() {
    try {
      return delegate.listTools().join();
    } catch (CompletionException | CancellationException e) {
      throw unwrapException(e);
    }
  }

  @Override
  public Map<String, ToolDefinition> loadToolset(final String toolsetName) {
    try {
      return delegate.loadToolset(toolsetName).join();
    } catch (CompletionException | CancellationException e) {
      throw unwrapException(e);
    }
  }

  @Override
  public Map<String, Tool> loadToolset(
      final String toolsetName,
      final Map<String, Map<String, Object>> paramBinds,
      final Map<String, Map<String, AuthTokenGetter>> authBinds,
      final boolean strict) {
    try {
      return delegate.loadToolset(toolsetName, paramBinds, authBinds, strict).join();
    } catch (CompletionException | CancellationException e) {
      throw unwrapException(e);
    }
  }

  @Override
  public Tool loadTool(final String toolName) {
    try {
      return delegate.loadTool(toolName).join();
    } catch (CompletionException | CancellationException e) {
      throw unwrapException(e);
    }
  }

  @Override
  public Tool loadTool(final String toolName, final Map<String, AuthTokenGetter> authTokenGetters) {
    try {
      return delegate.loadTool(toolName, authTokenGetters).join();
    } catch (CompletionException | CancellationException e) {
      throw unwrapException(e);
    }
  }

  @Override
  public ToolResult invokeTool(final String toolName, final Map<String, Object> arguments) {
    try {
      return delegate.invokeTool(toolName, arguments).join();
    } catch (CompletionException | CancellationException e) {
      throw unwrapException(e);
    }
  }

  @Override
  public ToolResult invokeTool(
      final String toolName,
      final Map<String, Object> arguments,
      final Map<String, String> extraHeaders) {
    try {
      return delegate.invokeTool(toolName, arguments, extraHeaders).join();
    } catch (CompletionException | CancellationException e) {
      throw unwrapException(e);
    }
  }

  private RuntimeException unwrapException(final Throwable e) {
    final Throwable cause = e.getCause();
    if (cause == null) {
      return new McpToolboxException(e);
    }
    if (cause instanceof McpToolboxException) {
      return (McpToolboxException) cause;
    }
    if (cause instanceof IllegalArgumentException) {
      return (IllegalArgumentException) cause;
    }
    return new McpToolboxException(cause.getMessage(), cause);
  }
}
