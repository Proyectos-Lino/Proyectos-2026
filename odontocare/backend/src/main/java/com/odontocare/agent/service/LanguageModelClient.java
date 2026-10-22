package com.odontocare.agent.service;

import java.util.*;

public interface LanguageModelClient {
  record ToolCall(String id, String name, String arguments) {}

  record Reply(String content, List<ToolCall> tools, int inputTokens, int outputTokens) {}

  Reply reply(List<Map<String, Object>> messages, List<Map<String, Object>> tools);
}
