package com.odontocare.agent.service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Checks numeric catalogue claims before an assistant response can reach the patient. */
final class AgentCatalogEvidence {
  private static final Pattern PRICE =
      Pattern.compile(
          "(?:S\\s*/\\.?|[A-Z]{3}|[$€])\\s*\\*{0,2}\\s*(\\d[\\d.,]*)|"
              + "(\\d[\\d.,]*)\\s*\\*{0,2}\\s*(?:soles|dolares|euros)\\b");
  private static final Pattern MINUTES =
      Pattern.compile("(\\d+)\\s*\\*{0,2}\\s*min(?:utos|s|\\.)?\\b");
  private final Set<BigDecimal> prices = new HashSet<>();
  private final Set<Integer> durations = new HashSet<>();

  void record(String tool, Object result) {
    if (!tool.equals("consultar_servicios")
        || !(result instanceof Map<?, ?> data)
        || !(data.get("items") instanceof List<?> items)) return;
    for (Object item : items) {
      if (!(item instanceof Map<?, ?> service)) continue;
      if (service.get("price") instanceof Number price)
        prices.add(new BigDecimal(price.toString()).stripTrailingZeros());
      if (service.get("duration_minutes") instanceof Number duration)
        durations.add(duration.intValue());
    }
  }

  boolean supports(String response) {
    String text =
        Normalizer.normalize(response, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replace('\u202f', ' ')
            .replace('\u00a0', ' ');
    var amounts = PRICE.matcher(text);
    while (amounts.find()) {
      String value = amounts.group(1) != null ? amounts.group(1) : amounts.group(2);
      try {
        if (!prices.contains(amount(value))) return false;
      } catch (NumberFormatException failure) {
        return false;
      }
    }
    var minutes = MINUTES.matcher(text.toLowerCase(Locale.ROOT));
    while (minutes.find()) {
      if (!durations.contains(Integer.parseInt(minutes.group(1)))) return false;
    }
    return true;
  }

  static Set<BigDecimal> quotedAmounts(String response) {
    var result = new HashSet<BigDecimal>();
    var match = PRICE.matcher(response.replace('\u202f', ' ').replace('\u00a0', ' '));
    while (match.find())
      result.add(amount(match.group(1) != null ? match.group(1) : match.group(2)));
    return result;
  }

  private static BigDecimal amount(String value) {
    value = value.replaceAll("[.,]+$", "");
    int comma = value.lastIndexOf(','), dot = value.lastIndexOf('.');
    if (comma >= 0 && dot >= 0)
      value = comma > dot ? value.replace(".", "").replace(',', '.') : value.replace(",", "");
    else if (comma >= 0) value = value.replace(',', '.');
    return new BigDecimal(value).stripTrailingZeros();
  }
}
