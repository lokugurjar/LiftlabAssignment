package com.example.analytics;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationTests {

  @Autowired private MockMvc mvc;

  @Test void healthWorks() throws Exception {
    mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
  }

  @Test void validationErrors() throws Exception {
    String bad = "{"
      + "\"timestamp\":\"2024-03-15T14:30:00\","
      + "\"user_id\":\"u\","
      + "\"event_type\":\"page_view\","
      + "\"session_id\":\"s\""
      + "}";
    mvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(bad))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error").value("invalid_event"));
  }
}
