package com.odontocare.agent.service;

import com.odontocare.shared.web.ApiException;
import java.text.Normalizer;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.Pattern;

/** Resolves an unambiguous Spanish weekday against the received message's local date. */
final class AgentRequestedDate {
  private static final List<String> WEEKDAYS =
      List.of("lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo");

  static Optional<LocalDate> resolve(String message, LocalDate receivedDate) {
    String text =
        Normalizer.normalize(message.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
    var found = new ArrayList<DayOfWeek>();
    for (int i = 0; i < WEEKDAYS.size(); i++)
      if (Pattern.compile("\\b" + WEEKDAYS.get(i) + "\\b").matcher(text).find())
        found.add(DayOfWeek.of(i + 1));
    var absolute = new LinkedHashSet<LocalDate>();
    var iso = Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b").matcher(text);
    var local = Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})/(\\d{4})\\b").matcher(text);
    try {
      while (iso.find())
        absolute.add(
            LocalDate.of(
                Integer.parseInt(iso.group(1)),
                Integer.parseInt(iso.group(2)),
                Integer.parseInt(iso.group(3))));
      while (local.find())
        absolute.add(
            LocalDate.of(
                Integer.parseInt(local.group(3)),
                Integer.parseInt(local.group(2)),
                Integer.parseInt(local.group(1))));
    } catch (DateTimeException failure) {
      throw ApiException.badRequest("La fecha indicada no existe. Confirma el día que deseas.");
    }
    if (absolute.size() > 1)
      throw ApiException.badRequest("Indica una sola fecha para consultar el horario.");
    if (absolute.size() == 1) {
      var date = absolute.iterator().next();
      if (found.size() == 1 && found.getFirst() != date.getDayOfWeek())
        throw ApiException.badRequest(
            "La fecha y el día de semana indicados no coinciden. ¿Cuál deseas?");
      return Optional.of(date);
    }
    if (Pattern.compile(
            "\\b(?:enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre)\\b")
        .matcher(text)
        .find()) return Optional.empty();
    if (found.size() != 1) return Optional.empty();
    var day = found.getFirst();
    if (text.contains("proxima semana"))
      return Optional.of(
          receivedDate.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusDays(day.getValue() - 1));
    boolean next =
        text.matches("(?s).*\\bproxim[oa]\\s+" + WEEKDAYS.get(day.getValue() - 1) + "\\b.*");
    return Optional.of(
        receivedDate.with(next ? TemporalAdjusters.next(day) : TemporalAdjusters.nextOrSame(day)));
  }

  private AgentRequestedDate() {}
}
