import { FilterX, Search } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Button } from '../../../shared/components/ui';
import type { CatalogSnapshot, WorkOrderStatus } from '../../../shared/types/domain';
import type { WorkOrderFilters as Filters } from '../api/work-orders-api';
import { workOrderStatusLabels } from '../model/work-order-labels';

interface WorkOrderFiltersProps {
  catalog: CatalogSnapshot | null;
  initialValues?: Filters;
  onApply: (filters: Filters) => void;
}

const statuses = Object.keys(workOrderStatusLabels) as WorkOrderStatus[];
const initialFilters: Filters = {
  search: '',
  status: '',
  laboratoryAreaId: '',
  laboratoryDueFrom: '',
  laboratoryDueTo: '',
};

export function WorkOrderFilters({ catalog, initialValues, onApply }: WorkOrderFiltersProps) {
  const [filters, setFilters] = useState<Filters>({ ...initialFilters, ...initialValues });

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onApply(filters);
  }

  function clear() {
    setFilters(initialFilters);
    onApply(initialFilters);
  }

  return (
    <form
      className="grid min-w-0 grid-cols-1 gap-3 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm sm:grid-cols-2 xl:grid-cols-4"
      onSubmit={submit}
    >
      {filters.createdFrom || filters.createdTo ? (
        <div className="rounded-xl bg-lab-50 px-3 py-2 text-xs font-medium text-lab-800 sm:col-span-2 xl:col-span-4">
          Vista proveniente del dashboard · órdenes técnicas emitidas del{' '}
          {filters.createdFrom || 'inicio'} al {filters.createdTo || 'día de hoy'}.
        </div>
      ) : null}
      <div className="relative min-w-0 sm:col-span-2 xl:col-span-1">
        <Search
          aria-hidden="true"
          className="pointer-events-none absolute left-3.5 top-3 text-slate-400"
          size={16}
        />
        <input
          aria-label="Buscar orden de trabajo"
          className="w-full pl-9"
          onChange={(event) =>
            setFilters((current) => ({ ...current, search: event.target.value }))
          }
          placeholder="Código de OT u OS"
          value={filters.search}
        />
      </div>
      <select
        aria-label="Filtrar por estado técnico"
        onChange={(event) =>
          setFilters((current) => ({
            ...current,
            status: event.target.value as WorkOrderStatus | '',
          }))
        }
        value={filters.status}
      >
        <option value="">Todos los estados</option>
        {statuses.map((status) => (
          <option key={status} value={status}>
            {workOrderStatusLabels[status]}
          </option>
        ))}
      </select>
      <select
        aria-label="Filtrar por área técnica"
        onChange={(event) =>
          setFilters((current) => ({ ...current, laboratoryAreaId: event.target.value }))
        }
        value={filters.laboratoryAreaId}
      >
        <option value="">Todas las áreas</option>
        {catalog?.areas.map((area) => (
          <option key={area.id} value={area.id}>
            {area.name}
          </option>
        ))}
      </select>
      <div className="hidden xl:block" />
      <label className="min-w-0 space-y-1.5 text-xs font-semibold text-slate-600">
        Fecha técnica desde
        <input
          className="w-full"
          onChange={(event) =>
            setFilters((current) => ({ ...current, laboratoryDueFrom: event.target.value }))
          }
          type="date"
          value={filters.laboratoryDueFrom}
        />
        <span className="block font-normal text-slate-400">Vencimiento mínimo del laboratorio</span>
      </label>
      <label className="min-w-0 space-y-1.5 text-xs font-semibold text-slate-600">
        Fecha técnica hasta
        <input
          className="w-full"
          onChange={(event) =>
            setFilters((current) => ({ ...current, laboratoryDueTo: event.target.value }))
          }
          type="date"
          value={filters.laboratoryDueTo}
        />
        <span className="block font-normal text-slate-400">Vencimiento máximo del laboratorio</span>
      </label>
      <div className="flex min-w-0 flex-wrap items-start gap-2 pt-[1.375rem] sm:col-span-2 xl:col-span-2">
        <Button className="min-h-11" type="submit" variant="secondary">
          Buscar
        </Button>
        <Button
          aria-label="Limpiar filtros"
          className="min-h-11"
          onClick={clear}
          type="button"
          variant="ghost"
        >
          <FilterX size={17} />
          Limpiar
        </Button>
      </div>
    </form>
  );
}
