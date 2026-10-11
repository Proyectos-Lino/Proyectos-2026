import { Calculator, Grid3X3, Info, Plus } from 'lucide-react';
import type { KeyboardEvent } from 'react';
import { Button } from '../../../shared/components/ui';
import type { InitialResultTemplate, Unit } from '../../../shared/types/domain';
import type {
  MatrixCellInput,
  MatrixColumnInput,
  ResultFieldInput,
} from '../hooks/useResultEditor';
import { MatrixBulkPastePanel } from './MatrixBulkPastePanel';

interface AdvancedResultFieldsProps {
  addMatrixColumn: () => void;
  addMatrixRow: () => void;
  addReading: () => void;
  applyBulkMatrixValues: (values: string[][]) => void;
  disabled: boolean;
  fields: ResultFieldInput[];
  matrixCells: MatrixCellInput[];
  matrixColumns: MatrixColumnInput[];
  matrixRows: number;
  template: InitialResultTemplate | null;
  units: Unit[];
  resizeMatrix: (rowCount: number, columnCount: number) => void;
  updateFieldValue: (index: number, value: string) => void;
  updateMatrixCell: (
    rowIndex: number,
    columnIndex: number,
    changes: Partial<MatrixCellInput>,
  ) => void;
  updateMatrixColumn: (index: number, changes: Partial<MatrixColumnInput>) => void;
}

export function AdvancedResultFields(props: AdvancedResultFieldsProps) {
  const guidance = props.template?.fields.filter((field) => field.fieldKind === 'INFORMATIONAL');
  let fields;
  if (props.template?.resultMode === 'MATRIX') {
    fields = <MatrixFields {...props} />;
  } else if (props.template?.resultMode === 'REPEATABLE') {
    fields = <RepeatableFields {...props} />;
  } else {
    fields = (
      <ScalarFields
        disabled={props.disabled}
        fields={props.fields}
        onChange={props.updateFieldValue}
        units={props.units}
      />
    );
  }

  return (
    <div className="space-y-4">
      {guidance?.map((field) => (
        <aside
          className="flex items-start gap-3 rounded-2xl border border-sky-200 bg-sky-50 p-4 text-sky-950"
          key={field.fieldKey}
        >
          <Info className="mt-0.5 shrink-0 text-sky-700" size={18} />
          <div>
            <p className="text-sm font-bold">{field.label}</p>
            <p className="mt-1 text-sm leading-6 text-sky-900">{field.informationText}</p>
          </div>
        </aside>
      ))}
      {fields}
    </div>
  );
}

function ScalarFields({
  disabled,
  fields,
  onChange,
  units,
}: {
  disabled: boolean;
  fields: ResultFieldInput[];
  onChange: (index: number, value: string) => void;
  units: Unit[];
}) {
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      {fields.map((field, index) => (
        <ResultInput
          disabled={disabled}
          field={field}
          key={`${field.fieldKey}:${field.repetitionIndex}`}
          onChange={(value) => onChange(index, value)}
          units={units}
        />
      ))}
    </div>
  );
}

