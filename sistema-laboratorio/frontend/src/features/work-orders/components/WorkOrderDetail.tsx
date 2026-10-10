import { Ban, CheckCircle2, FileText, Printer, RotateCcw, UserRound } from 'lucide-react';
import { useState } from 'react';
import { Button, Card, EmptyState } from '../../../shared/components/ui';
import { formatDate } from '../../../shared/lib/format';
import type {
  CatalogSnapshot,
  PageResponse,
  WorkOrderDetail as WorkOrderDetailModel,
  WorkOrderHistoryEntry,
  WorkOrderTest,
} from '../../../shared/types/domain';
import { workOrderTestStatusLabels } from '../model/work-order-labels';
import { ResultEditorModal } from '../../results/components/ResultEditorModal';
import { WorkOrderHistory } from './WorkOrderHistory';
import { WorkOrderStatusBadge } from './WorkOrderStatusBadge';

interface WorkOrderDetailProps {
  canManageWorkOrders: boolean;
  canRecordResults: boolean;
  canValidateResults: boolean;
  currentUserId?: string;
  isAdministrator: boolean;
  catalog: CatalogSnapshot | null;
  history: PageResponse<WorkOrderHistoryEntry> | null;
  isSaving: boolean;
  onCancel: (order: WorkOrderDetailModel) => void;
  onApproveCorrection: (workOrderId: string, serviceOrderId: string) => void;
  onClose: (workOrderId: string) => void;
  onHistoryPageChange: (page: number) => void;
  onRefresh: () => void;
  onReturnToCommercial: (order: WorkOrderDetailModel) => void;
  onViewDocument: (workOrderId: string) => void;
  workOrder: WorkOrderDetailModel | null;
}

