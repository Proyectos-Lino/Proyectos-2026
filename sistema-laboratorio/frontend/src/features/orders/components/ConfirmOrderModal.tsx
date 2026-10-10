import { LockKeyhole, Send } from 'lucide-react';
import { Button, Modal, ModalFooter } from '../../../shared/components/ui';
import type { ServiceOrderSummary } from '../../../shared/types/domain';

interface ConfirmOrderModalProps {
  isSaving: boolean;
  onClose: () => void;
  onConfirm: (orderId: string) => Promise<boolean>;
  order: ServiceOrderSummary | null;
}

export function ConfirmOrderModal({ isSaving, onClose, onConfirm, order }: ConfirmOrderModalProps) {
  async function handleConfirm() {
    if (order && (await onConfirm(order.id))) {
      onClose();
    }
  }

  return (
    <Modal
      description="Confirma que la información comercial, las muestras y los ensayos están completos."
      isOpen={Boolean(order)}
      onClose={onClose}
      panelClassName="max-w-xl"
      title={order ? `Enviar ${order.code} al laboratorio` : 'Enviar orden al laboratorio'}
    >
      <div className="space-y-5">
        <section className="rounded-2xl border border-lab-100 bg-lab-50/60 p-4">
          <div className="flex items-start gap-3">
            <span className="rounded-xl bg-white p-2 text-lab-700 shadow-sm">
              <Send size={19} />
            </span>
            <div>
              <h3 className="font-bold text-slate-900">Inicio formal del trabajo técnico</h3>
              <p className="mt-1 text-sm leading-6 text-slate-600">
                La orden quedará disponible para emitir una o varias órdenes de trabajo y se
                generará su documento oficial.
              </p>
            </div>
          </div>
        </section>

        <div className="flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
          <LockKeyhole className="mt-0.5 shrink-0" size={18} />
          <p className="leading-6">
            Después de confirmar, los datos comerciales, muestras y ensayos quedarán bloqueados para
            preservar la trazabilidad.
          </p>
        </div>

        <ModalFooter>
          <Button disabled={isSaving} onClick={onClose} variant="secondary">
            Volver
          </Button>
          <Button disabled={isSaving} onClick={() => void handleConfirm()}>
            <Send size={16} />
            {isSaving ? 'Enviando…' : 'Confirmar y enviar'}
          </Button>
        </ModalFooter>
      </div>
    </Modal>
  );
}
