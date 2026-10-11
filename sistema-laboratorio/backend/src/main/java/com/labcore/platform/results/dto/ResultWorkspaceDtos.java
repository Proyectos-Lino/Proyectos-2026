package com.labcore.platform.results;

import com.labcore.platform.catalog.ResultMode;
import com.labcore.platform.workorders.WorkOrderTestStatus;
import java.time.Instant;
import java.util.UUID;

public final class ResultWorkspaceDtos {
  private ResultWorkspaceDtos() {}

  public record TechnicalResultTask(
      UUID workOrderTestId,
      UUID workOrderId,
      String workOrderCode,
      String laboratoryAreaName,
      Instant laboratoryDueAt,
      String laboratorySampleCode,
      String sampleTypeName,
      String testName,
      String methodName,
      UUID testDefinitionId,
      ResultMode resultMode,
      WorkOrderTestStatus testStatus,
      String assignedAnalystName,
      UUID resultId,
      ResultEntryStatus resultStatus,
      String recordedByName,
      Instant recordedAt,
      String correctionRequestReason,
      String correctionRequestedByName,
      String correctionRequestedByRole,
      Instant correctionRequestedAt) {}
}