export function WorkOrderDetail({
  canManageWorkOrders,
  canRecordResults,
  canValidateResults,
  currentUserId,
  isAdministrator,
  catalog,
  history,
  isSaving,
  onCancel,
  onApproveCorrection,
  onClose,
  onHistoryPageChange,
  onRefresh,
  onReturnToCommercial,
  onViewDocument,
  workOrder,
}: WorkOrderDetailProps) {
  const [activeTest, setActiveTest] = useState<WorkOrderTest | null>(null);

  if (!workOrder) {
    return (
      <Card>
        <EmptyState message="Selecciona una orden de trabajo para ver sus muestras y ensayos." />
      </Card>
    );
  }

  const activeTestDefinition = catalog?.tests.find(
    (item) => item.id === activeTest?.testDefinitionId,
  );
  const isCommercialCorrection =
    workOrder.serviceOrderStatus === 'COMMERCIAL_CORRECTION' ||
    workOrder.serviceOrderStatus === 'CORRECTION_REVIEW';
  const canControlCommercialCorrection =
    canManageWorkOrders && (isAdministrator || workOrder.responsibleId === currentUserId);

  return (
    <>
      <Card>
        <header className="flex flex-wrap items-start justify-between gap-4 border-b border-slate-100 px-5 py-4">
          <div>
            <p className="text-xs font-bold uppercase tracking-wide text-lab-700">
              Operación técnica
            </p>
            <h2 className="mt-1 text-lg font-bold text-slate-900">{workOrder.code}</h2>
            <p className="mt-1 text-xs text-slate-500">
              {workOrder.laboratoryAreaName} · Límite: {formatDate(workOrder.laboratoryDueAt)}
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <WorkOrderStatusBadge status={workOrder.status} />
            <Button onClick={() => onViewDocument(workOrder.id)} variant="secondary">
              <Printer size={16} />
              Documento
            </Button>
          </div>
        </header>

        <div className="space-y-5 p-5">
          <section className="grid gap-4 rounded-xl bg-slate-50 p-4 sm:grid-cols-3">
            <div>
              <p className="text-xs font-bold uppercase tracking-wide text-slate-400">
                Orden de servicio
              </p>
              <p className="mt-1 text-sm font-semibold text-slate-800">
                {workOrder.serviceOrderCode}
              </p>
            </div>
            <div>
              <p className="text-xs font-bold uppercase tracking-wide text-slate-400">
                Responsable técnico
              </p>
              <p className="mt-1 flex items-center gap-2 text-sm font-semibold text-slate-800">
                <UserRound size={15} />
                {workOrder.responsibleName}
              </p>
            </div>
            <div>
              <p className="text-xs font-bold uppercase tracking-wide text-slate-400">
                Orden de trabajo
              </p>
              <p className="mt-1 text-sm font-semibold text-slate-800">{workOrder.code}</p>
            </div>
          </section>

          {workOrder.cancellationReason ? (
            <section className="rounded-xl border border-rose-200 bg-rose-50 p-4">
              <p className="text-sm font-bold text-rose-800">Motivo de anulación</p>
              <p className="mt-2 text-sm leading-6 text-rose-800">{workOrder.cancellationReason}</p>
            </section>
          ) : null}

          {isCommercialCorrection ? (
            <section className="rounded-xl border border-amber-200 bg-amber-50 p-4">
              <p className="text-sm font-bold text-amber-900">
                {workOrder.serviceOrderStatus === 'CORRECTION_REVIEW'
                  ? 'Corrección comercial pendiente de aprobación'
                  : 'Operación técnica pausada por corrección comercial'}
              </p>
              <div className="mt-3 grid gap-3 text-sm text-amber-900">
                <div>
                  <p className="text-xs font-bold uppercase tracking-wide text-amber-700">
                    Motivo de la devolución
                  </p>
                  <p className="mt-1 leading-6">
                    {workOrder.serviceOrderCorrectionReason ??
                      'La orden de servicio está siendo corregida por el área comercial.'}
                  </p>
                </div>
                {workOrder.serviceOrderStatus === 'CORRECTION_REVIEW' ? (
                  <>
                    <div className="border-t border-amber-200 pt-3">
                      <p className="text-xs font-bold uppercase tracking-wide text-amber-700">
                        Cambios informados por Comercial
                      </p>
                      <p className="mt-1 leading-6">
                        {workOrder.serviceOrderCorrectionChangeNote ??
                          'Comercial completó la corrección y la envió a revisión.'}
                      </p>
                    </div>
                    <div className="border-t border-amber-200 pt-3">
                      <p className="text-xs font-bold uppercase tracking-wide text-amber-700">
                        Impacto técnico calculado
                      </p>
                      <p className="mt-1 leading-6">
                        {workOrder.serviceOrderCorrectionImpactSummary ??
                          'No se detectaron cambios técnicos que afecten los resultados registrados.'}
                      </p>
                    </div>
                    <div
                      className={`rounded-xl border p-3 font-semibold ${
                        workOrder.serviceOrderCorrectionRequiresTechnicalWork
                          ? 'border-orange-200 bg-orange-100 text-orange-900'
                          : 'border-emerald-200 bg-emerald-50 text-emerald-800'
                      }`}
                    >
                      {workOrder.serviceOrderCorrectionRequiresTechnicalWork
                        ? 'Requiere trabajo técnico: revisa las asignaciones afectadas y registra nuevamente los resultados indicados antes de continuar.'
                        : 'Los resultados técnicos anteriores se mantienen y podrán continuar con su flujo normal.'}
                    </div>
                  </>
                ) : null}
              </div>
            </section>
          ) : null}

          <section className="space-y-4">
            <div className="flex items-center justify-between gap-3">
              <h3 className="font-bold text-slate-900">Muestras y ensayos asignados</h3>
              <p className="text-xs text-slate-500">Vista técnica sin datos del cliente</p>
            </div>
            {workOrder.samples.map((sample) => (
              <section
                className="overflow-hidden rounded-xl border border-slate-200"
                key={sample.id}
              >
                <header className="flex items-center justify-between gap-3 border-b border-slate-100 bg-slate-50 px-4 py-3">
                  <div>
                    <p className="font-semibold text-slate-800">
                      Código interno: {sample.laboratoryCode}
                    </p>
                    <p className="mt-1 text-xs text-slate-500">
                      Clasificación técnica: {sample.technicalSampleTypeName}
                    </p>
                  </div>
                  <p className="text-xs text-slate-500">{sample.tests.length} ensayo(s)</p>
                </header>
                <div className="divide-y divide-slate-100">
                  {sample.tests.map((test) => (
                    <article
                      className="flex flex-wrap items-center justify-between gap-3 px-4 py-3.5"
                      key={test.id}
                    >
                      <div>
                        <p className="text-sm font-semibold text-slate-800">{test.testName}</p>
                        <p className="mt-1 text-xs text-slate-500">
                          {test.testMethodName} · Analista: {test.assignedAnalystName}
                        </p>
                      </div>
                      <div className="flex items-center gap-2">
                        <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-600">
                          {workOrderTestStatusLabels[test.status]}
                        </span>
                        <Button onClick={() => setActiveTest(test)} variant="secondary">
                          <FileText size={15} />
                          {test.status === 'PENDING' ? 'Resultado' : 'Ver resultado'}
                        </Button>
                      </div>
                    </article>
                  ))}
                </div>
              </section>
            ))}
          </section>

          {canControlCommercialCorrection ? (
            <div className="flex flex-wrap justify-end gap-3 border-t border-slate-100 pt-5">
              {!isCommercialCorrection && workOrder.status !== 'CANCELLED' ? (
                <Button
                  disabled={isSaving}
                  onClick={() => onReturnToCommercial(workOrder)}
                  variant="secondary"
                >
                  <RotateCcw size={16} />
                  Devolver OS a Comercial
                </Button>
              ) : null}
              {workOrder.serviceOrderStatus === 'CORRECTION_REVIEW' ? (
                <Button
                  disabled={isSaving}
                  onClick={() => onApproveCorrection(workOrder.id, workOrder.serviceOrderId)}
                >
                  <CheckCircle2 size={16} />
                  Aprobar corrección
                </Button>
              ) : null}
              {!isCommercialCorrection && workOrder.status === 'PENDING' ? (
                <Button disabled={isSaving} onClick={() => onCancel(workOrder)} variant="danger">
                  <Ban size={16} />
                  Anular OT
                </Button>
              ) : null}
              {!isCommercialCorrection && workOrder.status === 'VALIDATED' ? (
                <Button disabled={isSaving} onClick={() => onClose(workOrder.id)}>
                  <CheckCircle2 size={16} />
                  Cerrar OT
                </Button>
              ) : null}
            </div>
          ) : null}

          <WorkOrderHistory history={history} onPageChange={onHistoryPageChange} />
        </div>
      </Card>

      <ResultEditorModal
        canRecord={
          !isCommercialCorrection &&
          activeTest?.status !== 'INVALIDATED' &&
          canRecordResults &&
          (isAdministrator || activeTest?.assignedAnalystId === currentUserId)
        }
        canValidate={
          !isCommercialCorrection && activeTest?.status !== 'INVALIDATED' && canValidateResults
        }
        definition={activeTestDefinition}
        isOpen={activeTest !== null}
        onClose={() => setActiveTest(null)}
        onSaved={onRefresh}
        test={activeTest}
        testName={activeTest?.testName ?? ''}
        units={catalog?.units ?? []}
      />
    </>
  );
}
