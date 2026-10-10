import {
  Calculator,
  Check,
  Clock3,
  History,
  PencilLine,
  Play,
  RotateCcw,
  Save,
} from 'lucide-react';
import { type FormEvent } from 'react';
import { Button, ErrorNotice, Modal, ModalFooter } from '../../../shared/components/ui';
import { formatDate } from '../../../shared/lib/format';
import { labelForSystemCode } from '../../../shared/lib/labels';
import type {
  ResultMatrixCorrection,
  TestDefinition,
  Unit,
  WorkOrderTest,
} from '../../../shared/types/domain';
import { useResultEditor } from '../hooks/useResultEditor';
import { resultEntryStatusLabels } from '../model/result-labels';
import { AdvancedResultFields } from './AdvancedResultFields';
import { ResultReviewPanel } from './ResultReviewPanel';
import { ResultReviewTimeline } from './ResultReviewTimeline';

interface ResultEditorModalProps {
  canRecord: boolean;
  canValidate: boolean;
  definition: TestDefinition | undefined;
  isOpen: boolean;
  onClose: () => void;
  onSaved: () => void;
  test: WorkOrderTest | null;
  testName: string;
  units: Unit[];
}

function displayValue(valueText?: string, valueNumber?: number): string {
  if (valueNumber !== undefined) {
    return String(valueNumber);
  }

  return valueText || '—';
}

function matrixCorrectionLabel(correction: ResultMatrixCorrection): string {
  if (correction.targetType === 'CELL') {
    return `Yema ${(correction.rowIndex ?? 0) + 1} · Cargador ${(correction.columnIndex ?? 0) + 1}`;
  }
  if (correction.targetType === 'COLUMN') {
    return `Metadatos del cargador ${(correction.columnIndex ?? 0) + 1}`;
  }
  return 'Dimensiones de la matriz';
}

function calculationInputsLabel(serialized: string): string {
  try {
    const inputs = JSON.parse(serialized) as Record<string, number[]>;
    return Object.entries(inputs)
      .map(([key, values]) => `${key}: ${values.join(', ')}`)
      .join(' · ');
  } catch {
    return serialized;
  }
}

export function ResultEditorModal({
  canRecord,
  canValidate,
  definition,
  isOpen,
  onClose,
  onSaved,
  test,
  testName,
  units,
}: ResultEditorModalProps) {
  if (!test) {
    return null;
  }

  return (
    <ResultEditorContent
      canRecord={canRecord}
      canValidate={canValidate}
      definition={definition}
      isOpen={isOpen}
      onClose={onClose}
      onSaved={onSaved}
      test={test}
      testName={testName}
      units={units}
    />
  );
}

