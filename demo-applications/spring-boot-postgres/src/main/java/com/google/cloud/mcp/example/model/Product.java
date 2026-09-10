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

package com.google.cloud.mcp.example.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data transfer record representing a product in the catalog.
 *
 * @param id Unique identifier of the product.
 * @param name Name of the product.
 * @param category Catalog category.
 * @param price Retail price.
 * @param stock Quantity in stock.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Product(
    @JsonProperty("id") Long id,
    @JsonProperty("name") String name,
    @JsonProperty("category") String category,
    @JsonProperty("price") Double price,
    @JsonProperty("stock") Integer stock) {}
