![MCP Toolbox
Logo](https://raw.githubusercontent.com/googleapis/genai-toolbox/main/logo.png)

# MCP Toolbox SDK for Java ☕

[![License: Apache
2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

> [!IMPORTANT]
> Feature support in this Java SDK is currently in progress and is not at parity with the other SDKs ([Python](https://github.com/googleapis/mcp-toolbox-sdk-python), [JS/TS](https://github.com/googleapis/mcp-toolbox-sdk-js), and [Go](https://github.com/googleapis/mcp-toolbox-sdk-go)).

The official Java Client SDK for the MCP Toolbox.

This repository contains SDK designed to seamlessly integrate the
functionalities of the [MCP
Toolbox](https://github.com/googleapis/genai-toolbox) into your Agentic
applications. This allows you to load tools defined in Toolbox and use them
as standard Java applications (Spring Boot, Quarkus, Jakarta EE) or your custom code. It empowers your AI Agents to "use tools"—querying databases, calling APIs, or managing files—without you writing the boilerplate integration code.

This simplifies the process of incorporating external functionalities (like
Databases or APIs) managed by Toolbox into your Agentic applications. It is a framework-agnostic way to interact with Toolbox tools.

## Table of Contents

<!-- TOC ignore:true -->

- [Features](#features)
- [Supported Environments](#supported-environments)
- [Getting Started](#getting-started)
- [Installation](#installation)
- [Usage](#usage)
  - [Load the Client](#load-the-client)
  - [Load a Toolset](#load-a-toolset)
  - [Load a Tool](#load-a-tool)
  - [Invoke a Tool](#invoke-a-tool)
- [Quickstart](#quickstart)
- [Authentication](#authentication)
  - [Client to Server Authentication](#client-to-server-authentication)
  - [Authenticating the Tools](#authenticating-the-tools)
- [Binding Parameter Values](#binding-parameter-values)
  - [Why Bind Parameters?](#why-bind-parameters)
  - [Option A: Static Binding](#option-a-static-binding)
  - [Option B: Dynamic Binding](#option-b-dynamic-binding)
- [Error Handling](#error-handling)
- [Contributing](#contributing)
- [License](#license)

<!-- /TOC -->

## Features

* **Native Java 17+ Support:** Built on modern Java standards (record, CompletableFuture, HttpClient).  
* **Async-First Architecture:** Non-blocking I/O operations perfect for high-throughput AI agent workflows.  
* **Type-Safe Invocation:** Discover tools dynamically and invoke them with strongly typed maps.  
* **Zero-Config Authentication:** Built-in support for Google Cloud Run OIDC (ADC) authentication.  
* **Minimal Footprint:** Lightweight dependency graph (Jackson \+ Google Auth).

## Supported Environments

This SDK is designed to be versatile, supporting both modern cloud infrastructures and standard Java runtimes.

### Java Runtimes & Frameworks

| Runtime / Framework | Supported | Notes |
| ----- | ----- | ----- |
| **Java 17+ (LTS)** | ✅ | Base requirement. Tested on OpenJDK, Corretto, and Temurin. |
| **Java 21+ (LTS)** | ✅ | Fully compatible, including Virtual Threads support. |
| **Spring Boot 3.x** | ✅ | Works as a standard library bean. |
| **Quarkus** | ✅ | Compatible in JVM mode. |
| **GraalVM Native Image** | 🚧 | Experimental. Reflection configuration may be required for Jackson. |

### Cloud Infrastructure

| Environment | Supported | Notes |
| ----- | ----- | ----- |
| **Google Cloud Run** | ✅ | **First-class support.** Automatic OIDC authentication via built-in Service Account. |
| **Google Cloud Functions** | ✅ | Fully supported (Gen 2 recommended). |
| **Google Compute Engine** | ✅ | Supported via Metadata Server credentials. |
| **Local Development** | ✅ | Supported via `gcloud` CLI credentials. |
| **On-Premise / Hybrid** | ✅ | Supported via `GOOGLE_APPLICATION_CREDENTIALS` environment variable. |
| **AWS / Azure** | ✅ | Supported via Workload Identity Federation or Service Account Keys. |

## Getting Started

First make sure Toolbox Server is set up and is running (either locally or deployed on Cloud Run). Follow the instructions here: [**Toolbox Getting Started
    Guide**](https://github.com/googleapis/genai-toolbox?tab=readme-ov-file#getting-started)

## Installation

This SDK is distributed via a Google Cloud Storage Maven Repository.

### Maven

Add the repository to your pom.xml:

```

<repositories>
    <repository>
        <id>gcs-repo</id>
        <url>[https://storage.googleapis.com/mcp-toolbox-java-sdk/maven](https://storage.googleapis.com/mcp-toolbox-java-sdk/maven)</url>
    </repository>
</repositories>
```

Add the dependency to your pom.xml:
```
<dependencies>
    <dependency>
        <groupId>com.google.cloud.mcp</groupId>
        <artifactId>mcp-toolbox-sdk-java</artifactId>
        <version>0.1.0</version>
    </dependency>
</dependencies>

```

### Gradle

```

repositories {
    maven {
        url = uri("[https://storage.googleapis.com/mcp-toolbox-java-sdk/maven](https://storage.googleapis.com/mcp-toolbox-java-sdk/maven)")
    }
}

dependencies {
    implementation("com.google.cloud.mcp:mcp-toolbox-sdk-java:0.1.0")
}

```


## Usage

### Load the Client

The McpToolboxClient is your entry point. It is thread-safe and designed to be instantiated once and reused.

```
import com.google.cloud.mcp.McpToolboxClient;

McpToolboxClient client = McpToolboxClient.builder()
    .baseUrl("[https://my-toolbox-service.a.run.app](https://my-toolbox-service.a.run.app)") 
    // .apiKey("...") // Optional: Overrides automatic Google Auth
    .build();
```

### Load a Toolset

You can load all available tools or a specific subset (toolset) if your server supports it. This returns a Map of tool definitions.

```java
// Load all tools (alias for listTools)
client.loadToolset().thenAccept(tools -> {
    System.out.println("Available Tools: " + tools.keySet());
    
    tools.forEach((name, definition) -> {
        System.out.println("Tool: " + name);
        System.out.println("Description: " + definition.description());
    });
});
```

```java
// Load a specific toolset (e.g., 'retail-tools')
client.loadToolset("retail-tools").thenAccept(tools -> {
    System.out.println("Tools in Retail Set: " + tools.keySet());
});
```

### Load a Tool

If you know the specific tool you want to use, you can load its definition directly. This is useful for validation or inspecting the required parameters before execution.

```java
client.loadTool("get-toy-price").thenAccept(toolDef -> {
    System.out.println("Loaded Tool: " + toolDef.description());
    System.out.println("Parameters: " + toolDef.parameters());
});
```


### Invoke a Tool

Invoking a tool sends a request to the MCP Server to execute the logic (SQL, API call, etc.). Arguments are passed as a Map<String, Object>.

```java
import java.util.Map;

Map<String, Object> args = Map.of(
    "description", "plush dinosaur",
    "limit", 5
);

client.invokeTool("get-toy-price", args).thenAccept(result -> {
    System.out.println("Result: " + result.content().get(0).text());
```

## Quickstart

Here is the minimal code needed to connect to a toolbox and invoke a tool.

```
import com.google.cloud.mcp.McpToolboxClient;
import java.util.Map;

public class App {
    public static void main(String[] args) {
        // 1. Create the Client
        McpToolboxClient client = McpToolboxClient.builder()
            .baseUrl("[https://my-toolbox-service.a.run.app](https://my-toolbox-service.a.run.app)") 
            .build();

        // 2. Invoke a Tool
        client.invokeTool("get-toy-price", Map.of("description", "plush dinosaur"))
            .thenAccept(result -> {
                System.out.println("Tool Output: " + result.content().get(0).text());
            })
            .exceptionally(ex -> {
                System.err.println("Error: " + ex.getMessage());
                return null;
            })
            .join(); // Wait for completion
    }
}
```

For a detailed example, check the ExampleUsage.java file in teh example folder of this repo.

> [!NOTE]
>
> The SDK is Async-First, using Java's CompletableFuture to bridge both patterns naturally.
> - Asynchronous: Chain methods using .thenCompose(), .thenAccept(), and .exceptionally() > for non-blocking execution.
> - If you prefer synchronous execution, simply call .join() on the result to block until > completion.
```
// Async (Non-blocking)
client.invokeTool("tool-name", args).thenAccept(result -> ...);
// Sync (Blocking)
ToolResult result = client.invokeTool("tool-name", args).join();
```

## Authentication

## Client to Server Authentication

This section describes how to authenticate the `ToolboxClient` itself when connecting to a Toolbox server instance that requires authentication. This is crucial for securing your Toolbox server endpoint, especially when deployed on platforms like Cloud Run, GKE, or any environment where unauthenticated access is restricted.

### When is Client-to-Server Authentication Needed

You'll need this if your Toolbox server is configured to deny unauthenticated requests. For example:

* Your Toolbox server is deployed on **Google Cloud Run** and configured to "Require authentication" (default).  
* Your server is behind an Identity-Aware Proxy (IAP).  
* You have custom authentication middleware.

Without proper client authentication, attempts to connect (like `listTools`) will fail with `401 Unauthorized` or `403 Forbidden` errors.

### How it works

The Java SDK handles the generation of **Authorization headers** (Bearer tokens) using the **Google Auth Library**. It follows the **Application Default Credentials (ADC)** strategy to find the correct credentials based on the environment where your code is running.

You need to set up [ADC](https://cloud.google.com/docs/authentication/set-up-adc-local-dev-environment).

### Authenticating with Google Cloud Servers (Cloud Run

For Toolbox servers hosted on Google Cloud (e.g., Cloud Run), the SDK provides seamless OIDC authentication.

#### 1\. Configure Permissions

Grant the **`roles/run.invoker`** IAM role on the Cloud Run service to the principal calling the service.

* **Local Dev:** Grant this role to your *User Account Email*.  
* **Production:** Grant this role to the *Service Account* attached to your application.

#### 2\. Configure Credentials

**Option A: Local Development** If running on your laptop, use the `gcloud` CLI to set up your user credentials.

```
gcloud auth application-default login
```

The SDK will automatically detect these credentials and generate an OIDC ID Token intended for your Toolbox URL.

**Option B: Google Cloud Environments** When running within Google Cloud (e.g., Compute Engine, GKE, another Cloud Run service, Cloud Functions), ADC is configured automatically. The SDK uses the environment's default service account. No extra code or configuration is required.

**Option C: On-Premise / CI/CD** If running outside of Google Cloud (e.g., Jenkins, AWS), create a Service Account Key (JSON) and set the environment variable:

```
export GOOGLE_APPLICATION_CREDENTIALS="/path/to/key.json"
```

| Environment | Mechanism | Setup Required |
| :---- | :---- | :---- |
| **Local Dev** | Uses User Credentials | Run gcloud auth application-default login |
| **Cloud Run** | Uses Service Account | **None.** (Automatic) |
| **CI/CD** | Uses Service Account Key | Set GOOGLE\_APPLICATION\_CREDENTIALS=/path/to/key.json |

*Note: If you provide an .apiKey() in the builder, it overrides the automatic ADC mechanism.*

## Authenticating the Tools

Tools can be configured within the Toolbox service to require authentication, ensuring only authorized users or applications can invoke them, especially when accessing sensitive data.

#### Warning

Always use HTTPS to connect your application with the Toolbox service, especially in production environments or whenever the communication involves sensitive data (including scenarios where tools require authentication tokens). Using plain HTTP lacks encryption and exposes your application and data to significant security risks, such as eavesdropping and tampering.


### When is Authentication Needed?

Authentication is configured per-tool within the Toolbox service itself. If a tool you intend to use is marked as requiring authentication in the service, you must configure the SDK client to provide the necessary credentials (currently Oauth2 tokens) when invoking that specific tool.

### Supported Authentication Mechanisms

The Toolbox service enables secure tool usage through Authenticated Parameters. For detailed information on how these mechanisms work within the Toolbox service and how to configure them, please refer to [Toolbox Service Documentation \- Authenticated Parameters](https://googleapis.github.io/genai-toolbox/resources/tools/#authenticated-parameters)

### Step 1: Configure Tools in Toolbox Service

First, ensure the target tool(s) are configured correctly in the Toolbox service to require authentication. Refer to the [Toolbox Service Documentation \- Authenticated Parameters](https://googleapis.github.io/genai-toolbox/resources/tools/#authenticated-parameters) for instructions.

### Step 2: Configure SDK Client

Your application needs a way to obtain the required token for the authenticated user. The SDK requires you to provide a function capable of retrieving this token *when the tool is invoked*.

### Provide a Token Retriever Function

You must provide the SDK with an `AuthTokenGetter` (a function that returns a `CompletableFuture<String>`). This implementation depends on your application's authentication flow (e.g., retrieving a stored token, initiating an OAuth flow).

**Important:** The **Service Name** (or Auth Source) used when adding the getter (e.g., `"salesforce_auth"`) must exactly match the name of the corresponding auth source defined in the tool's configuration.

```
import com.google.cloud.mcp.AuthTokenGetter;

// Define your token retrieval logic
AuthTokenGetter salesforceTokenGetter = () -> {
    return CompletableFuture.supplyAsync(() -> fetchTokenFromVault()); 
};
//example tool: search-salesforce and related sample params
client.loadTool("search-salesforce").thenCompose(tool -> {
    // Register the getter. It will be called every time 'execute' is run.
    tool.addAuthTokenGetter("salesforce_auth", salesforceTokenGetter);
    
    return tool.execute(Map.of("query", "recent leads"));
});
```

**Tip:** Your token retriever function is invoked every time an authenticated parameter requires a token for a tool call. Consider implementing caching logic within this function to avoid redundant token fetching or generation, especially for tokens with longer validity periods or if the retrieval process is resource-intensive.

### Complete Authentication Example

Here is a complete example of how to configure and invoke an authenticated tool.

```java
import com.google.cloud.mcp.McpToolboxClient;
import com.google.cloud.mcp.AuthTokenGetter;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class AuthExample {
    public static void main(String[] args) {
        // 1. Define your token retrieval logic
        AuthTokenGetter tokenGetter = () -> {
            // Logic to retrieve ID token (e.g., from local storage, OAuth flow)
            return CompletableFuture.completedFuture("YOUR_ID_TOKEN"); 
        };

        // 2. Initialize the client
        McpToolboxClient client = McpToolboxClient.builder()
            .baseUrl("[http://127.0.0.1:5000](http://127.0.0.1:5000)")
            .build();

        // 3. Load tool, attach auth, and execute
        client.loadTool("my-tool")
            .thenCompose(tool -> {
                // "my_auth" must match the name in the tool's authSources config
                tool.addAuthTokenGetter("my_auth", tokenGetter);
                
                return tool.execute(Map.of("input", "some input"));
            })
            .thenAccept(result -> {
                System.out.println(result.content().get(0).text());
            })
            .join();
    }
}
```

## Binding Parameter Values

The SDK allows you to pre-set, or "bind", values for specific tool parameters before the tool is invoked or even passed to an LLM. These bound values are fixed and will not be requested or modified by the LLM during tool use.

### Why Bind Parameters?

* Protecting sensitive information: API keys, secrets, etc.  
* Enforcing consistency: Ensuring specific values for certain parameters.  
* Pre-filling known data: Providing defaults or context.

##### Important

The parameter names used for binding (e.g., `"api_key"`) must exactly match the parameter names defined in the tool's configuration within the Toolbox service.

##### Note

You do not need to modify the tool's configuration in the Toolbox service to bind parameter values using the SDK.

### Option A: Static Binding

Bind a fixed value to a tool object.

```
client.loadTool("get-toy-price").thenCompose(tool -> {
    // Bind 'currency' to 'USD' permanently for this tool instance
    tool.bindParam("currency", "USD");
    
    // Now invoke without specifying currency
    return tool.execute(Map.of("description", "lego set")); 
});
```

### Option B: Dynamic Binding

Instead of a static value, you can bind a parameter to a synchronous or asynchronous function (`Supplier`). This function will be called **each time** the tool is invoked to dynamically determine the parameter's value at runtime.

```
client.loadTool("check-order-status").thenCompose(tool -> {
    // Bind 'user_id' to a function that fetches the current user from context
    tool.bindParam("user_id", () -> SecurityContext.getCurrentUser().getId());
    
    // Invoke: The SDK will call the supplier to fill 'user_id'
    return tool.execute(Map.of("order_id", "12345"));
});
```

##### Important

You don't need to modify tool configurations to bind parameter values.

## Error Handling

The SDK uses Java's CompletableFuture API. Errors (Network issues, 4xx/5xx responses) are propagated as exceptions wrapped in CompletionException.

```

client.invokeTool("invalid-tool", Map.of())
    .handle((result, ex) -> {
        if (ex != null) {
            System.err.println("Invocation Failed: " + ex.getCause().getMessage());
            return null; // Handle error
        }
        return result; // Success path
    });

```

## Contributing

We welcome contributions\! Please see [CONTRIBUTING.md](https://www.google.com/search?q=CONTRIBUTING.md) for details on how to submit pull requests, report bugs, or request features.

## License

This project is licensed under the Apache 2.0 License \- see the [LICENSE](https://www.google.com/search?q=LICENSE) file for details.
