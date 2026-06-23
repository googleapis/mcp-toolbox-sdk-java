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

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import com.google.cloud.mcp.AuthTokenGetter;
import com.google.cloud.mcp.McpToolboxClient;
import java.io.FileInputStream;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Example demonstrating how to use parameter bindings and authenticated tool methods. */
public class ParameterBindingUsage {
  public static void main(String[] args) {
    // CONFIGURATION
    String targetUrl = System.getProperty("toolbox.url", "YOUR_TOOLBOX_SERVICE_ENDPOINT");

    // Match the Service URL if using Cloud Run OIDC
    String tokenAudience = System.getProperty("toolbox.tokenAudience", targetUrl);

    // --------------------------------------------------------------------------------
    // AUTHENTICATION SETUP
    // --------------------------------------------------------------------------------
    // FOR LOCAL DEVELOPMENT: Use a Service Account Key JSON file.
    // FOR PRODUCTION (Cloud Run): Comment out the 'keyPath' logic and use ADC directly.
    // --------------------------------------------------------------------------------

    String keyPath = System.getProperty("toolbox.keyPath", "YOUR_CREDENTIALS_JSON_FILE_PATH.json");

    System.out.println("--- Starting MCP Toolbox Parameter Binding Example ---");
    System.out.println("Target Server: " + targetUrl);

    try {
      System.out.println("    [Init] Fetching ID Token...");

      GoogleCredentials credentials;

      // --- OPTION A: LOCAL DEV (Explicit Key File) ---
      if (keyPath != null && !keyPath.isEmpty()) {
        System.out.println("    [Auth] Using Service Account Key File: " + keyPath);
        credentials = GoogleCredentials.fromStream(new FileInputStream(keyPath));
      }
      // --- OPTION B: PRODUCTION (ADC) ---
      else {
        System.out.println("    [Auth] Using Application Default Credentials (ADC)");
        credentials = GoogleCredentials.getApplicationDefault();
      }

      if (!(credentials instanceof IdTokenProvider)) {
        throw new RuntimeException("Loaded credentials do not support ID Tokens.");
      }

      // Generate Token for the specified Audience
      String idToken =
          ((IdTokenProvider) credentials)
              .idTokenWithAudience(tokenAudience, Collections.emptyList())
              .getTokenValue();
      System.out.println("    [Debug] Token Generated.");

      // Initialize Client
      McpToolboxClient client =
          McpToolboxClient.builder().baseUrl(targetUrl).apiKey(idToken).build();

      // STEP 1: LOAD TOOL WITH AUTH PROVIDERS
      System.out.println("\n[1] Testing Authenticated Tool: 'get-toy-price'...");

      // Define the getter for the 'google_auth' service
      AuthTokenGetter toolAuthGetter = () -> CompletableFuture.completedFuture(idToken);

      client
          .loadTool("get-toy-price", Map.of("google_auth", toolAuthGetter))
          .thenCompose(
              tool -> {
                System.out.println("    -> Loaded Tool: " + tool.definition().description());

                // STEP 2: TEST BINDING PARAMETERS SEQUENTIALLY
                System.out.println("\n[A] Executing UNBOUND (Runtime arg: 'barbie')...");

                return tool.execute(Map.of("description", "barbie"))
                    .thenCompose(
                        result1 -> {
                          if (result1.content() != null && !result1.content().isEmpty()) {
                            System.out.println(
                                "    -> Result (Unbound): " + result1.content().get(0).text());
                          }

                          // NOW bind the parameter
                          System.out.println("\n[B] Binding 'description' to 'soft toy'...");
                          tool.bindParam("description", "soft toy");

                          System.out.println(
                              "    -> Executing BOUND (Runtime arg: 'barbie' - should be"
                                  + " IGNORED)...");
                          // We pass 'barbie', but expecting 'soft toy' price because of binding
                          // override
                          return tool.execute(Map.of("description", "barbie"));
                        });
              })
          .thenAccept(
              result -> {
                System.out.println("\n[2] Final Result (Bound):");
                if (result.isError()) {
                  System.err.println("Tool execution failed: " + result.content().get(0).text());
                } else if (result.content() != null && !result.content().isEmpty()) {
                  String output = result.content().get(0).text();
                  System.out.println("    Result content: " + output);
                }
              })
          .join();

    } catch (Exception e) {
      e.printStackTrace();
    }
    System.out.println("\n--- Parameter Binding Example Complete ---");
  }
}
