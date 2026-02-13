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
import java.util.List;

/**
 * Represents the definition of a tool, including its description and parameters.
 *
 * @param description A description of what the tool does.
 * @param parameters A list of parameters the tool accepts.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolDefinition(String description, List<Parameter> parameters) {
  /**
   * Represents a parameter of a tool.
   *
   * @param name The name of the parameter.
   * @param type The type of the parameter (e.g., "string", "number").
   * @param required Whether the parameter is required.
   * @param description A description of the parameter.
   * @param authSources A list of authentication sources for this parameter.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Parameter(
      String name,
      String type,
      boolean required,
      String description,
      List<String> authSources // Maps services to parameters
      ) {}
}
