import {
  BookOpenText,
  ClipboardCheck,
  ClipboardList,
  FilePlus2,
  Pencil,
  Printer,
  ShieldCheck,
} from 'lucide-react';
import { Button, Card, EmptyState } from '../../../shared/components/ui';
import { formatDate } from '../../../shared/lib/format';
import type {
  PageResponse,
  ServiceOrderDetail,
  ServiceOrderHistoryEntry,
} from '../../../shared/types/domain';
import { custodyLabels } from '../model/order-commercial-details';
import { OrderHistory } from './OrderHistory';
import { OrderStatusBadge } from './OrderStatusBadge';
import { OrderTechnicalProgress } from './OrderTechnicalProgress';

interface OrderDetailProps {
  canManage: boolean;
  history: PageResponse<ServiceOrderHistoryEntry> | null;
  isChangingStatus: boolean;
  onEditCommercialDetails: (order: ServiceOrderDetail) => void;
  onEditSamples: (order: ServiceOrderDetail) => void;
  onFinalize: (orderId: string) => void;
  onOpenRecord: (order: ServiceOrderDetail) => void;
  onSubmitCorrection: (order: ServiceOrderDetail) => void;
  onViewDocument: (orderId: string) => void;
  order: ServiceOrderDetail | null;
}

interface DetailItemProps {
  label: string;
  value?: string | number | null;
}

function DetailItem({ label, value }: DetailItemProps) {
  return (
    <div>
      <p className="text-xs font-bold uppercase tracking-wide text-slate-400">{label}</p>
      <p className="mt-1 text-sm text-slate-700">{value || '—'}</p>
    </div>
  );
}

