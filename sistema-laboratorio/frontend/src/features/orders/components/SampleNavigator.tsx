import { useState } from 'react';
import { Button } from '../../../shared/components/ui';
import type { WizardSampleForm } from '../model/order-wizard';
import type { CommonDataGroup } from '../model/sample-common-data';

interface Props {
  samples: WizardSampleForm[];
  activeIndex: number;
  onSelect: (index: number) => void;
  onCopy?: (groups: CommonDataGroup[], overwrite: boolean) => void;
}

export function SampleNavigator({ samples, activeIndex, onSelect, onCopy }: Props) {
  const [groups, setGroups] = useState<CommonDataGroup[]>(['date']);
  const [overwrite, setOverwrite] = useState(false);
  const [message, setMessage] = useState('');
  return (
    <section className="space-y-3 rounded-2xl border border-lab-100 bg-lab-50/60 p-4">
      <div className="flex flex-wrap items-end gap-3">
        <label className="min-w-48 flex-1 space-y-1 text-sm font-semibold">
          Ir a muestra
          <select
            aria-label="Ir a muestra"
            value={activeIndex}
            onChange={(event) => {
              onSelect(Number(event.target.value));
              setMessage('');
            }}
          >
            {samples.map((sample, index) => (
              <option key={sample.clientKey} value={index}>
                {index + 1}.{' '}
                {sample.clientIdentification || sample.productName || 'Sin identificación'}
              </option>
            ))}
          </select>
        </label>
        <Button
          disabled={activeIndex === 0}
          onClick={() => onSelect(activeIndex - 1)}
          variant="secondary"
        >
          Anterior muestra
        </Button>
        <Button
          disabled={activeIndex >= samples.length - 1}
          onClick={() => onSelect(activeIndex + 1)}
          variant="secondary"
        >
          Siguiente muestra
        </Button>
        <span className="pb-3 text-xs text-slate-600">
          {activeIndex + 1} de {samples.length}
        </span>
      </div>
      {onCopy && samples.length > 1 ? (
        <details className="rounded-xl bg-white p-3 text-sm">
          <summary className="cursor-pointer font-semibold text-lab-800">
            Repetir datos de esta muestra en las demás
          </summary>
          <p className="mt-3 text-xs text-slate-600">
            Elige los datos que se copiarán a las otras {samples.length - 1} muestras. La
            identificación, las cantidades y los ensayos se conservan.
          </p>
          <div className="my-3 flex flex-wrap gap-4">
            {(
              [
                ['date', 'Fecha y hora de muestreo'],
                ['origin', 'Procedencia, lugar, muestreador y productor'],
                ['product', 'Producto y variedad'],
              ] as const
            ).map(([group, label]) => (
              <label key={group} className="flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={groups.includes(group)}
                  onChange={(event) =>
                    setGroups((current) =>
                      event.target.checked
                        ? [...current, group]
                        : current.filter((value) => value !== group),
                    )
                  }
                />
                {label}
              </label>
            ))}
          </div>
          <label className="mb-3 flex items-center gap-2">
            <input
              type="checkbox"
              checked={overwrite}
              onChange={(event) => setOverwrite(event.target.checked)}
            />
            Sobrescribir también los datos que ya están completos
          </label>
          <Button
            disabled={!groups.length}
            onClick={() => {
              onCopy(groups, overwrite);
              setMessage(
                `Datos aplicados a las otras ${samples.length - 1} muestras${overwrite ? '.' : ', completando solo campos vacíos.'}`,
              );
            }}
            variant="secondary"
          >
            Aplicar a las demás muestras
          </Button>
          {message ? (
            <p role="status" className="mt-2 text-lab-800">
              {message}
            </p>
          ) : null}
        </details>
      ) : null}
    </section>
  );
}
