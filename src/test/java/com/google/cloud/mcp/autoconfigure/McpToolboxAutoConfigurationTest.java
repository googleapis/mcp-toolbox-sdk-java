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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.google.cloud.mcp.McpToolboxClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class McpToolboxAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(McpToolboxAutoConfiguration.class));

  @Test
  void testAutoConfigurationBacksOffWithoutBaseUrlProperty() {
    this.contextRunner.run(
        context -> {
          assertThat(context).doesNotHaveBean(McpToolboxClient.class);
        });
  }

  @Test
  void testAutoConfigurationCreatesClientWithBaseUrlProperty() {
    this.contextRunner
        .withPropertyValues("google.cloud.mcp.toolbox.base-url=http://localhost:8080")
        .run(
            context -> {
              assertThat(context).hasSingleBean(McpToolboxClient.class);
              McpToolboxClient client = context.getBean(McpToolboxClient.class);
              assertThat(client).isNotNull();
            });
  }

  @Test
  void testAutoConfigurationRespectsUserDefinedClientBean() {
    this.contextRunner
        .withUserConfiguration(UserConfiguration.class)
        .withPropertyValues("google.cloud.mcp.toolbox.base-url=http://localhost:8080")
        .run(
            context -> {
              assertThat(context).hasSingleBean(McpToolboxClient.class);
              McpToolboxClient client = context.getBean(McpToolboxClient.class);
              McpToolboxClient mockClient = context.getBean(UserConfiguration.class).mockClient();
              assertThat(client).isSameAs(mockClient);
            });
  }

  @Test
  void testPropertiesGettersAndSetters() {
    McpToolboxProperties props = new McpToolboxProperties();
    props.setBaseUrl("http://localhost:8080");
    props.setApiKey("my-key");
    assertThat(props.getBaseUrl()).isEqualTo("http://localhost:8080");
    assertThat(props.getApiKey()).isEqualTo("my-key");
  }

  @Test
  void testDirectAutoConfigurationInstantiation() {
    McpToolboxProperties props = new McpToolboxProperties();
    props.setBaseUrl("http://localhost:8080");
    McpToolboxAutoConfiguration config = new McpToolboxAutoConfiguration(props);
    McpToolboxClient client = config.mcpToolboxClient();
    assertThat(client).isNotNull();

    // Test with apiKey
    props.setApiKey("some-key");
    config = new McpToolboxAutoConfiguration(props);
    client = config.mcpToolboxClient();
    assertThat(client).isNotNull();
  }

  @Configuration(proxyBeanMethods = false)
  static class UserConfiguration {

    private final McpToolboxClient mockClient = mock(McpToolboxClient.class);

    McpToolboxClient mockClient() {
      return mockClient;
    }

    @Bean
    McpToolboxClient customClient() {
      return mockClient;
    }
  }
}
