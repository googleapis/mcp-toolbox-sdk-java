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

/** Supported protocol versions for the Model Context Protocol. */
public enum ProtocolVersion {
  /** Protocol version 2026-06-18. */
  VERSION_2026_06_18("2026-06-18", true, true, false),

  /** Protocol version 2025-11-25. */
  VERSION_2025_11_25("2025-11-25", true, true, false),

  /** Protocol version 2025-06-18. */
  VERSION_2025_06_18("2025-06-18", true, true, false),

  /** Protocol version 2025-03-26. */
  VERSION_2025_03_26("2025-03-26", true, false, true),

  /** Protocol version 2024-11-05. */
  VERSION_2024_11_05("2024-11-05", false, false, false);

  private final String value;
  private final boolean requiresAcceptJson;
  private final boolean requiresVersionHeader;
  private final boolean requiresSessionIdHeader;

  ProtocolVersion(
      String value,
      boolean requiresAcceptJson,
      boolean requiresVersionHeader,
      boolean requiresSessionIdHeader) {
    this.value = value;
    this.requiresAcceptJson = requiresAcceptJson;
    this.requiresVersionHeader = requiresVersionHeader;
    this.requiresSessionIdHeader = requiresSessionIdHeader;
  }

  /**
   * Gets the string representation of the version (e.g. "2025-11-25").
   *
   * @return String version representation.
   */
  public String getValue() {
    return value;
  }

  /**
   * Returns true if this version requires the "Accept: application/json" header.
   *
   * @return true if Accept: application/json is required.
   */
  public boolean requiresAcceptJson() {
    return requiresAcceptJson;
  }

  /**
   * Returns true if this version requires the "MCP-Protocol-Version" header.
   *
   * @return true if MCP-Protocol-Version header is required.
   */
  public boolean requiresVersionHeader() {
    return requiresVersionHeader;
  }

  /**
   * Returns true if this version requires the stateful "Mcp-Session-Id" header.
   *
   * @return true if Mcp-Session-Id header is required.
   */
  public boolean requiresSessionIdHeader() {
    return requiresSessionIdHeader;
  }

  /**
   * Resolves a ProtocolVersion from its string representation.
   *
   * @param versionStr The string representation of the version.
   * @return The corresponding ProtocolVersion, or null if unsupported.
   */
  public static ProtocolVersion fromString(String versionStr) {
    if (versionStr == null) {
      return null;
    }
    for (ProtocolVersion v : values()) {
      if (v.value.equals(versionStr)) {
        return v;
      }
    }
    return null;
  }
}
