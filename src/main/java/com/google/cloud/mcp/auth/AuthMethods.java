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
import com.google.auth.oauth2.IdTokenProvider;
import java.io.IOException;
import java.util.Collections;

/** Utility methods for fetching OIDC credentials. */
public final class AuthMethods {
  private AuthMethods() {}

  /**
   * Fetches a Google ID token for the given audience using the provided credentials.
   *
   * @param credentials The Google credentials.
   * @param audience The audience for the ID token.
   * @return The token prefixed with "Bearer ".
   * @throws IOException If credentials refresh fails.
   */
  public static String getGoogleIdToken(GoogleCredentials credentials, String audience)
      throws IOException {
    if (credentials == null) {
      throw new IllegalArgumentException("Credentials must not be null");
    }
    credentials.refreshIfExpired();
    if (credentials instanceof IdTokenProvider) {
      String token =
          ((IdTokenProvider) credentials)
              .idTokenWithAudience(audience, Collections.emptyList())
              .getTokenValue();
      return token.startsWith("Bearer ") ? token : "Bearer " + token;
    }
    throw new IllegalArgumentException("Credentials are not an instance of IdTokenProvider");
  }
}
