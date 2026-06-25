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

import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * An implementation of CredentialsProvider that uses Google Application Default Credentials to
 * fetch OIDC ID tokens.
 */
public class GoogleCredentialsProvider implements CredentialsProvider {
  private final String audience;
  private final CredentialsLoader credentialsLoader;
  private volatile GoogleCredentials credentials;

  @FunctionalInterface
  interface CredentialsLoader {
    GoogleCredentials load() throws IOException;
  }

  /**
   * Constructs a new GoogleCredentialsProvider with a specified audience.
   *
   * @param audience The OIDC token audience (typically the service URL).
   */
  public GoogleCredentialsProvider(String audience) {
    this(audience, GoogleCredentials::getApplicationDefault);
  }

  // Package-private constructor for unit testing
  GoogleCredentialsProvider(String audience, CredentialsLoader credentialsLoader) {
    if (audience == null || audience.isEmpty()) {
      throw new IllegalArgumentException("Audience must not be null or empty");
    }
    this.audience = audience;
    this.credentialsLoader = credentialsLoader;
  }

  private GoogleCredentials getCredentials() throws IOException {
    GoogleCredentials localRef = credentials;
    if (localRef == null) {
      synchronized (this) {
        localRef = credentials;
        if (localRef == null) {
          credentials = localRef = credentialsLoader.load();
        }
      }
    }
    return localRef;
  }

  @Override
  public CompletableFuture<String> getAuthorizationHeader() {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            GoogleCredentials creds = getCredentials();
            if (creds == null) {
              return null;
            }
            return AuthMethods.getGoogleIdToken(creds, audience);
          } catch (Exception e) {
            // ADC not available or not OIDC-compatible. Proceed without global auth.
            return null;
          }
        });
  }
}
