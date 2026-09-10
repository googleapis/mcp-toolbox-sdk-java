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

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;

@ExtendWith(MockitoExtension.class)
@Timeout(value = 10, unit = TimeUnit.SECONDS)
public class TransitAgentControllerTest {

  @Mock private TransitAgent mockAgent;

  private TransitAgentController agentController;
  private CymbalTransitController cymbalController;

  @BeforeEach
  void setUp() {
    agentController = new TransitAgentController(mockAgent);
    cymbalController = new CymbalTransitController();
  }

  @Test
  void testIndexEndpoint() {
    assertEquals("index", cymbalController.index());
  }

  @Test
  void testHandleUserChatSuccess() {
    MockHttpSession session = new MockHttpSession();
    String sessionId = session.getId();
    String userMessage = "Find buses to Boston";
    String expectedResponse = "Here are the schedules: 09:00 AM, 12:00 PM.";

    when(mockAgent.chat(sessionId, userMessage)).thenReturn(expectedResponse);

    ResponseEntity<String> response = agentController.handleUserChat(userMessage, session);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(expectedResponse, response.getBody());
    verify(mockAgent).chat(sessionId, userMessage);
  }
}
