package com.odontocare.whatsapp.dto;

import jakarta.validation.constraints.*;

/** Bounded cursor query; cursors refer to persisted message arrival order. */
public class ChatQuery {
  @Positive private Long before;
  @Positive private Long after;

  @Min(1)
  @Max(50)
  private int size = 30;

  @Size(max = 160)
  private String search = "";

  @Pattern(regexp = "|INBOUND|OUTBOUND")
  private String messageDirection = "";

  @Pattern(
      regexp = "|RECEIVED|UNSUPPORTED|QUEUED|SENDING|ACCEPTED|SENT|DELIVERED|READ|FAILED|UNKNOWN")
  private String status = "";

  @AssertTrue(message = "Usa solo un cursor de mensajes a la vez.")
  public boolean isSingleCursor() {
    return before == null || after == null;
  }

  public Long getBefore() {
    return before;
  }

  public void setBefore(Long value) {
    before = value;
  }

  public Long getAfter() {
    return after;
  }

  public void setAfter(Long value) {
    after = value;
  }

  public int getSize() {
    return size;
  }

  public void setSize(int value) {
    size = value;
  }

  public String getSearch() {
    return search.strip();
  }

  public void setSearch(String value) {
    search = value == null ? "" : value;
  }

  public String getMessageDirection() {
    return messageDirection;
  }

  public void setMessageDirection(String value) {
    messageDirection = value == null ? "" : value;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String value) {
    status = value == null ? "" : value;
  }
}
