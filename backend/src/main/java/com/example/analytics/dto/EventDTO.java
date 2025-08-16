package com.example.analytics.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EventDTO {
  @NotBlank(message = "timestamp is required") private String timestamp;
  @NotBlank(message = "user_id is required") private String user_id;
  @NotBlank(message = "event_type is required") private String event_type;
  @NotBlank(message = "page_url is required") private String page_url;
  @NotBlank(message = "session_id is required") private String session_id;
}
