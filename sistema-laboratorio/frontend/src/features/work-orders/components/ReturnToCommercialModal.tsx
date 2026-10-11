import { useState, type FormEvent } from 'react';
import { Button, Modal, ModalFooter } from '../../../shared/components/ui';
import type { WorkOrderDetail } from '../../../shared/types/domain';

interface ReturnToCommercialModalProps {
  isSaving: boolean;
  onClose: () => void;
  onSubmit: (serviceOrderId: string, reason: string) => Promise<boolean>;
  workOrder: WorkOrderDetail | null;
}

export function ReturnToCommercialModal({
  isSaving,
  onClose,
  onSubmit,
  workOrder,
}: ReturnToCommercialModalProps) {
  const [reason, setReason] = useState('');

  function close() {
    setReason('');
    onClose();
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!workOrder || !reason.trim()) return;
    if (await onSubmit(workOrder.serviceOrderId, reason.trim())) {
      close();
    }
  }

  return (
    <Modal
      description="Las órdenes de trabajo se conservarán y quedarán pausadas hasta que la corrección sea revisada."
      isOpen={workOrder !== null}
      onClose={close}
      title={`Devolver ${workOrder?.serviceOrderCode ?? ''} a comercial`}
    >
      <form onSubmit={submit}>
        <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-900">
          Si existe un informe vigente, será anulado de forma trazable. Los resultados no se
          eliminarán y solo se invalidarán si los cambios técnicos los hacen incompatibles.
        </div>
        <div className="mt-5 space-y-2">
          <label htmlFor="commercial-return-reason">Motivo de la devolución</label>
          <textarea
            id="commercial-return-reason"
            onChange={(event) => setReason(event.target.value)}
            placeholder="Indica el dato incorrecto y la corrección que debe realizar comercial."
            required
            rows={5}
            value={reason}
          />
        </div>
        <ModalFooter>
          <Button onClick={close} variant="secondary">
            Cancelar
          </Button>
          <Button disabled={isSaving || !reason.trim()} type="submit" variant="danger">
            {isSaving ? 'Devolviendo…' : 'Confirmar devolución'}
          </Button>
        </ModalFooter>
      </form>
    </Modal>
  );
}
