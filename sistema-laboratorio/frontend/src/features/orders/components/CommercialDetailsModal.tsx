import { useState, type FormEvent } from 'react';
import { Button, Modal, ModalFooter } from '../../../shared/components/ui';
import type { ServiceOrderDetail } from '../../../shared/types/domain';
import type { CommercialDetailsInput } from '../api/orders-api';
import { CommercialDetailsFields } from './CommercialDetailsFields';
import type { CommercialDetailsFormValues } from '../model/order-commercial-details';

interface CommercialDetailsModalProps {
  isOpen: boolean;
  isSaving: boolean;
  order: ServiceOrderDetail | null;
  onClose: () => void;
  onSubmit: (orderId: string, input: CommercialDetailsInput) => Promise<boolean>;
}

function toLocalDateTime(value: string): string {
  const date = new Date(value);
  const localDate = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return localDate.toISOString().slice(0, 16);
}

function toFormValues(order: ServiceOrderDetail): CommercialDetailsFormValues {
  return {
    reportName: order.reportName ?? '',
    reportEmail: order.reportEmail ?? '',
    reportAddress: order.reportAddress ?? '',
    deliveryMethod: order.deliveryMethod,
    pickupPerson: order.pickupPerson ?? '',
    plannedDeliveryAt: toLocalDateTime(order.plannedDeliveryAt),
    proformaNumber: order.proformaNumber ?? '',
    technicalProposalNumber: order.technicalProposalNumber ?? '',
    developmentDays: order.developmentDays?.toString() ?? '',
    outsourced: order.outsourced,
    postAnalysisCustody: order.postAnalysisCustody,
    generalObservations: order.generalObservations ?? '',
  };
}

export function CommercialDetailsModal({
  isOpen,
  isSaving,
  order,
  onClose,
  onSubmit,
}: CommercialDetailsModalProps) {
  const [form, setForm] = useState<CommercialDetailsFormValues>(() =>
    order ? toFormValues(order) : ({} as CommercialDetailsFormValues),
  );

  function closeModal() {
    onClose();
  }

  function updateField(field: keyof CommercialDetailsFormValues, value: string | boolean) {
    setForm((current) => ({ ...current, [field]: value }) as CommercialDetailsFormValues);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!order) {
      return;
    }

    const wasSaved = await onSubmit(order.id, {
      reportName: form.reportName,
      reportEmail: form.reportEmail,
      reportAddress: form.reportAddress,
      deliveryMethod: form.deliveryMethod,
      pickupPerson: form.deliveryMethod === 'PICKUP' ? form.pickupPerson : undefined,
      plannedDeliveryAt: new Date(form.plannedDeliveryAt).toISOString(),
      proformaNumber: form.proformaNumber,
      technicalProposalNumber: form.technicalProposalNumber,
      developmentDays: form.developmentDays ? Number(form.developmentDays) : undefined,
      outsourced: form.outsourced,
      postAnalysisCustody: form.postAnalysisCustody,
      generalObservations: form.generalObservations,
    });

    if (wasSaved) {
      closeModal();
    }
  }

  return (
    <Modal
      description="Los cambios se registrarán en el historial de la orden."
      isOpen={isOpen}
      onClose={closeModal}
      title={order ? `Editar datos comerciales · ${order.code}` : 'Editar datos comerciales'}
    >
      <form className="grid gap-5 md:grid-cols-2" onSubmit={handleSubmit}>
        <CommercialDetailsFields onChange={updateField} values={form} />
        <ModalFooter className="md:col-span-2">
          <Button onClick={closeModal} variant="secondary">
            Cancelar
          </Button>
          <Button disabled={isSaving} type="submit">
            {isSaving ? 'Guardando…' : 'Guardar cambios'}
          </Button>
        </ModalFooter>
      </form>
    </Modal>
  );
}
