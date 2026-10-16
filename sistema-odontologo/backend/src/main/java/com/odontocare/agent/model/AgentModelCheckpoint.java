package com.odontocare.agent.model;

import java.util.List;
import java.util.Map;

/** Internal administrative context only; never part of an HTTP response. */
public record AgentModelCheckpoint(
    List<Map<String, Object>> messages, List<Map<String, Object>> evidence, int completedCalls) {}