function RepeatableFields(props: AdvancedResultFieldsProps) {
  const aggregateFields = props.fields.filter(
    (field) => field.fieldKind === 'CALCULATED' && field.calculationScope === 'AGGREGATE',
  );
  const readingFields = props.fields.filter(
    (field) => !(field.fieldKind === 'CALCULATED' && field.calculationScope === 'AGGREGATE'),
  );
  const repetitions = [...new Set(readingFields.map((field) => field.repetitionIndex))].sort(
    (left, right) => left - right,
  );
  const canAdd =
    !props.disabled &&
    (!props.template?.maximumRepetitions || repetitions.length < props.template.maximumRepetitions);

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 className="font-bold text-slate-900">Lecturas repetibles</h3>
          <p className="mt-1 text-xs text-slate-500">
            Registra cada repetición por separado para conservar su trazabilidad.
          </p>
        </div>
        <Button disabled={!canAdd} onClick={props.addReading} type="button" variant="secondary">
          <Plus size={15} />
          Agregar lectura
        </Button>
      </div>
      <div className="space-y-3">
        {repetitions.map((repetitionIndex) => (
          <article
            className="rounded-2xl border border-slate-200 bg-slate-50/70 p-4"
            key={repetitionIndex}
          >
            <p className="mb-3 text-xs font-bold uppercase tracking-wider text-lab-700">
              Lectura {repetitionIndex + 1}
            </p>
            <div className="grid gap-4 sm:grid-cols-2">
              {props.fields.map((field, fieldIndex) =>
                field.repetitionIndex === repetitionIndex ? (
                  field.fieldKind === 'CALCULATED' &&
                  field.calculationScope === 'AGGREGATE' ? null : (
                    <ResultInput
                      disabled={props.disabled}
                      field={field}
                      key={`${field.fieldKey}:${field.repetitionIndex}`}
                      onChange={(value) => props.updateFieldValue(fieldIndex, value)}
                      units={props.units}
                    />
                  )
                ) : null,
              )}
            </div>
          </article>
        ))}
      </div>
      {aggregateFields.length ? (
        <article className="rounded-2xl border border-indigo-200 bg-indigo-50/70 p-4">
          <div className="mb-3 flex items-start gap-3">
            <Calculator className="mt-0.5 text-indigo-700" size={18} />
            <div>
              <p className="text-sm font-bold text-indigo-950">Resumen calculado</p>
              <p className="mt-1 text-xs text-indigo-800">
                Resultados obtenidos con todas las lecturas visibles.
              </p>
            </div>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            {aggregateFields.map((field) => {
              const fieldIndex = props.fields.indexOf(field);
              return (
                <ResultInput
                  disabled
                  field={field}
                  key={`${field.fieldKey}:aggregate`}
                  onChange={(value) => props.updateFieldValue(fieldIndex, value)}
                  units={props.units}
                />
              );
            })}
          </div>
        </article>
      ) : null}
    </section>
  );
}

function ResultInput({
  disabled,
  field,
  onChange,
  units,
}: {
  disabled: boolean;
  field: ResultFieldInput;
  onChange: (value: string) => void;
  units: Unit[];
}) {
  const id = `result-value-${field.fieldKey}-${field.repetitionIndex}`;
  const unit = field.measurementUnitId
    ? units.find((item) => item.id === field.measurementUnitId)
    : undefined;
  return (
    <div
      className={
        field.fieldKind === 'CALCULATED'
          ? 'space-y-2 rounded-xl border border-indigo-200 bg-white p-3'
          : 'space-y-2'
      }
    >
      <label className="flex items-center gap-2" htmlFor={id}>
        {field.fieldKind === 'CALCULATED' ? (
          <Calculator className="text-indigo-700" size={15} />
        ) : null}
        <span>
          {field.label}
          {unit ? ` · ${unit.name} (${unit.symbol})` : ''}
        </span>
      </label>
      <input
        className={field.fieldKind === 'CALCULATED' ? 'bg-indigo-50 font-semibold' : undefined}
        disabled={disabled || field.readOnly}
        id={id}
        onChange={(event) => onChange(event.target.value)}
        required={field.required}
        step={field.resultType === 'NUMERIC' ? 'any' : undefined}
        type={field.resultType === 'NUMERIC' ? 'number' : 'text'}
        value={field.value}
      />
      {field.fieldKind === 'CALCULATED' ? (
        <p className="font-mono text-[11px] leading-5 text-indigo-700">{field.formula}</p>
      ) : null}
    </div>
  );
}

