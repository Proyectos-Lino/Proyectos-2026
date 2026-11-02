import { useState } from 'react';
import { Button } from '../../../shared/components/ui';
import { getErrorMessage } from '../../../shared/lib/errors';

interface Props {
  kind: 'producto' | 'variedad';
  onClose: () => void;
  onSave: (input: { code: string; name: string }) => Promise<void>;
}

export function QuickProductRegistration({ kind, onClose, onSave }: Props) {
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function save() {
    if (!code.trim() || !name.trim()) {
      setError('Completa el código y el nombre.');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await onSave({ code: code.trim(), name: name.trim() });
      onClose();
    } catch (reason) {
      setError(getErrorMessage(reason, `No se pudo registrar ${kind}.`));
    } finally {
      setSaving(false);
    }
  }

  return (
    <section
      className="mt-3 space-y-3 rounded-xl border border-lab-200 bg-lab-50 p-4"
      aria-label={`Registrar ${kind}`}
    >
      <p className="text-sm font-bold">Registrar {kind} en el catálogo</p>
      <label className="block">
        Código
        <input
          aria-label={`Código de ${kind}`}
          maxLength={30}
          value={code}
          onChange={(event) => setCode(event.target.value)}
        />
      </label>
      <label className="block">
        Nombre
        <input
          aria-label={`Nombre de ${kind}`}
          maxLength={160}
          value={name}
          onChange={(event) => setName(event.target.value)}
        />
      </label>
      {error ? (
        <p role="alert" className="text-sm text-rose-700">
          {error}
        </p>
      ) : null}
      <div className="flex flex-wrap gap-2">
        <Button disabled={saving} onClick={() => void save()}>
          {saving ? 'Guardando…' : 'Guardar y seleccionar'}
        </Button>
        <Button disabled={saving} onClick={onClose} variant="ghost">
          Cancelar
        </Button>
      </div>
    </section>
  );
}
