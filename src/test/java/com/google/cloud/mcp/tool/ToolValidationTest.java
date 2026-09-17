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

package com.google.cloud.mcp.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.mcp.McpToolboxClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;

@Timeout(10)
class ToolValidationTest {

  private McpToolboxClient mockClient;

  @BeforeEach
  void setUp() {
    mockClient = mock(McpToolboxClient.class);
  }

  @Test
  void testValidateAndSanitizeArgs_nullsRemoved() throws Exception {
    ToolDefinition def = new ToolDefinition("test-tool", List.of(), List.of());

    List<Map<String, Object>> capturedArgs = new ArrayList<>();
    when(mockClient.invokeTool(anyString(), anyMap(), anyMap()))
        .thenAnswer(
            inv -> {
              capturedArgs.add(new HashMap<>(inv.getArgument(1)));
              return CompletableFuture.completedFuture(new ToolResult(List.of(), false));
            });

    Tool tool = new Tool("test-tool", def, mockClient);
    Map<String, Object> inputArgs = new HashMap<>();
    inputArgs.put("param-null", null);
    inputArgs.put("param-valid", "value");

    tool.execute(inputArgs).join();

    assertEquals(1, capturedArgs.size());
    Map<String, Object> args = capturedArgs.get(0);
    assertTrue(args.containsKey("param-valid"));
    assertFalse(args.containsKey("param-null"));
  }

  @Test
  void testValidateAndSanitizeArgs_missingRequired() {
    List<ToolDefinition.Parameter> params =
        List.of(new ToolDefinition.Parameter("p-required", "string", true, "desc", List.of()));
    ToolDefinition def = new ToolDefinition("test-tool", params, List.of());
    Tool tool = new Tool("test-tool", def, mockClient);

    CompletionException exception =
        org.junit.jupiter.api.Assertions.assertThrows(
            CompletionException.class, () -> tool.execute(Map.of()).join());
    assertTrue(exception.getCause() instanceof IllegalArgumentException);
    assertTrue(
        exception.getCause().getMessage().contains("Missing required parameter 'p-required'"));
  }

  @Test
  void testValidateAndSanitizeArgs_typeMismatches() {
    List<ToolDefinition.Parameter> params =
        List.of(
            new ToolDefinition.Parameter("p-string", "string", false, "desc", List.of()),
            new ToolDefinition.Parameter("p-int", "integer", false, "desc", List.of()),
            new ToolDefinition.Parameter("p-number", "number", false, "desc", List.of()),
            new ToolDefinition.Parameter("p-bool", "boolean", false, "desc", List.of()),
            new ToolDefinition.Parameter("p-array", "array", false, "desc", List.of()),
            new ToolDefinition.Parameter("p-obj", "object", false, "desc", List.of()));
    ToolDefinition def = new ToolDefinition("test-tool", params, List.of());
    Tool tool = new Tool("test-tool", def, mockClient);

    // Expected string, got integer
    CompletionException ex1 =
        org.junit.jupiter.api.Assertions.assertThrows(
            CompletionException.class, () -> tool.execute(Map.of("p-string", 123)).join());
    assertTrue(ex1.getCause() instanceof IllegalArgumentException);

    // Expected integer, got string
    CompletionException ex2 =
        org.junit.jupiter.api.Assertions.assertThrows(
            CompletionException.class, () -> tool.execute(Map.of("p-int", "not-an-int")).join());
    assertTrue(ex2.getCause() instanceof IllegalArgumentException);

    // Expected number, got string
    CompletionException ex3 =
        org.junit.jupiter.api.Assertions.assertThrows(
            CompletionException.class,
            () -> tool.execute(Map.of("p-number", "not-a-number")).join());
    assertTrue(ex3.getCause() instanceof IllegalArgumentException);

    // Expected boolean, got string
    CompletionException ex4 =
        org.junit.jupiter.api.Assertions.assertThrows(
            CompletionException.class,
            () -> tool.execute(Map.of("p-bool", "not-a-boolean")).join());
    assertTrue(ex4.getCause() instanceof IllegalArgumentException);

    // Expected array, got string
    CompletionException ex5 =
        org.junit.jupiter.api.Assertions.assertThrows(
            CompletionException.class,
            () -> tool.execute(Map.of("p-array", "not-an-array")).join());
    assertTrue(ex5.getCause() instanceof IllegalArgumentException);

    // Expected object, got string
    CompletionException ex6 =
        org.junit.jupiter.api.Assertions.assertThrows(
            CompletionException.class, () -> tool.execute(Map.of("p-obj", "not-an-object")).join());
    assertTrue(ex6.getCause() instanceof IllegalArgumentException);
  }

