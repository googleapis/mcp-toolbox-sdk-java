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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Handles concurrent resolution of token getters into a {@link ResolvedAuth} instance. */
public final class AuthResolver {
  private AuthResolver() {}

  /**
   * Concurrently resolves all registered token getters.
   *
   * @param getters The map of service name to token getter.
   * @return A CompletableFuture containing the resolved auth object.
   */
  public static CompletableFuture<ResolvedAuth> resolve(Map<String, AuthTokenGetter> getters) {
    if (getters.isEmpty()) {
      return CompletableFuture.completedFuture(new ResolvedAuth(Map.of()));
    }

    var entries = List.copyOf(getters.entrySet());
    var futures = entries.stream().map(entry -> entry.getValue().getToken()).toList();

    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
        .thenApply(
            v -> {
              Map<String, String> resolved = new HashMap<>();
              for (int i = 0; i < entries.size(); i++) {
                String token = futures.get(i).join();
                if (token != null) {
                  resolved.put(entries.get(i).getKey(), token);
                }
              }
              return new ResolvedAuth(resolved);
            });
  }
}
