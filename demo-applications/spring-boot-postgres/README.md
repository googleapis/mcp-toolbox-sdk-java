# Spring Boot MCP Toolbox PostgreSQL Example 🚀

This sample application demonstrates how to build a modern **Spring Boot 3** microservice integrated with the **MCP Toolbox Java SDK** (`com.google.cloud.mcp:mcp-toolbox-sdk-java`).

The application connects to an official [MCP Toolbox](https://github.com/googleapis/mcp-toolbox) server running in Docker, which exposes prebuilt database tools (`execute_sql`, `list_tables`, `database_overview`, etc.) backed by a live **PostgreSQL** instance.

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
│  --prebuilt postgres            │
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
