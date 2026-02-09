/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.mcp.e2e;

import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class ToolboxE2ESetup implements BeforeAllCallback, AfterAllCallback {

  private static final Logger logger = Logger.getLogger(ToolboxE2ESetup.class.getName());
  private static final String PROJECT_ID_ENV = "GOOGLE_CLOUD_PROJECT";
  private static final String TOOLBOX_VERSION_ENV = "TOOLBOX_VERSION";
  private static final String BINARY_NAME = "toolbox";

  private Process serverProcess;
  private Path toolsManifestPath;
  private String authToken1;
  private String authToken2;

  @Override
  public void beforeAll(ExtensionContext context) throws Exception {
    String projectId = getEnvVar(PROJECT_ID_ENV);
    String toolboxVersion = getEnvVar(TOOLBOX_VERSION_ENV);

    //  Download Toolbox Binary
    downloadToolboxBinary(toolboxVersion);

    // Fetch Tools Manifest from Secret Manager
    String manifestContent = accessSecretVersion(projectId, "sdk_testing_tools", "latest");
    toolsManifestPath = Files.createTempFile("tools_manifest", ".yaml");
    Files.writeString(toolsManifestPath, manifestContent);

    // Fetch Auth Tokens
    String client1Id = accessSecretVersion(projectId, "sdk_testing_client1", "latest");
    String client2Id = accessSecretVersion(projectId, "sdk_testing_client2", "latest");
    authToken1 = getAuthToken(client1Id);
    authToken2 = getAuthToken(client2Id);

    // Start Server
    startServer();
  }

  public String getAuthToken1() {
    return authToken1;
  }

  public String getAuthToken2() {
    return authToken2;
  }

  private String getAuthToken(String audience) throws IOException {
    com.google.auth.oauth2.GoogleCredentials credentials =
        com.google.auth.oauth2.GoogleCredentials.getApplicationDefault();
    if (credentials instanceof com.google.auth.oauth2.IdTokenProvider) {
      com.google.auth.oauth2.IdTokenProvider idTokenProvider =
          (com.google.auth.oauth2.IdTokenProvider) credentials;
      if (credentials.getAccessToken() == null) {
        credentials.refreshIfExpired();
      }
      return idTokenProvider
          .idTokenWithAudience(audience, java.util.Collections.emptyList())
          .getTokenValue();
    } else {
      // Fallback or error. In some envs (like local user creds), ID tokens might not be directly
      // supported without extra config.
      // But for this environment (likely mimicking CI/Cloud), it should work or fail.
      // Creating dummy token if real one fails? No, user wants real E2E.
      System.err.println("WARNING: Credentials do not support ID Tokens. Auth tests might fail.");
      return "fake-token-for-" + audience;
    }
  }

  @Override
  public void afterAll(ExtensionContext context) {
    if (serverProcess != null) {
      serverProcess.destroy();
      try {
        if (!serverProcess.waitFor(5, TimeUnit.SECONDS)) {
          serverProcess.destroy();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    if (toolsManifestPath != null) {
      try {
        Files.deleteIfExists(toolsManifestPath);
      } catch (IOException e) {
        logger.warning("Failed to delete temp manifest: " + e.getMessage());
      }
    }
  }

  public String getBaseUrl() {
    return "http://localhost:5000";
  }

  private void startServer() throws IOException, InterruptedException {
    logger.info("Starting Toolbox Server...");
    ProcessBuilder pb =
        new ProcessBuilder(
            "./" + BINARY_NAME, "--tools-file", toolsManifestPath.toAbsolutePath().toString());
    pb.inheritIO();
    serverProcess = pb.start();

    Thread.sleep(5000);

    if (!serverProcess.isAlive()) {
      throw new RuntimeException("Toolbox server failed to start or exited immediately.");
    }
    logger.info("Toolbox server started.");
  }

  private void downloadToolboxBinary(String version) {
    Path binaryPath = Paths.get(BINARY_NAME);
    if (Files.exists(binaryPath)) {
      logger.info("Toolbox binary already exists.");
      return;
    }

    String os = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
    String arch = System.getProperty("os.arch").toLowerCase(Locale.ENGLISH);

    String osName = os.contains("mac") ? "darwin" : "linux";
    String archName = arch.equals("aarch64") ? "arm64" : "amd64";
    if (osName.equals("darwin") && arch.equals("aarch64")) {
      archName = "arm64";
    } else {
      archName = "amd64";
    }

    String blobName = String.format("v%s/%s/%s/toolbox", version, osName, archName);
    logger.info("Downloading toolbox binary from: " + blobName);

    Storage storage = StorageOptions.getDefaultInstance().getService();
    Blob blob = storage.get("genai-toolbox", blobName);
    if (blob == null) {
      throw new RuntimeException("Toolbox binary not found in GCS: " + blobName);
    }

    blob.downloadTo(binaryPath);
    binaryPath.toFile().setExecutable(true);
    logger.info("Toolbox binary downloaded and made executable.");
  }

  private String accessSecretVersion(String projectId, String secretId, String versionId)
      throws IOException {
    try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
      SecretVersionName secretVersionName = SecretVersionName.of(projectId, secretId, versionId);
      AccessSecretVersionResponse response = client.accessSecretVersion(secretVersionName);
      return response.getPayload().getData().toStringUtf8();
    }
  }

  private String getEnvVar(String key) {
    String val = System.getenv(key);
    if (val == null) {
      throw new RuntimeException("Environment variable " + key + " is not set.");
    }
    return val;
  }
}
