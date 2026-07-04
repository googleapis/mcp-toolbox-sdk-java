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

import com.google.cloud.mcp.tool.ToolDefinition;
import java.util.Map;

/** Represents the raw tools manifest returned by the transport. */
public final class TransportManifest {
  private final Map<String, ToolDefinition> tools;

  /**
   * Constructs a new TransportManifest with a map of tool definitions.
   *
   * @param tools Map of tool name to definition.
   */
  public TransportManifest(Map<String, ToolDefinition> tools) {
    this.tools = tools;
  }

  /**
   * Returns the map of tools in the manifest.
   *
   * @return The tools map.
   */
  public Map<String, ToolDefinition> getTools() {
    return tools;
  }
}