function MatrixFields(props: AdvancedResultFieldsProps) {
  const categories = props.template?.categories ?? [];
  const canAddColumn =
    !props.disabled &&
    (!props.template?.maximumColumns || props.matrixColumns.length < props.template.maximumColumns);
  const canAddRow =
    !props.disabled &&
    (!props.template?.maximumRows || props.matrixRows < props.template.maximumRows);
  const completedCells = props.matrixCells.filter((cell) =>
    categories.length ? Boolean(cell.categoryCode) : Boolean(cell.value.trim()),
  ).length;
  const totalCells = props.matrixRows * props.matrixColumns.length;
  const localProgress = totalCells ? Math.round((completedCells * 100) / totalCells) : 0;

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-start gap-3">
          <span className="grid size-10 place-items-center rounded-xl bg-emerald-50 text-emerald-700">
            <Grid3X3 size={19} />
          </span>
          <div>
            <h3 className="font-bold text-slate-900">Matriz de yemas y cargadores</h3>
            <p className="mt-1 text-xs leading-5 text-slate-500">
              Configura cada cargador y clasifica cada yema en la cuadrícula.
            </p>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <MatrixBulkPastePanel
            categories={categories}
            columnCount={props.matrixColumns.length}
            disabled={props.disabled}
            onApply={props.applyBulkMatrixValues}
            rowCount={props.matrixRows}
          />
          <Button
            disabled={!canAddRow}
            onClick={props.addMatrixRow}
            type="button"
            variant="secondary"
          >
            <Plus size={15} />
            Yema
          </Button>
          <Button
            disabled={!canAddColumn}
            onClick={props.addMatrixColumn}
            type="button"
            variant="secondary"
          >
            <Plus size={15} />
            Cargador
          </Button>
        </div>
      </div>

      <section className="grid gap-4 rounded-2xl border border-slate-200 bg-slate-50 p-4 md:grid-cols-[12rem_12rem_minmax(0,1fr)]">
        <label className="space-y-1.5 text-xs font-semibold text-slate-600">
          Yemas declaradas
          <input
            disabled={props.disabled}
            max={props.template?.maximumRows}
            min={1}
            onChange={(event) =>
              props.resizeMatrix(Number(event.target.value), props.matrixColumns.length)
            }
            type="number"
            value={props.matrixRows}
          />
        </label>
        <label className="space-y-1.5 text-xs font-semibold text-slate-600">
          Cargadores declarados
          <input
            disabled={props.disabled}
            max={props.template?.maximumColumns}
            min={1}
            onChange={(event) => props.resizeMatrix(props.matrixRows, Number(event.target.value))}
            type="number"
            value={props.matrixColumns.length}
          />
        </label>
        <div className="self-center">
          <div className="flex items-center justify-between gap-3 text-xs font-semibold text-slate-600">
            <span>
              {completedCells} de {totalCells} celdas completadas
            </span>
            <span className="text-lab-700">{localProgress}%</span>
          </div>
          <div className="mt-2 h-2 overflow-hidden rounded-full bg-slate-200">
            <div
              className="h-full rounded-full bg-gradient-to-r from-teal-500 to-emerald-600 transition-[width]"
              style={{ width: `${localProgress}%` }}
            />
          </div>
        </div>
      </section>

      <div className="grid gap-3 lg:grid-cols-2">
        {props.matrixColumns.map((column, columnIndex) => (
          <article
            className="rounded-2xl border border-emerald-200 bg-emerald-50/50 p-4"
            key={columnIndex}
          >
            <p className="mb-3 text-xs font-bold uppercase tracking-wider text-emerald-800">
              Cargador {columnIndex + 1}
            </p>
            <div className="grid gap-3 sm:grid-cols-2">
              <MatrixMetadataInput
                disabled={props.disabled}
                label="Identificación"
                onChange={(value) => props.updateMatrixColumn(columnIndex, { carrierLabel: value })}
                value={column.carrierLabel}
              />
              <MatrixMetadataInput
                disabled={props.disabled}
                label="Forma"
                onChange={(value) => props.updateMatrixColumn(columnIndex, { shape: value })}
                value={column.shape}
              />
              <MatrixMetadataInput
                disabled={props.disabled}
                label="Distancia"
                numeric
                onChange={(value) => props.updateMatrixColumn(columnIndex, { distance: value })}
                value={column.distance}
              />
              <MatrixMetadataInput
                disabled={props.disabled}
                label="Diámetro"
                numeric
                onChange={(value) => props.updateMatrixColumn(columnIndex, { diameter: value })}
                value={column.diameter}
              />
            </div>
          </article>
        ))}
      </div>

      <div className="overflow-x-auto rounded-2xl border border-slate-200" data-matrix-grid="true">
        <table className="min-w-[44rem] text-sm">
          <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="w-28 px-4 py-3">Yema</th>
              {props.matrixColumns.map((column, index) => (
                <th className="px-3 py-3" key={index}>
                  {column.carrierLabel || `Cargador ${index + 1}`}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 bg-white">
            {Array.from({ length: props.matrixRows }, (_, rowIndex) => (
              <tr key={rowIndex}>
                <th className="px-4 py-3 text-left font-semibold text-slate-700">
                  Yema {rowIndex + 1}
                </th>
                {props.matrixColumns.map((_, columnIndex) => {
                  const cell =
                    props.matrixCells[rowIndex * props.matrixColumns.length + columnIndex];
                  return (
                    <td className="min-w-40 px-3 py-2.5" key={columnIndex}>
                      {categories.length ? (
                        <select
                          aria-label={`Yema ${rowIndex + 1}, cargador ${columnIndex + 1}`}
                          data-matrix-cell={rowIndex * props.matrixColumns.length + columnIndex}
                          disabled={props.disabled}
                          onKeyDown={(event) =>
                            moveBetweenCells(
                              event,
                              rowIndex * props.matrixColumns.length + columnIndex,
                              totalCells,
                            )
                          }
                          onChange={(event) =>
                            props.updateMatrixCell(rowIndex, columnIndex, {
                              categoryCode: event.target.value,
                            })
                          }
                          required
                          value={cell?.categoryCode ?? ''}
                        >
                          <option value="">Selecciona</option>
                          {categories.map((category) => (
                            <option key={category.code} value={category.code}>
                              {category.label}
                            </option>
                          ))}
                        </select>
                      ) : (
                        <input
                          aria-label={`Yema ${rowIndex + 1}, cargador ${columnIndex + 1}`}
                          data-matrix-cell={rowIndex * props.matrixColumns.length + columnIndex}
                          disabled={props.disabled}
                          onKeyDown={(event) =>
                            moveBetweenCells(
                              event,
                              rowIndex * props.matrixColumns.length + columnIndex,
                              totalCells,
                            )
                          }
                          onChange={(event) =>
                            props.updateMatrixCell(rowIndex, columnIndex, {
                              value: event.target.value,
                            })
                          }
                          required
                          value={cell?.value ?? ''}
                        />
                      )}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="text-xs text-slate-500">
        Usa Tab para avanzar normalmente o Enter y Mayús + Enter para moverte entre celdas.
      </p>
    </section>
  );
}

function moveBetweenCells(
  event: KeyboardEvent<HTMLInputElement | HTMLSelectElement>,
  currentIndex: number,
  totalCells: number,
) {
  if (event.key !== 'Enter') {
    return;
  }
  event.preventDefault();
  const direction = event.shiftKey ? -1 : 1;
  const targetIndex = currentIndex + direction;
  if (targetIndex < 0 || targetIndex >= totalCells) {
    return;
  }
  event.currentTarget
    .closest('[data-matrix-grid]')
    ?.querySelector<HTMLElement>(`[data-matrix-cell="${targetIndex}"]`)
    ?.focus();
}

function MatrixMetadataInput({
  disabled,
  label,
  numeric = false,
  onChange,
  value,
}: {
  disabled: boolean;
  label: string;
  numeric?: boolean;
  onChange: (value: string) => void;
  value: string;
}) {
  return (
    <label className="space-y-1.5 text-xs font-semibold text-slate-600">
      {label}
      <input
        className="bg-white"
        disabled={disabled}
        min={numeric ? 0 : undefined}
        onChange={(event) => onChange(event.target.value)}
        required
        step={numeric ? 'any' : undefined}
        type={numeric ? 'number' : 'text'}
        value={value}
      />
    </label>
  );
}
