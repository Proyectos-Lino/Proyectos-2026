import { Beaker, History, PackageSearch, UserRound } from 'lucide-react';
import { EmptyState, Modal, Pagination } from '../../../shared/components/ui';
import { formatDate } from '../../../shared/lib/format';
import type {
  CatalogSnapshot,
  PageResponse,
  ServiceOrderDetail,
  ServiceOrderHistoryEntry,
  ServiceOrderSample,
} from '../../../shared/types/domain';
import { custodyLabels } from '../model/order-commercial-details';
import { serviceOrderHistoryActionLabels, serviceOrderStatusLabels } from '../model/order-labels';
import { OrderStatusBadge } from './OrderStatusBadge';

interface OrderRecordModalProps {
  catalog: CatalogSnapshot | null;
  history: PageResponse<ServiceOrderHistoryEntry> | null;
  onClose: () => void;
  onHistoryPageChange: (page: number) => void;
  order: ServiceOrderDetail | null;
}

interface RecordItemProps {
  label: string;
  value?: string | number | null;
}

function RecordItem({ label, value }: RecordItemProps) {
  return (
    <div className="min-w-0 rounded-xl border border-slate-200 bg-white p-3">
      <p className="text-xs font-bold uppercase tracking-wide text-slate-400">{label}</p>
      <p className="mt-1 break-words text-sm font-medium text-slate-700">{value || '—'}</p>
    </div>
  );
}

function catalogName(items: Array<{ id: string; name: string }> | undefined, id?: string): string {
  return items?.find((item) => item.id === id)?.name ?? 'No especificado';
}

function formatQuantity(sample: ServiceOrderSample, catalog: CatalogSnapshot | null): string {
  if (sample.quantity === undefined) {
    return '—';
  }

  const measurementUnit = catalogName(catalog?.units, sample.measurementUnitId);
  return measurementUnit === 'No especificado'
    ? String(sample.quantity)
    : `${sample.quantity} ${measurementUnit}`;
}

