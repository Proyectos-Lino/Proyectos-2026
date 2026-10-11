import {
  Ban,
  Download,
  Eye,
  FileCheck2,
  FileClock,
  FilePlus2,
  RefreshCw,
  Search,
} from 'lucide-react';
import { useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useAuth } from '../../auth/hooks/useAuth';
import {
  Button,
  Card,
  EmptyState,
  ErrorNotice,
  Modal,
  ModalFooter,
  PageHeader,
  Pagination,
  StatusBadge,
} from '../../../shared/components/ui';
import { formatDate } from '../../../shared/lib/format';
import { labelForAction, localizeSystemText } from '../../../shared/lib/labels';
import type { LaboratoryReportStatus } from '../../../shared/types/domain';
import { exportReports, type ReportFilters } from '../api/reports-api';
import { ReportPdfModal } from '../components/ReportPdfModal';
import { useReports } from '../hooks/useReports';

const statusLabels: Record<LaboratoryReportStatus, string> = {
  DRAFT: 'Borrador',
  ISSUED: 'Emitido',
  CANCELLED: 'Anulado',
};

export function ReportsPage() {
  const [searchParams] = useSearchParams();
  const initialFilters = useMemo<ReportFilters>(
    () => ({
      status: (searchParams.get('status') as LaboratoryReportStatus | null) ?? undefined,
      issuedFrom: searchParams.get('issuedFrom') ?? undefined,
      issuedTo: searchParams.get('issuedTo') ?? undefined,
      laboratoryAreaId: searchParams.get('laboratoryAreaId') ?? undefined,
    }),
    [searchParams],
  );
  const { hasRole } = useAuth();
  const canPrepare = hasRole('ADMIN', 'TECHNICAL_MANAGER', 'VALIDATOR');
  const canControl = hasRole('ADMIN', 'TECHNICAL_MANAGER');
  const canExportIdentity = hasRole('ADMIN', 'COMMERCIAL');
  const reports = useReports(canPrepare, initialFilters);
  const [filters, setFilters] = useState<ReportFilters>(initialFilters);
  const [serviceOrderId, setServiceOrderId] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [showPdf, setShowPdf] = useState(false);
  const [showCancel, setShowCancel] = useState(false);
  const [cancellationReason, setCancellationReason] = useState('');

  function submitFilters(event: FormEvent) {
    event.preventDefault();
    void reports.load({ ...filters, page: 0 });
  }

  async function createDraft(event: FormEvent) {
    event.preventDefault();
    if (serviceOrderId && (await reports.create(serviceOrderId))) {
      setShowCreate(false);
      setServiceOrderId('');
    }
  }

  async function cancel(event: FormEvent) {
    event.preventDefault();
    if (
      reports.selectedReport &&
      (await reports.cancel(reports.selectedReport.id, cancellationReason))
    ) {
      setShowCancel(false);
      setCancellationReason('');
    }
  }

  async function exportCsv() {
    const blob = await exportReports(filters);
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = 'informes-resultados.csv';
    anchor.click();
    URL.revokeObjectURL(url);
  }

  return (
    <div className="space-y-6">
      <PageHeader
        actions={
          <div className="flex flex-wrap gap-2">
            {canExportIdentity ? (
              <Button onClick={() => void exportCsv()} variant="secondary">
                <Download size={17} />
                Exportar CSV
              </Button>
            ) : null}
            {canPrepare ? (
              <Button onClick={() => setShowCreate(true)}>
                <FilePlus2 size={17} />
                Preparar informe
              </Button>
            ) : null}
          </div>
        }
        description="Versiona, emite, consulta y conserva los informes oficiales de resultados."
        eyebrow="Documentación técnica"
        title="Informes de resultados"
      />

      <ErrorNotice message={reports.error} />

      <form
        className="grid gap-3 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm sm:grid-cols-[minmax(0,1fr)_13rem_auto]"
        onSubmit={submitFilters}
      >
        {filters.issuedFrom || filters.issuedTo || filters.laboratoryAreaId ? (
          <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl bg-lab-50 px-3 py-2 text-xs font-semibold text-lab-800 sm:col-span-3">
            <span>Vista filtrada desde el dashboard para el período y área seleccionados.</span>
            <button
              className="rounded-lg px-2 py-1 hover:bg-white"
              onClick={() => {
                const next = {
                  ...filters,
                  issuedFrom: undefined,
                  issuedTo: undefined,
                  laboratoryAreaId: undefined,
                  page: 0,
                };
                setFilters(next);
                void reports.load(next);
              }}
              type="button"
            >
              Quitar período
            </button>
          </div>
        ) : null}
        <label className="relative">
          <span className="sr-only">Buscar informe</span>
          <Search className="absolute left-3 top-3 text-slate-400" size={17} />
          <input
            className="w-full rounded-xl border border-slate-200 py-2.5 pl-10 pr-3 text-sm outline-none focus:border-lab-500 focus:ring-4 focus:ring-lab-100"
            onChange={(event) =>
              setFilters((current) => ({ ...current, search: event.target.value }))
            }
            placeholder="Código de informe"
            value={filters.search ?? ''}
          />
        </label>
        <select
          className="rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm outline-none focus:border-lab-500 focus:ring-4 focus:ring-lab-100"
          onChange={(event) =>
            setFilters((current) => ({
              ...current,
              status: event.target.value as LaboratoryReportStatus | '',
            }))
          }
          value={filters.status ?? ''}
        >
          <option value="">Todos los estados</option>
          <option value="DRAFT">Borradores</option>
          <option value="ISSUED">Emitidos</option>
          <option value="CANCELLED">Anulados</option>
        </select>
        <Button type="submit" variant="secondary">
          Filtrar
        </Button>
      </form>

      <div className="grid min-w-0 gap-5 xl:grid-cols-[minmax(0,1.3fr)_minmax(22rem,0.7fr)]">
        <Card className="overflow-hidden">
          <div className="border-b border-slate-100 px-5 py-4">
            <h2 className="font-bold text-slate-950">Versiones registradas</h2>
            <p className="mt-1 text-xs text-slate-500">
              Selecciona una fila para revisar el expediente.
            </p>
          </div>
          {reports.isLoading ? (
            <p className="px-5 py-10 text-sm text-slate-500">Cargando informes…</p>
          ) : reports.reports.length ? (
            <div className="divide-y divide-slate-100">
              {reports.reports.map((report) => (
                <button
                  className="grid w-full gap-3 px-5 py-4 text-left transition hover:bg-slate-50 focus-visible:bg-lab-50 focus-visible:outline-none sm:grid-cols-[1fr_1.2fr_auto]"
                  key={report.id}
                  onClick={() => void reports.open(report.id)}
                  type="button"
                >
                  <div>
                    <p className="font-bold text-slate-900">{report.code}</p>
                    <p className="mt-1 text-xs text-slate-500">Versión {report.version}</p>
                  </div>
                  <div>
                    <p className="text-sm font-semibold text-slate-700">
                      {report.serviceOrderCode}
                    </p>
                    <p className="mt-1 text-xs text-slate-500">
                      {report.clientName || 'Identidad reservada hasta abrir el informe'}
                    </p>
                  </div>
                  <div className="sm:text-right">
                    <StatusBadge value={statusLabels[report.status]} />
                    <p className="mt-2 text-xs text-slate-400">{formatDate(report.createdAt)}</p>
                  </div>
                </button>
              ))}
            </div>
          ) : (
            <EmptyState message="Todavía no existen informes de resultados." />
          )}
        </Card>

        <Card className="self-start overflow-hidden xl:sticky xl:top-6">
          {reports.selectedReport ? (
            <>
              <div className="border-b border-slate-100 px-5 py-4">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="font-bold text-slate-950">{reports.selectedReport.code}</p>
                    <p className="mt-1 text-xs text-slate-500">
                      {reports.selectedReport.snapshot.serviceOrderCode} · versión{' '}
                      {reports.selectedReport.version}
                    </p>
                  </div>
                  <StatusBadge value={statusLabels[reports.selectedReport.status]} />
                </div>
              </div>
              <div className="space-y-5 p-5">
                <dl className="grid gap-4 rounded-xl bg-slate-50 p-4 sm:grid-cols-2">
                  <div>
                    <dt className="text-xs font-bold uppercase text-slate-500">Cliente</dt>
                    <dd className="mt-1 text-sm text-slate-800">
                      {reports.selectedReport.snapshot.clientName}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs font-bold uppercase text-slate-500">Área</dt>
                    <dd className="mt-1 text-sm text-slate-800">
                      {reports.selectedReport.snapshot.laboratoryAreaName}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs font-bold uppercase text-slate-500">Muestras</dt>
                    <dd className="mt-1 text-sm text-slate-800">
                      {reports.selectedReport.snapshot.samples.length}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs font-bold uppercase text-slate-500">Integridad</dt>
                    <dd className="mt-1 font-mono text-xs text-slate-700">
                      {reports.selectedReport.checksum.slice(0, 16)}…
                    </dd>
                  </div>
                </dl>

                {reports.selectedReport.cancellationReason ? (
                  <div className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">
                    <p className="font-bold">Motivo de anulación</p>
                    <p className="mt-1">{reports.selectedReport.cancellationReason}</p>
                  </div>
                ) : null}

                <div className="flex flex-wrap gap-2">
                  <Button onClick={() => setShowPdf(true)} variant="secondary">
                    <Eye size={16} />
                    Vista previa
                  </Button>
                  {canControl && reports.selectedReport.status === 'DRAFT' ? (
                    <Button
                      disabled={reports.isSaving}
                      onClick={() => void reports.issue(reports.selectedReport!.id)}
                    >
                      <FileCheck2 size={16} />
                      Emitir
                    </Button>
                  ) : null}
                  {canControl && reports.selectedReport.status === 'ISSUED' ? (
                    <Button onClick={() => setShowCancel(true)} variant="danger">
                      <Ban size={16} />
                      Anular
                    </Button>
                  ) : null}
                  {canControl && reports.selectedReport.status === 'CANCELLED' ? (
                    <Button
                      disabled={reports.isSaving}
                      onClick={() => void reports.reissue(reports.selectedReport!.id)}
                    >
                      <RefreshCw size={16} />
                      Reemitir
                    </Button>
                  ) : null}
                </div>

                <section>
                  <h3 className="flex items-center gap-2 font-bold text-slate-900">
                    <FileClock className="text-lab-700" size={17} />
                    Historial de versión
                  </h3>
                  <div className="mt-3 space-y-2">
                    {reports.history?.content.map((entry) => (
                      <article className="rounded-xl border border-slate-200 p-3" key={entry.id}>
                        <div className="flex justify-between gap-3 text-sm">
                          <p className="font-semibold text-slate-800">
                            {labelForAction(entry.action)}
                          </p>
                          <time className="text-xs text-slate-400">
                            {formatDate(entry.occurredAt)}
                          </time>
                        </div>
                        <p className="mt-1 text-xs text-slate-600">
                          {localizeSystemText(entry.changeNote)}
                        </p>
                        <p className="mt-2 text-xs text-slate-400">{entry.performedBy}</p>
                      </article>
                    ))}
                  </div>
                </section>
              </div>
            </>
          ) : (
            <EmptyState message="Selecciona un informe para ver su detalle." />
          )}
        </Card>
      </div>

      <Pagination
        onPageChange={(page) => void reports.load({ ...filters, page })}
        {...reports.pagination}
      />

      <Modal
        description="Solo se admiten órdenes finalizadas con todos sus resultados validados."
        isOpen={showCreate}
        onClose={() => setShowCreate(false)}
        title="Preparar informe de resultados"
      >
        <form onSubmit={createDraft}>
          <div className="mb-4 rounded-xl border border-sky-100 bg-sky-50 p-3 text-xs leading-5 text-sky-800">
            La lista técnica no expone clientes. La identidad se revelará únicamente dentro del
            informe preparado y el acceso quedará registrado en auditoría.
          </div>
          <label className="text-sm font-semibold text-slate-700" htmlFor="report-order">
            Orden de servicio
          </label>
          <select
            className="mt-2 w-full rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm outline-none focus:border-lab-500 focus:ring-4 focus:ring-lab-100"
            id="report-order"
            onChange={(event) => setServiceOrderId(event.target.value)}
            required
            value={serviceOrderId}
          >
            <option value="">Selecciona una orden finalizada</option>
            {reports.preparationCandidates.map((order) => (
              <option key={order.id} value={order.id}>
                {order.code} · entrega {formatDate(order.plannedDeliveryAt)}
              </option>
            ))}
          </select>
          <ModalFooter>
            <Button onClick={() => setShowCreate(false)} variant="secondary">
              Cancelar
            </Button>
            <Button disabled={reports.isSaving || !serviceOrderId} type="submit">
              Preparar borrador
            </Button>
          </ModalFooter>
        </form>
      </Modal>

      <Modal
        description="La versión quedará conservada y no podrá volver al estado emitido."
        isOpen={showCancel}
        onClose={() => setShowCancel(false)}
        title="Anular informe emitido"
      >
        <form onSubmit={cancel}>
          <label className="text-sm font-semibold text-slate-700" htmlFor="report-reason">
            Motivo de anulación
          </label>
          <textarea
            className="mt-2 min-h-28 w-full rounded-xl border border-slate-200 p-3 text-sm outline-none focus:border-lab-500 focus:ring-4 focus:ring-lab-100"
            id="report-reason"
            maxLength={1000}
            onChange={(event) => setCancellationReason(event.target.value)}
            required
            value={cancellationReason}
          />
          <ModalFooter>
            <Button onClick={() => setShowCancel(false)} variant="secondary">
              Volver
            </Button>
            <Button
              disabled={reports.isSaving || !cancellationReason.trim()}
              type="submit"
              variant="danger"
            >
              Confirmar anulación
            </Button>
          </ModalFooter>
        </form>
      </Modal>

      {reports.selectedReport ? (
        <ReportPdfModal
          code={reports.selectedReport.code}
          isOpen={showPdf}
          onClose={() => setShowPdf(false)}
          reportId={reports.selectedReport.id}
        />
      ) : null}
    </div>
  );
}
