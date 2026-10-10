import { useEffect, useMemo, useState } from 'react';
import { getErrorMessage } from '../../../shared/lib/errors';
import type {
  InitialResultTemplate,
  ResultResponse,
  TestDefinition,
  WorkOrderTest,
} from '../../../shared/types/domain';
import {
  correctAndValidateResult,
  findResult,
  getInitialResultTemplate,
  previewResultCalculations,
  saveMatrixDraft,
  startMatrixExecution,
  type RecordResultInput,
  recordResult,
  rejectResult,
  validateResult,
} from '../api/results-api';

interface ResultEditorOptions {
  definition: TestDefinition | undefined;
  onSaved: () => void;
  test: WorkOrderTest;
}

export interface ResultFieldInput {
  fieldKey: string;
  label: string;
  measurementUnitId?: string;
  required: boolean;
  resultType: 'NUMERIC' | 'TEXT' | 'QUALITATIVE';
  repetitionIndex: number;
  value: string;
  fieldKind: 'CAPTURED' | 'CALCULATED' | 'INFORMATIONAL';
  readOnly: boolean;
  formula?: string;
  calculationScope: 'PER_REPETITION' | 'AGGREGATE';
  defaultValue?: string;
}

export interface MatrixColumnInput {
  carrierLabel: string;
  diameter: string;
  distance: string;
  shape: string;
}

export interface MatrixCellInput {
  categoryCode: string;
  value: string;
}

export type ResultReviewDecision = 'APPROVE' | 'CORRECT_AND_VALIDATE' | 'REJECT';

function defaultFields(definition: TestDefinition | undefined): ResultFieldInput[] {
  return [
    {
      fieldKey: 'resultado',
      label: 'Resultado',
      measurementUnitId: definition?.resultUnitId,
      required: true,
      resultType: definition?.resultType ?? 'TEXT',
      repetitionIndex: 0,
      value: '',
      fieldKind: 'CAPTURED',
      readOnly: false,
      calculationScope: 'PER_REPETITION',
    },
  ];
}

function fieldsFor(
  template: InitialResultTemplate | null,
  definition: TestDefinition | undefined,
  result: ResultResponse | null,
): ResultFieldInput[] {
  const storedValues = new Map(
    result?.values.map((value) => [
      `${value.fieldKey}:${value.repetitionIndex}`,
      value.valueNumber?.toString() ?? value.valueText ?? '',
    ]) ?? [],
  );
  const baseFields = template?.fields.length
    ? template.fields.filter((field) => field.fieldKind !== 'INFORMATIONAL')
    : defaultFields(definition);
  const perReadingFields = baseFields.filter(
    (field) => field.fieldKind !== 'CALCULATED' || field.calculationScope !== 'AGGREGATE',
  );
  const aggregateFields = baseFields.filter(
    (field) => field.fieldKind === 'CALCULATED' && field.calculationScope === 'AGGREGATE',
  );
  const storedRepetitions = result?.values.map((value) => value.repetitionIndex) ?? [];
  const repetitionCount =
    template?.resultMode === 'REPEATABLE'
      ? Math.max(template.minimumRepetitions, ...storedRepetitions.map((index) => index + 1), 1)
      : 1;

  return Array.from({ length: repetitionCount }, (_, repetitionIndex) =>
    perReadingFields.map((field) => ({
      fieldKey: field.fieldKey,
      label: field.label,
      measurementUnitId: field.measurementUnitId,
      required: field.required,
      resultType: field.resultType,
      repetitionIndex,
      value:
        storedValues.get(`${field.fieldKey}:${repetitionIndex}`) ??
        (field.fieldKind === 'CAPTURED' && field.readOnly ? (field.defaultValue ?? '') : ''),
      fieldKind: field.fieldKind,
      readOnly: field.readOnly,
      formula: field.formula,
      calculationScope: field.calculationScope,
      defaultValue: field.defaultValue,
    })),
  )
    .flat()
    .concat(
      aggregateFields.map((field) => ({
        fieldKey: field.fieldKey,
        label: field.label,
        measurementUnitId: field.measurementUnitId,
        required: field.required,
        resultType: field.resultType,
        repetitionIndex: 0,
        value: storedValues.get(`${field.fieldKey}:0`) ?? '',
        fieldKind: field.fieldKind,
        readOnly: true,
        formula: field.formula,
        calculationScope: field.calculationScope,
        defaultValue: field.defaultValue,
      })),
    );
}

