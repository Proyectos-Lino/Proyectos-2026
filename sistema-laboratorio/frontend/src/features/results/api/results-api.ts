import { ApiError, request } from '../../../shared/api/http-client';
import type {
  InitialResultTemplate,
  PageResponse,
  ResultResponse,
  TechnicalResultTask,
} from '../../../shared/types/domain';

export interface RecordResultInput {
  analystObservation: string;
  revisionReason?: string;
  values: Array<{
    fieldKey: string;
    label: string;
    repetitionIndex: number;
    valueText?: string;
    valueNumber?: number;
    measurementUnitId?: string;
  }>;
  matrix?: {
    rowCount: number;
    columnCount: number;
    columns: Array<{
      columnIndex: number;
      carrierLabel?: string;
      distance?: number;
      diameter?: number;
      shape?: string;
    }>;
    cells: Array<{
      rowIndex: number;
      columnIndex: number;
      valueText?: string;
      valueNumber?: number;
      categoryCode?: string;
    }>;
  };
}

export interface CorrectAndValidateResultInput extends Omit<RecordResultInput, 'revisionReason'> {
  reason: string;
  comment?: string;
}

export interface CalculatedValuePreview {
  fieldKey: string;
  label: string;
  repetitionIndex: number;
  value: number;
  measurementUnitId?: string;
  formula: string;
  inputs: Record<string, number[]>;
}

export function getResultEntryQueue(page = 0): Promise<PageResponse<TechnicalResultTask>> {
  return request<PageResponse<TechnicalResultTask>>(`/results/entry-queue?page=${page}&size=25`);
}

export async function getBudResultQueue(): Promise<PageResponse<TechnicalResultTask>> {
  const firstPage = await request<PageResponse<TechnicalResultTask>>(
    '/results/entry-queue?page=0&size=100',
  );
  const remainingPages = await Promise.all(
    Array.from({ length: Math.max(0, firstPage.totalPages - 1) }, (_, index) =>
      request<PageResponse<TechnicalResultTask>>(`/results/entry-queue?page=${index + 1}&size=100`),
    ),
  );
  const content = [firstPage, ...remainingPages]
    .flatMap((page) => page.content)
    .filter((task) => task.resultMode === 'MATRIX');
  return {
    content,
    page: 0,
    size: 100,
    totalElements: content.length,
    totalPages: content.length ? 1 : 0,
  };
}

export function getResultValidationQueue(page = 0): Promise<PageResponse<TechnicalResultTask>> {
  return request<PageResponse<TechnicalResultTask>>(
    `/results/validation-queue?page=${page}&size=25`,
  );
}

export function getResult(workOrderTestId: string): Promise<ResultResponse> {
  return request<ResultResponse>(`/work-order-tests/${workOrderTestId}/result`);
}

export async function findResult(workOrderTestId: string): Promise<ResultResponse | null> {
  try {
    return await getResult(workOrderTestId);
  } catch (reason) {
    if (reason instanceof ApiError && reason.status === 404) {
      return null;
    }
    throw reason;
  }
}

export async function getInitialResultTemplate(
  workOrderTestId: string,
): Promise<InitialResultTemplate | null> {
  try {
    return await request<InitialResultTemplate>(
      `/work-order-tests/${workOrderTestId}/result/template`,
    );
  } catch (reason) {
    if (reason instanceof ApiError && reason.status === 404) {
      return null;
    }
    throw reason;
  }
}

export function recordResult(
  workOrderTestId: string,
  input: RecordResultInput,
): Promise<ResultResponse> {
  return request<ResultResponse>(`/work-order-tests/${workOrderTestId}/result`, {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function startMatrixExecution(
  workOrderTestId: string,
  declaredRowCount: number,
  declaredColumnCount: number,
): Promise<ResultResponse> {
  return request<ResultResponse>(
    `/work-order-tests/${workOrderTestId}/result/matrix-execution/start`,
    {
      method: 'POST',
      body: JSON.stringify({ declaredRowCount, declaredColumnCount }),
    },
  );
}

export function saveMatrixDraft(
  workOrderTestId: string,
  matrix: NonNullable<RecordResultInput['matrix']>,
  analystObservation: string,
  adjustmentReason?: string,
): Promise<ResultResponse> {
  return request<ResultResponse>(
    `/work-order-tests/${workOrderTestId}/result/matrix-execution/draft`,
    {
      method: 'POST',
      body: JSON.stringify({ matrix, analystObservation, adjustmentReason }),
    },
  );
}

export function previewResultCalculations(
  workOrderTestId: string,
  values: RecordResultInput['values'],
): Promise<{ values: CalculatedValuePreview[] }> {
  return request<{ values: CalculatedValuePreview[] }>(
    `/work-order-tests/${workOrderTestId}/result/calculate-preview`,
    {
      method: 'POST',
      body: JSON.stringify({ values }),
    },
  );
}

export function validateResult(workOrderTestId: string, comment: string): Promise<ResultResponse> {
  return request<ResultResponse>(`/work-order-tests/${workOrderTestId}/result/validate`, {
    method: 'POST',
    body: JSON.stringify({ comment }),
  });
}

export function rejectResult(workOrderTestId: string, reason: string): Promise<ResultResponse> {
  return request<ResultResponse>(`/work-order-tests/${workOrderTestId}/result/reject`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  });
}

export function correctAndValidateResult(
  workOrderTestId: string,
  input: CorrectAndValidateResultInput,
): Promise<ResultResponse> {
  return request<ResultResponse>(
    `/work-order-tests/${workOrderTestId}/result/correct-and-validate`,
    {
      method: 'POST',
      body: JSON.stringify(input),
    },
  );
}
