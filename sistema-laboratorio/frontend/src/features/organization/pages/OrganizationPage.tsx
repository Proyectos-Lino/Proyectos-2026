import { Building2, ImagePlus, Plus, Save, Trash2 } from 'lucide-react';
import { useEffect, useState, type ChangeEvent, type FormEvent, type ReactNode } from 'react';
import { Button, ErrorNotice, PageHeader } from '../../../shared/components/ui';
import { getErrorMessage } from '../../../shared/lib/errors';
import type {
  OrganizationContact,
  OrganizationProfile,
  UserSummary,
} from '../../../shared/types/domain';
import { getUsers } from '../../users/api/users-api';
import {
  getOrganizationProfile,
  updateOrganizationProfile,
  type UpdateOrganizationInput,
} from '../api/organization-api';
import { useOrganization } from '../hooks/useOrganization';

const timeZones = ['America/Lima', 'America/Bogota', 'America/Santiago', 'America/Mexico_City'];

function emptyForm(): UpdateOrganizationInput {
  return {
    commercialName: '',
    legalName: '',
    documentType: '',
    documentNumber: '',
    address: '',
    logoDataUrl: '',
    primaryColor: '#0F766E',
    secondaryColor: '#0F172A',
    timeZone: 'America/Lima',
    legalText: '',
    accreditationName: '',
    accreditationCode: '',
    accreditationScope: '',
    serviceOrderPrefix: 'OS',
    workOrderPrefix: 'OT',
    reportPrefix: 'IR',
    contacts: [],
    authorizedSignerIds: [],
  };
}

function profileToForm(profile: OrganizationProfile): UpdateOrganizationInput {
  return {
    commercialName: profile.commercialName,
    legalName: profile.legalName,
    documentType: profile.documentType ?? '',
    documentNumber: profile.documentNumber ?? '',
    address: profile.address ?? '',
    logoDataUrl: profile.logoDataUrl ?? '',
    primaryColor: profile.primaryColor,
    secondaryColor: profile.secondaryColor,
    timeZone: profile.timeZone,
    legalText: profile.legalText ?? '',
    accreditationName: profile.accreditationName ?? '',
    accreditationCode: profile.accreditationCode ?? '',
    accreditationScope: profile.accreditationScope ?? '',
    serviceOrderPrefix: profile.serviceOrderPrefix,
    workOrderPrefix: profile.workOrderPrefix,
    reportPrefix: profile.reportPrefix,
    contacts: profile.contacts.map(({ type, label, value }) => ({ type, label, value })),
    authorizedSignerIds: profile.authorizedSigners.map((signer) => signer.id),
  };
}

