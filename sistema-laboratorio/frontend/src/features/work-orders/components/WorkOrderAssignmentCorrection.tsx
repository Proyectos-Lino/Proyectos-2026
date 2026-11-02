import { useState } from 'react';
import { correctTechnicalAssignments } from '../../workflow/api/workflow-api';
import { Button } from '../../../shared/components/ui';
import { getErrorMessage } from '../../../shared/lib/errors';
import type { UserSummary, WorkOrderDetail } from '../../../shared/types/domain';

export function WorkOrderAssignmentCorrection({
  order,
  analysts,
  onCompleted,
}: {
  order: WorkOrderDetail;
  analysts: UserSummary[];
  onCompleted: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [dueAt, setDueAt] = useState(() => {
    const date = new Date(order.laboratoryDueAt);
    return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
  });
  const tests = order.samples
    .flatMap((sample) => sample.tests)
    .filter((test) => test.status !== 'INVALIDATED');
  const [assignments, setAssignments] = useState(() =>
    Object.fromEntries(tests.map((test) => [test.id, test.assignedAnalystId])),
  );
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  async function save() {
    if (!dueAt || !reason.trim()) {
      setError('Completa la fecha técnica y el motivo.');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await correctTechnicalAssignments(order.id, {
        reason: reason.trim(),
        laboratoryDueAt: new Date(dueAt).toISOString(),
        assignments: tests.map((test) => ({ testId: test.id, analystId: assignments[test.id] })),
      });
      setOpen(false);
      onCompleted();
    } catch (failure) {
      setError(getErrorMessage(failure, 'No se pudieron corregir las asignaciones.'));
    } finally {
      setSaving(false);
    }
  }
  return (
    <section className="space-y-3 rounded-xl border border-slate-200 p-4">
      <Button variant="secondary" onClick={() => setOpen((current) => !current)}>
        Corregir fecha y analistas
      </Button>
      {open ? (
        <div className="space-y-4">
          <label className="block">
            Fecha técnica
            <input
              aria-label="Fecha técnica corregida"
              type="datetime-local"
              value={dueAt}
              onChange={(event) => setDueAt(event.target.value)}
            />
          </label>
          {tests.map((test) => (
            <label key={test.id} className="block text-sm">
              {test.testName}
              <select
                aria-label={`Analista de ${test.testName}`}
                value={assignments[test.id] ?? ''}
                onChange={(event) =>
                  setAssignments((current) => ({ ...current, [test.id]: event.target.value }))
                }
              >
                <option value="">Seleccionar analista</option>
                {analysts
                  .filter(
                    (analyst) =>
                      analyst.roles.includes('ADMIN') ||
                      analyst.laboratoryAreaId === order.laboratoryAreaId,
                  )
                  .map((analyst) => (
                    <option key={analyst.id} value={analyst.id}>
                      {analyst.fullName}
                    </option>
                  ))}
              </select>
            </label>
          ))}
          <label className="block">
            Motivo del cambio
            <textarea
              aria-label="Motivo del cambio técnico"
              maxLength={1000}
              value={reason}
              onChange={(event) => setReason(event.target.value)}
            />
          </label>
          {error ? (
            <p role="alert" className="text-sm text-rose-700">
              {error}
            </p>
          ) : null}
          <Button disabled={saving} onClick={() => void save()}>
            {saving ? 'Guardando…' : 'Guardar corrección técnica'}
          </Button>
        </div>
      ) : null}
    </section>
  );
}
