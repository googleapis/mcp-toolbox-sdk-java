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

/** Exception thrown when communication or transport level errors occur. */
public class McpTransportException extends McpToolboxException {
  /** The HTTP status code associated with this error, or -1 if not applicable. */
  private final int statusCode;

  /**
   * Constructs a new McpTransportException with the specified detail message.
   *
   * @param message The detail message.
   */
  public McpTransportException(final String message) {
    super(message);
    this.statusCode = -1;
  }

  /**
   * Constructs a new McpTransportException with message and status code.
   *
   * @param message The detail message.
   * @param statusCode The HTTP status code.
   */
  public McpTransportException(final String message, final int statusCode) {
    super(message);
    this.statusCode = statusCode;
  }

  /**
   * Constructs a new McpTransportException with message and cause.
   *
   * @param message The detail message.
   * @param cause The cause of the exception.
   */
  public McpTransportException(final String message, final Throwable cause) {
    super(message, cause);
    this.statusCode = -1;
  }

  /**
   * Constructs a new McpTransportException with message, status code, and cause.
   *
   * @param message The detail message.
   * @param statusCode The HTTP status code.
   * @param cause The cause of the exception.
   */
  public McpTransportException(final String message, final int statusCode, final Throwable cause) {
    super(message, cause);
    this.statusCode = statusCode;
  }

  /**
   * Returns the HTTP status code associated with this error, or -1 if not applicable.
   *
   * @return The HTTP status code.
   */
  public int getStatusCode() {
    return statusCode;
  }
}
