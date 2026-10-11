import type { TechnicalResultTask, WorkOrderTest } from '../../../shared/types/domain';

export function toWorkOrderTest(task: TechnicalResultTask): WorkOrderTest {
  return {
    id: task.workOrderTestId,
    sourceSampleTestId: '',
    testDefinitionId: task.testDefinitionId,
    testName: task.testName,
    testMethodId: '',
    testMethodName: task.methodName,
    assignedAnalystId: '',
    assignedAnalystName: task.assignedAnalystName,
    status: task.testStatus,
  };
}
