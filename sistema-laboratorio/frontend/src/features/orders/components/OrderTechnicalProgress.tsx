import { CheckCircle2, CircleAlert, ClipboardCheck, FlaskConical } from 'lucide-react';
import { Button } from '../../../shared/components/ui';
import type { ServiceOrderTechnicalProgress } from '../../../shared/types/domain';

interface OrderTechnicalProgressProps {
  isChangingStatus: boolean;
  onFinalize: () => void;
  progress: ServiceOrderTechnicalProgress;
}

function ProgressMetric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-3">
      <p className="text-xs font-bold uppercase tracking-wide text-slate-400">{label}</p>
      <p className="mt-1 text-lg font-bold text-slate-900">{value}</p>
    </div>
  );
}

export function OrderTechnicalProgress({
  isChangingStatus,
  onFinalize,
  progress,
}: OrderTechnicalProgressProps) {
  const validationPercentage =
    progress.totalRequestedTests === 0
      ? 0
      : Math.round((progress.validatedTests / progress.totalRequestedTests) * 100);

  return (
    <section className="overflow-hidden rounded-2xl border border-lab-100 bg-lab-50/40">
      <header className="flex flex-wrap items-start justify-between gap-3 border-b border-lab-100 bg-white/80 p-4">
        <div className="flex items-start gap-3">
          <span className="rounded-xl bg-lab-100 p-2 text-lab-800">
            <FlaskConical size={18} />
          </span>
          <div>
            <h3 className="font-bold text-slate-900">Avance del proceso técnico</h3>
            <p className="mt-1 text-xs leading-5 text-slate-500">
              La OS finalizará únicamente cuando todos los ensayos estén validados y sus OT
              cerradas.
            </p>
          </div>
        </div>
        <span className="rounded-full bg-white px-3 py-1.5 text-xs font-bold text-lab-800 shadow-sm">
          {validationPercentage}% validado
        </span>
      </header>

      <div className="space-y-4 p-4">
        <div
          aria-label={`${validationPercentage}% de ensayos validados`}
          aria-valuemax={100}
          aria-valuemin={0}
          aria-valuenow={validationPercentage}
          className="h-2 overflow-hidden rounded-full bg-slate-200"
          role="progressbar"
        >
          <div
            className="h-full rounded-full bg-lab-700 transition-all"
            style={{ width: `${validationPercentage}%` }}
          />
        </div>

        <div className="grid gap-3 sm:grid-cols-3">
          <ProgressMetric
            label="Ensayos asignados"
            value={`${progress.assignedTests}/${progress.totalRequestedTests}`}
          />
          <ProgressMetric
            label="Ensayos validados"
            value={`${progress.validatedTests}/${progress.totalRequestedTests}`}
          />
          <ProgressMetric
            label="OT cerradas"
            value={`${progress.totalWorkOrders - progress.openWorkOrders}/${progress.totalWorkOrders}`}
          />
        </div>

        {progress.canFinalize ? (
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-emerald-200 bg-emerald-50 p-4">
            <div className="flex items-start gap-2 text-emerald-900">
              <ClipboardCheck className="mt-0.5 shrink-0" size={18} />
              <div>
                <p className="text-sm font-bold">Proceso técnico completo</p>
                <p className="mt-1 text-xs leading-5">
                  La orden está lista para cerrarse y habilitar la preparación del informe.
                </p>
              </div>
            </div>
            <Button disabled={isChangingStatus} onClick={onFinalize}>
              <CheckCircle2 size={16} />
              {isChangingStatus ? 'Finalizando…' : 'Finalizar proceso'}
            </Button>
          </div>
        ) : (
          <div className="rounded-xl border border-amber-200 bg-amber-50 p-4">
            <div className="flex items-center gap-2 text-sm font-bold text-amber-950">
              <CircleAlert size={17} />
              Pendientes para finalizar
            </div>
            <ul className="mt-2 space-y-1.5 pl-6 text-xs leading-5 text-amber-900">
              {progress.blockingReasons.map((reason) => (
                <li className="list-disc" key={reason}>
                  {reason}
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </section>
  );
}
