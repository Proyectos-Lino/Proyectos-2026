package com.odontocare.agent.service;

import com.odontocare.agent.model.AgentRun;
import com.odontocare.agent.repository.*;
import com.odontocare.agent.repository.AgentPriceSequenceRepository.Sequence;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.*;

/** Persistent disclosure budget; model output cannot bypass a redacted catalogue result. */
@Service
public class AgentPricingPolicy {
  public static final String NOTICE =
      "Ya alcanzamos el límite de consultas de precios seguidas. Por ahora no puedo darte más"
          + " precios. Si deseas, puedo ayudarte a reservar una cita; para más información te"
          + " atenderá personalmente el consultorio.";
  private final AgentPriceSequenceRepository sequences;
  private final AgentInboxRepository inbox;
  private final ObjectMapper mapper;

  public AgentPricingPolicy(
      AgentPriceSequenceRepository sequences, AgentInboxRepository inbox, ObjectMapper mapper) {
    this.sequences = sequences;
    this.inbox = inbox;
    this.mapper = mapper;
  }

  public static boolean asksPrice(String text) {
    var normalized = AgentIdentityService.normalize(text);
    return normalized.matches(
        "(?s).*(\\b(precio|precios|costo|costos|coste|tarifa|tarifas|cotizacion|cotizaciones|cotizar|valor)\\b|\\bcuanto"
            + " (esta|es|sale|vale|cuesta|cobras|cobran|cobra)).*");
  }

  public static boolean priceOnly(String text) {
    return asksPrice(text)
        && (AgentConsent.forbidsProposal(text)
            || !AgentIdentityService.normalize(text)
                .matches(
                    "(?s).*\\b(cita|citas|reserva|reservar|agendar|horarios|disponibilidad|reprogramar|cancelar)\\b.*"));
  }

  public String response(Map<?, ?> catalogue) {
    var text = new StringBuilder();
    var items = (List<?>) catalogue.get("items");
    for (var raw : items)
      if (raw instanceof Map<?, ?> item && item.get("price") instanceof Number price) {
        text.append("• ")
            .append(item.get("name"))
            .append(": ")
            .append(catalogue.get("currency"))
            .append(" ")
            .append(new BigDecimal(price.toString()).setScale(2).toPlainString())
            .append(" · ")
            .append(item.get("duration_minutes"))
            .append(" minutos.\n");
      }
    if (Boolean.TRUE.equals(catalogue.get("prices_restricted"))) text.append("\n").append(NOTICE);
    else text.append("\nSi deseas una cita, puedo ayudarte a coordinarla.");
    return text.toString();
  }

  private Sequence active(AgentRun run) {
    var message = inbox.message(run.messageId(), false).orElseThrow();
    var state = sequences.lock(run.conversationId(), message.source());
    if (state.lastQueryAt() != null
        && !state.lastQueryAt().isAfter(message.createdAt().minusSeconds(1800))) {
      state = new Sequence(new LinkedHashSet<>(), null, 0, null);
      sequences.save(run.conversationId(), message.source(), state);
    }
    return state;
  }

  @Transactional
  public Map<String, Object> catalogue(
      AgentRun run, List<Map<String, Object>> services, String currency, int page) {
    var message = inbox.message(run.messageId(), false).orElseThrow();
    var state = active(run);
    if (asksPrice(message.body())) {
      state =
          new Sequence(state.services(), message.createdAt(), state.warnings(), state.deniedRun());
      sequences.save(run.conversationId(), message.source(), state);
    }
    var permitted = new LinkedHashSet<>(state.services());
    for (var prior : lookups(run))
      if (prior.hasNonNull("price")) permitted.add(UUID.fromString(prior.path("id").asString()));
    int limit = sequences.limit();
    boolean restricted = false;
    var result = new ArrayList<Map<String, Object>>();
    for (var service : services) {
      UUID id = UUID.fromString(service.get("id").toString());
      var item = new LinkedHashMap<>(service);
      if (!permitted.contains(id) && permitted.size() >= limit) {
        item.remove("price");
        item.put("price_restricted", true);
        restricted = true;
      } else {
        permitted.add(id);
        item.put("price_restricted", false);
      }
      result.add(item);
    }
    return Map.of(
        "items",
        result,
        "currency",
        currency,
        "page",
        page,
        "page_size",
        5,
        "prices_restricted",
        restricted,
        "price_limit_notice",
        restricted ? NOTICE : "");
  }