function capturedValues(fields: ResultFieldInput[]): RecordResultInput['values'] {
  return fields
    .filter((field) => field.fieldKind === 'CAPTURED' && !field.readOnly)
    .map((field) => ({
      fieldKey: field.fieldKey,
      label: field.label,
      repetitionIndex: field.repetitionIndex,
      valueText: field.resultType === 'NUMERIC' ? undefined : field.value,
      valueNumber:
        field.resultType === 'NUMERIC' && field.value.trim() ? Number(field.value) : undefined,
      measurementUnitId: field.measurementUnitId,
    }));
}

export function useResultEditor({ definition, onSaved, test }: ResultEditorOptions) {
  const [existingResult, setExistingResult] = useState<ResultResponse | null>(null);
  const [template, setTemplate] = useState<InitialResultTemplate | null>(null);
  const [fields, setFields] = useState<ResultFieldInput[]>(() => defaultFields(definition));
  const [matrixColumns, setMatrixColumns] = useState<MatrixColumnInput[]>([]);
  const [matrixCells, setMatrixCells] = useState<MatrixCellInput[]>([]);
  const [matrixRows, setMatrixRows] = useState(1);
  const [observation, setObservation] = useState('');
  const [revisionReason, setRevisionReason] = useState('');
  const [validationComment, setValidationComment] = useState('');
  const [reviewDecision, setReviewDecision] = useState<ResultReviewDecision>('APPROVE');
  const [reviewReason, setReviewReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isCalculating, setIsCalculating] = useState(false);
  const [calculationError, setCalculationError] = useState<string | null>(null);

  const capturedInputs = useMemo(() => capturedValues(fields), [fields]);
  const capturedSignature = useMemo(() => JSON.stringify(capturedInputs), [capturedInputs]);
  const hasInvalidCalculationInput = useMemo(
    () =>
      fields
        .filter((field) => field.fieldKind === 'CAPTURED' && !field.readOnly)
        .some(
          (field) =>
            (field.required && !field.value.trim()) ||
            (field.resultType === 'NUMERIC' && Number.isNaN(Number(field.value))),
        ),
    [fields],
  );

  useEffect(() => {
    let isMounted = true;

    async function loadInitialData() {
      setIsLoading(true);
      try {
        const [result, template] = await Promise.all([
          findResult(test.id),
          getInitialResultTemplate(test.id),
        ]);

        if (isMounted) {
          setExistingResult(result);
          setTemplate(template);
          setFields(fieldsFor(template, definition, result));
          const rowCount = result?.matrix?.rowCount ?? template?.defaultRows ?? 1;
          const columnCount = result?.matrix?.columnCount ?? template?.defaultColumns ?? 1;
          setMatrixRows(rowCount);
          setMatrixColumns(
            Array.from({ length: columnCount }, (_, index) => {
              const column = result?.matrix?.columns.find((item) => item.columnIndex === index);
              return {
                carrierLabel: column?.carrierLabel ?? `Cargador ${index + 1}`,
                distance: column?.distance?.toString() ?? '',
                diameter: column?.diameter?.toString() ?? '',
                shape: column?.shape ?? '',
              };
            }),
          );
          setMatrixCells(
            Array.from({ length: rowCount * columnCount }, (_, index) => {
              const rowIndex = Math.floor(index / columnCount);
              const columnIndex = index % columnCount;
              const cell = result?.matrix?.cells.find(
                (item) => item.rowIndex === rowIndex && item.columnIndex === columnIndex,
              );
              return {
                categoryCode: cell?.categoryCode ?? '',
                value: cell?.valueNumber?.toString() ?? cell?.valueText ?? '',
              };
            }),
          );
          setObservation(result?.analystObservation ?? '');
          setRevisionReason('');
          setValidationComment('');
          setReviewDecision('APPROVE');
          setReviewReason('');
          setError(null);
        }
      } catch (reason) {
        if (isMounted) {
          setExistingResult(null);
          setTemplate(null);
          setFields(defaultFields(definition));
          setError(getErrorMessage(reason, 'No se pudo cargar la plantilla de resultados.'));
        }
      } finally {
        if (isMounted) {
          setIsLoading(false);
        }
      }
    }

    void loadInitialData();

    return () => {
      isMounted = false;
    };
  }, [definition, test.id]);

  useEffect(() => {
    const hasCalculations = template?.fields.some((field) => field.fieldKind === 'CALCULATED');
    if (
      !hasCalculations ||
      template?.resultMode === 'MATRIX' ||
      isLoading ||
      hasInvalidCalculationInput
    ) {
      return;
    }

    let active = true;
    const timeout = window.setTimeout(async () => {
      setIsCalculating(true);
      try {
        const previewValues = JSON.parse(capturedSignature) as RecordResultInput['values'];
        const preview = await previewResultCalculations(test.id, previewValues);
        if (!active) return;
        const byKey = new Map(
          preview.values.map((value) => [
            `${value.fieldKey}:${value.repetitionIndex}`,
            value.value.toString(),
          ]),
        );
        setFields((current) =>
          current.map((field) =>
            field.fieldKind === 'CALCULATED'
              ? {
                  ...field,
                  value: byKey.get(`${field.fieldKey}:${field.repetitionIndex}`) ?? field.value,
                }
              : field,
          ),
        );
        setCalculationError(null);
      } catch (reason) {
        if (active) {
          setCalculationError(getErrorMessage(reason, 'No se pudo previsualizar el cálculo.'));
        }
      } finally {
        if (active) setIsCalculating(false);
      }
    }, 350);

    return () => {
      active = false;
      window.clearTimeout(timeout);
    };
  }, [capturedSignature, hasInvalidCalculationInput, isLoading, template, test.id]);

  const isValidated = existingResult?.status === 'VALIDATED';
  const isMatrixMode = template?.resultMode === 'MATRIX';
  const matrixExecution = existingResult?.matrix;
  const hasDimensionAdjustment = Boolean(
    matrixExecution &&
    (matrixExecution.declaredRowCount !== matrixRows ||
      matrixExecution.declaredColumnCount !== matrixColumns.length),
  );

  function updateFieldValue(index: number, value: string) {
    setIsCalculating(false);
    setCalculationError(null);
    setFields((current) =>
      current.map((field, fieldIndex) => (fieldIndex === index ? { ...field, value } : field)),
    );
  }

  function addReading() {
    const repetitionIndex = Math.max(-1, ...fields.map((field) => field.repetitionIndex)) + 1;
    const baseFields = template?.fields.length
      ? template.fields.filter(
          (field) =>
            field.fieldKind !== 'INFORMATIONAL' &&
            !(field.fieldKind === 'CALCULATED' && field.calculationScope === 'AGGREGATE'),
        )
      : defaultFields(definition);
    setFields((current) => [
      ...current,
      ...baseFields.map((field) => ({
        fieldKey: field.fieldKey,
        label: field.label,
        measurementUnitId: field.measurementUnitId,
        required: field.required,
        resultType: field.resultType,
        repetitionIndex,
        value: field.fieldKind === 'CAPTURED' && field.readOnly ? (field.defaultValue ?? '') : '',
        fieldKind: field.fieldKind,
        readOnly: field.readOnly,
        formula: field.formula,
        calculationScope: field.calculationScope,
        defaultValue: field.defaultValue,
      })),
    ]);
  }

  function addMatrixColumn() {
    if (template?.maximumColumns && matrixColumns.length >= template.maximumColumns) {
      return;
    }
    resizeMatrix(matrixRows, matrixColumns.length + 1);
  }

  function addMatrixRow() {
    if (template?.maximumRows && matrixRows >= template.maximumRows) {
      return;
    }
    resizeMatrix(matrixRows + 1, matrixColumns.length);
  }

  function resizeMatrix(nextRowCount: number, nextColumnCount: number) {
    const maximumRows = template?.maximumRows ?? Number.MAX_SAFE_INTEGER;
    const maximumColumns = template?.maximumColumns ?? Number.MAX_SAFE_INTEGER;
    const safeRows = Math.min(Math.max(Math.trunc(nextRowCount), 1), maximumRows);
    const safeColumns = Math.min(Math.max(Math.trunc(nextColumnCount), 1), maximumColumns);
    const previousColumnCount = matrixColumns.length;
    setMatrixColumns((current) =>
      Array.from({ length: safeColumns }, (_, columnIndex) =>
        current[columnIndex]
          ? current[columnIndex]
          : {
              carrierLabel: `Cargador ${columnIndex + 1}`,
              distance: '',
              diameter: '',
              shape: '',
            },
      ),
    );
    setMatrixCells((current) =>
      Array.from({ length: safeRows * safeColumns }, (_, index) => {
        const rowIndex = Math.floor(index / safeColumns);
        const columnIndex = index % safeColumns;
        return (
          current[rowIndex * previousColumnCount + columnIndex] ?? {
            categoryCode: '',
            value: '',
          }
        );
      }),
    );
    setMatrixRows(safeRows);
  }

  function applyBulkMatrixValues(values: string[][]) {
    setMatrixCells((current) =>
      current.map((cell, index) => {
        const rowIndex = Math.floor(index / matrixColumns.length);
        const columnIndex = index % matrixColumns.length;
        const pastedValue = values[rowIndex]?.[columnIndex];
        if (pastedValue === undefined) {
          return cell;
        }
        return template?.categories.length
          ? { ...cell, categoryCode: pastedValue }
          : { ...cell, value: pastedValue };
      }),
    );
  }

  function updateMatrixColumn(index: number, changes: Partial<MatrixColumnInput>) {
    setMatrixColumns((current) =>
      current.map((column, columnIndex) =>
        columnIndex === index ? { ...column, ...changes } : column,
      ),
    );
  }

  function updateMatrixCell(
    rowIndex: number,
    columnIndex: number,
    changes: Partial<MatrixCellInput>,
  ) {
    const index = rowIndex * matrixColumns.length + columnIndex;
    setMatrixCells((current) =>
      current.map((cell, cellIndex) => (cellIndex === index ? { ...cell, ...changes } : cell)),
    );
  }

  async function saveResult() {
    if (isLoading) {
      return false;
    }
    const validationError = editorValidationError();
    if (validationError) {
      setError(validationError);
      return false;
    }

    setIsSaving(true);
    setError(null);

    try {
      await recordResult(test.id, buildResultInput());
      onSaved();
      return true;
    } catch (reason) {
      setError(getErrorMessage(reason, 'No se pudo registrar el resultado.'));
      return false;
    } finally {
      setIsSaving(false);
    }
  }

  async function beginMatrixExecution() {
    if (!isMatrixMode || matrixColumns.length === 0) {
      setError('La evaluación requiere al menos una yema y un cargador.');
      return false;
    }
    setIsSaving(true);
    setError(null);
    try {
      const result = await startMatrixExecution(test.id, matrixRows, matrixColumns.length);
      setExistingResult(result);
      return true;
    } catch (reason) {
      setError(getErrorMessage(reason, 'No se pudo iniciar la evaluación matricial.'));
      return false;
    } finally {
      setIsSaving(false);
    }
  }

  async function saveMatrixProgress() {
    if (!isMatrixMode || !matrixExecution || matrixExecution.executionStatus !== 'IN_PROGRESS') {
      setError('Primero inicia la evaluación matricial.');
      return false;
    }
    if (hasDimensionAdjustment && !revisionReason.trim()) {
      setError('Explica el ajuste de la cantidad declarada antes de guardar.');
      return false;
    }
    setIsSaving(true);
    setError(null);
    try {
      const result = await saveMatrixDraft(
        test.id,
        buildMatrixInput(),
        observation,
        revisionReason || undefined,
      );
      setExistingResult(result);
      setRevisionReason('');
      onSaved();
      return true;
    } catch (reason) {
      setError(getErrorMessage(reason, 'No se pudo guardar el avance de la evaluación.'));
      return false;
    } finally {
      setIsSaving(false);
    }
  }

  function editorValidationError(): string | null {
    const resultMode = template?.resultMode ?? 'SCALAR';
    const invalidField =
      resultMode === 'MATRIX'
        ? undefined
        : fields.find(
            (field) =>
              field.fieldKind === 'CAPTURED' &&
              !field.readOnly &&
              ((field.required && field.value.trim() === '') ||
                (field.resultType === 'NUMERIC' && Number.isNaN(Number(field.value)))),
          );
    if (invalidField) {
      return `Ingresa un valor válido para «${invalidField.label}».`;
    }
    if (resultMode === 'MATRIX' && matrixColumns.length === 0) {
      return 'Agrega al menos un cargador a la matriz.';
    }
    if (resultMode === 'MATRIX' && hasDimensionAdjustment && !revisionReason.trim()) {
      return 'Explica el ajuste de la cantidad declarada de yemas o cargadores antes de finalizar.';
    }
    if (
      resultMode === 'MATRIX' &&
      (matrixColumns.some(
        (column) =>
          !column.carrierLabel.trim() ||
          !column.distance.trim() ||
          !column.diameter.trim() ||
          !column.shape.trim(),
      ) ||
        matrixCells.some((cell) =>
          template?.categories.length ? !cell.categoryCode : !cell.value.trim(),
        ))
    ) {
      return 'Completa los metadatos de cada cargador y todas las celdas de la matriz.';
    }
    return null;
  }

  function buildResultInput(): RecordResultInput {
    const resultMode = template?.resultMode ?? 'SCALAR';
    return {
      analystObservation: observation,
      revisionReason: existingResult ? revisionReason : undefined,
      values: resultMode === 'MATRIX' ? [] : capturedValues(fields),
      matrix: resultMode === 'MATRIX' ? buildMatrixInput() : undefined,
    };
  }

  function buildMatrixInput(): NonNullable<RecordResultInput['matrix']> {
    return {
      rowCount: matrixRows,
      columnCount: matrixColumns.length,
      columns: matrixColumns.map((column, columnIndex) => ({
        columnIndex,
        carrierLabel: column.carrierLabel || undefined,
        distance: column.distance.trim() ? Number(column.distance) : undefined,
        diameter: column.diameter.trim() ? Number(column.diameter) : undefined,
        shape: column.shape || undefined,
      })),
      cells: matrixCells.map((cell, index) => ({
        rowIndex: Math.floor(index / matrixColumns.length),
        columnIndex: index % matrixColumns.length,
        valueText:
          !template?.categories.length && definition?.resultType !== 'NUMERIC'
            ? cell.value || undefined
            : undefined,
        valueNumber:
          !template?.categories.length && definition?.resultType === 'NUMERIC' && cell.value.trim()
            ? Number(cell.value)
            : undefined,
        categoryCode: cell.categoryCode || undefined,
      })),
    };
  }

  async function approveResult() {
    setIsSaving(true);
    setError(null);

    try {
      await validateResult(test.id, validationComment);
      onSaved();
      return true;
    } catch (reason) {
      setError(getErrorMessage(reason, 'No se pudo validar el resultado.'));
      return false;
    } finally {
      setIsSaving(false);
    }
  }

  async function rejectForCorrection() {
    if (!reviewReason.trim()) {
      setError('Explica por qué el resultado debe volver al analista.');
      return false;
    }
    setIsSaving(true);
    setError(null);
    try {
      await rejectResult(test.id, reviewReason);
      onSaved();
      return true;
    } catch (reason) {
      setError(getErrorMessage(reason, 'No se pudo devolver el resultado al analista.'));
      return false;
    } finally {
      setIsSaving(false);
    }
  }

  async function correctAndApproveResult() {
    if (!reviewReason.trim()) {
      setError('Explica el motivo de la corrección realizada por el validador.');
      return false;
    }
    const validationError = editorValidationError();
    if (validationError) {
      setError(validationError);
      return false;
    }
    setIsSaving(true);
    setError(null);
    try {
      const input = buildResultInput();
      await correctAndValidateResult(test.id, {
        analystObservation: input.analystObservation,
        values: input.values,
        matrix: input.matrix,
        reason: reviewReason,
        comment: validationComment || undefined,
      });
      onSaved();
      return true;
    } catch (reason) {
      setError(getErrorMessage(reason, 'No se pudo corregir y validar el resultado.'));
      return false;
    } finally {
      setIsSaving(false);
    }
  }

  return {
    approveResult,
    applyBulkMatrixValues,
    beginMatrixExecution,
    correctAndApproveResult,
    error,
    existingResult,
    fields,
    template,
    matrixCells,
    matrixColumns,
    matrixExecution,
    matrixRows,
    addMatrixColumn,
    addMatrixRow,
    addReading,
    isSaving,
    isLoading,
    isCalculating,
    calculationError,
    hasDimensionAdjustment,
    isValidated,
    observation,
    revisionReason,
    reviewDecision,
    reviewReason,
    rejectForCorrection,
    resizeMatrix,
    saveMatrixProgress,
    saveResult,
    setObservation,
    setRevisionReason,
    setReviewDecision,
    setReviewReason,
    setValidationComment,
    updateFieldValue,
    updateMatrixCell,
    updateMatrixColumn,
    validationComment,
  };
}
