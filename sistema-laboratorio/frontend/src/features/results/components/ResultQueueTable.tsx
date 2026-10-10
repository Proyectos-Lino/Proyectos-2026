import { AlertTriangle, ClipboardPenLine, ShieldCheck } from 'lucide-react';
import { Button, EmptyState, Pagination } from '../../../shared/components/ui';
import { formatDate } from '../../../shared/lib/format';
import type { PageResponse, TechnicalResultTask } from '../../../shared/types/domain';
import { resultEntryStatusLabels, workOrderTestStatusLabels } from '../model/result-labels';

interface ResultQueueTableProps {
  action: 'RECORD' | 'VALIDATE';
  isLoading: boolean;
  onOpen: (task: TechnicalResultTask) => void;
  onPageChange: (page: number) => void;
  queue: PageResponse<TechnicalResultTask> | null;
}

export function ResultQueueTable({
  action,
  isLoading,
  onOpen,
  onPageChange,
  queue,
}: ResultQueueTableProps) {
  const isValidation = action === 'VALIDATE';

  if (isLoading) {
    return <EmptyState message="Cargando bandeja técnica…" />;
  }

  if (!queue?.content.length) {
    return (
      <EmptyState
        message={
          isValidation
            ? 'No hay resultados pendientes de validación.'
            : 'No tienes resultados pendientes de registro.'
        }
      />
    );
  }

  return (
    <div className="space-y-4">
      <div className="overflow-x-auto rounded-2xl border border-slate-200 bg-white">
        <table className="min-w-225 w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-bold">Muestra técnica</th>
              <th className="px-4 py-3 font-bold">Ensayo y método</th>
              <th className="px-4 py-3 font-bold">OT / área</th>
              <th className="px-4 py-3 font-bold">{isValidation ? 'Registrado por' : 'Estado'}</th>
              <th className="px-4 py-3 font-bold">Acción</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {queue.content.map((task) => (
              <tr
                className={`align-top transition hover:bg-slate-50/70 ${
                  task.resultStatus === 'CORRECTION_REQUESTED' ? 'bg-amber-50/60' : ''
                }`}
                key={task.workOrderTestId}
              >
                <td className="px-4 py-4">
                  <p className="font-semibold text-slate-800">{task.laboratorySampleCode}</p>
                  <p className="mt-1 text-xs text-slate-500">{task.sampleTypeName}</p>
                </td>
                <td className="px-4 py-4">
                  <p className="font-semibold text-slate-800">{task.testName}</p>
                  <p className="mt-1 text-xs text-slate-500">{task.methodName}</p>
                </td>
                <td className="px-4 py-4">
                  <p className="font-semibold text-slate-800">{task.workOrderCode}</p>
                  <p className="mt-1 text-xs text-slate-500">{task.laboratoryAreaName}</p>
                  <p className="mt-1 text-xs text-slate-500">
                    Límite: {formatDate(task.laboratoryDueAt)}
                  </p>
                </td>
                <td className="px-4 py-4">
                  {isValidation ? (
                    <>
                      <p className="font-semibold text-slate-800">{task.recordedByName}</p>
                      {task.recordedAt ? (
                        <p className="mt-1 text-xs text-slate-500">{formatDate(task.recordedAt)}</p>
                      ) : null}
                    </>
                  ) : (
                    <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-600">
                      {workOrderTestStatusLabels[task.testStatus]}
                    </span>
                  )}
                  {task.resultStatus ? (
                    <p className="mt-2 text-xs font-semibold text-lab-800">
                      Resultado: {resultEntryStatusLabels[task.resultStatus]}
                    </p>
                  ) : null}
                  {task.resultStatus === 'CORRECTION_REQUESTED' ? (
                    <div className="mt-2 max-w-xs rounded-lg border border-amber-200 bg-amber-50 p-2 text-xs leading-5 text-amber-900">
                      <p className="flex items-center gap-1.5 font-bold">
                        <AlertTriangle size={14} /> Requiere tu atención
                      </p>
                      <p className="mt-1">{task.correctionRequestReason}</p>
                      {task.correctionRequestedByName ? (
                        <p className="mt-1 text-amber-700">
                          {task.correctionRequestedByName}
                          {task.correctionRequestedAt
                            ? ` · ${formatDate(task.correctionRequestedAt)}`
                            : ''}
                        </p>
                      ) : null}
                    </div>
                  ) : null}
                </td>
                <td className="px-4 py-4">
                  <Button
                    className="min-h-0 whitespace-nowrap px-3 py-2 text-xs"
                    onClick={() => onOpen(task)}
                    variant={isValidation ? 'secondary' : 'primary'}
                  >
                    {isValidation ? <ShieldCheck size={15} /> : <ClipboardPenLine size={15} />}
                    {isValidation
                      ? 'Revisar'
                      : task.resultStatus === 'CORRECTION_REQUESTED'
                        ? 'Atender devolución'
                        : task.resultStatus === 'DRAFT'
                          ? 'Continuar evaluación'
                          : task.resultId
                            ? 'Corregir'
                            : 'Registrar'}
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pagination
        onPageChange={onPageChange}
        page={queue.page}
        totalElements={queue.totalElements}
        totalPages={queue.totalPages}
      />
    </div>
  );
}
