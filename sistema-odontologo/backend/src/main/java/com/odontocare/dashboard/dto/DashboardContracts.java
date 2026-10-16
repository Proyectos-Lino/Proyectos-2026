package com.odontocare.dashboard.dto;

import com.odontocare.reports.dto.ReportContracts.Metric;
import java.time.*;
import java.util.*;

public final class DashboardContracts {
  private DashboardContracts() {}

  public record Appointments(
      long total, long waiting, long inProgress, long attended, long cancelled, long noShow) {}

  public record Conversations(long pending, long human) {}

  public record Point(LocalDate date, String collections, String expenses) {}

  public record Money(
      String currency,
      String received,
      String returned,
      String collections,
      String expenses,
      String net,
      long movements,
      List<Point> trend,
      String granularity) {}

  public record Response(
      LocalDate today,
      LocalDate day,
      LocalDate from,
      LocalDate to,
      String timeZone,
      Instant updatedAt,
      Appointments appointments,
      Conversations conversations,
      List<Money> money,
      List<Metric> overdue,
      List<Metric> balances) {}
}
