import { CalendarRange, FilterX } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Button } from '../../../shared/components/ui';
import type { DashboardFilters as Filters } from '../api/dashboard-api';

interface DashboardFiltersProps {
  areas: Array<{ id: string; label: string }>;
  filters: Filters;
  isLoading: boolean;
  onApply: (filters: Filters) => void;
}

export function DashboardFilters({ areas, filters, isLoading, onApply }: DashboardFiltersProps) {
  const [draft, setDraft] = useState(filters);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onApply(draft);
  }

  function clearArea() {
    const next = { ...draft, laboratoryAreaId: '' };
    setDraft(next);
    onApply(next);
  }

  return (
    <form
      className="grid gap-3 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm md:grid-cols-[minmax(10rem,1fr)_minmax(10rem,1fr)_minmax(14rem,1.35fr)_auto] md:items-end"
      onSubmit={submit}
    >
      <label className="space-y-1.5 text-xs font-semibold text-slate-600">
        Desde
        <input
          max={draft.to}
          onChange={(event) => setDraft((current) => ({ ...current, from: event.target.value }))}
          required
          type="date"
          value={draft.from}
        />
      </label>
      <label className="space-y-1.5 text-xs font-semibold text-slate-600">
        Hasta
        <input
          min={draft.from}
          onChange={(event) => setDraft((current) => ({ ...current, to: event.target.value }))}
          required
          type="date"
          value={draft.to}
        />
      </label>
      <label className="space-y-1.5 text-xs font-semibold text-slate-600">
        Área de laboratorio
        <select
          onChange={(event) =>
            setDraft((current) => ({ ...current, laboratoryAreaId: event.target.value }))
          }
          value={draft.laboratoryAreaId}
        >
          <option value="">Todas las áreas</option>
          {areas.map((area) => (
            <option key={area.id} value={area.id}>
              {area.label}
            </option>
          ))}
        </select>
      </label>
      <div className="flex gap-2">
        <Button disabled={isLoading} type="submit" variant="secondary">
          <CalendarRange size={17} />
          {isLoading ? 'Actualizando…' : 'Aplicar'}
        </Button>
        {draft.laboratoryAreaId ? (
          <Button
            aria-label="Quitar filtro de área"
            onClick={clearArea}
            type="button"
            variant="ghost"
          >
            <FilterX size={17} />
          </Button>
        ) : null}
      </div>
    </form>
  );
}
