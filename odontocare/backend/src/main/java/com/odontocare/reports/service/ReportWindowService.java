package com.odontocare.reports.service;

import com.odontocare.installation.repository.InstallationProfileRepository;
import com.odontocare.reports.dto.ReportContracts.Window;
import com.odontocare.reports.dto.ReportQuery;
import com.odontocare.shared.web.ApiException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

@Component
public class ReportWindowService {
  private final InstallationProfileRepository profiles;
  private final Clock clock;

  public ReportWindowService(InstallationProfileRepository profiles, Clock clock) {
    this.profiles = profiles;
    this.clock = clock;
  }

  public Window resolve(ReportQuery query) {
    ZoneId zone = ZoneId.of(profiles.findById((short) 1).orElseThrow().getTimeZone());
    LocalDate today = LocalDate.now(clock.withZone(zone));
    LocalDate to = query.getTo() == null ? today : query.getTo();
    LocalDate from = query.getFrom() == null ? to.withDayOfMonth(1) : query.getFrom();
    if (from.isAfter(to)
        || ChronoUnit.DAYS.between(from, to) > 365
        || from.isBefore(LocalDate.of(1900, 1, 1))
        || to.isAfter(LocalDate.of(2200, 12, 31)))
      throw ApiException.badRequest("Selecciona un período válido de hasta un año (366 días).");
    return new Window(from, to, zone);
  }

  public LocalDate today(ZoneId zone) {
    return LocalDate.now(clock.withZone(zone));
  }

  public Instant now() {
    return clock.instant();
  }
}
