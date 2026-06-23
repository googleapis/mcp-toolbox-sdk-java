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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdToken;
import com.google.auth.oauth2.IdTokenProvider;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class ToolboxActualServerVerifyTest {
    private static Process serverProcess;
    private static final int PORT = 8099;
    private static final String DB_PATH = "src/test/resources/verification.db";

    @BeforeAll
    public static void startActualServer() throws Exception {
        // 1. Clean up any previous DB file
        Files.deleteIfExists(Paths.get(DB_PATH));

        // 2. Initialize local SQLite database
        System.out.println("Initializing SQLite database at " + DB_PATH + "...");
        ProcessBuilder pbDb = new ProcessBuilder("/usr/bin/sqlite3", DB_PATH);
        pbDb.redirectInput(new File("src/test/resources/verify_schema.sql"));
        Process pDb = pbDb.start();
        boolean finished = pDb.waitFor(10, TimeUnit.SECONDS);
        if (!finished || pDb.exitValue() != 0) {
            throw new RuntimeException("Failed to initialize SQLite database. Exit value: " + pDb.exitValue());
        }
        System.out.println("Database initialized.");

        // 3. Start actual MCP Toolbox server
        System.out.println("Starting actual MCP Toolbox server on port " + PORT + "...");
        ProcessBuilder pbServer = new ProcessBuilder(
            "src/test/resources/toolbox",
            "--config", "src/test/resources/verify_tools.yaml",
            "--port", String.valueOf(PORT)
        );
        pbServer.inheritIO();
        serverProcess = pbServer.start();

        // Wait a few seconds for server to start up
        Thread.sleep(3000);

        if (!serverProcess.isAlive()) {
            throw new RuntimeException("Toolbox server process died immediately.");
        }
        System.out.println("Actual Toolbox server started successfully.");
    }

    @AfterAll
    public static void stopActualServer() throws Exception {
        System.out.println("Stopping actual MCP Toolbox server...");
        if (serverProcess != null) {
            serverProcess.destroy();
            if (!serverProcess.waitFor(5, TimeUnit.SECONDS)) {
                serverProcess.destroyForcibly();
            }
        }
        Files.deleteIfExists(Paths.get(DB_PATH));
        System.out.println("Server stopped and database cleaned.");
    }

    @Test
    public void testActualServerExampleFlow() throws Exception {
        // Set System properties to configure ExampleUsage to hit our local port 8099
        System.setProperty("toolbox.url", "http://localhost:" + PORT + "/mcp");
        System.setProperty("toolbox.keyPath", ""); // Empty string to bypass file checking and use ADC

        // 1. Programmatically compile ExampleUsage.java on the fly using test classpath
        File sourceFile = new File("example/src/main/java/cloudcode/helloworld/ExampleUsage.java");
        assertTrue(sourceFile.exists(), "ExampleUsage.java does not exist at " + sourceFile.getAbsolutePath());

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "System Java compiler is not available. Please run tests using a JDK, not a JRE.");
        
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        String classpath = System.getProperty("java.class.path");

        List<String> optionList = new ArrayList<>();
        optionList.add("-classpath");
        optionList.add(classpath);
        optionList.add("-d");
        optionList.add("target/test-classes");

        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(Arrays.asList(sourceFile));
        boolean compileSuccess = compiler.getTask(null, fileManager, null, optionList, null, compilationUnits).call();
        fileManager.close();

        assertTrue(compileSuccess, "Compilation of ExampleUsage.java failed.");
        System.out.println("ExampleUsage.java compiled successfully.");

        // 2. Load the compiled ExampleUsage class
        Class<?> clazz = Class.forName("cloudcode.helloworld.ExampleUsage");

        // Mock GoogleCredentials statically to return a dummy IdTokenProvider
        try (MockedStatic<GoogleCredentials> mockedCredentials = Mockito.mockStatic(GoogleCredentials.class)) {
            GoogleCredentials mockCreds = Mockito.mock(
                GoogleCredentials.class,
                Mockito.withSettings().extraInterfaces(IdTokenProvider.class)
            );
            IdToken mockToken = Mockito.mock(IdToken.class);
            Mockito.when(mockToken.getTokenValue()).thenReturn("dummy-token");
            Mockito.when(((IdTokenProvider) mockCreds).idTokenWithAudience(
                Mockito.anyString(), Mockito.anyList()
            )).thenReturn(mockToken);

            mockedCredentials.when(GoogleCredentials::getApplicationDefault).thenReturn(mockCreds);

            // Execute the actual example class's main method directly!
            System.out.println("Executing ExampleUsage.main...");
            clazz.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            System.out.println("ExampleUsage.main completed successfully.");
        } finally {
            System.clearProperty("toolbox.url");
            System.clearProperty("toolbox.keyPath");
        }
    }

    @Test
    public void testBulkToolsetExampleFlow() throws Exception {
        // Set System properties to configure BulkToolsetUsage to hit our local port 8099
        System.setProperty("toolbox.url", "http://localhost:" + PORT + "/mcp");
        System.setProperty("toolbox.keyPath", "");

        // 1. Programmatically compile BulkToolsetUsage.java on the fly
        File sourceFile = new File("example/src/main/java/cloudcode/helloworld/BulkToolsetUsage.java");
        assertTrue(sourceFile.exists(), "BulkToolsetUsage.java does not exist");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        String classpath = System.getProperty("java.class.path");

        List<String> optionList = new ArrayList<>();
        optionList.add("-classpath");
        optionList.add(classpath);
        optionList.add("-d");
        optionList.add("target/test-classes");

        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(Arrays.asList(sourceFile));
        boolean compileSuccess = compiler.getTask(null, fileManager, null, optionList, null, compilationUnits).call();
        fileManager.close();

        assertTrue(compileSuccess, "Compilation of BulkToolsetUsage.java failed.");

        // 2. Load the compiled BulkToolsetUsage class
        Class<?> clazz = Class.forName("cloudcode.helloworld.BulkToolsetUsage");

        // Mock GoogleCredentials statically to return a dummy IdTokenProvider
        try (MockedStatic<GoogleCredentials> mockedCredentials = Mockito.mockStatic(GoogleCredentials.class)) {
            GoogleCredentials mockCreds = Mockito.mock(
                GoogleCredentials.class,
                Mockito.withSettings().extraInterfaces(IdTokenProvider.class)
            );
            IdToken mockToken = Mockito.mock(IdToken.class);
            Mockito.when(mockToken.getTokenValue()).thenReturn("dummy-token");
            Mockito.when(((IdTokenProvider) mockCreds).idTokenWithAudience(
                Mockito.anyString(), Mockito.anyList()
            )).thenReturn(mockToken);

            mockedCredentials.when(GoogleCredentials::getApplicationDefault).thenReturn(mockCreds);

            // Execute the actual example class's main method directly!
            System.out.println("Executing BulkToolsetUsage.main...");
            clazz.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            System.out.println("BulkToolsetUsage.main completed successfully.");
        } finally {
            System.clearProperty("toolbox.url");
            System.clearProperty("toolbox.keyPath");
        }
    }
}
