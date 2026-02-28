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

import com.google.cloud.mcp.McpToolboxClient;
import com.google.cloud.mcp.Tool;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import java.io.FileInputStream;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

public class InputValidationTest {
    public static void main(String[] args) {
        String targetUrl = "YOUR_TOOLBOX_SERVICE_ENDPOINT"; 
        String tokenAudience = targetUrl; 
        // --------------------------------------------------------------------------------
        // AUTHENTICATION SETUP
        // --------------------------------------------------------------------------------
        // FOR LOCAL DEVELOPMENT: Use a Service Account Key JSON file.
        // FOR PRODUCTION (Cloud Run): Comment out the 'keyPath' logic and use ADC directly.
        // --------------------------------------------------------------------------------

        String keyPath = "/YOUR_CREDENTIALS_JSON_FILE_PATH.json"; 

        System.out.println("--- Starting MCP Toolbox Input Validation Test ---");

        try {
            // 1. Setup Auth (Same as before)
            System.out.println("    [Init] Fetching ID Token...");
            GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(keyPath));
            if (!(credentials instanceof IdTokenProvider)) {
                throw new RuntimeException("Loaded credentials do not support ID Tokens.");
            }
            String idToken = ((IdTokenProvider) credentials).idTokenWithAudience(tokenAudience, Collections.emptyList()).getTokenValue();

            // 2. Initialize Client
            McpToolboxClient client = McpToolboxClient.builder()
                .baseUrl(targetUrl)
                .build();

            // 3. Load the Tool
            // We MUST use loadTool() because validation relies on the ToolDefinition fetched from the server.
            System.out.println("    [Init] Loading tool 'get-toy-price'...");
            Tool tool = client.loadTool("get-toy-price").join();

            // 4. Register Auth
            // We manually register the token getter so the Tool object can inject the header.
            tool.addAuthTokenGetter("google_auth", () -> CompletableFuture.completedFuture(idToken));


            // --- Test Case A: Valid Input ---
            System.out.println("\n[Test A] Sending VALID input (String)...");
            try {
                Map<String, Object> validArgs = Map.of("description", "barbie");
                var result = tool.execute(validArgs).join();
                System.out.println(
                        "    ✅ Success! Output: " + (result.content().isEmpty() ? "Empty" : getTextContent(result)));
            } catch (Exception e) {
                System.err.println("    ❌ Unexpected failure: " + e.getMessage());
                e.printStackTrace();
            }


            // --- Test Case B: Invalid Type (Int instead of String) ---
            System.out.println("\n[Test B] Sending INVALID input (Integer instead of String)...");
            try {
                // The 'description' parameter is defined as type: string. We pass an Integer.
                Map<String, Object> invalidArgs = Map.of("description", 12345);
                
                tool.execute(invalidArgs).join();
                System.err.println("    ❌ FAILED: Validation did not catch the error!");
            } catch (Exception e) {
                // We expect a RuntimeException wrapping IllegalArgumentException
                Throwable cause = e.getCause();
                System.out.println("    ✅ Caught Expected Error: " + cause.getMessage());
            }
            

            // --- Test Case C: Null Value (Filtering) ---
            System.out.println("\n[Test C] Sending NULL value (should be filtered)...");
            try {
                // We use a HashMap because Map.of doesn't allow nulls
                Map<String, Object> nullArgs = new HashMap<>();
                nullArgs.put("description", "barbie"); // Valid param
                nullArgs.put("some_optional_param", null); // Null param
                
                // If validation works, 'some_optional_param' will be removed before sending
                var result = tool.execute(nullArgs).join();
                System.out.println("    ✅ Success! Null value was filtered and request succeeded.");
            } catch (Exception e) {
                 System.out.println("    ❌ Result: " + e.getCause().getMessage());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("\n--- Done ---");
    }

    private static String getTextContent(com.google.cloud.mcp.ToolResult result) {
        if (result.content() == null)
            return "";
        return result.content().stream()
                .filter(c -> "text".equals(c.type()) && c.text() != null)
                .map(c -> c.text())
                .collect(java.util.stream.Collectors.joining("\n"));
    }
}
