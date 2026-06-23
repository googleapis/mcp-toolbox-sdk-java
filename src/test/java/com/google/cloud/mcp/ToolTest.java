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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ToolTest {

  private ExecutorService pool;

  @BeforeEach
  void setUp() {
    pool = Executors.newFixedThreadPool(8);
  }

  @AfterEach
  void tearDown() {
    pool.shutdownNow();
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

    Tool tool = new Tool("race-tool", def, client);
    for (int i = 0; i < services; i++) {
      final String token = "tok-" + i;
      tool.addAuthTokenGetter(
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
      tool.execute(new HashMap<>()).join();
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
  void testValidateAndSanitizeArgs_nullsRemoved() throws Exception {
    ToolDefinition def = new ToolDefinition("test-tool", List.of(), List.of());
    McpToolboxClient client = mock(McpToolboxClient.class);

    List<Map<String, Object>> capturedArgs = new ArrayList<>();
    when(client.invokeTool(anyString(), anyMap(), anyMap()))
        .thenAnswer(
            inv -> {
              capturedArgs.add(new HashMap<>(inv.getArgument(1)));
              return CompletableFuture.completedFuture(new ToolResult(List.of(), false));
            });

    Tool tool = new Tool("test-tool", def, client);
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
    McpToolboxClient client = mock(McpToolboxClient.class);
    Tool tool = new Tool("test-tool", def, client);

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
    McpToolboxClient client = mock(McpToolboxClient.class);
    Tool tool = new Tool("test-tool", def, client);

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
    McpToolboxClient client = mock(McpToolboxClient.class);
    when(client.invokeTool(anyString(), anyMap(), anyMap()))
        .thenReturn(CompletableFuture.completedFuture(new ToolResult(List.of(), false)));

    Tool tool = new Tool("test-tool", def, client);
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
}
