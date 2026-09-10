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

package cloudcode.cymbal.web;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.mcp.McpToolboxClient;
import com.google.cloud.mcp.tool.Tool;
import com.google.cloud.mcp.tool.ToolResult;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@Timeout(value = 10, unit = TimeUnit.SECONDS)
public class McpToolboxServiceTest {

  @Mock private McpToolboxClient mockClient;
  @Mock private Tool mockTool;
  @Mock private Tool mockBoundTool;

  private McpToolboxService service;

  @BeforeEach
  void setUp() {
    service = new McpToolboxService(mockClient, "test-token");
    service.setTargetUrl("https://test-server.run.app/mcp");
  }

  @Test
  void testFindAllSchedulesSuccess() {
    ToolResult.Content item1 = new ToolResult.Content("text", "Route A - 08:00");
    ToolResult.Content item2 = new ToolResult.Content("text", "Route B - 12:00");
    ToolResult result = new ToolResult(List.of(item1, item2), false);

    when(mockClient.invokeTool(eq("find-bus-schedules"), anyMap()))
        .thenReturn(CompletableFuture.completedFuture(result));

    String schedules = service.findAllSchedules().join();
    assertEquals("[Route A - 08:00, Route B - 12:00]", schedules);
    verify(mockClient).invokeTool(eq("find-bus-schedules"), eq(Collections.emptyMap()));
  }

  @Test
  void testFindAllSchedulesError() {
    ToolResult result = new ToolResult(Collections.emptyList(), true);
    when(mockClient.invokeTool(eq("find-bus-schedules"), anyMap()))
        .thenReturn(CompletableFuture.completedFuture(result));

    String schedules = service.findAllSchedules().join();
    assertEquals("No schedules found.", schedules);
  }

  @Test
  void testFindAllSchedulesEmptyContent() {
    ToolResult result = new ToolResult(Collections.emptyList(), false);
    when(mockClient.invokeTool(eq("find-bus-schedules"), anyMap()))
        .thenReturn(CompletableFuture.completedFuture(result));

    String schedules = service.findAllSchedules().join();
    assertEquals("No schedules found.", schedules);
  }

  @Test
  void testFindAllSchedulesNullContent() {
    ToolResult result = new ToolResult(null, false);
    when(mockClient.invokeTool(eq("find-bus-schedules"), anyMap()))
        .thenReturn(CompletableFuture.completedFuture(result));

    String schedules = service.findAllSchedules().join();
    assertEquals("No schedules found.", schedules);
  }

  @Test
  void testQuerySchedulesSuccess() {
    ToolResult.Content content =
        new ToolResult.Content("text", "Trip 123: New York to Boston at 09:00");
    ToolResult result = new ToolResult(List.of(content), false);

    Map<String, Object> expectedParams = new LinkedHashMap<>();
    expectedParams.put("origin", "New York");
    expectedParams.put("destination", "Boston");

    when(mockClient.invokeTool(eq("query-schedules"), anyMap()))
        .thenReturn(CompletableFuture.completedFuture(result));

    String schedules = service.querySchedules("New York", "Boston").join();
    assertEquals("[Trip 123: New York to Boston at 09:00]", schedules);
    verify(mockClient).invokeTool(eq("query-schedules"), eq(expectedParams));
  }

  @Test
  void testQuerySchedulesNotFound() {
    ToolResult result = new ToolResult(Collections.emptyList(), false);
    when(mockClient.invokeTool(eq("query-schedules"), anyMap()))
        .thenReturn(CompletableFuture.completedFuture(result));

    String schedules = service.querySchedules("Seattle", "Miami").join();
    assertEquals("No specific schedules found.", schedules);
  }

  @Test
  void testQuerySchedulesError() {
    ToolResult result = new ToolResult(Collections.emptyList(), true);
    when(mockClient.invokeTool(eq("query-schedules"), anyMap()))
        .thenReturn(CompletableFuture.completedFuture(result));

    String schedules = service.querySchedules("Seattle", "Miami").join();
    assertEquals("No specific schedules found.", schedules);
  }

