import { CheckCircle2, PencilLine, RotateCcw } from 'lucide-react';
import type { ResultReviewDecision } from '../hooks/useResultEditor';

interface ResultReviewPanelProps {
  comment: string;
  decision: ResultReviewDecision;
  onCommentChange: (value: string) => void;
  onDecisionChange: (value: ResultReviewDecision) => void;
  onReasonChange: (value: string) => void;
  reason: string;
}

const decisions: Array<{
  description: string;
  icon: typeof CheckCircle2;
  label: string;
  value: ResultReviewDecision;
}> = [
  {
    description: 'Confirma la versión recibida sin alterar sus datos.',
    icon: CheckCircle2,
    label: 'Aprobar sin cambios',
    value: 'APPROVE',
  },
  {
    description: 'Ajusta el dato, registra el motivo y valida la nueva versión.',
    icon: PencilLine,
    label: 'Corregir y validar',
    value: 'CORRECT_AND_VALIDATE',
  },
  {
    description: 'Devuelve el resultado al analista con una instrucción obligatoria.',
    icon: RotateCcw,
    label: 'Solicitar corrección',
    value: 'REJECT',
  },
];

export function ResultReviewPanel({
  comment,
  decision,
  onCommentChange,
  onDecisionChange,
  onReasonChange,
  reason,
}: ResultReviewPanelProps) {
  const requiresReason = decision !== 'APPROVE';

  return (
    <section className="rounded-2xl border border-sky-200 bg-sky-50/70 p-4">
      <div>
        <h3 className="text-sm font-bold text-sky-950">Decisión de revisión independiente</h3>
        <p className="mt-1 text-xs leading-5 text-sky-800">
          Selecciona una acción. El sistema conservará el usuario, rol, fecha, motivo y versión.
        </p>
      </div>

      <div className="mt-4 grid gap-3 lg:grid-cols-3">
        {decisions.map((item) => {
          const Icon = item.icon;
          const selected = decision === item.value;
          return (
            <button
              aria-pressed={selected}
              className={`rounded-xl border p-3 text-left transition focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-sky-200 ${
                selected
                  ? 'border-sky-500 bg-white shadow-sm'
                  : 'border-sky-100 bg-white/60 hover:border-sky-300 hover:bg-white'
              }`}
              key={item.value}
              onClick={() => onDecisionChange(item.value)}
              type="button"
            >
              <span className="flex items-center gap-2 text-sm font-bold text-slate-900">
                <Icon className={selected ? 'text-sky-700' : 'text-slate-500'} size={17} />
                {item.label}
              </span>
              <span className="mt-1.5 block text-xs leading-5 text-slate-600">
                {item.description}
              </span>
            </button>
          );
        })}
      </div>

      {requiresReason ? (
        <div className="mt-4 space-y-2">
          <label htmlFor="result-review-reason">
            {decision === 'REJECT' ? 'Motivo e indicaciones para el analista' : 'Motivo del cambio'}
          </label>
          <textarea
            className="bg-white"
            id="result-review-reason"
            onChange={(event) => onReasonChange(event.target.value)}
            placeholder={
              decision === 'REJECT'
                ? 'Indica qué debe revisar o repetir el analista…'
                : 'Explica qué dato se corrigió y por qué…'
            }
            required
            rows={3}
            value={reason}
          />
        </div>
      ) : null}

      {decision !== 'REJECT' ? (
        <div className="mt-4 space-y-2">
          <label htmlFor="result-validation-comment">Comentario de validación</label>
          <textarea
            className="bg-white"
            id="result-validation-comment"
            onChange={(event) => onCommentChange(event.target.value)}
            placeholder="Comentario técnico opcional"
            rows={2}
            value={comment}
          />
        </div>
      ) : null}
    </section>
  );
}
