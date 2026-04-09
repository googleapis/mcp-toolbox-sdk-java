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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ToolTest {

  @Test
  void testDefaultValueInjection() throws Exception {
    McpToolboxClient mockClient = mock(McpToolboxClient.class);

    ToolDefinition.Parameter paramWithDefault =
        new ToolDefinition.Parameter(
            "param1", "string", false, "A parameter", null, "default_value");
    ToolDefinition.Parameter paramNoDefault =
        new ToolDefinition.Parameter("param2", "string", false, "Another parameter", null, null);

    ToolDefinition def =
        new ToolDefinition("A test tool", List.of(paramWithDefault, paramNoDefault), null);

    Tool tool = new Tool("testTool", def, mockClient);

    when(mockClient.invokeTool(eq("testTool"), any(), any()))
        .thenReturn(
            CompletableFuture.completedFuture(new ToolResult(Collections.emptyList(), false)));

    Map<String, Object> args = new HashMap<>();
    args.put("param2", "provided_value");

    CompletableFuture<ToolResult> future = tool.execute(args);
    future.join(); // Wait for execution

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> argsCaptor = ArgumentCaptor.forClass(Map.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);

    verify(mockClient).invokeTool(eq("testTool"), argsCaptor.capture(), headersCaptor.capture());

    Map<String, Object> capturedArgs = argsCaptor.getValue();

    assertEquals(
        "default_value",
        capturedArgs.get("param1"),
        "Default value should be injected when not provided");
    assertEquals("provided_value", capturedArgs.get("param2"), "Provided value should be kept");
  }

  @Test
  void testDefaultValueNotOverwritten() throws Exception {
    McpToolboxClient mockClient = mock(McpToolboxClient.class);

    ToolDefinition.Parameter paramWithDefault =
        new ToolDefinition.Parameter(
            "param1", "string", false, "A parameter", null, "default_value");

    ToolDefinition def = new ToolDefinition("A test tool", List.of(paramWithDefault), null);

    Tool tool = new Tool("testTool", def, mockClient);

    when(mockClient.invokeTool(eq("testTool"), any(), any()))
        .thenReturn(
            CompletableFuture.completedFuture(new ToolResult(Collections.emptyList(), false)));

    Map<String, Object> args = new HashMap<>();
    args.put("param1", "custom_value");

    CompletableFuture<ToolResult> future = tool.execute(args);
    future.join(); // Wait for execution

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> argsCaptor = ArgumentCaptor.forClass(Map.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);

    verify(mockClient).invokeTool(eq("testTool"), argsCaptor.capture(), headersCaptor.capture());

    Map<String, Object> capturedArgs = argsCaptor.getValue();

    assertEquals(
        "custom_value",
        capturedArgs.get("param1"),
        "Provided value should not be overwritten by default value");
  }
}
