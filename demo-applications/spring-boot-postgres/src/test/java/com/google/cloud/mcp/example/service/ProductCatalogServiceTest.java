/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.mcp.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.mcp.McpToolboxClient;
import com.google.cloud.mcp.example.model.Product;
import com.google.cloud.mcp.tool.ToolDefinition;
import com.google.cloud.mcp.tool.ToolResult;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
class ProductCatalogServiceTest {

  private McpToolboxClient mockClient;
  private ObjectMapper objectMapper;
  private ProductCatalogService service;

  @BeforeEach
  void setUp() {
    mockClient = mock(McpToolboxClient.class);
    objectMapper = new ObjectMapper();
    service = new ProductCatalogService(mockClient, objectMapper);
  }

  @Test
  @DisplayName("listAvailableTools sorts discovered tool names alphabetically")
  void testListAvailableTools_SortsAlphabetically() {
    ToolDefinition toolA = mock(ToolDefinition.class);
    ToolDefinition toolB = mock(ToolDefinition.class);
    ToolDefinition toolC = mock(ToolDefinition.class);

    when(mockClient.listTools())
        .thenReturn(
            CompletableFuture.completedFuture(
                Map.of("list_tables", toolC, "execute_sql", toolA, "database_overview", toolB)));

    List<String> tools = service.listAvailableTools().join();

    assertThat(tools).containsExactly("database_overview", "execute_sql", "list_tables");
  }

  @Test
  @DisplayName("getAllProducts parses individual row objects in ToolResult content")
  void testGetAllProducts_SingleObjects() {
    ToolResult result =
        new ToolResult(
            List.of(
                new ToolResult.Content(
                    "text",
                    "{\"id\":1,\"name\":\"Mouse\",\"category\":\"Accessories\",\"price\":29.99,\"stock\":100}"),
                new ToolResult.Content(
                    "text",
                    "{\"id\":2,\"name\":\"Keyboard\",\"category\":null,\"price\":89.99,\"stock\":50}")),
            false);

    when(mockClient.invokeTool(eq("execute_sql"), any()))
        .thenReturn(CompletableFuture.completedFuture(result));

    List<Product> products = service.getAllProducts().join();

    assertThat(products).hasSize(2);
    assertEquals("Mouse", products.get(0).name());
    assertEquals("Keyboard", products.get(1).name());
    assertNull(products.get(1).category());
  }

  @Test
  @DisplayName("getAllProducts parses single content containing a JSON array")
  void testGetAllProducts_JsonArray() {
    String jsonArray =
        "[{\"id\":10,\"name\":\"Monitor\",\"category\":\"Displays\",\"price\":299.99,\"stock\":15},{\"id\":11,\"name\":\"Desk"
            + " Lamp\",\"category\":\"Lighting\",\"price\":39.99,\"stock\":40}]";
    ToolResult result = new ToolResult(List.of(new ToolResult.Content("text", jsonArray)), false);

    when(mockClient.invokeTool(eq("execute_sql"), any()))
        .thenReturn(CompletableFuture.completedFuture(result));

    List<Product> products = service.getAllProducts().join();

    assertThat(products).hasSize(2);
    assertEquals("Monitor", products.get(0).name());
    assertEquals("Desk Lamp", products.get(1).name());
  }

  @Test
  @DisplayName("addProduct formats SQL with sanitized values and quotes")
  void testAddProduct_Valid_FormatsSqlWithCategory() {
    ToolResult successResult =
        new ToolResult(List.of(new ToolResult.Content("text", "INSERT 0 1")), false);
    when(mockClient.invokeTool(eq("execute_sql"), any()))
        .thenReturn(CompletableFuture.completedFuture(successResult));

    service.addProduct("O'Reilly Book", "Books", 49.99, 10).join();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(mockClient).invokeTool(eq("execute_sql"), captor.capture());

    String sql = (String) captor.getValue().get("sql");
    assertThat(sql).contains("VALUES ('O''Reilly Book', 'Books', 49.99, 10);");
  }

  @Test
  @DisplayName("addProduct formats null or empty category as SQL NULL literal")
  void testAddProduct_Valid_FormatsSqlWithNullCategory() {
    ToolResult successResult =
        new ToolResult(List.of(new ToolResult.Content("text", "INSERT 0 1")), false);
    when(mockClient.invokeTool(eq("execute_sql"), any()))
        .thenReturn(CompletableFuture.completedFuture(successResult));

    service.addProduct("Generic Item", null, 9.99, 5).join();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(mockClient).invokeTool(eq("execute_sql"), captor.capture());

    String sql = (String) captor.getValue().get("sql");
    assertThat(sql).contains("VALUES ('Generic Item', NULL, 9.99, 5);");
  }

  @Test
  @DisplayName("addProduct rejects invalid inputs with IllegalArgumentException")
  void testAddProduct_ValidationFailures() {
    // Null name
    assertValidationFailure(service.addProduct(null, "Cat", 10.0, 1), "name cannot be null");

    // Blank name
    assertValidationFailure(service.addProduct("   ", "Cat", 10.0, 1), "name cannot be null");

    // Name too long (> 100)
    assertValidationFailure(
        service.addProduct("A".repeat(101), "Cat", 10.0, 1), "cannot exceed 100");

    // Category too long (> 50)
    assertValidationFailure(
        service.addProduct("Valid", "B".repeat(51), 10.0, 1), "cannot exceed 50");

    // Negative price
    assertValidationFailure(service.addProduct("Valid", "Cat", -1.0, 1), "price must be a valid");

    // NaN price
    assertValidationFailure(
        service.addProduct("Valid", "Cat", Double.NaN, 1), "price must be a valid");

    // Infinite price
    assertValidationFailure(
        service.addProduct("Valid", "Cat", Double.POSITIVE_INFINITY, 1), "price must be a valid");

    // Negative stock
    assertValidationFailure(
        service.addProduct("Valid", "Cat", 10.0, -1), "stock must be non-negative");
  }

  private void assertValidationFailure(CompletableFuture<?> future, String expectedMessage) {
    CompletionException ex = assertThrows(CompletionException.class, future::join);
    assertThat(ex.getCause()).isInstanceOf(IllegalArgumentException.class);
    if (expectedMessage != null) {
      assertThat(ex.getCause().getMessage()).contains(expectedMessage);
    }
  }

  @Test
  @DisplayName("getTableSchema passes 'table_names' parameter to list_tables tool")
  void testGetTableSchema_PassesTableNamesParam() {
    ToolResult schemaResult =
        new ToolResult(List.of(new ToolResult.Content("text", "{\"table\":\"products\"}")), false);
    when(mockClient.invokeTool(eq("list_tables"), any()))
        .thenReturn(CompletableFuture.completedFuture(schemaResult));

    String schema = service.getTableSchema("products").join();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(mockClient).invokeTool(eq("list_tables"), captor.capture());

    assertEquals("products", captor.getValue().get("table_names"));
    assertThat(schema).contains("products");
  }

  @Test
  @DisplayName("Tool execution error throws IllegalStateException")
  void testToolExecutionError_ThrowsIllegalStateException() {
    ToolResult errorResult =
        new ToolResult(List.of(new ToolResult.Content("text", "SQL error syntax")), true);
    when(mockClient.invokeTool(eq("execute_sql"), any()))
        .thenReturn(CompletableFuture.completedFuture(errorResult));

    CompletionException ex =
        assertThrows(CompletionException.class, () -> service.getAllProducts().join());
    assertThat(ex.getCause()).isInstanceOf(IllegalStateException.class);
    assertThat(ex.getCause().getMessage()).contains("SQL error syntax");
  }
}
