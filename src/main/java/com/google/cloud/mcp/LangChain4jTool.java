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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import java.util.Map;
import java.util.stream.Collectors;

/** Adapter for LangChain4j Tools. */
public class LangChain4jTool {

  private static final ObjectMapper mapper = new ObjectMapper();
  private final Tool tool;

  public LangChain4jTool(Tool tool) {
    this.tool = tool;
  }

  public ToolSpecification specification() {
    return ToolSpecification.builder()
        .name(tool.name())
        .description(tool.definition().description())
        // In a real implementation, we would map parameters here.
        // For now, we assume dynamic arguments.
        .build();
  }

  public ToolExecutor executor() {
    return (request, memoryId) -> {
      try {
        Map<String, Object> arguments =
            mapper.readValue(request.arguments(), new TypeReference<Map<String, Object>>() {});
        ToolResult result = tool.execute(arguments).join();
        return result.content().stream()
            .map(ToolResult.Content::text)
            .collect(Collectors.joining("\n"));
      } catch (Exception e) {
        throw new RuntimeException("Failed to execute tool", e);
      }
    };
  }
}
