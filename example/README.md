**These sample Java files allow you to test the features supported in the Java version of the MCP Toolbox SDK. **

1. To start with go ahead and create the MCP Toolbox Server for the sameple use case we are looking at.
  
2. To set up & install **Toolbox Server**, install the latest version from here:
   https://github.com/googleapis/genai-toolbox?tab=readme-ov-file#installing-the-server
   
3. Next, create **tools.yaml**, you can find the one we are using in our example in this repo.
   
4. Replace the placeholder variables in tools.yaml with values from your instance / environment (Recommedation: Do not hardcode for production applications)
   
5. Once it's done, from within the folder where your MCP Toolbox binary & `tools.yaml` are residing, run the following command:
   ```bash
   ./toolbox --tools-file "tools.yaml"
   ```

6. If you want to look at the toolset and tools in a simple web UI, use the command:
      ./toolbox --ui

7. You can then deploy this in Cloud Run to test the application or use the local instance running, while you try the example Java applications.
In any case remember to change the TOOLBOX ENDPOINT placeholder in the respective files.

If you decide to deploy your toolbox endpoint in cloud run, here's how you can do it:
[https://googleapis.github.io/genai-toolbox/how-to/deploy_toolbox](url)