  @Test
  void testValidateAndSanitizeArgs_typeMatches() throws Exception {
    List<ToolDefinition.Parameter> params =
        List.of(
            new ToolDefinition.Parameter("p-string", "string", false, "desc", List.of()),
            new ToolDefinition.Parameter("p-int", "integer", false, "desc", List.of()),
            new ToolDefinition.Parameter("p-int-val", "integer", false, "desc", List.of()),
            new ToolDefinition.Parameter("p-number", "number", false, "desc", List.of()),
            new ToolDefinition.Parameter("p-bool", "boolean", false, "desc", List.of()),
            new ToolDefinition.Parameter("p-array", "array", false, "desc", List.of()),
            new ToolDefinition.Parameter("p-array-arr", "array", false, "desc", List.of()),
            new ToolDefinition.Parameter("p-obj", "object", false, "desc", List.of()));
    ToolDefinition def = new ToolDefinition("test-tool", params, List.of());
    when(mockClient.invokeTool(anyString(), anyMap(), anyMap()))
        .thenReturn(CompletableFuture.completedFuture(new ToolResult(List.of(), false)));

    Tool tool = new Tool("test-tool", def, mockClient);
    tool.execute(
            Map.of(
                "p-string",
                "valid-string",
                "p-int",
                123L,
                "p-int-val",
                123,
                "p-number",
                4.56,
                "p-bool",
                true,
                "p-array",
                List.of("item"),
                "p-array-arr",
                new String[] {"item"},
                "p-obj",
                Map.of("key", "val")))
        .join(); // should succeed without exceptions
  }

  @Test
  void testValidateAndSanitizeArgs_customTypeMatch() throws Exception {
    List<ToolDefinition.Parameter> params =
        List.of(
            new ToolDefinition.Parameter("p-custom", "custom-type-name", false, "desc", List.of()));
    ToolDefinition def = new ToolDefinition("test-tool", params, List.of());
    when(mockClient.invokeTool(anyString(), anyMap(), anyMap()))
        .thenReturn(CompletableFuture.completedFuture(new ToolResult(List.of(), false)));

    Tool tool = new Tool("test-tool", def, mockClient);
    tool.execute(Map.of("p-custom", "any-value")).join(); // should succeed
  }

  @Test
  void testValidateAndSanitizeArgs_withNullParameters() throws Exception {
    ToolDefinition def = new ToolDefinition("test-tool", null, List.of());
    when(mockClient.invokeTool(anyString(), anyMap(), anyMap()))
        .thenReturn(CompletableFuture.completedFuture(new ToolResult(List.of(), false)));

    Tool tool = new Tool("test-tool", def, mockClient);
    tool.execute(Map.of("any-param", "any-value")).join(); // should bypass validation loop safely
  }