  private List<JsonNode> lookups(AgentRun run) {
    var result = new ArrayList<JsonNode>();
    for (var data : sequences.lookups(run.id()))
      mapper.readTree(data).path("items").forEach(result::add);
    return result;
  }

  @Transactional
  public boolean denied(AgentRun run) {
    var message = inbox.message(run.messageId(), false).orElseThrow();
    var state = active(run);
    if (run.id().equals(state.deniedRun())) return state.warnings() > 1;
    int warnings = state.warnings() + 1;
    sequences.save(
        run.conversationId(),
        message.source(),
        new Sequence(state.services(), message.createdAt(), warnings, run.id()));
    return warnings > 1;
  }

  public record Outgoing(String text, boolean handoff) {}

  @Transactional
  public Outgoing outgoing(AgentRun run, String text) {
    // Confirmation UUIDs and administrative identifiers are not money amounts.
    boolean money =
        text.matches(
            "(?is).*(S\\s*/|\\b(?:PEN|USD|EUR)\\b|[$€]|\\b(?:soles|d[oó]lares|euros)\\b).*");
    if (!money) return new Outgoing(text, false);
    var items = lookups(run);
    var normalized = " " + AgentIdentityService.normalize(text) + " ";
    Set<BigDecimal> amounts;
    try {
      amounts = AgentCatalogEvidence.quotedAmounts(text);
    } catch (NumberFormatException e) {
      return refusal(run);
    }
    if (amounts.isEmpty()) return new Outgoing(text, false);
    for (var item : items)
      if (!item.hasNonNull("price")
          && normalized.contains(
              " " + AgentIdentityService.normalize(item.path("name").asString()) + " "))
        return refusal(run);
    var candidates = new LinkedHashSet<UUID>();
    var backed = new HashSet<BigDecimal>();
    for (var item : items)
      if (item.hasNonNull("price")) {
        var price = item.path("price").decimalValue().stripTrailingZeros();
        backed.add(price);
        if (amounts.contains(price)
            && (items.size() == 1 || named(item.path("name").asString(), normalized)))
          candidates.add(UUID.fromString(item.path("id").asString()));
      }
    if (!backed.containsAll(amounts)) return refusal(run);
    if (candidates.isEmpty())
      for (var item : items)
        if (item.hasNonNull("price")
            && amounts.contains(item.path("price").decimalValue().stripTrailingZeros()))
          candidates.add(UUID.fromString(item.path("id").asString()));
    var message = inbox.message(run.messageId(), false).orElseThrow();
    var state = active(run);
    var next = new LinkedHashSet<>(state.services());
    next.addAll(candidates);
    if (next.size() > sequences.limit()) return refusal(run);
    sequences.save(
        run.conversationId(),
        message.source(),
        new Sequence(next, message.createdAt(), state.warnings(), state.deniedRun()));
    return new Outgoing(text, false);
  }

  private boolean named(String name, String normalized) {
    if (normalized.contains(" " + AgentIdentityService.normalize(name) + " ")) return true;
    return Arrays.stream(AgentIdentityService.normalize(name).split(" "))
        .filter(word -> word.length() >= 4)
        .filter(word -> !Set.of("dental", "general", "servicio", "tratamiento").contains(word))
        .anyMatch(word -> normalized.contains(" " + word + " "));
  }

  private Outgoing refusal(AgentRun run) {
    boolean handoff = denied(run);
    return new Outgoing(
        NOTICE + (handoff ? " Voy a dejar tu consulta pendiente de atención personal." : ""),
        handoff);
  }

  @Transactional
  public void resetForReservation(AgentRun run) {
    var message = inbox.message(run.messageId(), false).orElseThrow();
    sequences.lock(run.conversationId(), message.source());
    sequences.save(
        run.conversationId(), message.source(), new Sequence(new LinkedHashSet<>(), null, 0, null));
  }
}
