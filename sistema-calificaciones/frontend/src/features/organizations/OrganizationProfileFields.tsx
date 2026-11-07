import type { OrganizationProfileInput } from './profile'

const fields = [
  { key: 'legalName', label: 'Nombre institucional / razón social', required: true, max: 180 },
  { key: 'taxId', label: 'Identificación fiscal (RUC u otra)', max: 40 },
  { key: 'address', label: 'Dirección', required: true, max: 250 },
  { key: 'city', label: 'Ciudad', required: true, max: 100 },
  { key: 'country', label: 'País', required: true, max: 100 },
  { key: 'email', label: 'Correo institucional', type: 'email', required: true, max: 254 },
  { key: 'phone', label: 'Teléfono de contacto', type: 'tel', max: 40 },
  { key: 'website', label: 'Sitio web (https://...)', type: 'url', max: 250 },
  { key: 'office', label: 'Área responsable de las evaluaciones', max: 180 },
  { key: 'signatoryName', label: 'Nombre del responsable institucional', max: 160 },
  { key: 'signatoryTitle', label: 'Cargo del responsable', max: 160 },
] as const

export function OrganizationProfileFields({ value, onChange }: {
  value: OrganizationProfileInput; onChange: (value: OrganizationProfileInput) => void
}) {
  return <div className="modal-form__grid organization-profile-fields">{fields.map((field) => <label className="field" key={field.key}>
    <span>{field.label}{'required' in field && field.required ? ' *' : ''}</span>
    <input type={'type' in field ? field.type : 'text'} value={value[field.key]} maxLength={field.max}
      required={'required' in field && field.required} onChange={(event) => onChange({ ...value, [field.key]: event.target.value })} />
  </label>)}</div>
}
