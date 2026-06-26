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

/** Exception thrown when the MCP Server requests a protocol version fallback/negotiation. */
public final class McpProtocolNegotiationException extends McpException {

  private final String negotiatedVersion;

  /**
   * Constructs a new McpProtocolNegotiationException.
   *
   * @param message The detail message.
   * @param negotiatedVersion The negotiated protocol version (e.g. "2025-11-25").
   */
  public McpProtocolNegotiationException(String message, String negotiatedVersion) {
    super(message);
    this.negotiatedVersion = negotiatedVersion;
  }

  /**
   * Gets the negotiated protocol version string.
   *
   * @return The protocol version string.
   */
  public String getNegotiatedVersion() {
    return negotiatedVersion;
  }
}
