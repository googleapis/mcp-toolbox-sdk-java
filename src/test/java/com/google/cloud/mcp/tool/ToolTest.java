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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.mcp.McpToolboxClient;
import com.google.cloud.mcp.auth.ResolvedAuth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;

@Timeout(10)
class ToolTest {

  private ExecutorService pool;
  private McpToolboxClient mockClient;
  private ToolDefinition toolDefinition;
  private Tool tool;

  @BeforeEach
  void setUp() {
    pool = Executors.newFixedThreadPool(8);
    mockClient = mock(McpToolboxClient.class);
    toolDefinition = new ToolDefinition("Test Tool", null, null);
    tool = new Tool("test_tool", toolDefinition, mockClient);
  }

  @AfterEach
  void tearDown() {
    if (pool != null) {
      pool.shutdownNow();
    }
  }

  /**
   * Regression test: when several authenticated services resolve their tokens concurrently, {@link
   * Tool#execute(Map)} must not drop any credential header or authenticated argument from the
   * outgoing request. The previous implementation mutated the non-thread-safe finalArgs /
   * extraHeaders HashMaps from each getter's completion thread, which could lose writes.
   */
  @Test
  void execute_withManyConcurrentAuthGetters_doesNotDropCredentials() {
    int services = Integer.getInteger("toolTest.services", 24);
    int iterations = Integer.getInteger("toolTest.iters", 2500);

    List<ToolDefinition.Parameter> params = new ArrayList<>();
    for (int i = 0; i < services; i++) {
      params.add(new ToolDefinition.Parameter("p" + i, "string", false, "", List.of("svc" + i)));
    }
    ToolDefinition def = new ToolDefinition("race-tool", params, new ArrayList<>());

    List<Map<String, Object>> capturedArgs = new ArrayList<>();
    List<Map<String, String>> capturedHeaders = new ArrayList<>();

    McpToolboxClient client = mock(McpToolboxClient.class);
    when(client.invokeTool(anyString(), anyMap(), anyMap()))
        .thenAnswer(
            inv -> {
              capturedArgs.add(new HashMap<>(inv.getArgument(1)));
              capturedHeaders.add(new HashMap<>(inv.getArgument(2)));
              return CompletableFuture.completedFuture(
                  new ToolResult(List.of(new ToolResult.Content("text", "ok")), false));
            });

    Tool raceTool = new Tool("race-tool", def, client);
    for (int i = 0; i < services; i++) {
      final String token = "tok-" + i;
      raceTool.addAuthTokenGetter(
          "svc" + i,
          () ->
              CompletableFuture.supplyAsync(
                  () -> {
                    int spins = ThreadLocalRandom.current().nextInt(50);
                    for (int s = 0; s < spins; s++) {
                      Thread.onSpinWait();
                    }
                    return token;
                  },
                  pool));
    }

    for (int iter = 0; iter < iterations; iter++) {
      raceTool.execute(new HashMap<>()).join();
    }

    assertEquals(iterations, capturedHeaders.size(), "every invocation should reach the client");
    for (int iter = 0; iter < iterations; iter++) {
      Map<String, String> headers = capturedHeaders.get(iter);
      Map<String, Object> args = capturedArgs.get(iter);
      for (int i = 0; i < services; i++) {
        assertEquals(
            "tok-" + i,
            headers.get("svc" + i + "_token"),
            "missing/garbled svc" + i + "_token header on iteration " + iter);
        assertEquals(
            "tok-" + i, args.get("p" + i), "missing/garbled p" + i + " arg on iteration " + iter);
      }
    }
  }