function ResultEditorContent({
  canRecord,
  canValidate,
  definition,
  isOpen,
  onClose,
  onSaved,
  test,
  testName,
  units,
}: Omit<ResultEditorModalProps, 'test'> & { test: WorkOrderTest }) {
  const {
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
    isLoading,
    isSaving,
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
  } = useResultEditor({ definition, onSaved, test });

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const wasSaved = await saveResult();

    if (wasSaved) {
      onClose();
    }
  }

  async function handleReviewAction() {
    const completed =
      reviewDecision === 'APPROVE'
        ? await approveResult()
        : reviewDecision === 'CORRECT_AND_VALIDATE'
          ? await correctAndApproveResult()
          : await rejectForCorrection();

    if (completed) {
      onClose();
    }
  }

  async function handleStartExecution() {
    await beginMatrixExecution();
  }

  async function handleSaveProgress() {
    await saveMatrixProgress();
  }

  const canReview =
    canValidate &&
    (existingResult?.status === 'RECORDED' || existingResult?.status === 'CORRECTED');
  const validatorCanEdit = canReview && reviewDecision === 'CORRECT_AND_VALIDATE';
  const analystCanEdit = canRecord && !canReview;
  const fieldsDisabled = isValidated || (!analystCanEdit && !validatorCanEdit);
  const isMatrixMode = template?.resultMode === 'MATRIX';
  const isMatrixDraft = existingResult?.status === 'DRAFT';

  return (
    <Modal
      description={
        isValidated
          ? 'Resultado validado y bloqueado para nuevas modificaciones.'
          : isMatrixDraft
            ? 'Continúa la evaluación, guarda avances parciales y finaliza cuando la matriz esté completa.'
            : existingResult?.status === 'CORRECTION_REQUESTED'
              ? 'Atiende la devolución, documenta el cambio y reenvía una nueva versión.'
              : canReview
                ? 'Compara los datos y selecciona una decisión de revisión independiente.'
                : 'Registra valores trazables y la observación técnica correspondiente.'
      }
      isOpen={isOpen}
      onClose={onClose}
      panelClassName={template?.resultMode === 'MATRIX' ? 'max-w-6xl' : 'max-w-4xl'}
      title={`Resultado: ${testName}`}
    >
      <form className="space-y-5" onSubmit={handleSubmit}>
        <ErrorNotice message={error} />

        {isLoading ? (
          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-6 text-center text-sm text-slate-500">
            Cargando estructura del resultado…
          </div>
        ) : null}

        {existingResult ? (
          <section className="grid gap-3 rounded-xl border border-slate-200 bg-slate-50 p-4 sm:grid-cols-2">
            <div>
              <p className="text-xs font-bold uppercase tracking-wide text-slate-400">
                {isMatrixDraft ? 'Iniciado por' : 'Registrado por'}
              </p>
              <p className="mt-1 text-sm font-semibold text-slate-800">
                {existingResult.recordedByName}
              </p>
              <p className="mt-1 text-xs text-slate-500">{formatDate(existingResult.recordedAt)}</p>
            </div>
            <div>
              <p className="text-xs font-bold uppercase tracking-wide text-slate-400">Estado</p>
              <p className="mt-1 text-sm font-semibold text-slate-800">
                {resultEntryStatusLabels[existingResult.status]}
              </p>
              <p className="mt-1 text-xs text-slate-500">
                Versión actual: {existingResult.resultRevision}
              </p>
              {existingResult.validation ? (
                <p className="mt-1 text-xs text-slate-500">
                  Validado por {existingResult.validation.validatorName} ·{' '}
                  {formatDate(existingResult.validation.validatedAt)}
                </p>
              ) : null}
            </div>
          </section>
        ) : null}

        {matrixExecution ? (
          <section className="rounded-2xl border border-teal-200 bg-teal-50/70 p-4">
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div className="flex items-start gap-3">
                <span className="grid size-10 place-items-center rounded-xl bg-white text-teal-700 shadow-sm">
                  <Clock3 size={18} />
                </span>
                <div>
                  <p className="text-sm font-bold text-teal-950">
                    {matrixExecution.executionStatus === 'COMPLETED'
                      ? 'Evaluación finalizada'
                      : 'Evaluación en ejecución'}
                  </p>
                  <p className="mt-1 text-xs text-teal-800">
                    Inició {formatDate(matrixExecution.startedAt)}
                    {matrixExecution.completedAt
                      ? ` · Terminó ${formatDate(matrixExecution.completedAt)}`
                      : ''}
                  </p>
                  <p className="mt-1 text-xs text-teal-800">
                    Última modificación por {matrixExecution.lastModifiedByName} ·{' '}
                    {formatDate(matrixExecution.lastModifiedAt)}
                  </p>
                </div>
              </div>
              <div className="min-w-44 text-right">
                <p className="text-2xl font-bold text-teal-950">
                  {matrixExecution.completionPercentage}%
                </p>
                <p className="text-xs font-semibold text-teal-700">avance guardado</p>
              </div>
            </div>
          </section>
        ) : null}

        {matrixExecution?.categorySummary.length ? (
          <section className="rounded-2xl border border-slate-200 bg-white p-4">
            <div className="flex items-center gap-2">
              <Calculator className="text-lab-700" size={17} />
              <h3 className="text-sm font-bold text-slate-900">Resumen por categoría</h3>
            </div>
            <div className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {matrixExecution.categorySummary.map((category) => (
                <article
                  className="rounded-xl border border-slate-200 bg-slate-50 px-3.5 py-3"
                  key={category.categoryCode}
                >
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="text-xs font-bold uppercase tracking-wide text-slate-500">
                        {category.categoryCode}
                      </p>
                      <p className="mt-1 text-sm font-semibold text-slate-800">
                        {category.categoryLabel}
                      </p>
                    </div>
                    <p className="text-lg font-bold text-lab-800">{category.percentage}%</p>
                  </div>
                  <p className="mt-2 text-xs text-slate-500">
                    {category.count} celda(s) clasificadas
                  </p>
                </article>
              ))}
            </div>
          </section>
        ) : null}

        {existingResult?.status === 'CORRECTION_REQUESTED' ? (
          <section className="rounded-2xl border border-amber-300 bg-amber-50 p-4">
            <div className="flex items-center gap-2 text-sm font-bold text-amber-950">
              <RotateCcw size={17} />
              Este resultado fue devuelto para corrección
            </div>
            <p className="mt-2 text-sm leading-6 text-amber-900">
              {existingResult.correctionRequestReason}
            </p>
            <p className="mt-2 text-xs text-amber-800">
              Solicitado por {existingResult.correctionRequestedByName} ·{' '}
              {labelForSystemCode(existingResult.correctionRequestedByRole)}
              {existingResult.correctionRequestedAt
                ? ` · ${formatDate(existingResult.correctionRequestedAt)}`
                : ''}
            </p>
          </section>
        ) : null}

        {!isLoading ? (
          <section className="space-y-4">
            <AdvancedResultFields
              addMatrixColumn={addMatrixColumn}
              addMatrixRow={addMatrixRow}
              addReading={addReading}
              applyBulkMatrixValues={applyBulkMatrixValues}
              disabled={fieldsDisabled}
              fields={fields}
              matrixCells={matrixCells}
              matrixColumns={matrixColumns}
              matrixRows={matrixRows}
              resizeMatrix={resizeMatrix}
              template={template}
              units={units}
              updateFieldValue={updateFieldValue}
              updateMatrixCell={updateMatrixCell}
              updateMatrixColumn={updateMatrixColumn}
            />
            {isCalculating ? (
              <p className="flex items-center gap-2 text-xs font-semibold text-indigo-700">
                <Calculator className="animate-pulse" size={15} />
                Recalculando vista previa en el servidor…
              </p>
            ) : null}
            {calculationError ? (
              <p className="rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900">
                {calculationError}
              </p>
            ) : null}
            <div className="space-y-2">
              <label htmlFor="result-observation">Observación técnica del analista</label>
              <textarea
                disabled={fieldsDisabled}
                id="result-observation"
                onChange={(event) => setObservation(event.target.value)}
                rows={3}
                value={observation}
              />
            </div>
          </section>
        ) : null}

        {existingResult &&
        !isValidated &&
        analystCanEdit &&
        (!isMatrixDraft || hasDimensionAdjustment) ? (
          <section className="rounded-xl border border-amber-200 bg-amber-50 p-4">
            <label className="text-amber-950" htmlFor="result-revision-reason">
              {isMatrixDraft ? 'Motivo del ajuste técnico' : 'Motivo de corrección'}
            </label>
            <p className="mt-1 text-xs leading-5 text-amber-800">
              {isMatrixDraft
                ? 'Explica por qué cambió la cantidad declarada de yemas o cargadores. El ajuste quedará auditado.'
                : existingResult.status === 'CORRECTION_REQUESTED'
                  ? 'Describe cómo atendiste la observación del revisor. El motivo es obligatorio para reenviar.'
                  : 'Si algún valor cambia, este motivo se registrará junto con el valor anterior, el corregido, tu usuario y la fecha.'}
            </p>
            <input
              className="mt-3 bg-white"
              id="result-revision-reason"
              onChange={(event) => setRevisionReason(event.target.value)}
              placeholder={
                isMatrixDraft
                  ? 'Justificación técnica del cambio de dimensiones'
                  : 'Describe la causa de la corrección'
              }
              required
              value={revisionReason}
            />
          </section>
        ) : null}

        {canReview ? (
          <ResultReviewPanel
            comment={validationComment}
            decision={reviewDecision}
            onCommentChange={setValidationComment}
            onDecisionChange={setReviewDecision}
            onReasonChange={setReviewReason}
            reason={reviewReason}
          />
        ) : null}

        {existingResult ? (
          <ResultReviewTimeline events={existingResult.reviewHistory ?? []} />
        ) : null}

        {existingResult?.corrections.length ? (
          <section className="border-t border-slate-200 pt-5">
            <div className="flex items-center gap-2">
              <History className="text-lab-700" size={17} />
              <h3 className="font-bold text-slate-900">Historial de correcciones</h3>
            </div>
            <div className="mt-3 space-y-3">
              {existingResult.corrections.map((correction) => (
                <article
                  className="rounded-xl border border-slate-200 bg-slate-50 p-3.5"
                  key={correction.id}
                >
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <p className="text-sm font-semibold text-slate-800">{correction.fieldLabel}</p>
                    <p className="text-xs text-slate-500">{formatDate(correction.changedAt)}</p>
                  </div>
                  <p className="mt-2 text-sm text-slate-600">
                    <span className="line-through decoration-rose-400">
                      {displayValue(correction.oldValueText, correction.oldValueNumber)}
                    </span>{' '}
                    <span className="mx-1 text-slate-400">→</span>
                    <span className="font-semibold text-emerald-700">
                      {displayValue(correction.newValueText, correction.newValueNumber)}
                    </span>
                  </p>
                  <p className="mt-2 text-sm leading-6 text-slate-700">{correction.reason}</p>
                  <p className="mt-2 text-xs text-slate-500">
                    Corregido por {correction.changedByName} · {correction.changedByRole}
                  </p>
                </article>
              ))}
            </div>
          </section>
        ) : null}

        {existingResult?.calculationSnapshots.length ? (
          <section className="border-t border-slate-200 pt-5">
            <div className="flex items-center gap-2">
              <Calculator className="text-indigo-700" size={17} />
              <h3 className="font-bold text-slate-900">Trazabilidad de cálculos</h3>
            </div>
            <p className="mt-1 text-xs leading-5 text-slate-500">
              Cada versión conserva la fórmula, sus entradas y el resultado producido por el
              servidor.
            </p>
            <div className="mt-3 space-y-3">
              {existingResult.calculationSnapshots.map((snapshot) => (
                <article
                  className="rounded-xl border border-indigo-100 bg-indigo-50/50 p-3.5"
                  key={snapshot.id}
                >
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <p className="text-sm font-bold text-indigo-950">
                      {template?.fields.find((field) => field.fieldKey === snapshot.fieldKey)
                        ?.label ?? snapshot.fieldKey}
                      {' · '}versión {snapshot.resultRevision}
                    </p>
                    <p className="text-xs text-slate-500">{formatDate(snapshot.calculatedAt)}</p>
                  </div>
                  <p className="mt-2 font-mono text-xs text-indigo-800">
                    {snapshot.formulaUsed} = {snapshot.calculatedValue}
                  </p>
                  <p className="mt-2 text-xs leading-5 text-slate-600">
                    Entradas: {calculationInputsLabel(snapshot.calculationInputs)}
                  </p>
                  <p className="mt-1 text-xs text-slate-500">
                    Calculado por {snapshot.calculatedByRole}
                  </p>
                </article>
              ))}
            </div>
          </section>
        ) : null}

        {existingResult?.matrixCorrections.length ? (
          <section className="border-t border-slate-200 pt-5">
            <div className="flex items-center gap-2">
              <History className="text-emerald-700" size={17} />
              <h3 className="font-bold text-slate-900">Auditoría de matriz</h3>
            </div>
            <div className="mt-3 space-y-3">
              {existingResult.matrixCorrections.map((correction) => (
                <article
                  className="rounded-xl border border-emerald-200 bg-emerald-50/40 p-3.5"
                  key={correction.id}
                >
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <p className="text-sm font-semibold text-slate-800">
                      {matrixCorrectionLabel(correction)}
                    </p>
                    <p className="text-xs text-slate-500">{formatDate(correction.changedAt)}</p>
                  </div>
                  <p className="mt-2 break-words text-sm text-slate-600">
                    <span className="line-through decoration-rose-400">
                      {correction.oldValue || 'Sin valor'}
                    </span>{' '}
                    <span className="mx-1 text-slate-400">→</span>
                    <span className="font-semibold text-emerald-700">
                      {correction.newValue || 'Sin valor'}
                    </span>
                  </p>
                  <p className="mt-2 text-sm leading-6 text-slate-700">{correction.reason}</p>
                  <p className="mt-2 text-xs text-slate-500">
                    Modificado por {correction.changedByName} · {correction.changedByRole}
                  </p>
                </article>
              ))}
            </div>
          </section>
        ) : null}

        <ModalFooter>
          <Button onClick={onClose} type="button" variant="secondary">
            Cerrar
          </Button>
          {!isValidated && analystCanEdit && isMatrixMode && !existingResult ? (
            <Button
              disabled={isLoading || isSaving}
              onClick={() => void handleStartExecution()}
              type="button"
            >
              <Play size={16} />
              {isSaving ? 'Iniciando…' : 'Iniciar evaluación'}
            </Button>
          ) : null}
          {!isValidated && analystCanEdit && isMatrixDraft ? (
            <Button
              disabled={isLoading || isSaving}
              onClick={() => void handleSaveProgress()}
              type="button"
              variant="secondary"
            >
              <Save size={16} />
              {isSaving ? 'Guardando…' : 'Guardar avance'}
            </Button>
          ) : null}
          {!isValidated && analystCanEdit && (!isMatrixMode || existingResult) ? (
            <Button disabled={isLoading || isSaving} type="submit">
              <Save size={16} />
              {isSaving
                ? 'Guardando…'
                : isMatrixDraft
                  ? 'Finalizar evaluación'
                  : existingResult
                    ? 'Guardar corrección'
                    : 'Guardar resultado'}
            </Button>
          ) : null}
          {canReview ? (
            <Button
              disabled={isSaving}
              onClick={() => void handleReviewAction()}
              variant={reviewDecision === 'REJECT' ? 'danger' : 'primary'}
            >
              {reviewDecision === 'APPROVE' ? <Check size={16} /> : null}
              {reviewDecision === 'CORRECT_AND_VALIDATE' ? <PencilLine size={16} /> : null}
              {reviewDecision === 'REJECT' ? <RotateCcw size={16} /> : null}
              {isSaving
                ? 'Procesando…'
                : reviewDecision === 'APPROVE'
                  ? 'Aprobar sin cambios'
                  : reviewDecision === 'CORRECT_AND_VALIDATE'
                    ? 'Corregir y validar'
                    : 'Devolver al analista'}
            </Button>
          ) : null}
        </ModalFooter>
      </form>
    </Modal>
  );
}