  @Test
  void testSearchPoliciesSuccess() {
    ToolResult.Content content =
        new ToolResult.Content("text", "Pets under 25 lbs allowed in carrier.");
    ToolResult result = new ToolResult(List.of(content), false);

    Map<String, Object> expectedParams = new LinkedHashMap<>();
    expectedParams.put("search_query", "Can I bring my pet?");

    when(mockClient.invokeTool(eq("search-policies"), anyMap()))
        .thenReturn(CompletableFuture.completedFuture(result));

    String policy = service.searchPolicies("Can I bring my pet?").join();
    assertEquals("[Pets under 25 lbs allowed in carrier.]", policy);
    verify(mockClient).invokeTool(eq("search-policies"), eq(expectedParams));
  }

  @Test
  void testSearchPoliciesNotFound() {
    ToolResult result = new ToolResult(Collections.emptyList(), false);
    when(mockClient.invokeTool(eq("search-policies"), anyMap()))
        .thenReturn(CompletableFuture.completedFuture(result));

    String policy = service.searchPolicies("spaceships").join();
    assertEquals("No policy information found.", policy);
  }

  @Test
  void testSearchPoliciesError() {
    ToolResult result = new ToolResult(Collections.emptyList(), true);
    when(mockClient.invokeTool(eq("search-policies"), anyMap()))
        .thenReturn(CompletableFuture.completedFuture(result));

    String policy = service.searchPolicies("pets").join();
    assertEquals("No policy information found.", policy);
  }

  @Test
  void testBookTicketSuccess() {
    ToolResult.Content content =
        new ToolResult.Content("text", "Booking confirmed: ID-9988 for Jane Doe");
    ToolResult result = new ToolResult(List.of(content), false);

    when(mockClient.loadTool(eq("book-ticket"), anyMap()))
        .thenReturn(CompletableFuture.completedFuture(mockTool));
    when(mockTool.bindParam("passenger_name", "Jane Doe")).thenReturn(mockBoundTool);
    when(mockBoundTool.execute(eq(Collections.singletonMap("trip_id", "trip-456"))))
        .thenReturn(CompletableFuture.completedFuture(result));

    String response = service.bookTicket("trip-456", "Jane Doe").join();
    assertEquals("Booking confirmed: ID-9988 for Jane Doe", response);

    verify(mockClient).loadTool(eq("book-ticket"), anyMap());
    verify(mockTool).bindParam("passenger_name", "Jane Doe");
    verify(mockBoundTool).execute(Collections.singletonMap("trip_id", "trip-456"));
  }

  @Test
  void testBookTicketFailureContentEmpty() {
    // Verifies that empty content does not throw IndexOutOfBoundsException
    ToolResult result = new ToolResult(Collections.emptyList(), true);

    when(mockClient.loadTool(eq("book-ticket"), anyMap()))
        .thenReturn(CompletableFuture.completedFuture(mockTool));
    when(mockTool.bindParam("passenger_name", "Jane Doe")).thenReturn(mockBoundTool);
    when(mockBoundTool.execute(anyMap())).thenReturn(CompletableFuture.completedFuture(result));

    String response = service.bookTicket("trip-456", "Jane Doe").join();
    assertEquals("Transaction failed.", response);
  }

  @Test
  void testBookTicketFailureContentNull() {
    ToolResult result = new ToolResult(null, true);

    when(mockClient.loadTool(eq("book-ticket"), anyMap()))
        .thenReturn(CompletableFuture.completedFuture(mockTool));
    when(mockTool.bindParam("passenger_name", "Jane Doe")).thenReturn(mockBoundTool);
    when(mockBoundTool.execute(anyMap())).thenReturn(CompletableFuture.completedFuture(result));

    String response = service.bookTicket("trip-456", "Jane Doe").join();
    assertEquals("Transaction failed.", response);
  }

  @Test
  void testInitHandlesExceptionGracefully() {
    McpToolboxService uninitialized = new McpToolboxService();
    uninitialized.setTargetUrl("https://invalid-host-for-testing.example.com/mcp");
    // init() should catch credentials/discovery exceptions and not crash
    assertDoesNotThrow(() -> uninitialized.init());
  }
}
