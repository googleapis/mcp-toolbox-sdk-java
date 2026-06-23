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

package cloudcode.bulk;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import com.google.cloud.mcp.AuthTokenGetter;
import com.google.cloud.mcp.McpToolboxClient;
import com.google.cloud.mcp.Tool;
import java.io.FileInputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Example demonstrating how to use the advanced Bulk Toolset Loading API. This showcases how to
 * pre-bind parameters and credentials to multiple tools at startup, retrieving a map of fully
 * configured and ready-to-use Tool objects.
 */
public class BulkToolsetUsage {
  public static void main(String[] args) {
    // CONFIGURATION
    String targetUrl = System.getProperty("toolbox.url", "YOUR_TOOLBOX_SERVICE_ENDPOINT");
    String tokenAudience = System.getProperty("toolbox.tokenAudience", targetUrl);
    String keyPath = System.getProperty("toolbox.keyPath", "YOUR_CREDENTIALS_JSON_FILE_PATH.json");

    System.out.println("--- Starting MCP Toolbox Bulk Toolset Example ---");
    System.out.println("Target Server: " + targetUrl);

    try {
      System.out.println("    [Init] Fetching ID Token...");
      GoogleCredentials credentials;
      if (keyPath != null
          && !keyPath.isEmpty()
          && !keyPath.contains("YOUR_CREDENTIALS_JSON_FILE_PATH")) {
        System.out.println("    [Auth] Using Service Account Key File: " + keyPath);
        credentials = GoogleCredentials.fromStream(new FileInputStream(keyPath));
      } else {
        System.out.println("    [Auth] Using Application Default Credentials (ADC)");
        credentials = GoogleCredentials.getApplicationDefault();
      }

      if (!(credentials instanceof IdTokenProvider)) {
        throw new RuntimeException("Loaded credentials do not support ID Tokens.");
      }

      String idToken =
          ((IdTokenProvider) credentials)
              .idTokenWithAudience(tokenAudience, Collections.emptyList())
              .getTokenValue();

      // 1. Initialize Client
      McpToolboxClient client =
          McpToolboxClient.builder().baseUrl(targetUrl).apiKey(idToken).build();

      // 2. Prepare Bulk Parameter Bindings
      // Map structure: Tool Name -> (Parameter Name -> Parameter Value)
      Map<String, Map<String, Object>> paramBinds = new HashMap<>();

      Map<String, Object> toyParams = new HashMap<>();
      toyParams.put(
          "description",
          "teddy bear"); // Pre-bind "description" to "teddy bear" for "get-toy-price"
      paramBinds.put("get-toy-price", toyParams);

      // 3. Prepare Bulk Auth Bindings
      // Map structure: Tool Name -> (Service Name -> Auth Token Getter)
      Map<String, Map<String, AuthTokenGetter>> authBinds = new HashMap<>();

      Map<String, AuthTokenGetter> toyAuth = new HashMap<>();
      AuthTokenGetter toolAuthGetter =
          () -> CompletableFuture.completedFuture("dummy-auth-token-from-provider");
      toyAuth.put(
          "google_auth", toolAuthGetter); // Pre-bind "google_auth" service for "get-toy-price"
      authBinds.put("get-toy-price", toyAuth);

      // 4. Load the entire toolset and apply all bindings at once
      System.out.println("    [Init] Loading and binding toolset in bulk...");
      client
          .loadToolset(null, paramBinds, authBinds, true)
          .thenAccept(
              boundTools -> {
                System.out.println(
                    "\n[1] Toolset loaded in bulk. Total tools bound: " + boundTools.size());

                // Let's verify and execute "get-retail-facet-filters" (simple tool, no
                // pre-bindings)
                Tool filterTool = boundTools.get("get-retail-facet-filters");
                if (filterTool != null) {
                  System.out.println("\n[2] Executing 'get-retail-facet-filters'...");
                  filterTool
                      .execute(Map.of())
                      .thenAccept(
                          result -> {
                            System.out.println("    -> Result: " + result.content().get(0).text());
                          })
                      .join();
                } else {
                  System.err.println("Tool 'get-retail-facet-filters' not found in toolset!");
                }

                // Let's verify and execute "get-toy-price" (pre-bound to "teddy bear")
                Tool priceTool = boundTools.get("get-toy-price");
                if (priceTool != null) {
                  System.out.println(
                      "\n"
                          + "[3] Executing 'get-toy-price' (using pre-bound parameter 'teddy"
                          + " bear')...");
                  // We run execute with empty map, so it falls back to the bound 'teddy bear'
                  priceTool
                      .execute(Map.of())
                      .thenAccept(
                          result -> {
                            System.out.println(
                                "    -> Result (Teddy Bear): " + result.content().get(0).text());
                          })
                      .join();

                  System.out.println(
                      "\n"
                          + "[4] Attempting to override parameter at runtime to 'barbie' (should be"
                          + " ignored/overridden by bound value)...");
                  // We pass description at runtime but it will be overridden by bound "teddy bear"
                  priceTool
                      .execute(Map.of("description", "barbie"))
                      .thenAccept(
                          result -> {
                            System.out.println(
                                "    -> Result (Actual): "
                                    + result.content().get(0).text()
                                    + " (Expect price for teddy bear: 14.99)");
                          })
                      .join();
                } else {
                  System.err.println("Tool 'get-toy-price' not found in toolset!");
                }
              })
          .join();

    } catch (Exception e) {
      e.printStackTrace();
    }
    System.out.println("\n--- Bulk Toolset Example Complete ---");
  }
}
