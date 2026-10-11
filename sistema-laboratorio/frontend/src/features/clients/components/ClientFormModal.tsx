import { useState, type FormEvent } from 'react';
import { Button, Modal, ModalFooter } from '../../../shared/components/ui';
import { documentTypeLabels } from '../../../shared/lib/labels';
import type { Client, DocumentType, ParticipantType } from '../../../shared/types/domain';
import type { CreateClientInput } from '../api/clients-api';

interface ClientFormModalProps {
  allowedParticipantTypes?: ParticipantType[];
  isOpen: boolean;
  isSaving: boolean;
  client: Client | null;
  onClose: () => void;
  onSubmit: (clientId: string | null, input: CreateClientInput) => Promise<boolean>;
}

const initialForm: CreateClientInput = {
  participantType: 'CLIENT',
  documentType: 'RUC',
  documentNumber: '',
  legalName: '',
  reportName: '',
  email: '',
  address: '',
  contactName: '',
  phone: '',
};

const documentTypes: DocumentType[] = ['RUC', 'DNI', 'PASSPORT', 'OTHER'];
const participantTypeOptions: Array<{ value: ParticipantType; label: string }> = [
  { value: 'CLIENT', label: 'Cliente' },
  { value: 'PRODUCER', label: 'Productor' },
  { value: 'BOTH', label: 'Cliente y productor' },
];

export function ClientFormModal({
  allowedParticipantTypes,
  client,
  isOpen,
  isSaving,
  onClose,
  onSubmit,
}: ClientFormModalProps) {
  const visibleParticipantTypes = participantTypeOptions.filter(
    ({ value }) => !allowedParticipantTypes || allowedParticipantTypes.includes(value),
  );
  const [form, setForm] = useState<CreateClientInput>(() =>
    client
      ? {
          participantType: client.participantType,
          documentType: client.documentType,
          documentNumber: client.documentNumber,
          legalName: client.legalName,
          reportName: client.reportName ?? '',
          email: client.email ?? '',
          address: client.address ?? '',
          contactName: client.contactName ?? '',
          phone: client.phone ?? '',
        }
      : initialForm,
  );

  function closeModal() {
    setForm(initialForm);
    onClose();
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const wasSaved = await onSubmit(client?.id ?? null, form);

    if (wasSaved) {
      closeModal();
    }
  }

  return (
    <Modal
      description="Los datos del informe son opcionales y se pueden complementar en cada orden."
      isOpen={isOpen}
      onClose={closeModal}
      title={client ? 'Editar cliente o productor' : 'Registrar cliente o productor'}
    >
      <form className="grid gap-5 md:grid-cols-2" onSubmit={handleSubmit}>
        <div className="space-y-2 md:col-span-2">
          <label htmlFor="client-participant-type">Clasificación del participante</label>
          <select
            id="client-participant-type"
            onChange={(event) =>
              setForm((current) => ({
                ...current,
                participantType: event.target.value as ParticipantType,
              }))
            }
            value={form.participantType}
          >
            {visibleParticipantTypes.map((type) => (
              <option key={type.value} value={type.value}>
                {type.label}
              </option>
            ))}
          </select>
          <p className="text-xs text-slate-500">
            Determina si puede solicitar servicios, figurar como productor de una muestra o ambos.
          </p>
        </div>
        <div className="space-y-2">
          <label htmlFor="client-document-type">Tipo de documento</label>
          <select
            id="client-document-type"
            onChange={(event) =>
              setForm((current) => ({
                ...current,
                documentType: event.target.value as DocumentType,
              }))
            }
            value={form.documentType}
          >
            {documentTypes.map((type) => (
              <option key={type} value={type}>
                {documentTypeLabels[type]}
              </option>
            ))}
          </select>
        </div>

        <div className="space-y-2">
          <label htmlFor="client-contact-name">Persona de contacto</label>
          <input
            id="client-contact-name"
            onChange={(event) =>
              setForm((current) => ({ ...current, contactName: event.target.value }))
            }
            placeholder="Nombre y apellidos"
            value={form.contactName}
          />
        </div>

        <div className="space-y-2">
          <label htmlFor="client-document-number">Número de documento</label>
          <input
            id="client-document-number"
            onChange={(event) =>
              setForm((current) => ({ ...current, documentNumber: event.target.value }))
            }
            required
            value={form.documentNumber}
          />
        </div>

        <div className="space-y-2">
          <label htmlFor="client-phone">Teléfono</label>
          <input
            id="client-phone"
            onChange={(event) => setForm((current) => ({ ...current, phone: event.target.value }))}
            placeholder="Número de contacto"
            type="tel"
            value={form.phone}
          />
        </div>

        <div className="space-y-2 md:col-span-2">
          <label htmlFor="client-legal-name">Razón social o nombre</label>
          <input
            id="client-legal-name"
            onChange={(event) =>
              setForm((current) => ({ ...current, legalName: event.target.value }))
            }
            required
            value={form.legalName}
          />
        </div>

        <div className="space-y-2">
          <label htmlFor="client-report-name">Nombre para informe</label>
          <input
            id="client-report-name"
            onChange={(event) =>
              setForm((current) => ({ ...current, reportName: event.target.value }))
            }
            value={form.reportName}
          />
        </div>

        <div className="space-y-2">
          <label htmlFor="client-email">Correo electrónico</label>
          <input
            id="client-email"
            onChange={(event) => setForm((current) => ({ ...current, email: event.target.value }))}
            type="email"
            value={form.email}
          />
        </div>

        <div className="space-y-2 md:col-span-2">
          <label htmlFor="client-address">Dirección</label>
          <input
            id="client-address"
            onChange={(event) =>
              setForm((current) => ({ ...current, address: event.target.value }))
            }
            value={form.address}
          />
        </div>

        <ModalFooter className="md:col-span-2">
          <Button onClick={closeModal} variant="secondary">
            Cancelar
          </Button>
          <Button disabled={isSaving} type="submit">
            {isSaving ? 'Guardando…' : client ? 'Guardar cambios' : 'Guardar participante'}
          </Button>
        </ModalFooter>
      </form>
    </Modal>
  );
}
