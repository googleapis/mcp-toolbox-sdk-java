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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
public class TransitAgentToolsTest {

  @Mock private McpToolboxService mockService;

  private TransitAgentTools tools;

  @BeforeEach
  void setUp() {
    tools = new TransitAgentTools(mockService);
  }

  @Test
  void testFindAllSchedules() {
    when(mockService.findAllSchedules())
        .thenReturn(CompletableFuture.completedFuture("[Route A, Route B]"));

    String result = tools.findAllSchedules();
    assertEquals("[Route A, Route B]", result);
    verify(mockService).findAllSchedules();
  }

  @Test
  void testQuerySchedules() {
    when(mockService.querySchedules("New York", "Boston"))
        .thenReturn(CompletableFuture.completedFuture("[Trip 123]"));

    String result = tools.querySchedules("New York", "Boston");
    assertEquals("[Trip 123]", result);
    verify(mockService).querySchedules("New York", "Boston");
  }

  @Test
  void testBookTicket() {
    when(mockService.bookTicket("trip-456", "Jane Doe"))
        .thenReturn(CompletableFuture.completedFuture("Booking confirmed"));

    String result = tools.bookTicket("trip-456", "Jane Doe");
    assertEquals("Booking confirmed", result);
    verify(mockService).bookTicket("trip-456", "Jane Doe");
  }

  @Test
  void testSearchPolicies() {
    when(mockService.searchPolicies("pets"))
        .thenReturn(CompletableFuture.completedFuture("[Pets policy]"));

    String result = tools.searchPolicies("pets");
    assertEquals("[Pets policy]", result);
    verify(mockService).searchPolicies("pets");
  }
}