  @Test
  void resolvedAuth_appliesTokensWithCorrectBearerNormalization() {
    ToolDefinition def = new ToolDefinition("test-tool", List.of(), List.of());
    Map<String, String> tokens =
        Map.of(
            "svc-raw", "rawToken123",
            "svc-prefixed", "Bearer alreadyPrefixed456",
            "svc-lowercase-prefixed", "bearer alreadyPrefixed789");

    ResolvedAuth resolvedAuth = new ResolvedAuth(tokens);
    Map<String, Object> finalArgs = new HashMap<>();
    Map<String, String> extraHeaders = new HashMap<>();

    resolvedAuth.applyTo(finalArgs, extraHeaders, def);

    // Verify token values map to sdk custom headers
    assertEquals("rawToken123", extraHeaders.get("svc-raw_token"));
    assertEquals("Bearer alreadyPrefixed456", extraHeaders.get("svc-prefixed_token"));
    assertEquals("bearer alreadyPrefixed789", extraHeaders.get("svc-lowercase-prefixed_token"));

    // Verify standard OIDC authorization header matches and doesn't double prefix
    String authHeader = extraHeaders.get("Authorization");
    assertTrue(
        authHeader.equals("Bearer rawToken123")
            || authHeader.equals("Bearer alreadyPrefixed456")
            || authHeader.equals("bearer alreadyPrefixed789"));
  }

  @Test
  void resolvedAuth_withNullAndEmptyTokens_ignoresThemSafely() {
    ToolDefinition def = new ToolDefinition("test-tool", List.of(), List.of());
    Map<String, String> tokens = new HashMap<>();
    tokens.put("svc-null", null);
    tokens.put("svc-empty", "");
    tokens.put("svc-valid", "validToken");

    ResolvedAuth resolvedAuth = new ResolvedAuth(tokens);
    Map<String, Object> finalArgs = new HashMap<>();
    Map<String, String> extraHeaders = new HashMap<>();

    resolvedAuth.applyTo(finalArgs, extraHeaders, def);

    // Only the valid token should be mapped
    assertEquals("Bearer validToken", extraHeaders.get("Authorization"));
    assertEquals("validToken", extraHeaders.get("svc-valid_token"));
    assertTrue(!extraHeaders.containsKey("svc-null_token"));
    assertTrue(!extraHeaders.containsKey("svc-empty_token"));
  }

  @Test
  void testToolGetters() {
    ToolDefinition def = new ToolDefinition("test-tool", List.of(), List.of());
    McpToolboxClient client = mock(McpToolboxClient.class);
    Tool tool = new Tool("test-tool", def, client);

    assertEquals("test-tool", tool.name());
    assertEquals(def, tool.definition());
  }

  @Test
  void testBindParamStaticAndSupplier() throws Exception {
    List<ToolDefinition.Parameter> params =
        List.of(
            new ToolDefinition.Parameter("p-static", "string", false, "desc", List.of()),
            new ToolDefinition.Parameter("p-supplier", "string", false, "desc", List.of()));
    ToolDefinition def = new ToolDefinition("test-tool", params, List.of());
    McpToolboxClient client = mock(McpToolboxClient.class);

    List<Map<String, Object>> capturedArgs = new ArrayList<>();
    when(client.invokeTool(anyString(), anyMap(), anyMap()))
        .thenAnswer(
            inv -> {
              capturedArgs.add(new HashMap<>(inv.getArgument(1)));
              return CompletableFuture.completedFuture(new ToolResult(List.of(), false));
            });

    Tool tool = new Tool("test-tool", def, client);
    tool.bindParam("p-static", "static-value");
    tool.bindParam("p-supplier", () -> "supplier-value");

    tool.execute(Map.of()).join();

    assertEquals(1, capturedArgs.size());
    Map<String, Object> args = capturedArgs.get(0);
    assertEquals("static-value", args.get("p-static"));
    assertEquals("supplier-value", args.get("p-supplier"));
  }

  @Test
  void testResolvedAuth_withNullParametersListInDefinition() {
    ToolDefinition def = new ToolDefinition("test-tool", null, List.of());
    ResolvedAuth resolvedAuth = new ResolvedAuth(Map.of("svc", "token"));
    Map<String, Object> finalArgs = new HashMap<>();
    Map<String, String> extraHeaders = new HashMap<>();

    resolvedAuth.applyTo(finalArgs, extraHeaders, def);

    assertEquals("Bearer token", extraHeaders.get("Authorization"));
    assertTrue(finalArgs.isEmpty());
  }

