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

package com.google.cloud.mcp.transport;

import java.util.Map;
import java.util.UUID;

/** Namespace for JSON-RPC 2.0 MC Protocol data structures. */
public class JsonRpc {
  private JsonRpc() {}

  /** Represents a JSON-RPC request. */
  public static class Request {
    /** The JSON-RPC version. */
    public String jsonrpc = "2.0";

    /** The request ID. */
    public String id;

    /** The method name. */
    public String method;

    /** The parameters. */
    public Object params;

    /**
     * Constructs a new Request.
     *
     * @param method The method name.
     * @param params The parameters.
     */
    public Request(final String method, final Object params) {
      this.id = UUID.randomUUID().toString();
      this.method = method;
      this.params = params;
    }
  }

  /** Represents a JSON-RPC notification. */
  public static class Notification {
    /** The JSON-RPC version. */
    public String jsonrpc = "2.0";

    /** The method name. */
    public String method;

    /** The parameters. */
    public Object params;

    /**
     * Constructs a new Notification.
     *
     * @param method The method name.
     * @param params The parameters.
     */
    public Notification(final String method, final Object params) {
      this.method = method;
      this.params = params;
    }
  }

  /** Parameters for calling a tool. */
  public static class CallToolParams {
    /** The name of the tool to call. */
    public String name;

    /** The arguments for the tool call. */
    public Map<String, Object> arguments;

    /**
     * Constructs a new CallToolParams.
     *
     * @param name The name of the tool.
     * @param arguments The arguments.
     */
    public CallToolParams(final String name, final Map<String, Object> arguments) {
      this.name = name;
      this.arguments = arguments;
    }
  }

  /** Parameters for initializing the connection. */
  public static class InitializeParams {
    /** The protocol version. */
    public String protocolVersion;

    /** The client capabilities. */
    public Map<String, Object> capabilities;

    /** The client info. */
    public Map<String, String> clientInfo;

    /**
     * Constructs a new InitializeParams.
     *
     * @param version The protocol version.
     * @param clientName The client name.
     */
    public InitializeParams(final String version, final String clientName) {
      this.protocolVersion = version;
      this.capabilities = Map.of();
      this.clientInfo = Map.of("name", clientName, "version", "1.0.0");
    }
  }
}
