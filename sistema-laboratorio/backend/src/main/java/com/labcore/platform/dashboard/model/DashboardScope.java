package com.labcore.platform.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

record DashboardScope(
    LocalDate from,
    LocalDate to,
    Instant fromInclusive,
    Instant toExclusive,
    UUID laboratoryAreaId,
    UUID analystId,
    String timeZone) {}