  @Test
  void testDefaultValueInjection() throws Exception {
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

  @Test
  void testDefaultValueDeepCloning() throws Exception {
    Map<String, Object> complexDefault = new HashMap<>();
    complexDefault.put("key", "value");

    ToolDefinition.Parameter paramWithDefault =
        new ToolDefinition.Parameter(
            "param1", "object", false, "A parameter", null, complexDefault);

    ToolDefinition def = new ToolDefinition("A test tool", List.of(paramWithDefault), null);

    Tool tool = new Tool("testTool", def, mockClient);

    when(mockClient.invokeTool(eq("testTool"), any(), any()))
        .thenReturn(
            CompletableFuture.completedFuture(new ToolResult(Collections.emptyList(), false)));

    Map<String, Object> args = new HashMap<>();
    CompletableFuture<ToolResult> future = tool.execute(args);
    future.join();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> argsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(mockClient).invokeTool(eq("testTool"), argsCaptor.capture(), any());

    Map<String, Object> capturedArgs = argsCaptor.getValue();
    @SuppressWarnings("unchecked")
    Map<String, Object> injectedDefault = (Map<String, Object>) capturedArgs.get("param1");

    // Mutate the injected map
    injectedDefault.put("key", "mutated_value");

    // Ensure the original defaultValue stored in the definition remains untouched
    @SuppressWarnings("unchecked")
    Map<String, Object> defValueInDefinition =
        (Map<String, Object>) def.parameters().get(0).defaultValue();
    assertEquals(
        "value",
        defValueInDefinition.get("key"),
        "The default value in definition must remain unmutated");
  }

  @Test
  void testDefaultValueDeepCloning_withList() throws Exception {
    List<Object> complexDefault = new ArrayList<>();
    complexDefault.add("item1");
    complexDefault.add(Map.of("nestedKey", "nestedValue"));

    ToolDefinition.Parameter paramWithDefault =
        new ToolDefinition.Parameter("param1", "array", false, "A parameter", null, complexDefault);

    ToolDefinition def = new ToolDefinition("A test tool", List.of(paramWithDefault), null);

    Tool tool = new Tool("testTool", def, mockClient);

    when(mockClient.invokeTool(eq("testTool"), any(), any()))
        .thenReturn(
            CompletableFuture.completedFuture(new ToolResult(Collections.emptyList(), false)));

    Map<String, Object> args = new HashMap<>();
    CompletableFuture<ToolResult> future = tool.execute(args);
    future.join();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> argsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(mockClient).invokeTool(eq("testTool"), argsCaptor.capture(), any());

    Map<String, Object> capturedArgs = argsCaptor.getValue();
    @SuppressWarnings("unchecked")
    List<Object> injectedDefault = (List<Object>) capturedArgs.get("param1");

    // Mutate the injected list
    injectedDefault.set(0, "mutated_item");

    // Ensure the original defaultValue stored in the definition remains untouched
    @SuppressWarnings("unchecked")
    List<Object> defValueInDefinition = (List<Object>) def.parameters().get(0).defaultValue();
    assertEquals(
        "item1",
        defValueInDefinition.get(0),
        "The default value in definition must remain unmutated");
  }

  @Test
  void testValidateAndSanitizeArgs_requiredParameterProvided() throws Exception {
    List<ToolDefinition.Parameter> params =
        List.of(new ToolDefinition.Parameter("p-required", "string", true, "desc", List.of()));
    ToolDefinition def = new ToolDefinition("test-tool", params, List.of());
    when(mockClient.invokeTool(anyString(), anyMap(), anyMap()))
        .thenReturn(CompletableFuture.completedFuture(new ToolResult(List.of(), false)));

    Tool tool = new Tool("test-tool", def, mockClient);
    tool.execute(Map.of("p-required", "provided-value")).join(); // should succeed
  }

  @Test
  void testValidateAndSanitizeArgs_nullTypeWithNonNullValue() throws Exception {
    List<ToolDefinition.Parameter> params =
        List.of(new ToolDefinition.Parameter("p-no-type", null, false, "desc", List.of()));
    ToolDefinition def = new ToolDefinition("test-tool", params, List.of());
    when(mockClient.invokeTool(anyString(), anyMap(), anyMap()))
        .thenReturn(CompletableFuture.completedFuture(new ToolResult(List.of(), false)));

    Tool tool = new Tool("test-tool", def, mockClient);
    tool.execute(Map.of("p-no-type", "some-value")).join(); // should succeed without checking type
  }
}
