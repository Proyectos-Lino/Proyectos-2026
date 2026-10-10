import { FilterX, Search } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Button } from '../../../shared/components/ui';
import type { CatalogSnapshot, Client, ServiceOrderStatus } from '../../../shared/types/domain';
import type { ServiceOrderFilters } from '../api/orders-api';
import { serviceOrderStatusLabels } from '../model/order-labels';

interface OrderFiltersProps {
  catalog: CatalogSnapshot | null;
  clients: Client[];
  initialValues?: ServiceOrderFilters;
  onApply: (filters: ServiceOrderFilters) => void;
}

const statuses = Object.keys(serviceOrderStatusLabels) as ServiceOrderStatus[];
const initialFilters: ServiceOrderFilters = {
  search: '',
  status: '',
  clientId: '',
  laboratoryAreaId: '',
  plannedDeliveryFrom: '',
  plannedDeliveryTo: '',
};

export function OrderFilters({ catalog, clients, initialValues, onApply }: OrderFiltersProps) {
  const [filters, setFilters] = useState<ServiceOrderFilters>({
    ...initialFilters,
    ...initialValues,
  });

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onApply(filters);
  }

  function clearFilters() {
    setFilters(initialFilters);
    onApply(initialFilters);
  }

  return (
    <form
      className="grid min-w-0 grid-cols-1 gap-3 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm sm:grid-cols-2 xl:grid-cols-4"
      onSubmit={handleSubmit}
    >
      {filters.createdFrom || filters.createdTo ? (
        <div className="rounded-xl bg-lab-50 px-3 py-2 text-xs font-medium text-lab-800 sm:col-span-2 xl:col-span-4">
          Vista proveniente del dashboard · órdenes recibidas del {filters.createdFrom || 'inicio'}
          {' al '}
          {filters.createdTo || 'día de hoy'}.
        </div>
      ) : null}
      <div className="relative min-w-0 sm:col-span-2 xl:col-span-1">
        <Search
          aria-hidden="true"
          className="pointer-events-none absolute left-3.5 top-3 text-slate-400"
          size={16}
        />
        <input
          aria-label="Buscar orden"
          className="w-full pl-9"
          onChange={(event) =>
            setFilters((current) => ({ ...current, search: event.target.value }))
          }
          placeholder="Código de orden"
          value={filters.search}
        />
      </div>
      <select
        aria-label="Filtrar por estado"
        onChange={(event) =>
          setFilters((current) => ({
            ...current,
            status: event.target.value as ServiceOrderStatus | '',
          }))
        }
        value={filters.status}
      >
        <option value="">Todos los estados</option>
        {statuses.map((status) => (
          <option key={status} value={status}>
            {serviceOrderStatusLabels[status]}
          </option>
        ))}
      </select>
      <select
        aria-label="Filtrar por cliente"
        onChange={(event) =>
          setFilters((current) => ({ ...current, clientId: event.target.value }))
        }
        value={filters.clientId}
      >
        <option value="">Todos los clientes</option>
        {clients.map((client) => (
          <option key={client.id} value={client.id}>
            {client.legalName}
          </option>
        ))}
      </select>
      <select
        aria-label="Filtrar por laboratorio"
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
      <label className="min-w-0 space-y-1.5 text-xs font-semibold text-slate-600">
        Entrega programada desde
        <input
          className="w-full"
          onChange={(event) =>
            setFilters((current) => ({ ...current, plannedDeliveryFrom: event.target.value }))
          }
          type="date"
          value={filters.plannedDeliveryFrom}
        />
        <span className="block font-normal text-slate-400">Fecha mínima de entrega</span>
      </label>
      <label className="min-w-0 space-y-1.5 text-xs font-semibold text-slate-600">
        Entrega programada hasta
        <input
          className="w-full"
          onChange={(event) =>
            setFilters((current) => ({ ...current, plannedDeliveryTo: event.target.value }))
          }
          type="date"
          value={filters.plannedDeliveryTo}
        />
        <span className="block font-normal text-slate-400">Fecha máxima de entrega</span>
      </label>
      <div className="flex min-w-0 flex-wrap items-start gap-2 pt-[1.375rem] sm:col-span-2 xl:col-span-2">
        <Button className="min-h-11" type="submit" variant="secondary">
          Buscar
        </Button>
        <Button
          aria-label="Limpiar filtros"
          className="min-h-11"
          onClick={clearFilters}
          type="button"
          variant="ghost"
        >
          <FilterX size={17} />
        </Button>
      </div>
    </form>
  );
}
