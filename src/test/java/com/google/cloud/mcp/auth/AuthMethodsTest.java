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

package com.google.cloud.mcp.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.lang.reflect.Constructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthMethodsTest {

  private int loadCount;

  @BeforeEach
  void setUp() {
    loadCount = 0;
  }

  @Test
  void testGetGoogleIdToken_Success() throws Exception {
    String mockToken = "mock-id-token-xyz";
    String audience = "https://test-mcp-service.com";

    // Setup Mock credentials implementing GoogleCredentials and IdTokenProvider
    GoogleCredentials credentials =
        mock(GoogleCredentials.class, withSettings().extraInterfaces(IdTokenProvider.class));
    IdToken mockIdToken = mock(IdToken.class);
    when(mockIdToken.getTokenValue()).thenReturn(mockToken);
    when(((IdTokenProvider) credentials).idTokenWithAudience(eq(audience), any()))
        .thenReturn(mockIdToken);

    String token = AuthMethods.getGoogleIdToken(credentials, audience);

    assertEquals("Bearer " + mockToken, token);
  }

  @Test
  void testGetGoogleIdToken_NotAnIdTokenProvider() {
    String audience = "https://test-mcp-service.com";

    // Regular credentials that do not implement IdTokenProvider
    GoogleCredentials credentials = mock(GoogleCredentials.class);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> AuthMethods.getGoogleIdToken(credentials, audience));
    assertTrue(exception.getMessage().contains("not an instance of IdTokenProvider"));
  }

  @Test
  void testGoogleCredentialsProvider_Success() throws Exception {
    String mockToken = "mock-id-token-provider";
    String audience = "https://test-mcp-service.com";

    GoogleCredentials credentials =
        mock(GoogleCredentials.class, withSettings().extraInterfaces(IdTokenProvider.class));
    IdToken mockIdToken = mock(IdToken.class);
    when(mockIdToken.getTokenValue()).thenReturn(mockToken);
    when(((IdTokenProvider) credentials).idTokenWithAudience(eq(audience), any()))
        .thenReturn(mockIdToken);

    GoogleCredentialsProvider provider = new GoogleCredentialsProvider(audience, () -> credentials);
    String header = provider.getAuthorizationHeader().get();

    assertEquals("Bearer " + mockToken, header);
  }

  @Test
  void testGoogleCredentialsProvider_Caching() throws Exception {
    String mockToken = "mock-id-token-caching";
    String audience = "https://test-mcp-service.com";

    GoogleCredentials credentials =
        mock(GoogleCredentials.class, withSettings().extraInterfaces(IdTokenProvider.class));
    IdToken mockIdToken = mock(IdToken.class);
    when(mockIdToken.getTokenValue()).thenReturn(mockToken);
    when(((IdTokenProvider) credentials).idTokenWithAudience(eq(audience), any()))
        .thenReturn(mockIdToken);

    GoogleCredentialsProvider.CredentialsLoader loader =
        () -> {
          loadCount++;
          return credentials;
        };

    GoogleCredentialsProvider provider = new GoogleCredentialsProvider(audience, loader);

    // First call loads the credentials
    String token1 = provider.getAuthorizationHeader().get();
    // Second call should reuse the cached credentials
    String token2 = provider.getAuthorizationHeader().get();

    assertEquals("Bearer " + mockToken, token1);
    assertEquals("Bearer " + mockToken, token2);
    assertEquals(1, loadCount, "Credentials should be loaded exactly once due to caching");
  }

  @Test
  void testGoogleCredentialsProvider_FallbackOnException() throws Exception {
    String audience = "https://test-mcp-service.com";

    // Fail loading credentials
    GoogleCredentialsProvider.CredentialsLoader loader =
        () -> {
          throw new IOException("Cannot load credentials");
        };

    GoogleCredentialsProvider provider = new GoogleCredentialsProvider(audience, loader);
    String header = provider.getAuthorizationHeader().get();

    // Verification that it gracefully returns null (proceed without auth)
    assertNull(header);
  }

  @Test
  void testGoogleCredentialsProvider_OidcFailure() throws Exception {
    String audience = "https://test-mcp-service.com";
    GoogleCredentials creds = mock(GoogleCredentials.class); // Does NOT implement IdTokenProvider
    GoogleCredentialsProvider provider = new GoogleCredentialsProvider(audience, () -> creds);
    String header = provider.getAuthorizationHeader().get();
    assertNull(header, "OIDC incompatible credentials should return null auth header");
  }

  @Test
  void testGoogleCredentialsProvider_InvalidAudience() {
    assertThrows(
        IllegalArgumentException.class, () -> new GoogleCredentialsProvider(null, () -> null));
    assertThrows(
        IllegalArgumentException.class, () -> new GoogleCredentialsProvider("", () -> null));
  }

  @Test
  void testGoogleCredentialsProvider_PublicConstructor() throws Exception {
    GoogleCredentialsProvider provider = new GoogleCredentialsProvider("https://test.com");
    // Should run gracefully, even if ADC fails locally it returns null
    String header = provider.getAuthorizationHeader().get();
    // No assertion on value, just verify it runs without crashing to cover constructor instructions
  }

  @Test
  void testAuthMethods_PrivateConstructor() throws Exception {
    Constructor<AuthMethods> constructor = AuthMethods.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    AuthMethods instance = constructor.newInstance();
    assertNotNull(instance);
  }

  @Test
  void testAuthMethods_NullCredentials() {
    assertThrows(
        IllegalArgumentException.class, () -> AuthMethods.getGoogleIdToken(null, "audience"));
  }

  @Test
  void testAuthMethods_RefreshException() throws Exception {
    GoogleCredentials creds = mock(GoogleCredentials.class);
    IOException simulatedException = new IOException("Refresh failed");
    org.mockito.Mockito.doThrow(simulatedException).when(creds).refreshIfExpired();

    assertThrows(IOException.class, () -> AuthMethods.getGoogleIdToken(creds, "audience"));
  }

  @Test
  void testGoogleCredentialsProvider_NullCredentialsLoaded() throws Exception {
    String audience = "https://test-mcp-service.com";
    GoogleCredentialsProvider provider = new GoogleCredentialsProvider(audience, () -> null);
    String header = provider.getAuthorizationHeader().get();
    assertNull(header, "Null credentials from loader should return null auth header");
  }

  @Test
  void testAuthMethods_BearerTokenAlreadyPrefixed() throws Exception {
    String mockToken = "Bearer custom-already-prefixed-token";
    String audience = "https://test-mcp-service.com";

    GoogleCredentials credentials =
        mock(GoogleCredentials.class, withSettings().extraInterfaces(IdTokenProvider.class));
    IdToken mockIdToken = mock(IdToken.class);
    when(mockIdToken.getTokenValue()).thenReturn(mockToken);
    when(((IdTokenProvider) credentials).idTokenWithAudience(eq(audience), any()))
        .thenReturn(mockIdToken);

    String resolvedToken = AuthMethods.getGoogleIdToken(credentials, audience);
    assertEquals(mockToken, resolvedToken, "Should not double-prefix Bearer tokens");
  }
}
