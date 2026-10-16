package com.odontocare.agent.service;

public class AgentInterrupted extends RuntimeException {
  public AgentInterrupted() {
    super("La automatización fue interrumpida por el control de la conversación.");
  }
}
