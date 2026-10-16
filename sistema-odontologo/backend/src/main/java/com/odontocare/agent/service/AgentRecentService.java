package com.odontocare.agent.service;

import com.odontocare.agent.model.AgentRun;
import com.odontocare.agent.repository.AgentRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Reuses only a service the user actually named, never one guessed in an old reservation. */
@Component
public class AgentRecentService {
  private final AgentRepository runs;
  private final ObjectMapper mapper;

  public AgentRecentService(AgentRepository runs, ObjectMapper mapper) {
    this.runs = runs;
    this.mapper = mapper;
  }

  public Optional<String> selectedName(AgentRun run, Instant receivedAt) {
    for (var lookup : runs.recentCatalogLookups(run, receivedAt.minusSeconds(1800))) {
      var service = mapper.readTree(lookup.result()).path("items").get(0);
      if (service == null) continue;
      String name = service.path("name").asString("");
      String request = " " + AgentIdentityService.normalize(lookup.request()) + " ";
      boolean named =
          Arrays.stream(AgentIdentityService.normalize(name).split(" "))
              .filter(word -> word.length() >= 4)
              .filter(
                  word ->
                      !Set.of(
                              "dental",
                              "general",
                              "servicio",
                              "tratamiento",
                              "odontologico",
                              "odontologica")
                          .contains(word))
              .anyMatch(word -> request.contains(" " + word + " "));
      if (named && !name.isBlank()) return Optional.of(name);
    }
    return Optional.empty();
  }
}
