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

public class JsonRpc {
  public static class Request {
    public String jsonrpc = "2.0";
    public String id;
    public String method;
    public Object params;

    public Request(final String method, final Object params) {
      this.id = UUID.randomUUID().toString();
      this.method = method;
      this.params = params;
    }
  }

  public static class Notification {
    public String jsonrpc = "2.0";
    public String method;
    public Object params;

    public Notification(final String method, final Object params) {
      this.method = method;
      this.params = params;
    }
  }

  public static class RequestMetadata {
    public String traceparent;
    public String tracestate;

    public RequestMetadata(String traceparent, String tracestate) {
      this.traceparent = traceparent;
      this.tracestate = tracestate;
    }
  }

  /** Parameters for calling a tool. */
  public static class CallToolParams {
    public String name;
    public Map<String, Object> arguments;
    public RequestMetadata _meta;

    public CallToolParams(final String name, final Map<String, Object> arguments) {
      this(name, arguments, null);
    }

    public CallToolParams(String name, Map<String, Object> arguments, RequestMetadata meta) {
      this.name = name;
      this.arguments = arguments;
      this._meta = meta;
    }
  }

  public static class ListToolsParams {
    public String cursor;
    public RequestMetadata _meta;

    public ListToolsParams(String cursor, RequestMetadata meta) {
      this.cursor = cursor;
      this._meta = meta;
    }
  }

  public static class InitializeParams {
    public String protocolVersion;
    public Map<String, Object> capabilities;
    public Map<String, String> clientInfo;
    public RequestMetadata _meta;

    public InitializeParams(final String version, final String clientName) {
      this(version, clientName, null);
    }

    public InitializeParams(String version, String clientName, RequestMetadata meta) {
      this.protocolVersion = version;
      this.capabilities = Map.of();
      this.clientInfo = Map.of("name", clientName, "version", "1.0.0");
      this._meta = meta;
    }
  }
}
