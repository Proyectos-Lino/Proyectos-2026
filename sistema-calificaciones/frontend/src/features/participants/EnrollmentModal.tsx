import { useState, type FormEvent } from 'react'
import type { EvaluationOverview, Group } from '../../api/types'
import { Modal } from '../../components/Modal'
import { Notice } from '../../components/Notice'
import { errorMessage } from '../../utils/format'
import { groupLabel } from '../../utils/group'
import { participantsApi } from './participantsApi'
import type { Enrollment, EnrollmentInput, Person } from './types'
import { PersonPickerModal } from './PersonPickerModal'

export function EnrollmentModal({ org, evaluation, groups, enrollment, onClose, onSaved }: {
  org: number; evaluation: EvaluationOverview; groups: Group[]; enrollment: Enrollment | null; onClose: () => void; onSaved: (value: Enrollment) => void
}) {
  const [form, setForm] = useState<EnrollmentInput>({ code: enrollment?.code ?? '', personId: enrollment?.automatic ? null : enrollment?.personId ?? null,
    names: enrollment?.names ?? '', surnames: enrollment?.surnames ?? '', documentType: enrollment?.documentType ?? 'DOCUMENTO',
    document: enrollment?.document ?? '', email: enrollment?.email ?? '', groupId: enrollment?.groupId ?? null })
  const [pickerOpen, setPickerOpen] = useState(false)
  const [selectedPerson, setSelectedPerson] = useState<Person | null>(null)
  const [working, setWorking] = useState(false)
  const [error, setError] = useState<string | null>(null)
  function choose(person: Person) {
    setForm((value) => ({ ...value, personId: person.id, names: person.names, surnames: person.surnames ?? '', documentType: person.documentType,
      document: person.document ?? '', email: person.email ?? '' })); setSelectedPerson(person); setPickerOpen(false)
  }
  async function save(event: FormEvent) {
    event.preventDefault(); setWorking(true); setError(null)
    try { onSaved(enrollment ? await participantsApi.update(org, evaluation.id, enrollment.id, form) : await participantsApi.create(org, evaluation.id, form)) }
    catch (cause) { setError(errorMessage(cause)) } finally { setWorking(false) }
  }
  return <><Modal title={enrollment ? 'Editar participante' : 'Registrar participante'} description={`${evaluation.name} · El código identifica únicamente su inscripción en esta evaluación.`} onClose={onClose} busy={working}>
    <form className="modal-form" onSubmit={(event) => void save(event)}>
      {(!enrollment || enrollment.automatic) && <button type="button" className="button button--secondary" onClick={() => setPickerOpen(true)}>Usar una persona existente</button>}
      {selectedPerson && <><p className="muted-copy">Se conservarán los datos personales de {selectedPerson.names} {selectedPerson.surnames}. Asigna su código y grupo para este proceso.</p>
        <button type="button" className="text-link" onClick={() => { setSelectedPerson(null); setForm((value) => ({ ...value, personId: null, names: '', surnames: '', document: '', email: '', documentType: 'DOCUMENTO' })) }}>Ingresar datos de una persona nueva</button></>}
      {enrollment && !enrollment.automatic && <p className="muted-copy">Los datos personales se comparten con sus otras inscripciones de la organización. El código y el grupo pertenecen solo a esta evaluación.</p>}
      <div className="modal-form__grid"><label className="field"><span>Código en esta evaluación</span><input required maxLength={80} value={form.code} readOnly={enrollment?.hasExam}
        onChange={(event) => setForm({ ...form, code: event.target.value })} /></label>
        {groups.length > 0 && <label className="field"><span>{groupLabel(evaluation.groupType)}</span><select required value={form.groupId ?? ''} onChange={(event) => setForm({ ...form, groupId: event.target.value ? Number(event.target.value) : null })}>
          <option value="">Selecciona una opción</option>{groups.map((group) => <option key={group.id} value={group.id}>{group.code} — {group.name}</option>)}</select></label>}
      </div>{enrollment?.hasExam && <p className="muted-copy">El código está protegido porque ya tiene un examen asociado.</p>}
      <fieldset className="participant-person-fields" disabled={Boolean(selectedPerson)}><legend>Datos de la persona</legend><div className="modal-form__grid">
        <label className="field"><span>Nombres</span><input required maxLength={160} value={form.names} onChange={(event) => setForm({ ...form, names: event.target.value })} /></label>
        <label className="field"><span>Apellidos</span><input maxLength={160} value={form.surnames} onChange={(event) => setForm({ ...form, surnames: event.target.value })} /></label>
        <label className="field"><span>Tipo de documento</span><select value={form.documentType} onChange={(event) => setForm({ ...form, documentType: event.target.value })}>
          <option value="DOCUMENTO">Documento / identificador</option><option value="DNI">DNI</option><option value="CE">Carné de extranjería</option><option value="PASAPORTE">Pasaporte</option><option value="OTRO">Otro</option></select></label>
        <label className="field"><span>Documento (opcional)</span><input maxLength={40} value={form.document} onChange={(event) => setForm({ ...form, document: event.target.value })} /></label>
        <label className="field"><span>Correo de contacto</span><input type="email" maxLength={254} value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} /></label>
      </div></fieldset>
      {!enrollment && !selectedPerson && <p className="muted-copy">Si el tipo y número de documento ya están registrados, se reutilizará esa persona conservando sus datos. Sin documento, registra una persona nueva o selecciónala con el botón superior.</p>}
      {error && <Notice tone="error">{error}</Notice>}
      <div className="modal-form__actions"><button type="button" className="button button--secondary" disabled={working} onClick={onClose}>Cancelar</button><button className="button button--primary" disabled={working}>{working ? 'Guardando…' : enrollment ? 'Guardar cambios' : 'Registrar e inscribir'}</button></div>
    </form></Modal>{pickerOpen && <PersonPickerModal org={org} onClose={() => setPickerOpen(false)} onSelect={choose} />}</>
}