export function OrganizationPage() {
  const { setOrganization } = useOrganization();
  const [form, setForm] = useState<UpdateOrganizationInput>(emptyForm);
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    void getOrganizationProfile()
      .then((profile) => setForm(profileToForm(profile)))
      .catch((reason: unknown) =>
        setError(getErrorMessage(reason, 'No se pudo cargar la configuración institucional.')),
      );
  }, []);

  useEffect(() => {
    void getUsers({ active: true, size: 100 })
      .then((page) =>
        setUsers(
          page.content.filter((user) =>
            user.roles.some((role) => ['ADMIN', 'TECHNICAL_MANAGER', 'VALIDATOR'].includes(role)),
          ),
        ),
      )
      .catch((reason: unknown) =>
        setError(getErrorMessage(reason, 'No se pudieron cargar los responsables disponibles.')),
      );
  }, []);

  function updateField<K extends keyof UpdateOrganizationInput>(
    field: K,
    value: UpdateOrganizationInput[K],
  ) {
    setForm((current) => ({ ...current, [field]: value }));
    setSuccess(null);
  }

  function updateContact(index: number, changes: Partial<OrganizationContact>) {
    updateField(
      'contacts',
      form.contacts.map((contact, contactIndex) =>
        contactIndex === index ? { ...contact, ...changes } : contact,
      ),
    );
  }

  function readLogo(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) return;
    if (file.size > 1_000_000) {
      setError('El logo no debe superar 1 MB.');
      return;
    }
    const reader = new FileReader();
    reader.onload = () => updateField('logoDataUrl', String(reader.result));
    reader.readAsDataURL(file);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsSaving(true);
    setError(null);
    try {
      const updated = await updateOrganizationProfile(form);
      setOrganization({
        commercialName: updated.commercialName,
        logoDataUrl: updated.logoDataUrl,
        primaryColor: updated.primaryColor,
        secondaryColor: updated.secondaryColor,
        timeZone: updated.timeZone,
      });
      setForm(profileToForm(updated));
      setSuccess('La identidad institucional y documental se actualizó correctamente.');
    } catch (reason) {
      setError(getErrorMessage(reason, 'No fue posible guardar la configuración.'));
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <div className="space-y-7">
      <PageHeader
        description="Personaliza la instalación sin incorporar datos fijos de una empresa en el código."
        eyebrow="Configuración general"
        title="Identidad de la organización"
      />
      <ErrorNotice message={error} />
      {success ? (
        <p className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-semibold text-emerald-800">
          {success}
        </p>
      ) : null}

      <form className="space-y-6" onSubmit={handleSubmit}>
        <section className="grid gap-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm lg:grid-cols-[1fr_15rem]">
          <div>
            <SectionTitle
              description="Esta información identifica al laboratorio en el sistema y en sus documentos."
              title="Datos institucionales"
            />
            <div className="mt-5 grid gap-5 md:grid-cols-2">
              <Field label="Nombre comercial">
                <input
                  onChange={(event) => updateField('commercialName', event.target.value)}
                  required
                  value={form.commercialName}
                />
              </Field>
              <Field label="Razón social">
                <input
                  onChange={(event) => updateField('legalName', event.target.value)}
                  required
                  value={form.legalName}
                />
              </Field>
              <Field label="Tipo de documento">
                <input
                  onChange={(event) => updateField('documentType', event.target.value)}
                  placeholder="RUC, NIT, RFC…"
                  value={form.documentType}
                />
              </Field>
              <Field label="Número de documento">
                <input
                  onChange={(event) => updateField('documentNumber', event.target.value)}
                  value={form.documentNumber}
                />
              </Field>
              <Field className="md:col-span-2" label="Dirección">
                <input
                  onChange={(event) => updateField('address', event.target.value)}
                  value={form.address}
                />
              </Field>
              <Field label="Zona horaria">
                <input
                  list="organization-time-zones"
                  onChange={(event) => updateField('timeZone', event.target.value)}
                  required
                  value={form.timeZone}
                />
                <datalist id="organization-time-zones">
                  {timeZones.map((zone) => (
                    <option key={zone} value={zone}>
                      {zone}
                    </option>
                  ))}
                </datalist>
              </Field>
            </div>
          </div>
          <div className="rounded-2xl border border-dashed border-slate-300 bg-slate-50 p-4 text-center">
            <div className="mx-auto grid h-28 place-items-center overflow-hidden rounded-xl bg-white p-3 shadow-sm">
              {form.logoDataUrl ? (
                <img
                  alt="Vista previa del logo"
                  className="max-h-full max-w-full"
                  src={form.logoDataUrl}
                />
              ) : (
                <Building2 className="text-slate-300" size={40} />
              )}
            </div>
            <label className="mt-4 inline-flex cursor-pointer items-center gap-2 text-sm font-bold text-lab-700">
              <ImagePlus size={16} />
              Cargar logo
              <input
                accept="image/png,image/jpeg,image/webp"
                className="hidden"
                onChange={readLogo}
                type="file"
              />
            </label>
            <p className="mt-2 text-xs text-slate-500">PNG, JPG o WebP · máximo 1 MB</p>
            {form.logoDataUrl ? (
              <button
                className="mt-3 text-xs font-semibold text-rose-600"
                onClick={() => updateField('logoDataUrl', '')}
                type="button"
              >
                Retirar logo
              </button>
            ) : null}
          </div>
        </section>

        <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <SectionTitle
            description="Define la apariencia institucional y la numeración de documentos nuevos."
            title="Marca y códigos documentales"
          />
          <div className="mt-5 grid gap-5 sm:grid-cols-2 lg:grid-cols-5">
            <ColorField
              label="Color principal"
              onChange={(value) => updateField('primaryColor', value)}
              value={form.primaryColor}
            />
            <ColorField
              label="Color secundario"
              onChange={(value) => updateField('secondaryColor', value)}
              value={form.secondaryColor}
            />
            <PrefixField
              label="Prefijo de OS"
              onChange={(value) => updateField('serviceOrderPrefix', value)}
              value={form.serviceOrderPrefix}
            />
            <PrefixField
              label="Prefijo de OT"
              onChange={(value) => updateField('workOrderPrefix', value)}
              value={form.workOrderPrefix}
            />
            <PrefixField
              label="Prefijo de informe"
              onChange={(value) => updateField('reportPrefix', value)}
              value={form.reportPrefix}
            />
          </div>
        </section>

        <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <SectionTitle
              description="Se mostrarán según corresponda en los documentos oficiales."
              title="Teléfonos y correos institucionales"
            />
            <Button
              onClick={() =>
                updateField('contacts', [...form.contacts, { type: 'EMAIL', label: '', value: '' }])
              }
              variant="secondary"
            >
              <Plus size={16} /> Añadir contacto
            </Button>
          </div>
          <div className="mt-5 space-y-3">
            {form.contacts.length === 0 ? (
              <p className="rounded-xl bg-slate-50 p-4 text-sm text-slate-500">
                Aún no se registraron contactos institucionales.
              </p>
            ) : null}
            {form.contacts.map((contact, index) => (
              <div
                className="grid gap-3 rounded-xl bg-slate-50 p-3 md:grid-cols-[9rem_1fr_1.5fr_auto]"
                key={`${contact.type}-${index}`}
              >
                <select
                  aria-label={`Tipo del contacto ${index + 1}`}
                  onChange={(event) =>
                    updateContact(index, { type: event.target.value as 'EMAIL' | 'PHONE' })
                  }
                  value={contact.type}
                >
                  <option value="EMAIL">Correo</option>
                  <option value="PHONE">Teléfono</option>
                </select>
                <input
                  aria-label={`Etiqueta del contacto ${index + 1}`}
                  onChange={(event) => updateContact(index, { label: event.target.value })}
                  placeholder="Ej. Recepción"
                  value={contact.label ?? ''}
                />
                <input
                  aria-label={`Valor del contacto ${index + 1}`}
                  onChange={(event) => updateContact(index, { value: event.target.value })}
                  required
                  type={contact.type === 'EMAIL' ? 'email' : 'tel'}
                  value={contact.value}
                />
                <Button
                  aria-label={`Eliminar contacto ${index + 1}`}
                  onClick={() =>
                    updateField(
                      'contacts',
                      form.contacts.filter((_, contactIndex) => contactIndex !== index),
                    )
                  }
                  variant="ghost"
                >
                  <Trash2 size={16} />
                </Button>
              </div>
            ))}
          </div>
        </section>

        <section className="grid gap-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm lg:grid-cols-2">
          <div>
            <SectionTitle
              description="La restricción se aplica al emitir informes cuando se configure al menos uno."
              title="Responsables autorizados"
            />
            <div className="mt-5 space-y-2">
              {users.map((user) => (
                <label
                  className="flex items-center gap-3 rounded-xl border border-slate-200 p-3"
                  key={user.id}
                >
                  <input
                    checked={form.authorizedSignerIds.includes(user.id)}
                    onChange={(event) =>
                      updateField(
                        'authorizedSignerIds',
                        event.target.checked
                          ? [...form.authorizedSignerIds, user.id]
                          : form.authorizedSignerIds.filter((id) => id !== user.id),
                      )
                    }
                    type="checkbox"
                  />
                  <span>
                    <span className="block text-sm font-bold text-slate-800">{user.fullName}</span>
                    <span className="text-xs text-slate-500">{user.email}</span>
                  </span>
                </label>
              ))}
            </div>
          </div>
          <div className="space-y-5">
            <SectionTitle
              description="Información formal incluida en el cierre de los informes."
              title="Acreditación y texto legal"
            />
            <Field label="Entidad o nombre de acreditación">
              <input
                onChange={(event) => updateField('accreditationName', event.target.value)}
                value={form.accreditationName}
              />
            </Field>
            <Field label="Código de acreditación">
              <input
                onChange={(event) => updateField('accreditationCode', event.target.value)}
                value={form.accreditationCode}
              />
            </Field>
            <Field label="Alcance">
              <textarea
                onChange={(event) => updateField('accreditationScope', event.target.value)}
                rows={3}
                value={form.accreditationScope}
              />
            </Field>
            <Field label="Texto legal">
              <textarea
                onChange={(event) => updateField('legalText', event.target.value)}
                rows={4}
                value={form.legalText}
              />
            </Field>
          </div>
        </section>

        <div className="sticky bottom-4 flex justify-end rounded-2xl border border-slate-200 bg-white/95 p-4 shadow-lg backdrop-blur">
          <Button disabled={isSaving} type="submit">
            <Save size={17} /> {isSaving ? 'Guardando…' : 'Guardar configuración'}
          </Button>
        </div>
      </form>
    </div>
  );
}

