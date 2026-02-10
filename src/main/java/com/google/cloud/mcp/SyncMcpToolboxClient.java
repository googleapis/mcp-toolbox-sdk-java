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

/**
 * Synchronous client for interacting with a Toolbox service.
 * A wrapper around {@link McpToolboxClient} that blocks on operations.
 */
public class SyncMcpToolboxClient {

    private final McpToolboxClient asyncClient;

    public SyncMcpToolboxClient(McpToolboxClient asyncClient) {
        this.asyncClient = asyncClient;
    }

    /**
     * Blocks and retrieves the list of tools from the server.
     */
    public Map<String, ToolDefinition> listTools() {
        return asyncClient.listTools().join();
    }

    /**
     * Blocks and loads a tool definition.
     */
    public Tool loadTool(String toolName) {
        return asyncClient.loadTool(toolName).join();
    }

    /**
     * Blocks and invokes a tool.
     */
    public ToolResult invokeTool(String toolName, Map<String, Object> arguments) {
        return asyncClient.invokeTool(toolName, arguments).join();
    }
}
