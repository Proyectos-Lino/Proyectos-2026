import { Calculator, Info, Keyboard, Trash2 } from 'lucide-react';
import { Button } from '../../../shared/components/ui';
import type { Unit } from '../../../shared/types/domain';
import type { TemplateField } from '../api/catalog-management-api';

interface TemplateFieldEditorProps {
  allowCalculated: boolean;
  field: TemplateField;
  index: number;
  onChange: (changes: Partial<TemplateField>) => void;
  onRemove: () => void;
  removable: boolean;
  units: Unit[];
}

export function TemplateFieldEditor({
  allowCalculated,
  field,
  index,
  onChange,
  onRemove,
  removable,
  units,
}: TemplateFieldEditorProps) {
  const calculated = field.fieldKind === 'CALCULATED';
  const informational = field.fieldKind === 'INFORMATIONAL';

  function changeKind(fieldKind: TemplateField['fieldKind']) {
    onChange({
      fieldKind,
      readOnly: fieldKind !== 'CAPTURED' || field.readOnly,
      required: fieldKind === 'INFORMATIONAL' ? false : field.required,
      resultType:
        fieldKind === 'CALCULATED'
          ? 'NUMERIC'
          : fieldKind === 'INFORMATIONAL'
            ? 'TEXT'
            : field.resultType,
    });
  }

  return (
    <article className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 bg-slate-50/80 px-4 py-3">
        <div className="flex items-center gap-3">
          <span className="grid size-8 place-items-center rounded-lg bg-lab-100 text-xs font-bold text-lab-800">
            {index + 1}
          </span>
          <div>
            <p className="text-sm font-bold text-slate-900">
              {field.label || `Campo ${index + 1}`}
            </p>
            <p className="text-xs text-slate-500">
              {calculated
                ? 'Calculado y protegido por el servidor'
                : informational
                  ? 'Texto de apoyo, no solicita un valor'
                  : field.readOnly
                    ? 'Valor fijo de solo lectura'
                    : 'Dato capturado por el analista'}
            </p>
          </div>
        </div>
        <Button
          aria-label="Eliminar campo"
          disabled={!removable}
          onClick={onRemove}
          type="button"
          variant="ghost"
        >
          <Trash2 size={16} />
        </Button>
      </header>

      <div className="grid gap-4 p-4 sm:grid-cols-2 lg:grid-cols-4">
        <label className="space-y-2 text-xs font-semibold text-slate-600">
          Clave estable
          <input
            maxLength={80}
            onChange={(event) => onChange({ fieldKey: event.target.value.toLowerCase() })}
            pattern="[a-z][a-z0-9_]*"
            placeholder="humedad_promedio"
            required
            value={field.fieldKey}
          />
        </label>
        <label className="space-y-2 text-xs font-semibold text-slate-600">
          Etiqueta visible
          <input
            maxLength={120}
            onChange={(event) => onChange({ label: event.target.value })}
            placeholder="Humedad promedio"
            required
            value={field.label}
          />
        </label>
        <label className="space-y-2 text-xs font-semibold text-slate-600">
          Comportamiento
          <select
            onChange={(event) => changeKind(event.target.value as TemplateField['fieldKind'])}
            value={field.fieldKind}
          >
            <option value="CAPTURED">Capturado</option>
            <option disabled={!allowCalculated} value="CALCULATED">
              Calculado
            </option>
            <option value="INFORMATIONAL">Informativo</option>
          </select>
        </label>
        <label className="space-y-2 text-xs font-semibold text-slate-600">
          Tipo de dato
          <select
            disabled={calculated || informational}
            onChange={(event) =>
              onChange({ resultType: event.target.value as TemplateField['resultType'] })
            }
            value={field.resultType}
          >
            <option value="NUMERIC">Numérico</option>
            <option value="TEXT">Texto</option>
            <option value="QUALITATIVE">Cualitativo</option>
          </select>
        </label>

        {!informational ? (
          <label className="space-y-2 text-xs font-semibold text-slate-600">
            Unidad
            <select
              disabled={field.resultType !== 'NUMERIC'}
              onChange={(event) => onChange({ measurementUnitId: event.target.value })}
              value={field.measurementUnitId ?? ''}
            >
              <option value="">Sin unidad</option>
              {units.map((unit) => (
                <option key={unit.id} value={unit.id}>
                  {unit.name} ({unit.symbol})
                </option>
              ))}
            </select>
          </label>
        ) : null}

        {!informational ? (
          <div className="flex flex-wrap items-end gap-3 sm:col-span-1 lg:col-span-3">
            <label className="flex min-h-11 items-center gap-2 rounded-xl border border-slate-200 px-3 text-xs font-semibold text-slate-600">
              <input
                checked={field.required}
                onChange={(event) => onChange({ required: event.target.checked })}
                type="checkbox"
              />
              Obligatorio
            </label>
            <label className="flex min-h-11 items-center gap-2 rounded-xl border border-slate-200 px-3 text-xs font-semibold text-slate-600">
              <input
                checked={field.readOnly || calculated}
                disabled={calculated}
                onChange={(event) => onChange({ readOnly: event.target.checked })}
                type="checkbox"
              />
              Solo lectura
            </label>
          </div>
        ) : null}

        {calculated ? (
          <div className="space-y-4 rounded-2xl border border-indigo-200 bg-indigo-50/60 p-4 sm:col-span-2 lg:col-span-4">
            <div className="flex items-start gap-3">
              <Calculator className="mt-0.5 text-indigo-700" size={18} />
              <div>
                <p className="text-sm font-bold text-indigo-950">Fórmula segura</p>
                <p className="mt-1 text-xs leading-5 text-indigo-800">
                  Usa claves de otros campos. Funciones: SUM, AVG, MIN, MAX, COUNT, ROUND y PERCENT.
                  También admite +, −, ×, ÷, paréntesis y %.
                </p>
              </div>
            </div>
            <div className="grid gap-4 lg:grid-cols-[1fr_13rem_10rem]">
              <label className="space-y-2 text-xs font-semibold text-indigo-950">
                Expresión
                <input
                  className="bg-white font-mono"
                  maxLength={1000}
                  onChange={(event) => onChange({ formula: event.target.value })}
                  placeholder="ROUND(AVG(lectura), 2)"
                  required
                  value={field.formula ?? ''}
                />
              </label>
              <label className="space-y-2 text-xs font-semibold text-indigo-950">
                Alcance
                <select
                  className="bg-white"
                  onChange={(event) =>
                    onChange({
                      calculationScope: event.target.value as TemplateField['calculationScope'],
                    })
                  }
                  value={field.calculationScope}
                >
                  <option value="PER_REPETITION">Por lectura</option>
                  <option value="AGGREGATE">Todas las lecturas</option>
                </select>
              </label>
              <label className="space-y-2 text-xs font-semibold text-indigo-950">
                Decimales
                <input
                  className="bg-white"
                  max={10}
                  min={0}
                  onChange={(event) => onChange({ decimalPrecision: Number(event.target.value) })}
                  type="number"
                  value={field.decimalPrecision ?? 2}
                />
              </label>
            </div>
          </div>
        ) : null}

        {informational ? (
          <label className="space-y-2 rounded-2xl border border-sky-200 bg-sky-50/70 p-4 text-xs font-semibold text-sky-950 sm:col-span-2 lg:col-span-4">
            <span className="flex items-center gap-2 text-sm font-bold">
              <Info size={17} /> Mensaje para el analista
            </span>
            <textarea
              className="min-h-20 bg-white"
              maxLength={500}
              onChange={(event) => onChange({ informationText: event.target.value })}
              placeholder="Indicación, criterio o referencia que debe mostrarse durante el registro."
              required
              value={field.informationText ?? ''}
            />
          </label>
        ) : null}

        {field.fieldKind === 'CAPTURED' && field.readOnly ? (
          <label className="space-y-2 rounded-2xl border border-amber-200 bg-amber-50/70 p-4 text-xs font-semibold text-amber-950 sm:col-span-2 lg:col-span-4">
            <span className="flex items-center gap-2 text-sm font-bold">
              <Keyboard size={17} /> Valor predeterminado protegido
            </span>
            <input
              className="bg-white"
              onChange={(event) => onChange({ defaultValue: event.target.value })}
              required
              type={field.resultType === 'NUMERIC' ? 'number' : 'text'}
              value={field.defaultValue ?? ''}
            />
          </label>
        ) : null}
      </div>
    </article>
  );
}
