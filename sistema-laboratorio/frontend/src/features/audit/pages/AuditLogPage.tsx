import { Download, Filter, History, Search } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import {
  Button,
  Card,
  EmptyState,
  ErrorNotice,
  PageHeader,
  Pagination,
} from '../../../shared/components/ui';
import { formatDate } from '../../../shared/lib/format';
import { getErrorMessage } from '../../../shared/lib/errors';
import { labelForAction, localizeSystemText } from '../../../shared/lib/labels';
import type { AuditLogEntry, PageResponse } from '../../../shared/types/domain';
import { exportAuditLog, getAuditLog, type AuditFilters } from '../api/audit-api';

const moduleLabels: Record<string, string> = {
  SERVICE_ORDER: 'Orden de servicio',
  WORK_ORDER: 'Orden de trabajo',
  RESULT: 'Resultado',
  RESULT_REVIEW: 'Revisión de resultado',
  RESULT_CALCULATION: 'Cálculo de resultado',
  RESULT_MATRIX: 'Matriz de resultados',
  REPORT: 'Informe',
  IDENTITY_ACCESS: 'Acceso a identidad',
  ORGANIZATION: 'Configuración de organización',
};

export function AuditLogPage() {
  const [filters, setFilters] = useState<AuditFilters>({});
  const [page, setPage] = useState<PageResponse<AuditLogEntry> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const load = useCallback(async (nextFilters: AuditFilters = {}) => {
    setIsLoading(true);
    try {
      setPage(await getAuditLog(nextFilters));
      setError(null);
    } catch (reason) {
      setError(getErrorMessage(reason, 'No fue posible cargar la bitácora.'));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    let isMounted = true;
    void getAuditLog()
      .then((response) => {
        if (isMounted) {
          setPage(response);
          setError(null);
        }
      })
      .catch((reason: unknown) => {
        if (isMounted) {
          setError(getErrorMessage(reason, 'No fue posible cargar la bitácora.'));
        }
      })
      .finally(() => {
        if (isMounted) setIsLoading(false);
      });
    return () => {
      isMounted = false;
    };
  }, []);

  function submit(event: FormEvent) {
    event.preventDefault();
    void load({ ...filters, page: 0 });
  }

  async function exportCsv() {
    try {
      const blob = await exportAuditLog(filters);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = 'bitacora-auditoria.csv';
      anchor.click();
      URL.revokeObjectURL(url);
    } catch (reason) {
      setError(getErrorMessage(reason, 'No fue posible exportar la bitácora.'));
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        actions={
          <Button onClick={() => void exportCsv()} variant="secondary">
            <Download size={17} />
            Exportar CSV
          </Button>
        }
        description="Consulta en un solo lugar los cambios comerciales, técnicos, matriciales y documentales."
        eyebrow="Administración"
        title="Bitácora de auditoría"
      />
      <ErrorNotice message={error} />

      <form
        className="grid gap-3 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm sm:grid-cols-2 xl:grid-cols-5"
        onSubmit={submit}
      >
        <label className="relative">
          <span className="sr-only">Usuario</span>
          <Search className="absolute left-3 top-3 text-slate-400" size={17} />
          <input
            className="w-full rounded-xl border border-slate-200 py-2.5 pl-10 pr-3 text-sm outline-none focus:border-lab-500 focus:ring-4 focus:ring-lab-100"
            onChange={(event) =>
              setFilters((current) => ({ ...current, actor: event.target.value }))
            }
            placeholder="Correo del usuario"
            value={filters.actor ?? ''}
          />
        </label>
        <select
          className="rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm outline-none focus:border-lab-500 focus:ring-4 focus:ring-lab-100"
          onChange={(event) =>
            setFilters((current) => ({ ...current, module: event.target.value }))
          }
          value={filters.module ?? ''}
        >
          <option value="">Todos los módulos</option>
          {Object.entries(moduleLabels).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
        <label>
          <span className="mb-1 block text-xs font-semibold text-slate-500">Desde</span>
          <input
            className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm outline-none focus:border-lab-500 focus:ring-4 focus:ring-lab-100"
            onChange={(event) =>
              setFilters((current) => ({ ...current, from: event.target.value }))
            }
            type="date"
            value={filters.from ?? ''}
          />
        </label>
        <label>
          <span className="mb-1 block text-xs font-semibold text-slate-500">Hasta</span>
          <input
            className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm outline-none focus:border-lab-500 focus:ring-4 focus:ring-lab-100"
            onChange={(event) => setFilters((current) => ({ ...current, to: event.target.value }))}
            type="date"
            value={filters.to ?? ''}
          />
        </label>
        <Button className="self-end" type="submit" variant="secondary">
          <Filter size={17} />
          Aplicar filtros
        </Button>
      </form>

      <Card className="overflow-hidden">
        <div className="border-b border-slate-100 px-5 py-4">
          <h2 className="flex items-center gap-2 font-bold text-slate-950">
            <History className="text-lab-700" size={18} />
            Eventos registrados
          </h2>
        </div>
        {isLoading ? (
          <p className="px-5 py-10 text-sm text-slate-500">Cargando eventos…</p>
        ) : page?.content.length ? (
          <div className="divide-y divide-slate-100">
            {page.content.map((entry) => (
              <article
                className="grid gap-3 px-5 py-4 md:grid-cols-[10rem_10rem_minmax(0,1fr)_13rem]"
                key={`${entry.module}-${entry.id}`}
              >
                <div>
                  <p className="text-xs font-bold uppercase tracking-wide text-lab-700">
                    {moduleLabels[entry.module] ?? entry.module}
                  </p>
                  <p className="mt-1 font-semibold text-slate-900">{entry.resourceCode}</p>
                </div>
                <div>
                  <p className="text-xs font-bold uppercase text-slate-500">Acción</p>
                  <p className="mt-1 text-sm text-slate-700">{labelForAction(entry.action)}</p>
                </div>
                <div>
                  <p className="text-sm text-slate-700">{localizeSystemText(entry.description)}</p>
                  <p className="mt-2 text-xs text-slate-400">{entry.actor}</p>
                </div>
                <time className="text-xs text-slate-500 md:text-right">
                  {formatDate(entry.occurredAt)}
                </time>
              </article>
            ))}
          </div>
        ) : (
          <EmptyState message="No existen eventos para los filtros seleccionados." />
        )}
      </Card>

      <Pagination
        onPageChange={(nextPage) => void load({ ...filters, page: nextPage })}
        page={page?.page ?? 0}
        totalElements={page?.totalElements ?? 0}
        totalPages={page?.totalPages ?? 0}
      />
    </div>
  );
}
