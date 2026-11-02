import { useState } from 'react';
import { RotateCcw } from 'lucide-react';
import { returnOperation } from '../api/workflow-api';
import { Button } from '../../../shared/components/ui';
import { getErrorMessage } from '../../../shared/lib/errors';

interface Props {
  endpoint: string;
  label: string;
  description: string;
  onCompleted: () => void;
}

export function ReturnOperationControl({ endpoint, label, description, onCompleted }: Props) {
  const [expanded, setExpanded] = useState(false);
  const [reason, setReason] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    if (!reason.trim()) {
      setError('Indica el motivo de la devolución.');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await returnOperation(endpoint, reason.trim());
      setExpanded(false);
      setReason('');
      onCompleted();
    } catch (failure) {
      setError(getErrorMessage(failure, 'No se pudo devolver la operación.'));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="space-y-3">
      <Button variant="secondary" onClick={() => setExpanded((current) => !current)}>
        <RotateCcw size={16} />
        {label}
      </Button>
      {expanded ? (
        <section className="space-y-3 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm">
          <p className="font-semibold text-amber-950">{description}</p>
          <p className="text-amber-900">
            La versión anterior se conservará para consulta. Se registrarán el motivo, tu usuario y
            la fecha. Los informes vigentes afectados quedarán anulados y deberán generarse
            nuevamente.
          </p>
          <label className="block space-y-1">
            Motivo de devolución
            <textarea
              aria-label="Motivo de devolución"
              maxLength={1000}
              rows={3}
              value={reason}
              onChange={(event) => setReason(event.target.value)}
            />
          </label>
          {error ? (
            <p role="alert" className="text-rose-700">
              {error}
            </p>
          ) : null}
          <div className="flex flex-wrap gap-2">
            <Button disabled={saving} onClick={() => void submit()}>
              {saving ? 'Devolviendo…' : 'Confirmar devolución'}
            </Button>
            <Button disabled={saving} variant="ghost" onClick={() => setExpanded(false)}>
              Cancelar
            </Button>
          </div>
        </section>
      ) : null}
    </div>
  );
}
