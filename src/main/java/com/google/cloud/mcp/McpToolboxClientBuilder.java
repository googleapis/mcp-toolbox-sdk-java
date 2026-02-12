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

public class McpToolboxClientBuilder implements McpToolboxClient.Builder {
  private String baseUrl;
  private String apiKey;

  @Override
  public McpToolboxClient.Builder baseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
    return this;
  }

  @Override
  public McpToolboxClient.Builder apiKey(String apiKey) {
    this.apiKey = apiKey;
    return this;
  }

  @Override
  public McpToolboxClient build() {
    if (baseUrl == null || baseUrl.isEmpty()) {
      throw new IllegalArgumentException("Base URL must be provided");
    }
    // Normalize URL: remove trailing slash if present
    if (baseUrl.endsWith("/")) {
      baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
    }
    return new HttpMcpToolboxClient(baseUrl, apiKey);
  }
}
