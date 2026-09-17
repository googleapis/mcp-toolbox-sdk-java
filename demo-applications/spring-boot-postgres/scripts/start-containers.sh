#!/usr/bin/env bash
# Copyright 2026 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -euo pipefail

NETWORK_NAME="${NETWORK_NAME:-mcp-network}"

echo "==> Cleaning up previous test containers and network if running..."
docker rm -f mcp-toolbox mcp-postgres 2>/dev/null || true
docker network rm "${NETWORK_NAME}" 2>/dev/null || true
docker network create "${NETWORK_NAME}"

POSTGRES_USER="${POSTGRES_USER:-mcpuser}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-mcppass}"
POSTGRES_DATABASE="${POSTGRES_DATABASE:-mcpdb}"

echo "==> Starting PostgreSQL container (port 5433:5432)..."
docker run -d --name mcp-postgres --network "${NETWORK_NAME}" --network-alias postgres -p 5433:5432 \
  -e POSTGRES_USER="${POSTGRES_USER}" \
  -e POSTGRES_PASSWORD="${POSTGRES_PASSWORD}" \
  -e POSTGRES_DB="${POSTGRES_DATABASE}" \
  postgres:15-alpine

echo "==> Waiting for PostgreSQL to be ready..."
until docker exec mcp-postgres pg_isready -U "${POSTGRES_USER}" -d "${POSTGRES_DATABASE}"; do
  sleep 1
done

echo "==> Initializing schema and seed data in PostgreSQL from schema.sql..."
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
docker exec -i mcp-postgres psql -U "${POSTGRES_USER}" -d "${POSTGRES_DATABASE}" < "${SCRIPT_DIR}/../src/main/resources/schema.sql"

echo "==> Starting MCP Toolbox container with custom tools.yaml (port 5005:5000)..."
docker run -d --name mcp-toolbox --network "${NETWORK_NAME}" -p 5005:5000 \
  -e POSTGRES_HOST=postgres \
  -e POSTGRES_PORT=5432 \
  -e POSTGRES_DATABASE="${POSTGRES_DATABASE}" \
  -e POSTGRES_USER="${POSTGRES_USER}" \
  -e POSTGRES_PASSWORD="${POSTGRES_PASSWORD}" \
  -v "${SCRIPT_DIR}/../tools.yaml:/tools.yaml:ro" \
  us-central1-docker.pkg.dev/database-toolbox/toolbox/toolbox:latest \
  --config /tools.yaml --address 0.0.0.0 --port 5000

echo "==> Waiting for MCP Toolbox to be ready on http://localhost:5005/mcp..."
until curl -s -X POST http://localhost:5005/mcp -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}' | grep -q "get-all-products"; do
  sleep 1
done

echo "==> MCP Toolbox Server is healthy and accepting requests on http://localhost:5005/mcp."
