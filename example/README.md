# Example Usage

These sample Java files allow you to test the features supported in the Java version of the MCP Toolbox SDK.

1. First set up database (AlloyDB in this case) and ingest data for the tools referenced in this example. In order to demonstrate the use of tools across applications, we have considered a RETAIL STORE and TOY STORE application data together. In order to install cluster, instance and setup data, for this sample use case, follow the first 4 steps of the first codelab (hybrid search) and the 5th step of the second codelab (toystore app) below:

   https://codelabs.developers.google.com/hybrid-search-on-cloudrun
   and
   https://codelabs.developers.google.com/toy-store-app

2. To start with tools, go ahead and create the MCP Toolbox Server for the sameple use case we are looking at.
  
3. To set up & install **MCP Toolbox Server**, install the latest version from here:
   https://github.com/googleapis/genai-toolbox?tab=readme-ov-file#installing-the-server
   
4. Next, create **tools.yaml**, you can find the one we are using in our example in this repo.
   
5. Replace the placeholder variables in `tools.yaml` with values from your instance / environment

> [!TIP]
> Do not hardcode for production applications.
   
5. Once it's done, from within the folder where your MCP Toolbox binary & `tools.yaml` are residing, run the following command:
   ```bash
   ./toolbox --tools-file "tools.yaml"
   ```

6. If you want to look at the toolset and tools in a simple web UI, use the command:
   ```bash
   ./toolbox --ui
   ```

7. You can then deploy this in Cloud Run to test the application or use the local instance running, while you try the example Java applications.
In any case remember to change the `YOUR_TOOLBOX_SERVICE_ENDPOINT` placeholder in the respective files.

    If you decide to deploy your toolbox endpoint in cloud run, here's how you can do it: https://googleapis.github.io/genai-toolbox/how-to/deploy_toolbox

> [!NOTE]
> At this point you must be having a working MCP Toolbox server accessible.

8. You should be able to run the Java classes in this example to understand the usage of this library.

9. Use the `pom.xml` from this repo. Note that we have the dependency `mcp-toolbox-sdk-java` included:
   ```xml
   <dependency>
     <groupId>com.google.cloud.mcp</groupId>
     <artifactId>mcp-toolbox-sdk-java</artifactId>
     <version>0.1.1</version> <!-- x-release-please-version -->
   </dependency>
   ```

10. Compile the class that you want to test:
    ```bash
    mvn compile
    ```
    Now run the example class:
    ```bash
    mvn clean compile exec:java -Dexec.mainClass="cloudcode.helloworld.ExampleUsage"
    ```

