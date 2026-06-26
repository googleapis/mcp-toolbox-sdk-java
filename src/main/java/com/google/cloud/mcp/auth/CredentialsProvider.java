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

import java.util.concurrent.CompletableFuture;

/** Functional interface for supplying the Authorization header. */
@FunctionalInterface
public interface CredentialsProvider {
  /**
   * Retrieves the Authorization header value (e.g. "Bearer {@code <token>}").
   *
   * @return A CompletableFuture containing the full Authorization header value.
   */
  CompletableFuture<String> getAuthorizationHeader();
}