function SampleRecord({
  catalog,
  sample,
}: {
  catalog: CatalogSnapshot | null;
  sample: ServiceOrderSample;
}) {
  return (
    <article
      className="overflow-hidden rounded-2xl border border-slate-200 bg-slate-50/60"
      key={sample.id}
    >
      <header className="flex flex-wrap items-start justify-between gap-3 border-b border-slate-200 bg-white px-4 py-4">
        <div>
          <p className="text-xs font-bold uppercase tracking-wide text-lab-700">
            Muestra {sample.commercialSequence}
          </p>
          <h4 className="mt-1 font-bold text-slate-900">
            {sample.productName || 'Producto sin nombre'}
          </h4>
          <p className="mt-1 text-xs text-slate-500">
            {catalogName(catalog?.sampleTypes, sample.sampleTypeId)}
            {sample.varietyName ? ` · ${sample.varietyName}` : ''}
          </p>
        </div>
        {sample.clientIdentification ? (
          <span className="rounded-full bg-lab-50 px-3 py-1.5 text-xs font-bold text-lab-800">
            ID: {sample.clientIdentification}
          </span>
        ) : null}
      </header>

      <div className="grid gap-3 p-4 sm:grid-cols-2 xl:grid-cols-3">
        <RecordItem label="Número comercial" value={`Muestra ${sample.commercialSequence}`} />
        <RecordItem label="Identificación del cliente" value={sample.clientIdentification} />
        <RecordItem label="Productor o propietario" value={sample.producerName} />
        <RecordItem label="Documento del productor" value={sample.producerDocument} />
        <RecordItem
          label="Código interno de laboratorio"
          value={sample.technicalIdentification?.laboratoryCode || 'Pendiente de clasificación'}
        />
        <RecordItem
          label="Clasificación técnica"
          value={sample.technicalIdentification?.technicalSampleTypeName}
        />
        <RecordItem label="Clasificada por" value={sample.technicalIdentification?.classifiedBy} />
        <RecordItem
          label="Fecha de clasificación"
          value={
            sample.technicalIdentification?.classifiedAt
              ? formatDate(sample.technicalIdentification.classifiedAt)
              : undefined
          }
        />
        <RecordItem label="Descripción física" value={sample.physicalDescription} />
        <RecordItem label="Lugar de muestreo" value={sample.samplingLocation} />
        <RecordItem label="Origen / procedencia" value={sample.origin} />
        <RecordItem label="Muestreado por" value={sample.samplerName} />
        <RecordItem
          label="Fecha de muestreo"
          value={sample.sampledAt ? formatDate(sample.sampledAt) : undefined}
        />
        <RecordItem label="Cantidad recibida" value={formatQuantity(sample, catalog)} />
        <RecordItem label="Recipiente" value={sample.containerDescription} />
        <RecordItem label="Lote" value={sample.batchNumber} />
        <RecordItem label="Año de producción" value={sample.productionYear} />
        <RecordItem label="Marca" value={sample.brand} />
        <RecordItem label="Certificado de origen" value={sample.originCertificate} />
        <RecordItem label="Registro sanitario" value={sample.sanitaryRegistration} />
        <RecordItem label="Edad del cultivo" value={sample.cropAge} />
        <RecordItem label="Número de cargadores" value={sample.carrierCount} />
        <RecordItem label="Número de yemas" value={sample.budCount} />
        <RecordItem
          label="Contramuestra"
          value={sample.counterSampleInCustody ? 'En custodia' : 'No registrada'}
        />
      </div>

      {sample.observations ? (
        <div className="mx-4 mb-4 rounded-xl border border-amber-100 bg-amber-50 p-3 text-sm text-amber-900">
          <span className="font-bold">Observaciones de la muestra: </span>
          {sample.observations}
        </div>
      ) : null}

      <div className="border-t border-slate-200 bg-white p-4">
        <div className="flex items-center gap-2">
          <Beaker className="text-lab-700" size={16} />
          <h5 className="text-sm font-bold text-slate-900">Ensayos solicitados</h5>
        </div>
        <div className="mt-3 overflow-x-auto rounded-xl border border-slate-200">
          <table className="min-w-160 w-full text-left text-sm">
            <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-3 py-2.5 font-bold">Servicio</th>
                <th className="px-3 py-2.5 font-bold">Ensayo</th>
                <th className="px-3 py-2.5 font-bold">Método</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {sample.requestedTests.map((test) => (
                <tr key={test.id}>
                  <td className="px-3 py-3 text-slate-600">
                    {catalogName(catalog?.services, test.serviceId)}
                  </td>
                  <td className="px-3 py-3 font-semibold text-slate-800">
                    {catalogName(catalog?.tests, test.testDefinitionId)}
                  </td>
                  <td className="px-3 py-3 text-slate-600">
                    {catalogName(catalog?.methods, test.testMethodId)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </article>
  );
}

export function OrderRecordModal({
  catalog,
  history,
  onClose,
  onHistoryPageChange,
  order,
}: OrderRecordModalProps) {
  if (!order) {
    return null;
  }

  return (
    <Modal
      description="Consulta integral y trazable de la orden de servicio."
      isOpen
      onClose={onClose}
      panelClassName="max-w-6xl"
      title={`Expediente completo · ${order.code}`}
    >
      <article className="space-y-7">
        <header className="flex flex-wrap items-start justify-between gap-4 rounded-2xl border border-lab-100 bg-lab-50/60 p-5">
          <div>
            <p className="text-xs font-bold uppercase tracking-wide text-lab-700">
              Orden de servicio
            </p>
            <h3 className="mt-1 text-2xl font-bold tracking-tight text-slate-950">{order.code}</h3>
            <p className="mt-2 text-sm text-slate-600">
              Registrada el {formatDate(order.createdAt)} · última actualización{' '}
              {formatDate(order.updatedAt)}
            </p>
          </div>
          <OrderStatusBadge status={order.status} />
        </header>

        <section>
          <div className="flex items-center gap-2">
            <UserRound className="text-lab-700" size={18} />
            <h3 className="font-bold text-slate-900">
              Solicitante, informe y trazabilidad comercial
            </h3>
          </div>
          <div className="mt-3 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            <RecordItem label="Cliente / razón social" value={order.clientName} />
            <RecordItem label="Documento" value={order.clientDocument} />
            <RecordItem label="Área de laboratorio" value={order.laboratoryAreaName} />
            <RecordItem label="Nombre de informe" value={order.reportName} />
            <RecordItem label="Correo de informe" value={order.reportEmail} />
            <RecordItem label="Dirección de informe" value={order.reportAddress} />
            <RecordItem
              label="Modalidad de entrega"
              value={order.deliveryMethod === 'EMAIL' ? 'Correo electrónico' : 'Recojo'}
            />
            <RecordItem label="Persona que recoge" value={order.pickupPerson} />
            <RecordItem label="Entrega programada" value={formatDate(order.plannedDeliveryAt)} />
            <RecordItem label="Número de proforma" value={order.proformaNumber} />
            <RecordItem label="Propuesta técnica" value={order.technicalProposalNumber} />
            <RecordItem
              label="Plazo de desarrollo"
              value={
                order.developmentDays === undefined ? undefined : `${order.developmentDays} día(s)`
              }
            />
            <RecordItem
              label="Modalidad del servicio"
              value={order.outsourced ? 'Subcontratado' : 'Interno'}
            />
            <RecordItem
              label="Custodia posensayo"
              value={custodyLabels[order.postAnalysisCustody]}
            />
            <RecordItem
              label="Confirmada para laboratorio"
              value={order.confirmedAt ? formatDate(order.confirmedAt) : undefined}
            />
            <RecordItem
              label="Finalizada el"
              value={order.finalizedAt ? formatDate(order.finalizedAt) : undefined}
            />
          </div>
        </section>

        {order.generalObservations ? (
          <section className="rounded-2xl border border-amber-100 bg-amber-50 p-4">
            <h3 className="font-bold text-amber-950">Observaciones generales</h3>
            <p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-amber-900">
              {order.generalObservations}
            </p>
          </section>
        ) : null}

        {order.cancellationReason ? (
          <section className="rounded-2xl border border-rose-200 bg-rose-50 p-4">
            <h3 className="font-bold text-rose-900">Anulación registrada</h3>
            <p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-rose-800">
              {order.cancellationReason}
            </p>
            {order.cancelledAt ? (
              <p className="mt-2 text-xs font-medium text-rose-700">
                {formatDate(order.cancelledAt)}
              </p>
            ) : null}
          </section>
        ) : null}

        <section>
          <div className="flex items-center gap-2">
            <PackageSearch className="text-lab-700" size={18} />
            <div>
              <h3 className="font-bold text-slate-900">Muestras y ensayos</h3>
              <p className="mt-1 text-sm text-slate-500">
                Registro completo de los datos recibidos y de los análisis solicitados.
              </p>
            </div>
          </div>
          <div className="mt-4 space-y-4">
            {order.samples.map((sample) => (
              <SampleRecord catalog={catalog} key={sample.id} sample={sample} />
            ))}
          </div>
        </section>

        <section className="border-t border-slate-200 pt-6">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <History className="text-lab-700" size={18} />
              <div>
                <h3 className="font-bold text-slate-900">Historial completo de cambios</h3>
                <p className="mt-1 text-sm text-slate-500">
                  {history
                    ? `${history.totalElements} movimiento(s) registrado(s).`
                    : 'Cargando historial…'}
                </p>
              </div>
            </div>
          </div>

          {history ? (
            history.content.length ? (
              <div className="mt-4 space-y-3">
                {history.content.map((entry) => (
                  <article
                    className="rounded-xl border border-slate-200 bg-white p-4"
                    key={entry.id}
                  >
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div>
                        <p className="font-semibold text-slate-800">
                          {serviceOrderHistoryActionLabels[entry.action]}
                        </p>
                        {entry.changeNote ? (
                          <p className="mt-1 text-sm leading-6 text-slate-600">
                            {entry.changeNote}
                          </p>
                        ) : null}
                      </div>
                      <p className="text-xs text-slate-500">{formatDate(entry.occurredAt)}</p>
                    </div>
                    <div className="mt-3 flex flex-wrap gap-x-5 gap-y-1 text-xs text-slate-500">
                      <span>Realizado por: {entry.performedBy}</span>
                      {entry.previousStatus || entry.newStatus ? (
                        <span>
                          Estado:{' '}
                          {entry.previousStatus
                            ? serviceOrderStatusLabels[entry.previousStatus]
                            : '—'}{' '}
                          → {entry.newStatus ? serviceOrderStatusLabels[entry.newStatus] : '—'}
                        </span>
                      ) : null}
                    </div>
                  </article>
                ))}
                <Pagination
                  onPageChange={onHistoryPageChange}
                  page={history.page}
                  totalElements={history.totalElements}
                  totalPages={history.totalPages}
                />
              </div>
            ) : (
              <div className="mt-4">
                <EmptyState message="No hay cambios registrados todavía." />
              </div>
            )
          ) : null}
        </section>
      </article>
    </Modal>
  );
}
