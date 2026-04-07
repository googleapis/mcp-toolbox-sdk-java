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

import java.util.concurrent.CompletableFuture;

/** A functional interface for post-processing tool results after invocation. */
@FunctionalInterface
public interface ToolPostProcessor {

  /**
   * Processes the result of a tool after it has been invoked.
   *
   * @param toolName The name of the tool that was invoked.
   * @param result The original tool result.
   * @return A CompletableFuture containing the processed tool result.
   */
  CompletableFuture<ToolResult> process(String toolName, ToolResult result);
}
