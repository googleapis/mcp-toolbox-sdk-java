# Spring Boot Auto-Configuration Example ☕

This is a sample Spring Boot 3.x application demonstrating how to integrate the MCP Toolbox SDK using Spring's auto-configuration capabilities.

With the auto-configuration module enabled, you do not need to write boilerplate code to instantiate, configure, or manage the lifecycle of the `McpToolboxClient`. The SDK client is automatically configured and registered as a Spring bean.

---

## Prerequisites

- **Java Development Kit (JDK):** Version 17 or higher.
- **Apache Maven:** Version 3.6 or higher.
- **MCP Toolbox Server:** A running instance of the Toolbox server (e.g. running locally or on Cloud Run).

---

## How It Works

1. **Auto-Detection:** The SDK's auto-configuration checks for the presence of the property `google.cloud.mcp.toolbox.base-url`.
2. **Bean Instantiation:** If the property is present, a singleton `McpToolboxClient` bean is instantiated and registered in the application context.
3. **Fallback Safe:** If a user registers their own custom `McpToolboxClient` bean, the SDK's auto-configuration backs off.

---

## Getting Started

### 1. Build and Install the Core SDK
Since the example depends on the local `0.3.0-SNAPSHOT` version of the SDK, you must first build and install the SDK to your local Maven cache (`~/.m2`):

```bash
# In the repository root directory (mcp-toolbox-sdk-java)
mvn install -DskipTests -Dfmt.skip=true -Djacoco.skip=true
```

### 2. Configure the Toolbox Connection
Open `src/main/resources/application.properties` and configure the base URL of your running MCP Toolbox instance:

```properties
google.cloud.mcp.toolbox.base-url=http://localhost:5000/mcp
```

### 3. Run the Application
Navigate to the example directory and run the Spring Boot app:

```bash
cd example-spring-boot
mvn spring-boot:run
```

Upon a successful connection and tool execution, you will see output in the logs similar to:

```text
=== Spring Boot Auto-Configuration Success ===
Connecting to MCP server: McpToolboxClientImpl{baseUrl=http://localhost:5000/mcp}
Tool execution result content:
 - Result value 1
 - Result value 2
```
