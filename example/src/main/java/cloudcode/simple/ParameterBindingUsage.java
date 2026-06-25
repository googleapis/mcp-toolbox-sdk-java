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
import com.google.cloud.mcp.Tool;
import com.google.cloud.mcp.ToolResult;
import java.io.FileInputStream;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Example demonstrating how to use parameter bindings and authenticated tool methods. */
public class ParameterBindingUsage {
  public static void main(String[] args) {
    String targetUrl = System.getProperty("toolbox.url", "YOUR_TOOLBOX_SERVICE_ENDPOINT");
    String tokenAudience = System.getProperty("toolbox.tokenAudience", targetUrl);
    String keyPath = System.getProperty("toolbox.keyPath", "YOUR_CREDENTIALS_JSON_FILE_PATH.json");

    try {
      GoogleCredentials credentials;
      if (keyPath != null && !keyPath.isEmpty()) {
        credentials = GoogleCredentials.fromStream(new FileInputStream(keyPath));
      } else {
        credentials = GoogleCredentials.getApplicationDefault();
      }

      if (!(credentials instanceof IdTokenProvider)) {
        throw new RuntimeException("Loaded credentials do not support ID Tokens.");
      }

      String idToken =
          ((IdTokenProvider) credentials)
              .idTokenWithAudience(tokenAudience, Collections.emptyList())
              .getTokenValue();

      // Initialize Client
      McpToolboxClient client =
          McpToolboxClient.builder().baseUrl(targetUrl).apiKey(idToken).build();

      // 1. Load the tool with authentication providers
      AuthTokenGetter toolAuthGetter = () -> CompletableFuture.completedFuture(idToken);
      Tool tool = client.loadTool("get-toy-price", Map.of("google_auth", toolAuthGetter)).join();

      // 2. Execute unbound (with explicit runtime argument)
      ToolResult resultUnbound = tool.execute(Map.of("description", "barbie")).join();
      System.out.println("Result (unbound): " + resultUnbound.content().get(0).text());

      // 3. Bind the parameter and execute bound (runtime arg will be overridden by binding)
      tool.bindParam("description", "soft toy");
      ToolResult resultBound = tool.execute(Map.of("description", "barbie")).join();
      System.out.println("Result (bound): " + resultBound.content().get(0).text());

    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
