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

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Defines the contract for an MCP transport layer that manages protocol-level formatting and
 * network communication.
 */
public interface Transport {
  /**
   * Returns the base URL of the remote service.
   *
   * @return The base URL string.
   */
  String getBaseUrl();

  /**
   * Asynchronously fetches available tools from the server.
   *
   * @param toolsetName The name of the toolset to load (optional).
   * @param metadata Request metadata or extra options to include.
   * @return A CompletableFuture containing the raw DTO manifest.
   */
  CompletableFuture<TransportManifest> listTools(String toolsetName, Map<String, String> metadata);

  /**
   * Asynchronously invokes a tool on the server.
   *
   * @param toolName The name of the tool to invoke.
   * @param arguments The arguments to pass to the tool.
   * @param metadata Request metadata or extra options to include.
   * @return A CompletableFuture containing the raw TransportResponse result of the tool execution.
   */
  CompletableFuture<TransportResponse> invokeTool(
      String toolName, Map<String, Object> arguments, Map<String, String> metadata);

  /** Closes any underlying network connections/resources. */
  void close();
}
