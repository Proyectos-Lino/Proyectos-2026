package com.labcore.platform.orders;

public enum ServiceOrderHistoryAction {
  CREATED,
  COMMERCIAL_DETAILS_UPDATED,
  SAMPLES_UPDATED,
  COMMERCIAL_CORRECTION_REQUESTED,
  COMMERCIAL_CORRECTION_SUBMITTED,
  COMMERCIAL_CORRECTION_APPROVED,
  TECHNICAL_DATA_INVALIDATED,
  CONFIRMED,
  /** Legacy value retained while historical databases are migrated by Flyway. */
  PROCESSING_STARTED,
  FINALIZED,
  CANCELLED
}
