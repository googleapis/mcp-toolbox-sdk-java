# Spring Boot MCP Toolbox PostgreSQL Example 🚀

This sample application demonstrates how to build a modern **Spring Boot 3** microservice integrated with the **MCP Toolbox Java SDK** (`com.google.cloud.mcp:mcp-toolbox-sdk-java`).

The application connects to an official [MCP Toolbox](https://github.com/googleapis/mcp-toolbox) server running in Docker, configured with a custom declarative **`tools.yaml`** that defines domain-specific database tools (`get-all-products`, `get-product-by-id`, `get-products-by-category`, `add-product`, `delete-product-by-id`, `list_tables`, `get-table-schema`) backed by a live **PostgreSQL** instance.

---

## Architecture Overview

```
┌─────────────────────────────────┐
│ Spring Boot 3 Application       │
│ (Port 8080)                     │
│  - ProductController (REST API) │
│  - ProductCatalogService        │
│  - McpToolboxClient (Java SDK)  │
└────────────────┬────────────────┘
                 │ HTTP (JSON-RPC 2.0 /mcp)
                 ▼
┌─────────────────────────────────┐
│ MCP Toolbox Server (Docker)     │
│ (Port 5005:5000)                │
│  --config /tools.yaml           │
│  (Custom declarative tools)     │
└────────────────┬────────────────┘
                 │ TCP (5432)
                 ▼
┌─────────────────────────────────┐
│ PostgreSQL 15 (Docker)          │
│ (Port 5433:5432, DB: mcpdb)     │
│  - Table: products              │
└─────────────────────────────────┘
```

---

## Custom Declarative `tools.yaml`

Rather than exposing arbitrary raw SQL execution, the application defines domain-specific tools declaratively in [`tools.yaml`](./tools.yaml):

| Tool Name | Parameters | Description |
| :--- | :--- | :--- |
| `get-all-products` | None | Retrieves all products ordered by ID. |
| `get-product-by-id` | `id` (integer) | Retrieves a single product by its ID. |
| `get-products-by-category` | `category` (string) | Retrieves products filtered by category. |
| `add-product` | `name` (string), `category` (string, optional), `price` (float), `stock` (integer) | Inserts a product using parameterized queries (`$1, $2, $3, $4`) and returns the created record via `RETURNING`. |
| `delete-product-by-id` | `id` (integer) | Deletes a product by ID. |
| `list_tables` | None | Lists public tables from `information_schema.tables`. |
| `get-table-schema` | `table_name` (string) | Introspects column types and nullability for a table. |

### Why Declarative Tools?
1. **Parameterized Security**: Toolbox binds arguments via PostgreSQL prepared statement parameters (`$1`, `$2`), eliminating SQL injection risks.
2. **Schema & Validation**: Input types, required parameters, and descriptions are enforced at the MCP layer before hitting the database.
3. **Domain Abstraction**: LLMs and microservices interact with clean business tools rather than raw SQL commands.

---

## Prerequisites

- **Java 17+** (JDK 17, 21, or 26)
- **Maven 3.9+**
- **Docker** container engine

---

## Quickstart

### 1. Start the Docker Containers

Run the automated startup script to launch PostgreSQL and MCP Toolbox:

```bash
./scripts/start-containers.sh
```

This script:
1. Spawns a PostgreSQL container (`mcp-postgres`) on port `5433`.
2. Seeds a sample `products` table with initial records.
3. Spawns the MCP Toolbox server (`mcp-toolbox`) linked to PostgreSQL on port `5005`.
4. Waits until the server responds to tool discovery.

### 2. Run the Test Suite

Execute the integration test suite:

```bash
mvn clean test -Dnet.bytebuddy.experimental=true
```

### 3. Run the Spring Boot Application

```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8080`.

---

## REST Endpoints

### 1. List Available MCP Tools
```bash
curl -s http://localhost:8080/api/tools | jq .
```

### 2. Get All Products
```bash
curl -s http://localhost:8080/api/products | jq .
```

### 3. Create a New Product
```bash
curl -s -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Ultra HD Monitor 34-inch",
    "category": "Electronics",
    "price": 649.99,
    "stock": 30
  }'
```

### 4. Inspect Table Schema
```bash
curl -s http://localhost:8080/api/schema/products | jq .
```

---

## Teardown

To shut down and remove the test containers:

```bash
./scripts/stop-containers.sh
```
