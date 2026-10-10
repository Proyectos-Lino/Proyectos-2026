import { useState, type FormEvent } from 'react';
import { Button, Modal, ModalFooter } from '../../../shared/components/ui';
import type { ServiceOrderSummary } from '../../../shared/types/domain';

interface CancelOrderModalProps {
  isSaving: boolean;
  order: ServiceOrderSummary | null;
  onClose: () => void;
  onSubmit: (orderId: string, reason: string) => Promise<boolean>;
}

export function CancelOrderModal({ isSaving, order, onClose, onSubmit }: CancelOrderModalProps) {
  const [reason, setReason] = useState('');

  function closeModal() {
    setReason('');
    onClose();
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (order && (await onSubmit(order.id, reason))) {
      closeModal();
    }
  }

  return (
    <Modal
      description="Esta acción queda registrada en el historial y no se puede deshacer."
      isOpen={Boolean(order)}
      onClose={closeModal}
      title={order ? `Anular ${order.code}` : 'Anular orden'}
    >
      <form className="space-y-5" onSubmit={handleSubmit}>
        <div className="space-y-2">
          <label htmlFor="cancel-order-reason">Motivo de anulación</label>
          <textarea
            id="cancel-order-reason"
            maxLength={1000}
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
          <Button disabled={isSaving} type="submit" variant="danger">
            {isSaving ? 'Anulando…' : 'Confirmar anulación'}
          </Button>
        </ModalFooter>
      </form>
    </Modal>
  );
}
