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

import java.util.concurrent.CompletableFuture;

/**
 * An implementation of CredentialsProvider that uses Google Application Default Credentials
 * to fetch OIDC ID tokens.
 */
public class GoogleCredentialsProvider implements CredentialsProvider {
  private final String audience;

  public GoogleCredentialsProvider(String audience) {
    if (audience == null || audience.isEmpty()) {
      throw new IllegalArgumentException("Audience must not be null or empty");
    }
    this.audience = audience;
  }

  @Override
  public CompletableFuture<String> getAuthorizationHeader() {
    return AuthMethods.getGoogleIdToken(audience)
        .handle((token, ex) -> {
          if (ex != null) {
            // ADC not available or not OIDC-compatible. Proceed without global auth.
            return null;
          }
          return token;
        });
  }
}
