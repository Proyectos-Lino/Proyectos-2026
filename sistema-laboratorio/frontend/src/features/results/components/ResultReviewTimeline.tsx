import { CheckCircle2, History, PencilLine, RotateCcw } from 'lucide-react';
import { formatDate } from '../../../shared/lib/format';
import { labelForSystemCode } from '../../../shared/lib/labels';
import type { ResultReviewHistory } from '../../../shared/types/domain';
import { resultEntryStatusLabels } from '../model/result-labels';

interface ResultReviewTimelineProps {
  events: ResultReviewHistory[];
}

const actionDetails = {
  REGISTERED: { icon: History, label: 'Resultado registrado', tone: 'text-slate-600' },
  CORRECTION_REQUESTED: {
    icon: RotateCcw,
    label: 'Corrección solicitada',
    tone: 'text-amber-700',
  },
  CORRECTED: { icon: PencilLine, label: 'Resultado corregido', tone: 'text-sky-700' },
  VALIDATED: { icon: CheckCircle2, label: 'Resultado validado', tone: 'text-emerald-700' },
  CORRECTED_AND_VALIDATED: {
    icon: CheckCircle2,
    label: 'Corregido y validado',
    tone: 'text-emerald-700',
  },
} as const;

export function ResultReviewTimeline({ events }: ResultReviewTimelineProps) {
  if (!events.length) {
    return null;
  }

  return (
    <section className="border-t border-slate-200 pt-5">
      <div className="flex items-center gap-2">
        <History className="text-violet-700" size={17} />
        <h3 className="font-bold text-slate-900">Ciclo de revisión y versiones</h3>
      </div>
      <div className="mt-3 space-y-3">
        {events.map((event) => {
          const detail = actionDetails[event.action];
          const Icon = detail.icon;
          return (
            <article className="rounded-xl border border-slate-200 bg-white p-3.5" key={event.id}>
              <div className="flex flex-wrap items-start justify-between gap-2">
                <p className={`flex items-center gap-2 text-sm font-bold ${detail.tone}`}>
                  <Icon size={16} />
                  {detail.label} · versión {event.resultRevision}
                </p>
                <p className="text-xs text-slate-500">{formatDate(event.performedAt)}</p>
              </div>
              <p className="mt-2 text-xs font-semibold text-slate-500">
                {event.previousStatus ? `${resultEntryStatusLabels[event.previousStatus]} → ` : ''}
                {resultEntryStatusLabels[event.newStatus]}
              </p>
              {event.reason ? (
                <p className="mt-2 text-sm leading-6 text-slate-700">
                  <span className="font-semibold">Motivo:</span> {event.reason}
                </p>
              ) : null}
              {event.comment ? (
                <p className="mt-1 text-sm leading-6 text-slate-600">
                  <span className="font-semibold">Comentario:</span> {event.comment}
                </p>
              ) : null}
              <p className="mt-2 text-xs text-slate-500">
                {event.performedByName} · {labelForSystemCode(event.performedByRole)}
              </p>
            </article>
          );
        })}
      </div>
    </section>
  );
}
