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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdToken;
import com.google.auth.oauth2.IdTokenProvider;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthMethodsTest {

  private int loadCount;

  @BeforeEach
  void setUp() {
    AuthMethods.resetCredentialsCache();
    loadCount = 0;
  }

  @Test
  void testGetGoogleIdToken_Success() throws Exception {
    String mockToken = "mock-id-token-xyz";
    String audience = "https://test-mcp-service.com";

    // Setup Mock credentials implementing GoogleCredentials and IdTokenProvider
    GoogleCredentials credentials = mock(GoogleCredentials.class, withSettings().extraInterfaces(IdTokenProvider.class));
    IdToken mockIdToken = mock(IdToken.class);
    when(mockIdToken.getTokenValue()).thenReturn(mockToken);
    when(((IdTokenProvider) credentials).idTokenWithAudience(eq(audience), any())).thenReturn(mockIdToken);

    AuthMethods.credentialsLoader = () -> credentials;

    CompletableFuture<String> futureToken = AuthMethods.getGoogleIdToken(audience);
    String token = futureToken.get();

    assertEquals("Bearer " + mockToken, token);
  }

  @Test
  void testGetGoogleIdToken_Caching() throws Exception {
    String mockToken = "mock-id-token-caching";
    String audience = "https://test-mcp-service.com";

    GoogleCredentials credentials = mock(GoogleCredentials.class, withSettings().extraInterfaces(IdTokenProvider.class));
    IdToken mockIdToken = mock(IdToken.class);
    when(mockIdToken.getTokenValue()).thenReturn(mockToken);
    when(((IdTokenProvider) credentials).idTokenWithAudience(eq(audience), any())).thenReturn(mockIdToken);

    AuthMethods.credentialsLoader = () -> {
      loadCount++;
      return credentials;
    };

    // First call loads the credentials
    String token1 = AuthMethods.getGoogleIdToken(audience).get();
    // Second call should reuse the cached credentials
    String token2 = AuthMethods.getGoogleIdToken(audience).get();

    assertEquals("Bearer " + mockToken, token1);
    assertEquals("Bearer " + mockToken, token2);
    assertEquals(1, loadCount, "Credentials should be loaded exactly once due to caching");
  }

  @Test
  void testGetGoogleIdToken_NotAnIdTokenProvider() {
    String audience = "https://test-mcp-service.com";

    // Regular credentials that do not implement IdTokenProvider
    GoogleCredentials credentials = mock(GoogleCredentials.class);
    AuthMethods.credentialsLoader = () -> credentials;

    CompletableFuture<String> futureToken = AuthMethods.getGoogleIdToken(audience);
    ExecutionException exception = assertThrows(ExecutionException.class, futureToken::get);
    assertTrue(exception.getCause().getMessage().contains("not an instance of IdTokenProvider"));
  }

  @Test
  void testGoogleCredentialsProvider_Success() throws Exception {
    String mockToken = "mock-id-token-provider";
    String audience = "https://test-mcp-service.com";

    GoogleCredentials credentials = mock(GoogleCredentials.class, withSettings().extraInterfaces(IdTokenProvider.class));
    IdToken mockIdToken = mock(IdToken.class);
    when(mockIdToken.getTokenValue()).thenReturn(mockToken);
    when(((IdTokenProvider) credentials).idTokenWithAudience(eq(audience), any())).thenReturn(mockIdToken);

    AuthMethods.credentialsLoader = () -> credentials;

    GoogleCredentialsProvider provider = new GoogleCredentialsProvider(audience);
    String header = provider.getAuthorizationHeader().get();

    assertEquals("Bearer " + mockToken, header);
  }

  @Test
  void testGoogleCredentialsProvider_FallbackOnException() throws Exception {
    String audience = "https://test-mcp-service.com";

    // Fail loading credentials
    AuthMethods.credentialsLoader = () -> {
      throw new IOException("Cannot load credentials");
    };

    GoogleCredentialsProvider provider = new GoogleCredentialsProvider(audience);
    String header = provider.getAuthorizationHeader().get();

    // Verification that it gracefully returns null (proceed without auth)
    assertNull(header);
  }
}
