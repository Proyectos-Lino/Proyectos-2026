import { useState, type FormEvent } from 'react';
import { Button, Modal, ModalFooter } from '../../../shared/components/ui';
import type { LaboratoryArea } from '../../../shared/types/domain';
import type { AreaInput } from '../api/laboratory-areas-api';

interface AreaFormModalProps {
  area: LaboratoryArea | null;
  isOpen: boolean;
  isSaving: boolean;
  onClose: () => void;
  onSubmit: (areaId: string | null, input: AreaInput) => Promise<boolean>;
}

const emptyForm: AreaInput = { code: '', name: '' };

export function AreaFormModal({ area, isOpen, isSaving, onClose, onSubmit }: AreaFormModalProps) {
  const [form, setForm] = useState<AreaInput>(() =>
    area ? { code: area.code, name: area.name } : emptyForm,
  );

  function closeModal() {
    setForm(emptyForm);
    onClose();
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const saved = await onSubmit(area?.id ?? null, form);
    if (saved) closeModal();
  }

  return (
    <Modal
      description="Las áreas determinan la organización técnica y la asignación de personal."
      isOpen={isOpen}
      onClose={closeModal}
      title={area ? 'Editar área de laboratorio' : 'Nueva área de laboratorio'}
    >
      <form className="space-y-5" onSubmit={handleSubmit}>
        <div className="space-y-2">
          <label htmlFor="area-code">Código</label>
          <input
            id="area-code"
            maxLength={30}
            onChange={(event) => setForm((current) => ({ ...current, code: event.target.value }))}
            placeholder="Ej. MICRO"
            required
            value={form.code}
          />
        </div>
        <div className="space-y-2">
          <label htmlFor="area-name">Nombre</label>
          <input
            id="area-name"
            maxLength={150}
            onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
            placeholder="Ej. Microbiología"
            required
            value={form.name}
          />
        </div>
        <ModalFooter>
          <Button onClick={closeModal} variant="secondary">
            Cancelar
          </Button>
          <Button disabled={isSaving} type="submit">
            {isSaving ? 'Guardando…' : 'Guardar área'}
          </Button>
        </ModalFooter>
      </form>
    </Modal>
  );
}
