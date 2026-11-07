import { useState, type FormEvent } from 'react'
import type { DbfColumnMapping, DbfFileColumns, DbfInspection } from '../../api/types'
import { Modal } from '../../components/Modal'

interface Field {
  key: keyof DbfColumnMapping
  label: string
}

const identificationFields: Field[] = [
  { key: 'identificationSheet', label: 'Código de hoja' },
  { key: 'identificationForm', label: 'Tema' },
  { key: 'identificationParticipant', label: 'Código del participante' },
]
const responseFields: Field[] = [
  { key: 'responseSheet', label: 'Código de hoja' },
  { key: 'responseForm', label: 'Tema marcado' },
  { key: 'responseAnswersFrom', label: 'Primera respuesta' },
]
const keyFields: Field[] = [
  { key: 'keyForm', label: 'Tema' },
  { key: 'keyAnswersFrom', label: 'Primera respuesta correcta' },
]

export function DbfMappingModal({ inspection, onClose, onApply }: {
  inspection: DbfInspection; onClose: () => void; onApply: (mapping: DbfColumnMapping) => void
}) {
  const [mapping, setMapping] = useState(inspection.suggestedMapping)

  function fields(title: string, table: DbfFileColumns, values: Field[]) {
    return <fieldset className="mapping-group"><legend>{title} · {table.rows} filas</legend>
      <div className="modal-form__grid">{values.map(({ key, label }) => <label className="field" key={key}>
        <span>{label}</span><select value={mapping[key]} required
          onChange={(event) => setMapping({ ...mapping, [key]: event.target.value })}>
          {table.columns.map((column) => <option value={column} key={column}>{column}</option>)}
        </select></label>)}</div>
      <p className="muted-copy">Primer registro: {table.columns.slice(0, 5).map((column, index) =>
        `${column} = ${table.sample[0]?.[index] || 'vacío'}`).join(' · ')}</p>
    </fieldset>
  }

  function submit(event: FormEvent) { event.preventDefault(); onApply(mapping) }

  return <Modal title="Relacionar columnas DBF"
    description="Comprueba los campos detectados. Las respuestas se leen consecutivamente desde la primera columna indicada, según las preguntas configuradas en cada tema."
    onClose={onClose}>
    <form className="modal-form" onSubmit={submit}>
      {fields('IDENTIFI', inspection.identification, identificationFields)}
      {fields('RESPUEST', inspection.responses, responseFields)}
      {fields('CLAVES', inspection.keys, keyFields)}
      <div className="modal-form__actions"><button type="button" className="button button--secondary" onClick={onClose}>Cancelar</button>
        <button className="button button--primary">Generar vista previa</button></div>
    </form>
  </Modal>
}