function SectionTitle({ description, title }: { description: string; title: string }) {
  return (
    <div>
      <h2 className="text-lg font-bold text-slate-900">{title}</h2>
      <p className="mt-1 text-sm leading-6 text-slate-500">{description}</p>
    </div>
  );
}

function Field({
  children,
  className = '',
  label,
}: {
  children: ReactNode;
  className?: string;
  label: string;
}) {
  return (
    <label className={`space-y-2 ${className}`}>
      <span className="block">{label}</span>
      {children}
    </label>
  );
}

function ColorField({
  label,
  onChange,
  value,
}: {
  label: string;
  onChange: (value: string) => void;
  value: string;
}) {
  return (
    <Field label={label}>
      <div className="flex gap-2">
        <input
          className="size-11 shrink-0 p-1"
          onChange={(event) => onChange(event.target.value)}
          type="color"
          value={value}
        />
        <input
          onChange={(event) => onChange(event.target.value)}
          pattern="#[0-9A-Fa-f]{6}"
          required
          value={value}
        />
      </div>
    </Field>
  );
}

function PrefixField({
  label,
  onChange,
  value,
}: {
  label: string;
  onChange: (value: string) => void;
  value: string;
}) {
  return (
    <Field label={label}>
      <input
        maxLength={10}
        onChange={(event) => onChange(event.target.value.toUpperCase())}
        pattern="[A-Za-z0-9]+"
        required
        value={value}
      />
    </Field>
  );
}
