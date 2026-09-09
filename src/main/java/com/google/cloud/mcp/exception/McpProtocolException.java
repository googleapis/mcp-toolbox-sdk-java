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

package com.google.cloud.mcp.exception;

/** Exception thrown when JSON-RPC or protocol level errors occur. */
public class McpProtocolException extends McpToolboxException {

  /**
   * Constructs a new McpProtocolException with the specified detail message.
   *
   * @param message The detail message.
   */
  public McpProtocolException(final String message) {
    super(message);
  }

  /**
   * Constructs a new McpProtocolException with the specified message and cause.
   *
   * @param message The detail message.
   * @param cause The cause of the exception.
   */
  public McpProtocolException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
