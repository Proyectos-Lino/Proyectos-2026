import { useState, type FormEvent } from 'react';
import { Button, Modal, ModalFooter } from '../../../shared/components/ui';
import type { ServiceOrderDetail } from '../../../shared/types/domain';

interface SubmitCorrectionModalProps {
  isSaving: boolean;
  onClose: () => void;
  onSubmit: (orderId: string, note: string) => Promise<boolean>;
  order: ServiceOrderDetail | null;
}

export function SubmitCorrectionModal({
  isSaving,
  onClose,
  onSubmit,
  order,
}: SubmitCorrectionModalProps) {
  const [note, setNote] = useState('');

  function close() {
    setNote('');
    onClose();
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!order || !note.trim()) return;
    if (await onSubmit(order.id, note.trim())) {
      close();
    }
  }

  return (
    <Modal
      description="El responsable técnico revisará los cambios antes de reanudar la operación."
      isOpen={order !== null}
      onClose={close}
      title={`Enviar corrección · ${order?.code ?? ''}`}
    >
      <form onSubmit={submit}>
        <div className="space-y-2">
          <label htmlFor="correction-change-note">Resumen de los cambios realizados</label>
          <textarea
            id="correction-change-note"
            onChange={(event) => setNote(event.target.value)}
            placeholder="Describe qué datos fueron corregidos y por qué."
            required
            rows={5}
            value={note}
          />
        </div>
        <ModalFooter>
          <Button onClick={close} variant="secondary">
            Cancelar
          </Button>
          <Button disabled={isSaving || !note.trim()} type="submit">
            {isSaving ? 'Enviando…' : 'Enviar a revisión técnica'}
          </Button>
        </ModalFooter>
      </form>
    </Modal>
  );
}
