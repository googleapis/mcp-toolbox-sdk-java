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

package cloudcode.simple;

import com.google.cloud.mcp.McpToolboxClient;
import java.util.Map;

/**
 * A minimal example demonstrating client initialization, tool discovery, and invocation.
 */
public class ExampleUsage {
  public static void main(String[] args) {
    String targetUrl = System.getProperty("toolbox.url", "YOUR_TOOLBOX_SERVICE_ENDPOINT");
    String apiKey = System.getProperty("toolbox.apiKey", "YOUR_API_KEY");

    System.out.println("--- Starting MCP Toolbox Simple Example ---");
    System.out.println("Connecting to: " + targetUrl);

    // Initialize the client
    McpToolboxClient client =
        McpToolboxClient.builder().baseUrl(targetUrl).apiKey(apiKey).build();

    // 1. List available tools
    client
        .listTools()
        .thenAccept(
            tools -> {
              System.out.println("Available tools: " + tools.size());
              for (String toolName : tools.keySet()) {
                System.out.println(" - " + toolName);
              }

              // 2. Invoke a simple tool
              if (tools.containsKey("get-retail-facet-filters")) {
                System.out.println("\nInvoking 'get-retail-facet-filters'...");
                client
                    .invokeTool("get-retail-facet-filters", Map.of())
                    .thenAccept(
                        result -> {
                          System.out.println("Result: " + result.content().get(0).text());
                        })
                    .join();
              }
            })
        .join();

    System.out.println("\n--- Simple Example Complete ---");
  }
}
