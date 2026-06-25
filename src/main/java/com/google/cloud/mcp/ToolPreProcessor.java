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

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** A functional interface for pre-processing tool inputs before invocation. */
@FunctionalInterface
public interface ToolPreProcessor {

  /**
   * Processes the input arguments for a tool before it is invoked.
   *
   * @param toolName The name of the tool being invoked.
   * @param arguments The original arguments provided to the tool.
   * @return A CompletableFuture containing the processed arguments.
   */
  CompletableFuture<Map<String, Object>> process(String toolName, Map<String, Object> arguments);
}
