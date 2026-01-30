/*
 * Copyright 2025 Google LLC
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

package cloudcode.helloworld;

import com.google.cloud.mcp.McpToolboxClient;
import com.google.cloud.mcp.Tool;
import java.util.Map;
import java.util.HashMap;

public class StrictFlagTest {
  public static void main(String[] args) {
    String targetUrl = "YOUR_TOOLBOX_SERVICE_ENDPOINT";
    System.out.println("--- Starting MCP Toolbox Strict Flag Test ---");

    McpToolboxClient client = McpToolboxClient.builder()
        .baseUrl(targetUrl)
        .build();

    // Prepare bindings for a NON-EXISTENT tool
    Map<String, Map<String, Object>> paramBinds = new HashMap<>();
    paramBinds.put("non-existent-tool", Map.of("param1", "value1"));

    // Case 1: Strict = false (Should succeed, ignoring the bad binding)
    System.out.println("\n[Test 1] Loading with Strict = FALSE...");
    try {
      Map<String, Tool> tools = client.loadToolset(null, paramBinds, null, false).join();
      System.out.println("    ✅ Success! Loaded " + tools.size() + " tools. Unknown binding was ignored.");
    } catch (Exception e) {
      System.err.println("    ❌ Failed unexpectedly: " + e.getMessage());
    }

    // Case 2: Strict = true (Should fail)
    System.out.println("\n[Test 2] Loading with Strict = TRUE...");
    try {
      client.loadToolset(null, paramBinds, null, true).join();
      System.err.println("    ❌ FAILED: Exception was not thrown!");
    } catch (Exception e) {
      // Expecting CompletionException -> IllegalArgumentException
      String msg = e.getCause().getMessage();
      if (msg.contains("Strict mode error")) {
        System.out.println("    ✅ Success! Caught expected error: " + msg);
      } else {
        System.err.println("    ❌ Wrong error type: " + msg);
      }
    }

    System.out.println("\n--- Done ---");
  }
}