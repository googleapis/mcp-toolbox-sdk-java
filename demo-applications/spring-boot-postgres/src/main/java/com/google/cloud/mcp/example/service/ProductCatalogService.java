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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.mcp.McpToolboxClient;
import com.google.cloud.mcp.example.model.Product;
import com.google.cloud.mcp.tool.ToolResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Service orchestrating product catalog operations against PostgreSQL via the MCP Toolbox SDK. */
@Service
public class ProductCatalogService {

  private static final Logger logger = LoggerFactory.getLogger(ProductCatalogService.class);

  private final McpToolboxClient client;
  private final ObjectMapper objectMapper;

  public ProductCatalogService(McpToolboxClient client, ObjectMapper objectMapper) {
    this.client = client;
    this.objectMapper = objectMapper;
  }

  /**
   * Discovers and lists all tools exposed by the MCP Toolbox server.
   *
   * @return CompletableFuture containing list of tool names.
   */
  public CompletableFuture<List<String>> listAvailableTools() {
    return client
        .listTools()
        .orTimeout(10, TimeUnit.SECONDS)
        .thenApplyAsync(
            tools -> {
              List<String> names = tools.keySet().stream().sorted().toList();
              logger.debug("Discovered {} tools: {}", names.size(), names);
              return names;
            });
  }

  /**
   * Retrieves all products from the PostgreSQL database using the 'get-all-products' tool.
   *
   * @return CompletableFuture containing list of {@link Product} objects.
   */
  public CompletableFuture<List<Product>> getAllProducts() {
    logger.debug("Invoking 'get-all-products' tool via MCP");
    return client
        .invokeTool("get-all-products", Collections.emptyMap())
        .orTimeout(10, TimeUnit.SECONDS)
        .thenApplyAsync(this::parseProductsResult);
  }

  /**
   * Retrieves a single product by its unique identifier using the 'get-product-by-id' tool.
   *
   * @param id The product ID (must be positive).
   * @return CompletableFuture containing {@link Product} or null if not found.
   */
  public CompletableFuture<Product> getProductById(int id) {
    if (id <= 0) {
      return CompletableFuture.failedFuture(
          new IllegalArgumentException("Product ID must be positive"));
    }
    logger.debug("Invoking 'get-product-by-id' tool via MCP for id: {}", id);
    return client
        .invokeTool("get-product-by-id", Map.of("id", id))
        .orTimeout(10, TimeUnit.SECONDS)
        .thenApplyAsync(this::parseProductsResult)
        .thenApply(products -> products.isEmpty() ? null : products.get(0));
  }

  /**
   * Retrieves products in a category using the 'get-products-by-category' tool.
   *
   * @param category The category name (required, non-blank).
   * @return CompletableFuture containing list of {@link Product} objects.
   */
  public CompletableFuture<List<Product>> getProductsByCategory(String category) {
    if (category == null || category.trim().isEmpty()) {
      return CompletableFuture.failedFuture(
          new IllegalArgumentException("Product category cannot be null or empty"));
    }
    String trimmedCategory = category.trim();
    logger.debug("Invoking 'get-products-by-category' tool via MCP for: {}", trimmedCategory);
    return client
        .invokeTool("get-products-by-category", Map.of("category", trimmedCategory))
        .orTimeout(10, TimeUnit.SECONDS)
        .thenApplyAsync(this::parseProductsResult);
  }

  /**
   * Inserts a new product into the database using the 'add-product' declarative tool.
   *
   * @param name Name of the product (required, non-blank, max 100 characters).
   * @param category Category of the product (optional, max 50 characters).
   * @param price Price of the product (must be non-negative, finite number).
   * @param stock Stock quantity (must be non-negative).
   * @return CompletableFuture containing the newly persisted {@link Product}.
   */
  public CompletableFuture<Product> addProduct(
      String name, String category, double price, int stock) {
    if (name == null || name.trim().isEmpty()) {
      return CompletableFuture.failedFuture(
          new IllegalArgumentException("Product name cannot be null or empty"));
    }
    String trimmedName = name.trim();
    if (trimmedName.length() > 100) {
      return CompletableFuture.failedFuture(
          new IllegalArgumentException("Product name cannot exceed 100 characters"));
    }

    String trimmedCategory = category != null ? category.trim() : null;
    if (trimmedCategory != null && trimmedCategory.length() > 50) {
      return CompletableFuture.failedFuture(
          new IllegalArgumentException("Product category cannot exceed 50 characters"));
    }

    if (Double.isNaN(price) || Double.isInfinite(price) || price < 0.0) {
      return CompletableFuture.failedFuture(
          new IllegalArgumentException("Product price must be a valid finite non-negative number"));
    }
    if (stock < 0) {
      return CompletableFuture.failedFuture(
          new IllegalArgumentException("Product stock must be non-negative"));
    }

    Map<String, Object> arguments = new HashMap<>();
    arguments.put("name", trimmedName);
    if (trimmedCategory != null && !trimmedCategory.isEmpty()) {
      arguments.put("category", trimmedCategory);
    }
    arguments.put("price", price);
    arguments.put("stock", stock);

    logger.debug("Invoking 'add-product' tool via MCP for: {}", trimmedName);

    return client
        .invokeTool("add-product", arguments)
        .orTimeout(10, TimeUnit.SECONDS)
        .thenApplyAsync(this::parseProductsResult)
        .thenApply(
            products -> {
              if (products.isEmpty()) {
                throw new IllegalStateException("Insert succeeded but no product record returned");
              }
              Product created = products.get(0);
              logger.info(
                  "Product successfully persisted with id {}: {}", created.id(), trimmedName);
              return created;
            });
  }