  @Test
  void testResolvedAuth_withNullTokensMap() {
    ToolDefinition def = new ToolDefinition("test-tool", List.of(), List.of());
    ResolvedAuth resolvedAuth = new ResolvedAuth(null);
    Map<String, Object> finalArgs = new HashMap<>();
    Map<String, String> extraHeaders = new HashMap<>();

    resolvedAuth.applyTo(finalArgs, extraHeaders, def);

    assertTrue(finalArgs.isEmpty());
    assertTrue(extraHeaders.isEmpty());
  }

  @Test
  void testResolvedAuth_withNullKeysAndValuesInTokensMap() {
    ToolDefinition def = new ToolDefinition("test-tool", List.of(), List.of());
    Map<String, String> tokens = new HashMap<>();
    tokens.put(null, "val1");
    tokens.put("svc2", null);
    tokens.put("svc3", "val3");

    ResolvedAuth resolvedAuth = new ResolvedAuth(tokens);
    Map<String, Object> finalArgs = new HashMap<>();
    Map<String, String> extraHeaders = new HashMap<>();

    resolvedAuth.applyTo(finalArgs, extraHeaders, def);

    assertEquals("Bearer val3", extraHeaders.get("Authorization"));
    assertEquals("val3", extraHeaders.get("svc3_token"));
    assertTrue(!extraHeaders.containsKey("svc2_token"));
    assertTrue(!extraHeaders.containsKey("null_token"));
  }

  @Test
  void testValidateAndSanitizeArgs_customTypeMatch() throws Exception {
    List<ToolDefinition.Parameter> params =
        List.of(
            new ToolDefinition.Parameter("p-custom", "custom-type-name", false, "desc", List.of()));
    ToolDefinition def = new ToolDefinition("test-tool", params, List.of());
    McpToolboxClient client = mock(McpToolboxClient.class);
    when(client.invokeTool(anyString(), anyMap(), anyMap()))
        .thenReturn(CompletableFuture.completedFuture(new ToolResult(List.of(), false)));

    Tool tool = new Tool("test-tool", def, client);
    tool.execute(Map.of("p-custom", "any-value")).join(); // should succeed
  }

  @Test
  void testValidateAndSanitizeArgs_withNullParameters() throws Exception {
    ToolDefinition def = new ToolDefinition("test-tool", null, List.of());
    McpToolboxClient client = mock(McpToolboxClient.class);
    when(client.invokeTool(anyString(), anyMap(), anyMap()))
        .thenReturn(CompletableFuture.completedFuture(new ToolResult(List.of(), false)));

    Tool tool = new Tool("test-tool", def, client);
    tool.execute(Map.of("any-param", "any-value")).join(); // should bypass validation loop safely
  }

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

  @Test
  void testDefaultValueDeepCloning() throws Exception {
    McpToolboxClient mockClient = mock(McpToolboxClient.class);

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
    McpToolboxClient mockClient = mock(McpToolboxClient.class);

    List<Object> complexDefault = new ArrayList<>();
    complexDefault.add("value");

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
    injectedDefault.set(0, "mutated_value");

    // Ensure the original defaultValue stored in the definition remains untouched
    @SuppressWarnings("unchecked")
    List<Object> defValueInDefinition = (List<Object>) def.parameters().get(0).defaultValue();
    assertEquals(
        "value",
        defValueInDefinition.get(0),
        "The default value in definition must remain unmutated");
  }

  @Test
  void testToolDefinitionHints() {
    ToolDefinition defWithHints =
        new ToolDefinition("A test tool", List.of(), List.of(), true, false);

    assertEquals(true, defWithHints.readOnlyHint());
    assertEquals(false, defWithHints.destructiveHint());

    ToolDefinition defWithoutHints = new ToolDefinition("A test tool", List.of(), List.of());
    assertEquals(null, defWithoutHints.readOnlyHint());
    assertEquals(null, defWithoutHints.destructiveHint());
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
