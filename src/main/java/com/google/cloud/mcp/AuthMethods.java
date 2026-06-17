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

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

/** Utility methods for fetching and caching OIDC credentials. */
public class AuthMethods {
  private static GoogleCredentials credentials;

  // Modifiable loader to enable unit testing without actual Google credential files
  static CredentialsLoader credentialsLoader = GoogleCredentials::getApplicationDefault;

  @FunctionalInterface
  interface CredentialsLoader {
    GoogleCredentials load() throws IOException;
  }

  static synchronized GoogleCredentials getCachedCredentials() throws IOException {
    if (credentials == null) {
      credentials = credentialsLoader.load();
    }
    return credentials;
  }

  /** Resets the cached credentials. Primarily used for unit testing. */
  public static synchronized void resetCredentialsCache() {
    credentials = null;
  }

  /**
   * Fetches a Google ID token for the given audience using Application Default Credentials.
   *
   * @param audience The audience for the ID token.
   * @return A CompletableFuture containing the token prefixed with "Bearer ".
   */
  public static CompletableFuture<String> getGoogleIdToken(String audience) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        GoogleCredentials creds = getCachedCredentials();
        creds.refreshIfExpired();
        if (creds instanceof IdTokenProvider) {
          String token = ((IdTokenProvider) creds)
              .idTokenWithAudience(audience, Collections.emptyList())
              .getTokenValue();
          return token.startsWith("Bearer ") ? token : "Bearer " + token;
        }
        throw new RuntimeException("Credentials are not an instance of IdTokenProvider");
      } catch (IOException e) {
        throw new RuntimeException("Failed to fetch Google ID token", e);
      }
    });
  }
}
