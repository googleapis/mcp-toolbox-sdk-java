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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ToolTest {

  private McpToolboxClient mockClient;
  private ToolDefinition toolDefinition;
  private Tool tool;

  @BeforeEach
  void setUp() {
    mockClient = mock(McpToolboxClient.class);
    toolDefinition = new ToolDefinition("Test Tool", null, null);
    tool = new Tool("test_tool", toolDefinition, mockClient);
  }

  @Test
  @SuppressWarnings("unchecked")
  void testExecute_withPreAndPostProcessors_modifiesArgsAndResult() throws Exception {
    // Arrange
    Map<String, Object> initialArgs = new HashMap<>();
    initialArgs.put("arg1", "val1");

    ToolResult originalResult =
        new ToolResult(List.of(new ToolResult.Content("text", "original")), false);
    ToolResult modifiedResult =
        new ToolResult(List.of(new ToolResult.Content("text", "modified")), false);

    ToolPreProcessor preProcessor1 =
        (name, args) -> {
          Map<String, Object> newArgs = new HashMap<>(args);
          newArgs.put("arg2", "val2");
          return CompletableFuture.completedFuture(newArgs);
        };

    ToolPreProcessor preProcessor2 =
        (name, args) -> {
          Map<String, Object> newArgs = new HashMap<>(args);
          newArgs.put("arg3", "val3");
          return CompletableFuture.completedFuture(newArgs);
        };

    ToolPostProcessor postProcessor =
        (name, result) -> {
          if (result.content().get(0).text().equals("original")) {
            return CompletableFuture.completedFuture(modifiedResult);
          }
          return CompletableFuture.completedFuture(result);
        };

    tool.addPreProcessor(preProcessor1);
    tool.addPreProcessor(preProcessor2);
    tool.addPostProcessor(postProcessor);

    when(mockClient.invokeTool(eq("test_tool"), anyMap(), anyMap()))
        .thenReturn(CompletableFuture.completedFuture(originalResult));

    // Act
    CompletableFuture<ToolResult> futureResult = tool.execute(initialArgs);
    ToolResult finalResult = futureResult.get();

    // Assert
    ArgumentCaptor<Map<String, Object>> argsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(mockClient, times(1)).invokeTool(eq("test_tool"), argsCaptor.capture(), anyMap());

    Map<String, Object> capturedArgs = argsCaptor.getValue();
    assertEquals(3, capturedArgs.size());
    assertEquals("val1", capturedArgs.get("arg1"));
    assertEquals("val2", capturedArgs.get("arg2"));
    assertEquals("val3", capturedArgs.get("arg3"));

    assertSame(modifiedResult, finalResult);
  }

  @Test
  void testExecute_preProcessorException_failsFutureWithoutInvokingClient() {
    // Arrange
    Map<String, Object> initialArgs = new HashMap<>();

    ToolPreProcessor preProcessor =
        (name, args) -> CompletableFuture.failedFuture(new RuntimeException("PreProcessor failed"));

    tool.addPreProcessor(preProcessor);

    // Act
    CompletableFuture<ToolResult> futureResult = tool.execute(initialArgs);

    // Assert
    assertTrue(futureResult.isCompletedExceptionally());

    Exception exception = null;
    try {
      futureResult.get();
    } catch (InterruptedException | ExecutionException e) {
      exception = e;
    }
    assertTrue(exception.getCause() instanceof RuntimeException);
    assertEquals("PreProcessor failed", exception.getCause().getMessage());

    verify(mockClient, never()).invokeTool(eq("test_tool"), anyMap(), anyMap());
    verify(mockClient, never()).invokeTool(eq("test_tool"), anyMap());
  }
}
