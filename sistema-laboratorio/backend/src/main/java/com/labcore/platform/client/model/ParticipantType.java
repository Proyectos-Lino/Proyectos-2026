package com.labcore.platform.client;

public enum ParticipantType {
  CLIENT,
  PRODUCER,
  BOTH;

  public boolean canActAsClient() {
    return this == CLIENT || this == BOTH;
  }

  public boolean canActAsProducer() {
    return this == PRODUCER || this == BOTH;
  }
}
