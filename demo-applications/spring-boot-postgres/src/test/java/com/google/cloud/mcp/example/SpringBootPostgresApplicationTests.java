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
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
 * End-to-end hermetic integration tests verifying Spring Boot integration with the MCP Toolbox Java
 * SDK, connecting to a containerized MCP Toolbox server and PostgreSQL instance.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class SpringBootPostgresApplicationTests {

  @Autowired private McpToolboxClient mcpToolboxClient;

  @Autowired private ProductCatalogService catalogService;

  @Autowired private TestRestTemplate restTemplate;

  @Test
  @DisplayName("Context loads and McpToolboxClient bean is created")
  void testContextLoadsAndClientBeanConfigured() {
    assertNotNull(mcpToolboxClient, "McpToolboxClient bean should be present in context");
    assertNotNull(catalogService, "ProductCatalogService bean should be present in context");
  }

  @Test
  @DisplayName("SDK client discovers PostgreSQL tools from MCP Toolbox")
  void testToolDiscovery_ContainsPostgresTools() {
    Map<String, ToolDefinition> tools = mcpToolboxClient.listTools().join();
    assertNotNull(tools, "Discovered tools map should not be null");
    assertThat(tools).isNotEmpty();
    assertThat(tools.keySet())
        .contains(
            "get-all-products",
            "get-product-by-id",
            "add-product",
            "delete-product-by-id",
            "list_tables",
            "get-table-schema");
  }

  @Test
  @DisplayName("Service retrieves seeded products via get-all-products tool")
  void testQueryProducts_ReturnsSeededItems() {
    List<Product> products = catalogService.getAllProducts().join();
    assertNotNull(products, "Products list should not be null");
    assertThat(products).hasSizeGreaterThanOrEqualTo(3);

    List<String> names = products.stream().map(Product::name).toList();
    assertThat(names)
        .contains("Quantum Laptop", "Ergonomic Mechanical Keyboard", "Noise Cancelling Headphones");
  }

  @Test
  @DisplayName("Service persists new product via add-product tool and queries it back")
  void testInsertProduct_PersistsAndCanBeQueried() {
    String uniqueItemName = "Gaming Mouse " + UUID.randomUUID().toString().substring(0, 8);
    Product created = catalogService.addProduct(uniqueItemName, "Gaming", 79.99, 50).join();
    assertNotNull(created);
    assertThat(created.id()).isNotNull();

    Product queried = catalogService.getProductById(created.id().intValue()).join();
    assertNotNull(queried);
    assertThat(queried.name()).isEqualTo(uniqueItemName);
    assertThat(queried.category()).isEqualTo("Gaming");
  }

  @Test
  @DisplayName("Service introspects table schema via get-table-schema tool")
  void testListTables_DiscoversProductsTable() throws Exception {
    String schemaOutput = catalogService.getTableSchema("products").join();
    assertNotNull(schemaOutput, "Schema output should not be null");
    JsonNode root = new ObjectMapper().readTree(schemaOutput);
    assertThat(root.isContainerNode()).isTrue();
    if (root.isArray()) {
      assertThat(root.size()).isGreaterThan(0);
      assertThat(root.get(0).path("table_name").asText()).isEqualTo("products");
    } else {
      assertThat(root.path("table_name").asText()).isEqualTo("products");
    }
  }

  @Test
  @DisplayName("SDK client handles invalid tool execution gracefully")
  void testExecuteTool_InvalidArguments_HandlesErrorGracefully() {
    ToolResult result =
        mcpToolboxClient
            .invokeTool("get-product-by-id", Map.of("id", "invalid_non_numeric_id"))
            .join();

    assertNotNull(result, "ToolResult should not be null");
    assertTrue(result.isError(), "Result should report error flag for invalid argument type");
    assertThat(result.content()).isNotEmpty();
  }

  @Test
  @DisplayName("REST Controller GET /api/tools returns available tools")
  void testRestController_GetTools() {
    ResponseEntity<String[]> response = restTemplate.getForEntity("/api/tools", String[].class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertNotNull(response.getBody());
    assertThat(response.getBody()).contains("get-all-products", "add-product");
  }

  @Test
  @DisplayName("REST Controller GET /api/products returns product list")
  void testRestController_GetProducts() {
    ResponseEntity<Product[]> response =
        restTemplate.getForEntity("/api/products", Product[].class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertNotNull(response.getBody());
    assertThat(response.getBody()).isNotEmpty();
  }

  @Test
  @DisplayName("REST Controller POST /api/products creates product with Location header and body")
  void testRestController_PostProduct() {
    String uniqueItemName = "Earbuds " + UUID.randomUUID().toString().substring(0, 8);
    Product newProduct = new Product(null, uniqueItemName, "Audio", 199.99, 85);

    ResponseEntity<Product> response =
        restTemplate.postForEntity("/api/products", newProduct, Product.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertNotNull(response.getBody());
    assertThat(response.getBody().id()).isNotNull();
    assertThat(response.getBody().name()).isEqualTo(uniqueItemName);

    assertNotNull(response.getHeaders().getLocation());
    assertThat(response.getHeaders().getLocation().getPath())
        .isEqualTo("/api/products/" + response.getBody().id());
  }

  @Test
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
  @DisplayName("REST Controller POST /api/products rejects null price with 400 Bad Request")
  void testRestController_PostProduct_NullPrice_ValidationFailure() {
    Product nullPriceProduct = new Product(null, "No Price Item", "Electronics", null, 10);
    HttpEntity<Product> request = new HttpEntity<>(nullPriceProduct);

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
  @DisplayName("Service handles null category by storing SQL NULL")
  void testInsertProduct_NullCategory_PersistsAsNull() {
    String itemName = "Uncategorized " + UUID.randomUUID().toString().substring(0, 8);
    Product created = catalogService.addProduct(itemName, null, 19.99, 10).join();
    assertNotNull(created);
    assertThat(created.category()).isNull();
  }

  @Test
  @DisplayName("REST Controller GET /api/products/{id} returns single product")
  void testRestController_GetProductById() {
    ResponseEntity<Product> response = restTemplate.getForEntity("/api/products/1", Product.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertNotNull(response.getBody());
    assertThat(response.getBody().id()).isEqualTo(1L);
    assertThat(response.getBody().name()).isEqualTo("Quantum Laptop");
  }

  @Test
  @DisplayName("REST Controller GET /api/products/category/{category} returns filtered products")
  void testRestController_GetProductsByCategory() {
    ResponseEntity<Product[]> response =
        restTemplate.getForEntity("/api/products/category/Electronics", Product[].class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertNotNull(response.getBody());
    assertThat(response.getBody()).isNotEmpty();
    for (Product p : response.getBody()) {
      assertThat(p.category()).isEqualTo("Electronics");
    }
  }

  @Test
  @DisplayName("REST Controller DELETE /api/products/{id} deletes product returning 204")
  void testRestController_DeleteProduct() {
    String tempName = "DeleteMe " + UUID.randomUUID().toString().substring(0, 8);
    Product created = catalogService.addProduct(tempName, "Temp", 15.0, 5).join();
    assertNotNull(created);
    Long id = created.id();

    ResponseEntity<Void> deleteResponse =
        restTemplate.exchange("/api/products/" + id, HttpMethod.DELETE, null, Void.class);
    assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    ResponseEntity<Product> getResponse =
        restTemplate.getForEntity("/api/products/" + id, Product.class);
    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
