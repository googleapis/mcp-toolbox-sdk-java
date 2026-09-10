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

package com.google.cloud.mcp.autoconfigure;

import com.google.cloud.mcp.McpToolboxClient;
import com.google.cloud.mcp.client.McpToolboxClientBuilder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Auto-configuration for the MCP Toolbox SDK Client. */
@AutoConfiguration
@ConditionalOnClass(McpToolboxClient.class)
@ConditionalOnProperty(name = "google.cloud.mcp.toolbox.base-url")
@EnableConfigurationProperties(McpToolboxProperties.class)
public class McpToolboxAutoConfiguration {

  private final McpToolboxProperties properties;

  public McpToolboxAutoConfiguration(McpToolboxProperties properties) {
    this.properties = properties;
  }

  /**
   * Registers a default {@link McpToolboxClient} bean if none is already defined.
   *
   * @return A configured {@link McpToolboxClient} instance.
   */
  @Bean
  @ConditionalOnMissingBean
  public McpToolboxClient mcpToolboxClient() {
    McpToolboxClient.Builder builder =
        new McpToolboxClientBuilder().baseUrl(properties.getBaseUrl());
    if (properties.getApiKey() != null && !properties.getApiKey().isEmpty()) {
      builder.apiKey(properties.getApiKey());
    }
    return builder.build();
  }
}
