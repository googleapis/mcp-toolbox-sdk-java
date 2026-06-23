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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class McpToolboxClientBuilderTest {

  @Test
  void testHeadersAndApiKey() {
    McpToolboxClient client =
        McpToolboxClient.builder()
            .baseUrl("http://localhost:8080")
            .apiKey("my-api-key")
            .headers(Map.of("X-Custom-Header", "value1", "Authorization", "Bearer custom-token"))
            .build();

    assertNotNull(client);
    assertTrue(client instanceof McpToolboxClientImpl);
  }

  @Test
  void testBaseUrlValidation() {
    assertThrows(
        IllegalArgumentException.class, () -> McpToolboxClient.builder().baseUrl(null).build());

    assertThrows(
        IllegalArgumentException.class, () -> McpToolboxClient.builder().baseUrl("").build());
  }

  @Test
  void testBaseUrlTrailingSlashNormalization() throws Exception {
    McpToolboxClient client = McpToolboxClient.builder().baseUrl("http://localhost:8080/").build();

    Field transportField = McpToolboxClientImpl.class.getDeclaredField("transport");
    transportField.setAccessible(true);
    Transport transport = (Transport) transportField.get(client);
    assertEquals("http://localhost:8080", transport.getBaseUrl());
  }

  @Test
  @SuppressWarnings("unchecked")
  void testApiKeyPreprocessing() throws Exception {
    Method getAuthHeaderMethod =
        McpToolboxClientImpl.class.getDeclaredMethod("getAuthorizationHeader");
    getAuthHeaderMethod.setAccessible(true);

    // 1. ApiKey is null or empty
    McpToolboxClient clientNullKey =
        McpToolboxClient.builder().baseUrl("http://localhost:8080").apiKey(null).build();
    CompletableFuture<String> futureNull =
        (CompletableFuture<String>) getAuthHeaderMethod.invoke(clientNullKey);
    assertNull(futureNull.join());

    // 2. ApiKey is raw (not prefixed with Bearer)
    McpToolboxClient clientRawKey =
        McpToolboxClient.builder().baseUrl("http://localhost:8080").apiKey("raw-key-123").build();
    CompletableFuture<String> futureRaw =
        (CompletableFuture<String>) getAuthHeaderMethod.invoke(clientRawKey);
    assertEquals("Bearer raw-key-123", futureRaw.join());

    // 3. ApiKey already contains Bearer prefix
    McpToolboxClient clientBearerKey =
        McpToolboxClient.builder()
            .baseUrl("http://localhost:8080")
            .apiKey("Bearer token-456")
            .build();
    CompletableFuture<String> futureBearer =
        (CompletableFuture<String>) getAuthHeaderMethod.invoke(clientBearerKey);
    assertEquals("Bearer token-456", futureBearer.join());

    // 4. ApiKey does not override existing Authorization header
    McpToolboxClient clientOverrideKey =
        McpToolboxClient.builder()
            .baseUrl("http://localhost:8080")
            .headers(Map.of("Authorization", "Bearer existing-token"))
            .apiKey("new-key-should-be-ignored")
            .build();
    CompletableFuture<String> futureOverride =
        (CompletableFuture<String>) getAuthHeaderMethod.invoke(clientOverrideKey);
    assertEquals("Bearer existing-token", futureOverride.join());
  }

  @Test
  void testHeadersNullHandledSafely() {
    McpToolboxClient client =
        McpToolboxClient.builder().baseUrl("http://localhost:8080").headers(null).build();
    assertNotNull(client);
  }

  @Test
  @SuppressWarnings("unchecked")
  void testCredentialsProviderConfiguration() throws Exception {
    CredentialsProvider provider = () -> CompletableFuture.completedFuture("Bearer test-token");
    McpToolboxClient client =
        McpToolboxClient.builder()
            .baseUrl("http://localhost:8080")
            .credentialsProvider(provider)
            .build();
    assertNotNull(client);

    Method getAuthHeaderMethod =
        McpToolboxClientImpl.class.getDeclaredMethod("getAuthorizationHeader");
    getAuthHeaderMethod.setAccessible(true);
    CompletableFuture<String> future =
        (CompletableFuture<String>) getAuthHeaderMethod.invoke(client);
    assertEquals("Bearer test-token", future.join());
  }

  @Test
  @SuppressWarnings("unchecked")
  void testEmptyApiKey_TreatedAsNoKey() throws Exception {
    McpToolboxClient client =
        McpToolboxClient.builder().baseUrl("http://localhost:8080").apiKey("").build();

    Method getAuthHeaderMethod =
        McpToolboxClientImpl.class.getDeclaredMethod("getAuthorizationHeader");
    getAuthHeaderMethod.setAccessible(true);
    CompletableFuture<String> future =
        (CompletableFuture<String>) getAuthHeaderMethod.invoke(client);
    assertNull(future.join());
  }
}
