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

class JsonRpc {
  static class Request {
    public String jsonrpc = "2.0";
    public String id;
    public String method;
    public Object params;

    public Request(String method, Object params) {
      this.id = UUID.randomUUID().toString();
      this.method = method;
      this.params = params;
    }
  }

  static class Notification {
    public String jsonrpc = "2.0";
    public String method;
    public Object params;

    public Notification(String method, Object params) {
      this.method = method;
      this.params = params;
    }
  }

  static class CallToolParams {
    public String name;
    public Map<String, Object> arguments;

    public CallToolParams(String name, Map<String, Object> arguments) {
      this.name = name;
      this.arguments = arguments;
    }
  }

  static class InitializeParams {
    public String protocolVersion;
    public Map<String, Object> capabilities;
    public Map<String, String> clientInfo;

    public InitializeParams(String version, String clientName) {
      this.protocolVersion = version;
      this.capabilities = Map.of();
      this.clientInfo = Map.of("name", clientName, "version", "1.0.0");
    }
  }
}
