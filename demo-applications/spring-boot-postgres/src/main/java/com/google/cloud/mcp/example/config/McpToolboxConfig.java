/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.mcp.example.config;

import com.google.cloud.mcp.McpToolboxClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configuration class that registers the {@link McpToolboxClient} as a Spring bean. */
@Configuration
public class McpToolboxConfig {

  private static final Logger logger = LoggerFactory.getLogger(McpToolboxConfig.class);

  @Value("${mcp.toolbox.url:http://localhost:5005/mcp}")
  private String toolboxUrl;

  @Bean
  public McpToolboxClient mcpToolboxClient() {
    logger.info("Initializing McpToolboxClient configured with baseUrl: {}", toolboxUrl);
    return McpToolboxClient.builder().baseUrl(toolboxUrl).build();
  }
}
