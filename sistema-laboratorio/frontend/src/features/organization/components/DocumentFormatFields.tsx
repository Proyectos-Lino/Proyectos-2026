import type { DocumentFormat } from '../../../shared/types/domain';

interface Props {
  title: string;
  value: DocumentFormat;
  onChange: (value: DocumentFormat) => void;
}

export function DocumentFormatFields({ title, value, onChange }: Props) {
  return (
    <fieldset className="rounded-xl border border-slate-200 p-4">
      <legend className="px-2 font-semibold text-slate-900">{title}</legend>
      <div className="grid gap-4 sm:grid-cols-3">
        <label className="space-y-2">
          <span className="block">Código del formato</span>
          <input
            maxLength={40}
            onChange={(event) => onChange({ ...value, code: event.target.value })}
            value={value.code ?? ''}
          />
        </label>
        <label className="space-y-2">
          <span className="block">Versión del formato</span>
          <input
            maxLength={20}
            onChange={(event) => onChange({ ...value, version: event.target.value })}
            value={value.version ?? ''}
          />
        </label>
        <label className="space-y-2">
          <span className="block">Fecha de aprobación</span>
          <input
            type="date"
            onChange={(event) =>
              onChange({ ...value, approvedOn: event.target.value || undefined })
            }
            value={value.approvedOn ?? ''}
          />
        </label>
      </div>
    </fieldset>
  );
}
