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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Represents the result of a tool invocation.
 *
 * @param content A list of content items returned by the tool.
 * @param isError Whether the invocation resulted in an error.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolResult(
    @JsonProperty("content") List<Content> content, @JsonProperty("isError") boolean isError) {
  /**
   * Represents a single content item in a tool result.
   *
   * @param type The type of content (e.g., "text").
   * @param text The text content.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Content(@JsonProperty("type") String type, @JsonProperty("text") String text) {}

  /**
   * Helper method to retrieve all text content from the result as a single string stream.
   *
   * @return A single concatenated string of all text content.
   */
  public String text() {
    if (content == null || content.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (Content c : content) {
      if ("text".equals(c.type()) && c.text() != null) {
        sb.append(c.text()).append("\n");
      }
    }
    return sb.toString().trim();
  }
}
