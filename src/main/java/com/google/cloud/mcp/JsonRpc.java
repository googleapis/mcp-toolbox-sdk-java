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

import java.util.Map;
import java.util.UUID;

/** Helper classes representing JSON-RPC requests, notifications, and parameters. */
public class JsonRpc {

  /** Hide default constructor. */
  private JsonRpc() {}

  /** Represents a JSON-RPC request with an ID. */
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

  /** Represents a JSON-RPC notification without an ID. */
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

  /** Represents telemetry metadata in JSON-RPC parameters. */
  public static class RequestMetadata {
    /** The traceparent header value. */
    public String traceparent;

    /** The tracestate header value. */
    public String tracestate;

    /**
     * Constructs a new RequestMetadata.
     *
     * @param traceparent The traceparent header value.
     * @param tracestate The tracestate header value.
     */
    public RequestMetadata(String traceparent, String tracestate) {
      this.traceparent = traceparent;
      this.tracestate = tracestate;
    }
  }

  /** Parameters for calling a tool. */
  public static class CallToolParams {
    /** The tool name. */
    public String name;

    /** The arguments. */
    public Map<String, Object> arguments;

    /** Telemetry metadata. */
    public RequestMetadata _meta;

    /**
     * Constructs a new CallToolParams without metadata.
     *
     * @param name The tool name.
     * @param arguments The arguments.
     */
    public CallToolParams(final String name, final Map<String, Object> arguments) {
      this(name, arguments, null);
    }

    /**
     * Constructs a new CallToolParams with metadata.
     *
     * @param name The tool name.
     * @param arguments The arguments.
     * @param meta The telemetry metadata.
     */
    public CallToolParams(String name, Map<String, Object> arguments, RequestMetadata meta) {
      this.name = name;
      this.arguments = arguments;
      this._meta = meta;
    }
  }

  /** Parameters for listing tools. */
  public static class ListToolsParams {
    /** The pagination cursor. */
    public String cursor;

    /** Telemetry metadata. */
    public RequestMetadata _meta;

    /**
     * Constructs a new ListToolsParams.
     *
     * @param cursor The pagination cursor.
     * @param meta The telemetry metadata.
     */
    public ListToolsParams(String cursor, RequestMetadata meta) {
      this.cursor = cursor;
      this._meta = meta;
    }
  }

  /** Parameters for initializing the connection. */
  public static class InitializeParams {
    /** The client protocol version. */
    public String protocolVersion;

    /** The client capabilities. */
    public Map<String, Object> capabilities;

    /** The client info. */
    public Map<String, String> clientInfo;

    /** Telemetry metadata. */
    public RequestMetadata _meta;

    /**
     * Constructs a new InitializeParams without metadata.
     *
     * @param version The protocol version.
     * @param clientName The client name.
     */
    public InitializeParams(final String version, final String clientName) {
      this(version, clientName, null);
    }

    /**
     * Constructs a new InitializeParams with metadata.
     *
     * @param version The protocol version.
     * @param clientName The client name.
     * @param meta The telemetry metadata.
     */
    public InitializeParams(String version, String clientName, RequestMetadata meta) {
      this.protocolVersion = version;
      this.capabilities = Map.of();
      this.clientInfo = Map.of("name", clientName, "version", "1.0.0");
      this._meta = meta;
    }
  }
}
