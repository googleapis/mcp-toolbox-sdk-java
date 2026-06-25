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

package com.google.cloud.mcp.auth;

import com.google.cloud.mcp.tool.ToolDefinition;
import java.util.Map;

/** Represents a resolved set of authentication credentials for a tool execution. */
public final class ResolvedAuth {
  private final Map<String, String> tokens;

  /**
   * Constructs a new ResolvedAuth.
   *
   * @param tokens The map of resolved auth tokens.
   */
  public ResolvedAuth(Map<String, String> tokens) {
    Map<String, String> copy = new java.util.HashMap<>();
    if (tokens != null) {
      for (Map.Entry<String, String> entry : tokens.entrySet()) {
        if (entry.getKey() != null && entry.getValue() != null) {
          copy.put(entry.getKey(), entry.getValue());
        }
      }
    }
    this.tokens = Map.copyOf(copy);
  }

  /**
   * Applies the resolved credentials to the outgoing request parameters and headers.
   *
   * @param finalArgs The map of arguments for the tool execution.
   * @param extraHeaders The map of extra headers for the tool execution.
   * @param definition The tool definition to inspect for parameter auth mappings.
   */
  public void applyTo(
      Map<String, Object> finalArgs, Map<String, String> extraHeaders, ToolDefinition definition) {

    for (Map.Entry<String, String> entry : tokens.entrySet()) {
      String serviceName = entry.getKey();
      String token = entry.getValue();
      if (token == null || token.isEmpty()) {
        continue;
      }

      // A. Parameter mapping
      String paramName = findParameterForService(definition, serviceName);
      if (paramName != null) {
        finalArgs.put(paramName, token);
      }

      // B. Header mapping
      // Normalize to prevent double-prefixing if the provider already prefixed the token
      String authorizationHeaderValue =
          token.regionMatches(true, 0, "Bearer ", 0, 7) ? token : "Bearer " + token;
      extraHeaders.put("Authorization", authorizationHeaderValue);
      extraHeaders.put(serviceName + "_token", token);
    }
  }

  private static String findParameterForService(ToolDefinition definition, String serviceName) {
    if (definition.parameters() == null) return null;
    for (ToolDefinition.Parameter param : definition.parameters()) {
      if (param.authSources() != null && param.authSources().contains(serviceName)) {
        return param.name();
      }
    }
    return null;
  }
}
