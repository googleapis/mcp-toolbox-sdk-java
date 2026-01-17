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

package cloudcode.helloworld;

import java.util.Map;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.io.FileInputStream;
import com.google.cloud.mcp.McpToolboxClient;
import com.google.cloud.mcp.AuthTokenGetter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenProvider;

/**
 * Sample Application to demostrate the usage of the MCP Toolbox Java SDK.
 * Covers: Global Auth, Parameterized Auth, Discovery, Simple Tool, Authenticated Tool, Parameter Binding.
 */
public class ExampleUsage {
    public static void main(String[] args) {
        // CONFIGURATION
        String targetUrl = "YOUR_TOOLBOX_SERVICE_ENDPOINT"; 
        
        // Match the Service URL if using Cloud Run OIDC
        String tokenAudience = targetUrl; 

        // --------------------------------------------------------------------------------
        // AUTHENTICATION SETUP
        // --------------------------------------------------------------------------------
        // FOR LOCAL DEVELOPMENT: Use a Service Account Key JSON file.
        // FOR PRODUCTION (Cloud Run): Comment out the 'keyPath' logic and use ADC directly.
        // --------------------------------------------------------------------------------

        String keyPath = "/YOUR_CREDENTIALS_JSON_FILE_PATH.json"; 

        System.out.println("--- Starting MCP Toolbox Integration Test ---");
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
            String idToken = ((IdTokenProvider) credentials).idTokenWithAudience(tokenAudience, Collections.emptyList()).getTokenValue();
            System.out.println("    [Debug] Token Generated.");

            // Initialize Client with Global Auth (Applies to ALL calls - Gate 1)
            McpToolboxClient client = McpToolboxClient.builder()
                .baseUrl(targetUrl)
                .apiKey(idToken) 
                .build();

            // STEP 1: TEST DISCOVERY METHODS
            client.listTools()
                .thenCompose(tools -> {
                    System.out.println("\n[1] listTools(): Success. Found " + tools.size() + " tools.");
                    return client.loadToolset(); 
                })
                .thenCompose(tools -> {
                    System.out.println("[2] loadToolset() (Alias): Success.");
                    return client.loadToolset("retail")
                        .handle((res, ex) -> {
                            if (ex == null) System.out.println("[3] loadToolset('retail'): Found " + res.size() + " tools.");
                            else System.out.println("[3] loadToolset('retail'): Skipped (Not configured on server).");
                            return null;
                        });
                })
                .thenCompose(ignore -> {
                    
                    // STEP 2: INVOKE TOOL WITHOUT EXTRA AUTH
                    System.out.println("\n[4] Testing Simple Tool: 'get-retail-facet-filters'...");
                    return client.invokeTool("get-retail-facet-filters", Map.of());
                })
                .thenCompose(result -> {
                    System.out.println("    -> Result: " + (result.content() != null ? "Received Data" : "Empty"));
                    
                    // STEP 3: INVOKE TOOL WITH AUTHENTICATED PARAMETERS
                    System.out.println("\n[5] Testing Authenticated Tool: 'get-toy-price'...");
                    
                    // Define the getter for the 'google_auth' service
                    AuthTokenGetter toolAuthGetter = () -> CompletableFuture.completedFuture(idToken);

                    // Load using the sophisticated overload
                    return client.loadTool("get-toy-price", Map.of("google_auth", toolAuthGetter));
                })
                .thenCompose(tool -> {
                    System.out.println("    -> Loaded Tool: " + tool.definition().description());
                    
                    // STEP 4: TEST BINDING PARAMETERS SEQUENTIALLY
                    System.out.println("\n[A] Executing UNBOUND (Runtime arg: 'barbie')...");
                    
                    return tool.execute(Map.of("description", "barbie"))
                        .thenCompose(result1 -> {
                            if (result1.content() != null && !result1.content().isEmpty()) {
                                System.out.println("    -> Result (Unbound): " + result1.content().get(0).text());
                            }
                            
                            // NOW bind the parameter
                            System.out.println("\n[B] Binding 'description' to 'soft toy'...");
                            tool.bindParam("description", "soft toy");
                            
                            System.out.println("    -> Executing BOUND (Runtime arg: 'barbie' - should be IGNORED)...");
                            // We pass 'barbie', but expecting 'soft toy' price because of binding override
                            return tool.execute(Map.of("description", "barbie"));
                        });
                })
                .thenAccept(result -> {
                    System.out.println("\n[6] Final Result (Bound):");
                    if (result.isError()) {
                        System.err.println("Tool execution failed: " + result.content().get(0).text());
                    } else if (result.content() != null && !result.content().isEmpty()) {
                        String output = result.content().get(0).text();
                        System.out.println("    " + output.substring(0, Math.min(output.length(), 200)) + "...");
                    } else {
                        System.out.println("    Empty Response");
                    }
                })
                .exceptionally(ex -> {
                    System.err.println("\n!!! TEST FAILED !!!");
                    ex.printStackTrace();
                    return null;
                })
                .join();

        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("\n--- Test Suite Complete ---");
    }
}