export function OrderDetail({
  canManage,
  history,
  isChangingStatus,
  onEditCommercialDetails,
  onEditSamples,
  onFinalize,
  onOpenRecord,
  onSubmitCorrection,
  onViewDocument,
  order,
}: OrderDetailProps) {
  return (
    <Card className="h-fit">
      <header className="flex items-start justify-between gap-4 border-b border-slate-100 px-5 py-4">
        <div>
          <h2 className="font-bold text-slate-900">Detalle comercial</h2>
          <p className="mt-1 text-xs text-slate-500">Datos de informe, custodia y trazabilidad.</p>
        </div>
        {order ? <OrderStatusBadge status={order.status} /> : null}
      </header>
      {order ? (
        <div className="space-y-5 p-5">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="text-lg font-bold text-slate-950">{order.code}</p>
              <p className="mt-1 text-sm text-slate-500">
                Registrada el {formatDate(order.createdAt)}
              </p>
            </div>
            <div className="flex flex-wrap gap-2">
              {canManage && order.status === 'DRAFT' ? (
                <>
                  <Button onClick={() => onEditCommercialDetails(order)} variant="secondary">
                    <Pencil size={16} />
                    Datos comerciales
                  </Button>
                  <Button onClick={() => onEditSamples(order)} variant="secondary">
                    <ClipboardList size={16} />
                    Muestras y ensayos
                  </Button>
                </>
              ) : null}
              {canManage && order.status === 'COMMERCIAL_CORRECTION' ? (
                <>
                  <Button onClick={() => onEditSamples(order)} variant="secondary">
                    <Pencil size={16} />
                    Corregir orden completa
                  </Button>
                  <Button onClick={() => onSubmitCorrection(order)}>
                    <ClipboardCheck size={16} />
                    Enviar a revisión
                  </Button>
                </>
              ) : null}
              {order.confirmedAt ? (
                <Button onClick={() => onViewDocument(order.id)} variant="secondary">
                  <Printer size={16} />
                  Ver documento
                </Button>
              ) : null}
              {canManage ? (
                <Button onClick={() => onOpenRecord(order)} variant="secondary">
                  <BookOpenText size={16} />
                  Expediente completo
                </Button>
              ) : null}
            </div>
          </div>

          {order.status === 'COMMERCIAL_CORRECTION' ? (
            <section className="rounded-xl border border-amber-200 bg-amber-50 p-4">
              <p className="text-sm font-bold text-amber-900">Corrección comercial pendiente</p>
              <p className="mt-2 text-sm leading-6 text-amber-900">
                {order.correctionReason || 'El responsable técnico solicitó corregir esta orden.'}
              </p>
              <p className="mt-2 text-xs text-amber-700">
                La operación técnica permanece pausada hasta que los cambios sean revisados.
              </p>
            </section>
          ) : null}

          {order.status === 'CORRECTION_REVIEW' ? (
            <section className="rounded-xl border border-sky-200 bg-sky-50 p-4">
              <p className="text-sm font-bold text-sky-900">Corrección enviada a revisión</p>
              <p className="mt-2 text-sm text-sky-800">
                Los datos están bloqueados mientras el responsable técnico revisa el impacto.
              </p>
            </section>
          ) : null}

          <section className="grid gap-4 rounded-xl bg-slate-50 p-4 sm:grid-cols-2">
            <DetailItem label="Nombre de informe" value={order.reportName} />
            <DetailItem label="Correo de informe" value={order.reportEmail} />
            <DetailItem label="Dirección" value={order.reportAddress} />
            <DetailItem
              label="Entrega"
              value={order.deliveryMethod === 'EMAIL' ? 'Correo' : 'Recojo'}
            />
            {order.deliveryMethod === 'PICKUP' ? (
              <DetailItem label="Persona que recoge" value={order.pickupPerson} />
            ) : null}
            <DetailItem label="Entrega programada" value={formatDate(order.plannedDeliveryAt)} />
            <DetailItem label="Proforma" value={order.proformaNumber} />
            <DetailItem label="Propuesta técnica" value={order.technicalProposalNumber} />
            <DetailItem
              label="Días de desarrollo"
              value={
                order.developmentDays === undefined ? undefined : `${order.developmentDays} día(s)`
              }
            />
            <DetailItem label="Servicio" value={order.outsourced ? 'Subcontratado' : 'Interno'} />
            <DetailItem
              label="Custodia posensayo"
              value={custodyLabels[order.postAnalysisCustody]}
            />
          </section>

          {order.status === 'IN_PROGRESS' ? (
            <OrderTechnicalProgress
              isChangingStatus={isChangingStatus}
              onFinalize={() => onFinalize(order.id)}
              progress={order.technicalProgress}
            />
          ) : null}

          {order.generalObservations ? (
            <section>
              <h3 className="text-sm font-bold text-slate-900">Observaciones generales</h3>
              <p className="mt-2 rounded-xl bg-slate-50 p-3 text-sm leading-6 text-slate-600">
                {order.generalObservations}
              </p>
            </section>
          ) : null}

          {order.cancellationReason ? (
            <section className="rounded-xl border border-rose-200 bg-rose-50 p-4">
              <div className="flex items-center gap-2 text-sm font-bold text-rose-800">
                <ShieldCheck size={16} />
                Motivo de anulación
              </div>
              <p className="mt-2 text-sm leading-6 text-rose-800">{order.cancellationReason}</p>
              {order.cancelledAt ? (
                <p className="mt-2 text-xs text-rose-700">{formatDate(order.cancelledAt)}</p>
              ) : null}
            </section>
          ) : null}

          <section className="space-y-3">
            <h3 className="font-bold text-slate-900">Muestras y ensayos</h3>
            {order.samples.map((sample) => (
              <article
                className="rounded-xl border border-slate-200 bg-slate-50/60 p-4"
                key={sample.id}
              >
                <p className="font-semibold text-slate-800">
                  Muestra {sample.commercialSequence}: {sample.productName || 'Sin producto'}
                </p>
                <p className="mt-1 text-xs text-slate-500">
                  {sample.requestedTests.length} ensayo(s) solicitado(s)
                </p>
                <div className="mt-3 grid gap-2 rounded-lg border border-slate-200 bg-white p-3 text-xs text-slate-600 sm:grid-cols-3">
                  <span>
                    <strong className="block text-slate-800">N.º comercial</strong>
                    Muestra {sample.commercialSequence}
                  </span>
                  <span>
                    <strong className="block text-slate-800">Identificación del cliente</strong>
                    {sample.clientIdentification || 'No indicada'}
                  </span>
                  <span>
                    <strong className="block text-slate-800">Código interno</strong>
                    {sample.technicalIdentification?.laboratoryCode || 'Pendiente de clasificación'}
                  </span>
                </div>
                <div className="mt-3 grid gap-2 text-xs text-slate-600 sm:grid-cols-2">
                  {sample.producerName ? (
                    <span>
                      Productor: {sample.producerName}
                      {sample.producerDocument ? ` · ${sample.producerDocument}` : ''}
                    </span>
                  ) : null}
                  {sample.samplingLocation ? <span>Lugar: {sample.samplingLocation}</span> : null}
                  {sample.origin ? <span>Origen: {sample.origin}</span> : null}
                  {sample.batchNumber ? <span>Lote: {sample.batchNumber}</span> : null}
                  {sample.counterSampleInCustody ? <span>Contramuestra en custodia</span> : null}
                </div>
              </article>
            ))}
          </section>

          {order.status === 'IN_PROGRESS' ? (
            <p className="flex gap-2 text-xs leading-5 text-slate-500">
              <FilePlus2 className="shrink-0 text-lab-700" size={15} />
              La orden se encuentra disponible para crear una orden de trabajo desde el módulo
              técnico.
            </p>
          ) : null}

          <OrderHistory history={history} onViewAll={() => onOpenRecord(order)} />
        </div>
      ) : (
        <EmptyState message="Selecciona una orden para ver su detalle." />
      )}
    </Card>
  );
}
