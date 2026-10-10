package com.labcore.platform.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class DashboardDtos {
  private DashboardDtos() {}

  public record Summary(
      DashboardPeriod period,
      List<FilterOption> availableAreas,
      List<Metric> metrics,
      List<StatusDistribution> serviceOrderStatuses,
      List<StatusDistribution> workOrderStatuses,
      List<TimeSeriesPoint> activityTrend,
      String activityTrendLabel,
      List<RankingItem> topServices,
      List<Distribution> distributions,
      List<CycleTime> cycleTimes,
      List<Alert> alerts,
      Instant generatedAt) {}

  public record DashboardPeriod(LocalDate from, LocalDate to, UUID laboratoryAreaId) {}

  public record FilterOption(UUID id, String label) {}

  public record Metric(
      String key, String label, long value, String context, String path, String trend) {}

  public record StatusDistribution(String status, String label, long value) {}

  public record TimeSeriesPoint(LocalDate date, long value) {}

  public record RankingItem(UUID id, String label, long value, String context, String path) {}

  public record Distribution(String key, String title, List<DistributionItem> items) {}

  public record DistributionItem(String key, String label, long value) {}

  public record CycleTime(String key, String label, Double averageHours, String context) {}

  public record Alert(
      String type,
      String severity,
      UUID resourceId,
      String code,
      String title,
      String description,
      Instant dueAt,
      String path) {}
}
