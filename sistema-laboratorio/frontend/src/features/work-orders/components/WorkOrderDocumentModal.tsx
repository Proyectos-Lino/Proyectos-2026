import { Printer } from 'lucide-react';
import { Button, Modal, ModalFooter } from '../../../shared/components/ui';
import { formatDate } from '../../../shared/lib/format';
import { printWorkOrderDocument } from '../../../shared/lib/document-printing';
import type { WorkOrderDocument } from '../../../shared/types/domain';
import { InstitutionalDocumentHeader } from '../../../shared/components/documents/InstitutionalDocumentHeader';

interface WorkOrderDocumentModalProps {
  document: WorkOrderDocument | null;
  onClose: () => void;
}

export function WorkOrderDocumentModal({
  document: workOrderDocument,
  onClose,
}: WorkOrderDocumentModalProps) {
  if (!workOrderDocument) {
    return null;
  }

  return (
    <Modal
      description="Documento técnico sin datos de contacto ni identidad del cliente."
      isOpen
      onClose={onClose}
      panelClassName="max-w-5xl"
      title={`Documento técnico · ${workOrderDocument.code}`}
    >
      <article className="space-y-7 bg-white text-slate-800" id="work-order-document">
        <InstitutionalDocumentHeader
          code={workOrderDocument.code}
          generatedAt={workOrderDocument.generatedAt}
          organization={workOrderDocument.organization}
          section={workOrderDocument.laboratoryAreaName}
          title="Orden de trabajo"
          version={workOrderDocument.version}
        />

        <section className="grid gap-4 border border-slate-700 p-4 sm:grid-cols-3">
          <div>
            <p className="text-xs font-bold uppercase tracking-wide text-slate-400">
              Orden de servicio
            </p>
            <p className="mt-1 text-sm font-semibold text-slate-800">
              {workOrderDocument.serviceOrderCode ?? 'No especificado'}
            </p>
          </div>
          <div>
            <p className="text-xs font-bold uppercase tracking-wide text-slate-400">
              Responsable técnico
            </p>
            <p className="mt-1 text-sm font-semibold text-slate-800">
              {workOrderDocument.responsibleName}
            </p>
          </div>
          <div>
            <p className="text-xs font-bold uppercase tracking-wide text-slate-400">
              Fecha límite de laboratorio
            </p>
            <p className="mt-1 text-sm font-semibold text-slate-800">
              {formatDate(workOrderDocument.laboratoryDueAt)}
            </p>
          </div>
        </section>

        <section>
          <h2 className="border border-slate-700 bg-slate-200 px-4 py-2 text-center text-sm font-extrabold uppercase text-slate-950">
            Muestras y asignaciones
          </h2>
          <div className="mt-3 space-y-4">
            {workOrderDocument.samples.map((sample) => (
              <article
                className="break-inside-avoid rounded-xl border border-slate-200 p-4"
                key={sample.laboratoryCode}
              >
                <header className="border-b border-slate-100 pb-3">
                  <h3 className="font-bold text-slate-900">Muestra {sample.laboratoryCode}</h3>
                  <p className="mt-1 text-xs text-slate-500">{sample.sampleTypeName}</p>
                </header>
                <div className="mt-4 overflow-x-auto">
                  <table className="w-full min-w-125 text-left text-sm">
                    <thead className="bg-lab-50 text-xs uppercase tracking-wide text-lab-800">
                      <tr>
                        <th className="px-3 py-2 font-bold">Ensayo</th>
                        <th className="px-3 py-2 font-bold">Método</th>
                        <th className="px-3 py-2 font-bold">Analista asignado</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {sample.tests.map((test) => (
                        <tr key={`${test.testName}-${test.methodName}-${test.analystName}`}>
                          <td className="px-3 py-2.5 font-semibold text-slate-800">
                            {test.testName}
                          </td>
                          <td className="px-3 py-2.5 text-slate-600">{test.methodName}</td>
                          <td className="px-3 py-2.5 text-slate-600">{test.analystName}</td>
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
        <Button onClick={() => printWorkOrderDocument(workOrderDocument)}>
          <Printer size={16} />
          Imprimir documento
        </Button>
      </ModalFooter>
    </Modal>
  );
}