  /**
   * Deletes a product from the database using the 'delete-product-by-id' tool.
   *
   * @param id The product ID (must be positive).
   * @return CompletableFuture containing true if deleted, false otherwise.
   */
  public CompletableFuture<Boolean> deleteProductById(int id) {
    if (id <= 0) {
      return CompletableFuture.failedFuture(
          new IllegalArgumentException("Product ID must be positive"));
    }
    logger.debug("Invoking 'delete-product-by-id' tool via MCP for id: {}", id);
    return client
        .invokeTool("delete-product-by-id", Map.of("id", id))
        .orTimeout(10, TimeUnit.SECONDS)
        .thenApply(
            result -> {
              if (result.isError()) {
                String errorMsg = extractErrorMessage(result);
                logger.error("Failed to delete product {}: {}", id, errorMsg);
                throw new IllegalStateException("Tool execution failed: " + errorMsg);
              }
              if (result.content() != null && !result.content().isEmpty()) {
                String text = result.content().get(0).text();
                return text != null && text.contains("\"id\"");
              }
              return false;
            });
  }

  /**
   * Introspects table existence and metadata using declarative schema tools.
   *
   * @param tableName Name of the table to introspect.
   * @return CompletableFuture containing table introspection metadata string.
   */
  public CompletableFuture<String> getTableSchema(String tableName) {
    logger.debug("Inspecting table metadata for: {}", tableName);
    boolean hasTable = tableName != null && !tableName.trim().isEmpty();
    String toolName = hasTable ? "get-table-schema" : "list_tables";
    Map<String, Object> args =
        hasTable ? Map.of("table_name", tableName.trim()) : Collections.emptyMap();

    return client
        .invokeTool(toolName, args)
        .orTimeout(10, TimeUnit.SECONDS)
        .thenApply(
            result -> {
              if (result.isError()) {
                throw new IllegalStateException(
                    "Schema inspection failed: " + extractErrorMessage(result));
              }
              if (result.content() != null && !result.content().isEmpty()) {
                return result.content().get(0).text();
              }
              return "{}";
            });
  }

  private List<Product> parseProductsResult(ToolResult result) {
    if (result.isError()) {
      String errorMsg = extractErrorMessage(result);
      logger.error("Tool query returned error: {}", errorMsg);
      throw new IllegalStateException("Query failed: " + errorMsg);
    }

    if (result.content() == null || result.content().isEmpty()) {
      return Collections.emptyList();
    }

    List<Product> products = new ArrayList<>();
    for (var content : result.content()) {
      String rawJson = content.text();
      if (rawJson != null && !rawJson.trim().isEmpty()) {
        String trimmed = rawJson.trim();
        try {
          if (trimmed.startsWith("[")) {
            Product[] parsedArray = objectMapper.readValue(trimmed, Product[].class);
            Collections.addAll(products, parsedArray);
          } else {
            Product product = objectMapper.readValue(trimmed, Product.class);
            products.add(product);
          }
        } catch (JsonProcessingException e) {
          logger.error("Could not deserialize content as Product: {}", trimmed, e);
          throw new IllegalStateException(
              "Failed to deserialize product catalog payload: " + e.getMessage(), e);
        }
      }
    }
    return products;
  }

  private String extractErrorMessage(ToolResult result) {
    if (result.content() != null && !result.content().isEmpty()) {
      return result.content().get(0).text();
    }
    return "Unknown error";
  }
}
