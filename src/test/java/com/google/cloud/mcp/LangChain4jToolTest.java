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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

public class LangChain4jToolTest {

  @Test
  public void testSpecification() {
    Tool mockTool = mock(Tool.class);
    ToolDefinition mockDef = mock(ToolDefinition.class);
    when(mockTool.name()).thenReturn("test-tool");
    when(mockTool.definition()).thenReturn(mockDef);
    when(mockDef.description()).thenReturn("test-description");

    LangChain4jTool adapter = new LangChain4jTool(mockTool);
    assertNotNull(adapter.specification());
    assertEquals("test-tool", adapter.specification().name());
    assertEquals("test-description", adapter.specification().description());
  }
}
