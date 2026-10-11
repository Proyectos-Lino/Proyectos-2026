import { useState, type FormEvent } from 'react';
import { Button, Modal, ModalFooter } from '../../../shared/components/ui';
import type { WorkOrderDetail } from '../../../shared/types/domain';

interface CancelWorkOrderModalProps {
  isSaving: boolean;
  onClose: () => void;
  onSubmit: (workOrderId: string, reason: string) => Promise<boolean>;
  workOrder: WorkOrderDetail | null;
}

export function CancelWorkOrderModal({
  isSaving,
  onClose,
  onSubmit,
  workOrder,
}: CancelWorkOrderModalProps) {
  const [reason, setReason] = useState('');

  function closeModal() {
    setReason('');
    onClose();
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!workOrder || !reason.trim()) {
      return;
    }
    if (await onSubmit(workOrder.id, reason.trim())) {
      closeModal();
    }
  }

  return (
    <Modal
      description="Solo se puede anular una orden sin resultados registrados. La acción quedará auditada."
      isOpen={Boolean(workOrder)}
      onClose={closeModal}
      title={workOrder ? `Anular ${workOrder.code}` : 'Anular orden de trabajo'}
    >
      <form className="space-y-5" onSubmit={handleSubmit}>
        <div className="space-y-2">
          <label htmlFor="work-order-cancellation-reason">Motivo de anulación</label>
          <textarea
            id="work-order-cancellation-reason"
            onChange={(event) => setReason(event.target.value)}
            required
            rows={4}
            value={reason}
          />
        </div>
        <ModalFooter>
          <Button onClick={closeModal} variant="secondary">
            Volver
          </Button>
          <Button disabled={isSaving || !reason.trim()} type="submit" variant="danger">
            {isSaving ? 'Anulando…' : 'Confirmar anulación'}
          </Button>
        </ModalFooter>
      </form>
    </Modal>
  );
}
