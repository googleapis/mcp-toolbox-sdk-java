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

package com.google.cloud.mcp.example.controller;

import com.google.cloud.mcp.example.model.Product;
import com.google.cloud.mcp.example.service.ProductCatalogService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller exposing product catalog and MCP tool inspection APIs. */
@RestController
@RequestMapping("/api")
public class ProductController {

  private final ProductCatalogService catalogService;

  public ProductController(ProductCatalogService catalogService) {
    this.catalogService = catalogService;
  }

  /**
   * Returns list of tools available on the MCP Toolbox server.
   *
   * @return List of tool names.
   */
  @GetMapping("/tools")
  public CompletableFuture<ResponseEntity<List<String>>> getAvailableTools() {
    return catalogService.listAvailableTools().thenApply(tools -> ResponseEntity.ok(tools));
  }

  /**
   * Returns all products in the database.
   *
   * @return List of products.
   */
  @GetMapping("/products")
  public CompletableFuture<ResponseEntity<List<Product>>> getAllProducts() {
    return catalogService.getAllProducts().thenApply(products -> ResponseEntity.ok(products));
  }

  /**
   * Creates a new product.
   *
   * @param product Product details.
   * @return HTTP 201 Created.
   */
  @PostMapping("/products")
  public CompletableFuture<ResponseEntity<Void>> createProduct(@RequestBody Product product) {
    return catalogService
        .addProduct(
            product.name(),
            product.category(),
            product.price() != null ? product.price() : 0.0,
            product.stock() != null ? product.stock() : 0)
        .thenApply(v -> ResponseEntity.status(HttpStatus.CREATED).build());
  }

  /**
   * Returns database table schema.
   *
   * @param tableName Name of the table.
   * @return Schema JSON string.
   */
  @GetMapping("/schema/{tableName}")
  public CompletableFuture<ResponseEntity<String>> getTableSchema(@PathVariable String tableName) {
    return catalogService.getTableSchema(tableName).thenApply(schema -> ResponseEntity.ok(schema));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
    String message = ex.getMessage() != null ? ex.getMessage() : "Invalid request argument";
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", message));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
    String message = ex.getMessage() != null ? ex.getMessage() : "Internal server state error";
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", message));
  }
}
