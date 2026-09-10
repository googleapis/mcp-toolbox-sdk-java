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

package com.google.cloud.mcp.example;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.mcp.McpToolboxClient;
import com.google.cloud.mcp.example.model.Product;
import com.google.cloud.mcp.example.service.ProductCatalogService;
import com.google.cloud.mcp.tool.ToolDefinition;
import com.google.cloud.mcp.tool.ToolResult;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end integration tests verifying Spring Boot integration with the MCP Toolbox Java SDK,
 * connecting to a containerized MCP Toolbox server and PostgreSQL instance.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class SpringBootPostgresApplicationTests {

  @Autowired private McpToolboxClient mcpToolboxClient;

  @Autowired private ProductCatalogService catalogService;

  @Autowired private TestRestTemplate restTemplate;

  @Test
  @Order(1)
  @DisplayName("Context loads and McpToolboxClient bean is created")
  void testContextLoadsAndClientBeanConfigured() {
    assertNotNull(mcpToolboxClient, "McpToolboxClient bean should be present in context");
    assertNotNull(catalogService, "ProductCatalogService bean should be present in context");
  }

  @Test
  @Order(2)
  @DisplayName("SDK client discovers PostgreSQL tools from MCP Toolbox")
  void testToolDiscovery_ContainsPostgresTools() {
    Map<String, ToolDefinition> tools = mcpToolboxClient.listTools().join();
    assertNotNull(tools, "Discovered tools map should not be null");
    assertThat(tools).isNotEmpty();
    assertThat(tools.keySet()).contains("execute_sql", "list_tables", "database_overview");
  }

  @Test
  @Order(3)
  @DisplayName("Service retrieves seeded products via execute_sql tool")
  void testQueryProducts_ReturnsSeededItems() {
    List<Product> products = catalogService.getAllProducts().join();
    assertNotNull(products, "Products list should not be null");
    assertThat(products).hasSizeGreaterThanOrEqualTo(3);

    List<String> names = products.stream().map(Product::name).toList();
    assertThat(names)
        .contains("Quantum Laptop", "Ergonomic Mechanical Keyboard", "Noise Cancelling Headphones");
  }

  @Test
  @Order(4)
  @DisplayName("Service persists new product via execute_sql and queries it back")
  void testInsertProduct_PersistsAndCanBeQueried() {
    String testItemName = "High-Precision Gaming Mouse";
    catalogService.addProduct(testItemName, "Gaming", 79.99, 50).join();

    List<Product> updatedProducts = catalogService.getAllProducts().join();
    assertThat(updatedProducts.stream().map(Product::name).toList()).contains(testItemName);
  }

  @Test
  @Order(5)
  @DisplayName("Service introspects table schema via list_tables tool")
  void testListTables_DiscoversProductsTable() throws Exception {
    String schemaOutput = catalogService.getTableSchema("products").join();
    assertNotNull(schemaOutput, "Schema output should not be null");
    JsonNode root = new ObjectMapper().readTree(schemaOutput);
    assertThat(root.isContainerNode()).isTrue();
    assertThat(schemaOutput).contains("products");
  }

  @Test
  @Order(6)
  @DisplayName("SDK client handles invalid SQL execution gracefully")
  void testExecuteSql_InvalidSql_HandlesErrorGracefully() {
    ToolResult result =
        mcpToolboxClient
            .invokeTool(
                "execute_sql", Map.of("sql", "SELECT * FROM non_existent_test_table_12345;"))
            .join();

    assertNotNull(result, "ToolResult should not be null");
    assertTrue(result.isError(), "Result should report error flag for invalid table query");
    assertThat(result.content()).isNotEmpty();
  }

  @Test
  @Order(7)
  @DisplayName("REST Controller GET /api/tools returns available tools")
  void testRestController_GetTools() {
    ResponseEntity<String[]> response = restTemplate.getForEntity("/api/tools", String[].class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertNotNull(response.getBody());
    assertThat(response.getBody()).contains("execute_sql");
  }

  @Test
  @Order(8)
  @DisplayName("REST Controller GET /api/products returns product list")
  void testRestController_GetProducts() {
    ResponseEntity<Product[]> response =
        restTemplate.getForEntity("/api/products", Product[].class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertNotNull(response.getBody());
    assertThat(response.getBody()).isNotEmpty();
  }

  @Test
  @Order(9)
  @DisplayName("REST Controller POST /api/products creates a product")
  void testRestController_PostProduct() {
    Product newProduct = new Product(null, "Wireless Earbuds Pro", "Audio", 199.99, 85);

    ResponseEntity<Void> response =
        restTemplate.postForEntity("/api/products", newProduct, Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    List<Product> products = catalogService.getAllProducts().join();
    assertThat(products.stream().map(Product::name).toList()).contains("Wireless Earbuds Pro");
  }

  @Test
  @Order(10)
  @DisplayName("REST Controller POST /api/products rejects invalid product with 400 Bad Request")
  void testRestController_PostProduct_ValidationFailure() {
    Product invalidProduct = new Product(null, "", "Electronics", -10.0, -5);
    HttpEntity<Product> request = new HttpEntity<>(invalidProduct);

    ResponseEntity<Map<String, String>> response =
        restTemplate.exchange(
            "/api/products",
            HttpMethod.POST,
            request,
            new ParameterizedTypeReference<Map<String, String>>() {});

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertNotNull(response.getBody());
    assertThat(response.getBody()).containsKey("error");
  }

  @Test
  @Order(11)
  @DisplayName("Service handles null category by storing SQL NULL")
  void testInsertProduct_NullCategory_PersistsAsNull() {
    String itemName = "Uncategorized Item";
    catalogService.addProduct(itemName, null, 19.99, 10).join();

    List<Product> products = catalogService.getAllProducts().join();
    Product found =
        products.stream().filter(p -> itemName.equals(p.name())).findFirst().orElse(null);
    assertNotNull(found);
    assertThat(found.category()).isNull();
  }
}
