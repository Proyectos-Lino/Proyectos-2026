package com.odontocare.agent.service;

import java.text.Normalizer;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Pattern;

/** Resolves explicit, unambiguous clock times without guessing a time from dates or ranges. */
final class AgentRequestedTime {
  private static final Pattern CLOCK =
      Pattern.compile(
          "(?<![\\d:])(?:(\\d{1,2}):(\\d{2})(?:\\s*(am|pm)\\b)?|(\\d{1,2})\\s*(am|pm)\\b)(?![\\d:])");

  private AgentRequestedTime() {}

  static Optional<LocalTime> resolve(String body) {
    String text =
        Normalizer.normalize(body.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replaceAll("de la manana", "am")
            .replaceAll("de la tarde|de la noche", "pm")
            .replaceAll("([ap])\\.\\s*m\\.", "$1m");
    var found = new HashSet<LocalTime>();
    var matcher = CLOCK.matcher(text);
    while (matcher.find()) {
      int hour = Integer.parseInt(matcher.group(1) != null ? matcher.group(1) : matcher.group(4));
      int minute = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
      String meridian = matcher.group(3) != null ? matcher.group(3) : matcher.group(5);
      if (minute > 59 || hour > 23 || (meridian != null && (hour < 1 || hour > 12)))
        return Optional.empty();
      if (meridian != null) hour = hour % 12 + (meridian.equals("pm") ? 12 : 0);
      found.add(LocalTime.of(hour, minute));
    }
    return found.size() == 1 ? Optional.of(found.iterator().next()) : Optional.empty();
  }
}
