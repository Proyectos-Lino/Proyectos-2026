import { useState, type FormEvent } from 'react';
import { Button, Modal, ModalFooter } from '../../../shared/components/ui';
import { roleLabels } from '../../../shared/lib/labels';
import type { LaboratoryAreaOption, RoleCode, UserSummary } from '../../../shared/types/domain';
import type { CreateUserInput, UpdateUserInput } from '../api/users-api';

interface UserFormModalProps {
  isOpen: boolean;
  isSaving: boolean;
  areas: LaboratoryAreaOption[];
  user: UserSummary | null;
  onClose: () => void;
  onSubmit: (userId: string | null, input: CreateUserInput | UpdateUserInput) => Promise<boolean>;
}

const roles = Object.keys(roleLabels) as RoleCode[];
type UserFormState = UpdateUserInput & { password: string };
const initialForm: UserFormState = {
  fullName: '',
  email: '',
  password: '',
  roles: ['ANALYST'],
  laboratoryAreaId: '',
};

export function UserFormModal({
  areas,
  isOpen,
  isSaving,
  onClose,
  onSubmit,
  user,
}: UserFormModalProps) {
  const [form, setForm] = useState<UserFormState>(() =>
    user
      ? {
          fullName: user.fullName,
          email: user.email,
          password: '',
          roles: user.roles,
          laboratoryAreaId: user.laboratoryAreaId ?? '',
        }
      : initialForm,
  );
  const [validationError, setValidationError] = useState<string | null>(null);

  function closeModal() {
    setForm(initialForm);
    setValidationError(null);
    onClose();
  }

  function toggleRole(role: RoleCode) {
    setForm((current) => ({
      ...current,
      roles: current.roles.includes(role)
        ? current.roles.filter((currentRole) => currentRole !== role)
        : [...current.roles, role],
    }));
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (form.roles.length === 0) {
      setValidationError('Selecciona al menos un rol para el usuario.');
      return;
    }

    const input = { ...form, laboratoryAreaId: form.laboratoryAreaId || undefined };
    const wasSaved = await onSubmit(user?.id ?? null, input);

    if (wasSaved) {
      closeModal();
    }
  }

  return (
    <Modal
      description="La contraseña temporal debe tener como mínimo 12 caracteres."
      isOpen={isOpen}
      onClose={closeModal}
      title={user ? 'Editar usuario' : 'Crear usuario'}
    >
      <form className="grid gap-5 md:grid-cols-2" onSubmit={handleSubmit}>
        <div className="space-y-2 md:col-span-2">
          <label htmlFor="user-full-name">Nombre completo</label>
          <input
            id="user-full-name"
            onChange={(event) =>
              setForm((current) => ({ ...current, fullName: event.target.value }))
            }
            required
            value={form.fullName}
          />
        </div>
        <div className="space-y-2">
          <label htmlFor="user-email">Correo electrónico</label>
          <input
            id="user-email"
            onChange={(event) => setForm((current) => ({ ...current, email: event.target.value }))}
            required
            type="email"
            value={form.email}
          />
        </div>
        <div className="space-y-2">
          <label htmlFor="user-password">
            {user ? 'Nueva contraseña (opcional)' : 'Contraseña temporal'}
          </label>
          <input
            id="user-password"
            minLength={12}
            onChange={(event) =>
              setForm((current) => ({ ...current, password: event.target.value }))
            }
            required={!user}
            type="password"
            value={form.password}
          />
        </div>

        <div className="space-y-2 md:col-span-2">
          <label htmlFor="user-area">Área de laboratorio</label>
          <select
            id="user-area"
            onChange={(event) =>
              setForm((current) => ({ ...current, laboratoryAreaId: event.target.value }))
            }
            value={form.laboratoryAreaId}
          >
            <option value="">Sin área asignada</option>
            {areas.map((area) => (
              <option key={area.id} value={area.id}>
                {area.name} ({area.code})
              </option>
            ))}
          </select>
        </div>

        <fieldset className="md:col-span-2">
          <legend className="text-sm font-semibold text-slate-700">Roles del usuario</legend>
          <p className="mt-1 text-xs text-slate-500">
            Cada rol habilita acciones distintas dentro del sistema.
          </p>
          <div className="mt-3 grid gap-2 sm:grid-cols-2">
            {roles.map((role) => (
              <label
                className={`flex cursor-pointer items-center gap-3 rounded-xl border px-3.5 py-3 text-sm transition ${
                  form.roles.includes(role)
                    ? 'border-lab-300 bg-lab-50 text-lab-900'
                    : 'border-slate-200 bg-white text-slate-700 hover:border-slate-300'
                }`}
                key={role}
              >
                <input
                  checked={form.roles.includes(role)}
                  onChange={() => toggleRole(role)}
                  type="checkbox"
                />
                {roleLabels[role]}
              </label>
            ))}
          </div>
          {validationError ? <p className="mt-2 text-sm text-rose-700">{validationError}</p> : null}
        </fieldset>

        <ModalFooter className="md:col-span-2">
          <Button onClick={closeModal} variant="secondary">
            Cancelar
          </Button>
          <Button disabled={isSaving} type="submit">
            {isSaving ? 'Guardando…' : user ? 'Guardar cambios' : 'Crear usuario'}
          </Button>
        </ModalFooter>
      </form>
    </Modal>
  );
}
