import { Printer } from 'lucide-react';
import { Button, Modal, ModalFooter } from '../../../shared/components/ui';
import { formatDate } from '../../../shared/lib/format';
import { printServiceOrderDocument } from '../../../shared/lib/document-printing';
import type { ServiceOrderDocument } from '../../../shared/types/domain';
import { InstitutionalDocumentHeader } from '../../../shared/components/documents/InstitutionalDocumentHeader';
import { custodyLabels } from '../model/order-commercial-details';

interface OrderDocumentModalProps {
  document: ServiceOrderDocument | null;
  onClose: () => void;
}

interface DocumentItemProps {
  label: string;
  value?: string | number | null;
}

function DocumentItem({ label, value }: DocumentItemProps) {
  return (
    <div>
      <p className="text-xs font-bold uppercase tracking-wide text-slate-400">{label}</p>
      <p className="mt-1 text-sm text-slate-700">{value || '—'}</p>
    </div>
  );
}

export function OrderDocumentModal({ document: orderDocument, onClose }: OrderDocumentModalProps) {
  if (!orderDocument) {
    return null;
  }

  return (
    <Modal
      description="Vista consolidada e inmutable de la orden finalizada."
      isOpen
      onClose={onClose}
      panelClassName="max-w-5xl"
      title={`Documento de orden · ${orderDocument.code}`}
    >
      <article className="space-y-7 bg-white text-slate-800" id="service-order-document">
        <InstitutionalDocumentHeader
          code={orderDocument.code}
          generatedAt={orderDocument.generatedAt}
          organization={orderDocument.organization}
          section={`${orderDocument.laboratoryAreaName} · Gestión comercial y recepción`}
          title="Orden de servicio"
          version={orderDocument.version}
        />

        <section>
          <h2 className="border border-slate-700 bg-slate-200 px-4 py-2 text-center text-sm font-extrabold uppercase text-slate-950">
            Datos generales
          </h2>
          <div className="grid gap-4 border-x border-b border-slate-700 p-4 sm:grid-cols-2">
            <DocumentItem label="Cliente" value={orderDocument.clientName} />
            <DocumentItem label="Documento" value={orderDocument.clientDocument} />
            <DocumentItem label="Nombre de informe" value={orderDocument.reportName} />
            <DocumentItem label="Correo de informe" value={orderDocument.reportEmail} />
            <DocumentItem label="Dirección" value={orderDocument.reportAddress} />
            <DocumentItem
              label="Entrega programada"
              value={formatDate(orderDocument.plannedDeliveryAt)}
            />
            <DocumentItem
              label="Modalidad de entrega"
              value={orderDocument.deliveryMethod === 'EMAIL' ? 'Correo electrónico' : 'Recojo'}
            />
            {orderDocument.deliveryMethod === 'PICKUP' ? (
              <DocumentItem label="Persona que recoge" value={orderDocument.pickupPerson} />
            ) : null}
            <DocumentItem label="Proforma" value={orderDocument.proformaNumber} />
            <DocumentItem label="Propuesta técnica" value={orderDocument.technicalProposalNumber} />
            <DocumentItem
              label="Días de desarrollo"
              value={
                orderDocument.developmentDays === undefined
                  ? undefined
                  : `${orderDocument.developmentDays} día(s)`
              }
            />
            <DocumentItem
              label="Modalidad de servicio"
              value={orderDocument.outsourced ? 'Subcontratado' : 'Interno'}
            />
            <DocumentItem
              label="Custodia posensayo"
              value={custodyLabels[orderDocument.postAnalysisCustody]}
            />
          </div>
        </section>

        {orderDocument.generalObservations ? (
          <section>
            <h2 className="text-base font-bold text-slate-950">Observaciones generales</h2>
            <p className="mt-3 rounded-xl bg-slate-50 p-4 text-sm leading-6 text-slate-700">
              {orderDocument.generalObservations}
            </p>
          </section>
        ) : null}

        <section>
          <h2 className="border border-slate-700 bg-slate-200 px-4 py-2 text-center text-sm font-extrabold uppercase text-slate-950">
            Datos de las muestras y ensayos solicitados
          </h2>
          <div className="mt-3 space-y-4">
            {orderDocument.samples.map((sample) => (
              <article
                className="break-inside-avoid rounded-xl border border-slate-200 p-4"
                key={sample.commercialSequence}
              >
                <header className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 pb-3">
                  <div>
                    <h3 className="font-bold text-slate-900">
                      Muestra {sample.commercialSequence}: {sample.productName}
                    </h3>
                    <p className="mt-1 text-xs text-slate-500">
                      {sample.sampleTypeName}
                      {sample.varietyName ? ` · ${sample.varietyName}` : ''}
                    </p>
                  </div>
                  {sample.clientIdentification ? (
                    <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-600">
                      {sample.clientIdentification}
                    </span>
                  ) : null}
                </header>

                <div className="mt-4 grid gap-x-5 gap-y-4 sm:grid-cols-2 lg:grid-cols-3">
                  <DocumentItem label="Productor o propietario" value={sample.producerName} />
                  <DocumentItem label="Documento del productor" value={sample.producerDocument} />
                  <DocumentItem label="Descripción física" value={sample.physicalDescription} />
                  <DocumentItem label="Lugar de muestreo" value={sample.samplingLocation} />
                  <DocumentItem label="Procedencia" value={sample.origin} />
                  <DocumentItem label="Muestreado por" value={sample.samplerName} />
                  <DocumentItem
                    label="Fecha de muestreo"
                    value={sample.sampledAt ? formatDate(sample.sampledAt) : undefined}
                  />
                  <DocumentItem
                    label="Cantidad recibida"
                    value={
                      sample.quantity === undefined
                        ? undefined
                        : `${sample.quantity} ${sample.measurementUnitName ?? ''}`.trim()
                    }
                  />
                  <DocumentItem label="Recipiente" value={sample.containerDescription} />
                  <DocumentItem label="Lote" value={sample.batchNumber} />
                  <DocumentItem label="Año de producción" value={sample.productionYear} />
                  <DocumentItem label="Marca" value={sample.brand} />
                  <DocumentItem label="Certificado de origen" value={sample.originCertificate} />
                  <DocumentItem label="Registro sanitario" value={sample.sanitaryRegistration} />
                  <DocumentItem label="Edad del cultivo" value={sample.cropAge} />
                  <DocumentItem label="Número de cargadores" value={sample.carrierCount} />
                  <DocumentItem label="Número de yemas" value={sample.budCount} />
                  <DocumentItem
                    label="Contramuestra"
                    value={sample.counterSampleInCustody ? 'En custodia' : 'No registrada'}
                  />
                </div>

                {sample.observations ? (
                  <p className="mt-4 rounded-lg bg-slate-50 p-3 text-sm text-slate-700">
                    <span className="font-bold">Observaciones: </span>
                    {sample.observations}
                  </p>
                ) : null}

                <div className="mt-4 overflow-x-auto">
                  <table className="w-full min-w-125 text-left text-sm">
                    <thead className="bg-lab-50 text-xs uppercase tracking-wide text-lab-800">
                      <tr>
                        <th className="px-3 py-2 font-bold">Servicio</th>
                        <th className="px-3 py-2 font-bold">Ensayo</th>
                        <th className="px-3 py-2 font-bold">Método</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {sample.requestedTests.map((test) => (
                        <tr key={`${test.serviceName}-${test.testName}-${test.methodName}`}>
                          <td className="px-3 py-2.5 text-slate-600">{test.serviceName}</td>
                          <td className="px-3 py-2.5 font-semibold text-slate-800">
                            {test.testName}
                          </td>
                          <td className="px-3 py-2.5 text-slate-600">{test.methodName}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </article>
            ))}
          </div>
        </section>
      </article>

      <ModalFooter>
        <Button onClick={() => printServiceOrderDocument(orderDocument)}>
          <Printer size={16} />
          Imprimir documento
        </Button>
      </ModalFooter>
    </Modal>
  );
}
