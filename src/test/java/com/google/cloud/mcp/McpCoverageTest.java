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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.google.cloud.mcp.auth.AuthTokenGetter;
import com.google.cloud.mcp.client.McpToolboxClientImpl;
import com.google.cloud.mcp.exception.McpException;
import com.google.cloud.mcp.tool.Tool;
import com.google.cloud.mcp.tool.ToolDefinition;
import com.google.cloud.mcp.tool.ToolResult;
import com.google.cloud.mcp.transport.Transport;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Miscellaneous unit tests to achieve 100% code coverage. */
@Timeout(5)
public class McpCoverageTest {

  @Test
  public void testMcpExceptionCoverage() {
    McpException ex = new McpException("error message", new RuntimeException("cause"));
    assertEquals("error message", ex.getMessage());
    assertEquals("cause", ex.getCause().getMessage());
  }

  @Test
  public void testMcpToolboxClientDefaultClose() {
    McpToolboxClient dummyClient =
        new McpToolboxClient() {
          @Override
          public CompletableFuture<Map<String, ToolDefinition>> listTools() {
            return null;
          }

          @Override
          public CompletableFuture<Map<String, ToolDefinition>> loadToolset(String name) {
            return null;
          }

          @Override
          public CompletableFuture<Map<String, Tool>> loadToolset(
              String name,
              Map<String, Map<String, Object>> p,
              Map<String, Map<String, AuthTokenGetter>> a,
              boolean s) {
            return null;
          }

          @Override
          public CompletableFuture<Tool> loadTool(String name) {
            return null;
          }

          @Override
          public CompletableFuture<Tool> loadTool(
              String name, Map<String, AuthTokenGetter> getters) {
            return null;
          }

          @Override
          public CompletableFuture<ToolResult> invokeTool(String name, Map<String, Object> args) {
            return null;
          }

          @Override
          public CompletableFuture<ToolResult> invokeTool(
              String name, Map<String, Object> args, Map<String, String> headers) {
            return null;
          }
        };
    // Call default close (no-op)
    dummyClient.close();
  }

  @Test
  public void testMcpToolboxClientImplCloseThrowsException() throws Exception {
    Transport mockTransport = mock(Transport.class);
    doThrow(new RuntimeException("transport close error")).when(mockTransport).close();

    McpToolboxClientImpl client = new McpToolboxClientImpl(mockTransport, java.util.Map.of(), null);
    McpException ex = assertThrows(McpException.class, client::close);
    assertEquals("Failed to close transport", ex.getMessage());
    assertEquals("transport close error", ex.getCause().getMessage());
  }

  @Test
  public void testProtocolVersionFromString() {
    assertNull(ProtocolVersion.fromString(null));
    assertNull(ProtocolVersion.fromString("invalid-version-string"));
    assertEquals(ProtocolVersion.VERSION_2025_11_25, ProtocolVersion.fromString("2025-11-25"));
  }
}
