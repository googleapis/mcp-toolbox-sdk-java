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
}
