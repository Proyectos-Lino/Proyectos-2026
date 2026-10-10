import { Calculator, CheckCircle2, Plus, Trash2 } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Button, Modal, ModalFooter } from '../../../shared/components/ui';
import { getErrorMessage } from '../../../shared/lib/errors';
import type { CatalogSnapshot } from '../../../shared/types/domain';
import type {
  CatalogKind,
  CatalogPayload,
  ManagedCatalogItem,
  ServiceTestConfiguration,
  TemplateCategory,
  TemplateField,
  FormulaPreviewValue,
} from '../api/catalog-management-api';
import { previewTemplateFormulas } from '../api/catalog-management-api';
import { catalogLabels } from '../model/catalog-labels';
import { TemplateFieldEditor } from './TemplateFieldEditor';

interface CatalogFormModalProps {
  catalog: CatalogSnapshot | null;
  isOpen: boolean;
  isSaving: boolean;
  item: ManagedCatalogItem | null;
  kind: CatalogKind;
  onClose: () => void;
  onSubmit: (id: string | null, payload: CatalogPayload) => Promise<boolean>;
}

interface CatalogFormState {
  code: string;
  categories: TemplateCategory[];
  defaultColumns: number;
  defaultRows: number;
  description: string;
  laboratoryAreaId: string;
  laboratoryCodePrefix: string;
  maximumColumns: string;
  maximumRepetitions: string;
  maximumRows: string;
  minimumRepetitions: number;
  name: string;
  productId: string;
  resultMode: 'SCALAR' | 'REPEATABLE' | 'MATRIX';
  resultType: 'NUMERIC' | 'TEXT' | 'QUALITATIVE';
  resultUnitId: string;
  sampleTypeId: string;
  symbol: string;
  testDefinitionId: string;
  tests: ServiceTestConfiguration[];
  fields: TemplateField[];
  versionLabel: string;
}

function defaultField(position = 1): TemplateField {
  return {
    fieldKey: position === 1 ? 'resultado' : `campo_${position}`,
    label: position === 1 ? 'Resultado' : `Campo ${position}`,
    resultType: 'NUMERIC',
    required: true,
    displayOrder: position,
    fieldKind: 'CAPTURED',
    readOnly: false,
    calculationScope: 'PER_REPETITION',
    decimalPrecision: 2,
  };
}

function createInitialForm(kind: CatalogKind, item: ManagedCatalogItem | null): CatalogFormState {
  return {
    code: item?.code ?? '',
    categories: item?.categories ?? [],
    defaultColumns: item?.defaultColumns ?? 1,
    defaultRows: item?.defaultRows ?? 1,
    description: item?.description ?? '',
    laboratoryAreaId: item?.laboratoryAreaId ?? '',
    laboratoryCodePrefix: item?.laboratoryCodePrefix ?? '',
    maximumColumns: item?.maximumColumns?.toString() ?? '',
    maximumRepetitions: item?.maximumRepetitions?.toString() ?? '',
    maximumRows: item?.maximumRows?.toString() ?? '',
    minimumRepetitions: item?.minimumRepetitions ?? 1,
    name: item?.name ?? '',
    productId: item?.productId ?? '',
    resultMode: item?.resultMode ?? 'SCALAR',
    resultType: item?.resultType ?? 'NUMERIC',
    resultUnitId: item?.resultUnitId ?? '',
    sampleTypeId: item?.sampleTypeId ?? '',
    symbol: item?.symbol ?? '',
    testDefinitionId: item?.testDefinitionId ?? '',
    tests: item?.tests ?? [],
    fields:
      item?.fields?.map((field) => ({
        ...field,
        measurementUnitId: field.measurementUnitId ?? '',
        fieldKind: field.fieldKind ?? 'CAPTURED',
        readOnly: field.readOnly ?? false,
        calculationScope: field.calculationScope ?? 'PER_REPETITION',
        decimalPrecision: field.decimalPrecision ?? 2,
      })) ?? (kind === 'result-templates' ? [defaultField()] : []),
    versionLabel: item?.versionLabel ?? '',
  };
}

function normalizedTemplateFields(form: CatalogFormState): TemplateField[] {
  return form.fields.map((field, index) => ({
    ...field,
    displayOrder: index + 1,
    fieldKey: field.fieldKey.trim().toLowerCase(),
    label: field.label.trim(),
    measurementUnitId:
      field.resultType === 'NUMERIC' ? field.measurementUnitId || undefined : undefined,
    readOnly: field.fieldKind !== 'CAPTURED' || field.readOnly,
    formula: field.fieldKind === 'CALCULATED' ? field.formula?.trim() : undefined,
    calculationScope: field.fieldKind === 'CALCULATED' ? field.calculationScope : 'PER_REPETITION',
    informationText:
      field.fieldKind === 'INFORMATIONAL' ? field.informationText?.trim() : undefined,
    defaultValue:
      field.fieldKind === 'CAPTURED' && field.readOnly ? field.defaultValue?.trim() : undefined,
    decimalPrecision: field.fieldKind === 'CALCULATED' ? field.decimalPrecision : undefined,
  }));
}

function payloadFor(kind: CatalogKind, form: CatalogFormState): CatalogPayload {
  const base = { code: form.code.trim(), name: form.name.trim() };

  switch (kind) {
    case 'units':
      return { ...base, symbol: form.symbol.trim() };
    case 'sample-types':
      return {
        ...base,
        laboratoryCodePrefix: form.laboratoryCodePrefix.trim(),
        resultMode: form.resultMode,
      };
    case 'tests':
      return {
        ...base,
        resultType: form.resultType,
        resultUnitId: form.resultType === 'NUMERIC' ? form.resultUnitId || undefined : undefined,
      };
    case 'methods':
      return {
        ...base,
        testDefinitionId: form.testDefinitionId,
        versionLabel: form.versionLabel.trim() || undefined,
      };
    case 'result-templates':
      return {
        categories:
          form.resultMode === 'MATRIX'
            ? form.categories.map((category, index) => ({
                ...category,
                code: category.code.trim(),
                displayOrder: index + 1,
                label: category.label.trim(),
              }))
            : [],
        defaultColumns: form.defaultColumns,
        defaultRows: form.defaultRows,
        description: form.description.trim() || undefined,
        fields: normalizedTemplateFields(form),
        name: form.name.trim(),
        maximumColumns: form.maximumColumns ? Number(form.maximumColumns) : undefined,
        maximumRepetitions: form.maximumRepetitions ? Number(form.maximumRepetitions) : undefined,
        maximumRows: form.maximumRows ? Number(form.maximumRows) : undefined,
        minimumRepetitions: form.minimumRepetitions,
        resultMode: form.resultMode,
        sampleTypeId: form.sampleTypeId || undefined,
        testDefinitionId: form.testDefinitionId,
      };
    case 'services':
      return {
        ...base,
        laboratoryAreaId: form.laboratoryAreaId,
        tests: form.tests.map(({ required, testDefinitionId }) => ({ required, testDefinitionId })),
      };
    case 'products':
      return base;
    case 'varieties':
      return { ...base, productId: form.productId };
  }
}

export function CatalogFormModal({
  catalog,
  isOpen,
  isSaving,
  item,
  kind,
  onClose,
  onSubmit,
}: CatalogFormModalProps) {
  const [form, setForm] = useState<CatalogFormState>(() => createInitialForm(kind, item));
  const [validationError, setValidationError] = useState<string | null>(null);
  const [formulaPreview, setFormulaPreview] = useState<FormulaPreviewValue[]>([]);
  const [isPreviewing, setIsPreviewing] = useState(false);
  const label = catalogLabels[kind].singular;

  function updateField<Key extends keyof CatalogFormState>(key: Key, value: CatalogFormState[Key]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  function toggleTest(testId: string) {
    setForm((current) => ({
      ...current,
      tests: current.tests.some((test) => test.testDefinitionId === testId)
        ? current.tests.filter((test) => test.testDefinitionId !== testId)
        : [...current.tests, { required: true, testDefinitionId: testId }],
    }));
  }

  function updateTemplateField(index: number, changes: Partial<TemplateField>) {
    setFormulaPreview([]);
    setForm((current) => ({
      ...current,
      fields: current.fields.map((field, fieldIndex) =>
        fieldIndex === index ? { ...field, ...changes } : field,
      ),
    }));
  }

  async function previewFormulas() {
    setIsPreviewing(true);
    setValidationError(null);
    try {
      const repetitions =
        form.resultMode === 'REPEATABLE' ? Math.max(3, form.minimumRepetitions) : 1;
      const sampleValues = Object.fromEntries(
        form.fields
          .filter((field) => field.fieldKind === 'CAPTURED' && field.resultType === 'NUMERIC')
          .map((field, fieldIndex) => [
            field.fieldKey.trim().toLowerCase(),
            Array.from({ length: repetitions }, (_, repetition) => {
              const configured = field.readOnly ? Number(field.defaultValue) : Number.NaN;
              return Number.isFinite(configured) ? configured : (fieldIndex + 1) * 10 + repetition;
            }),
          ]),
      );
      const preview = await previewTemplateFormulas(normalizedTemplateFields(form), sampleValues);
      setFormulaPreview(preview.values);
    } catch (reason) {
      setFormulaPreview([]);
      setValidationError(getErrorMessage(reason, 'No se pudo evaluar la fórmula.'));
    } finally {
      setIsPreviewing(false);
    }
  }

  function addTemplateField() {
    setFormulaPreview([]);
    setForm((current) => ({
      ...current,
      fields: [...current.fields, defaultField(current.fields.length + 1)],
    }));
  }

  function removeTemplateField(index: number) {
    setFormulaPreview([]);
    setForm((current) => ({
      ...current,
      fields: current.fields.filter((_, fieldIndex) => fieldIndex !== index),
    }));
  }

  function addCategory() {
    setForm((current) => ({
      ...current,
      categories: [
        ...current.categories,
        {
          code: `CAT_${current.categories.length + 1}`,
          label: `Categoría ${current.categories.length + 1}`,
          displayOrder: current.categories.length + 1,
        },
      ],
    }));
  }

  function updateCategory(index: number, changes: Partial<TemplateCategory>) {
    setForm((current) => ({
      ...current,
      categories: current.categories.map((category, categoryIndex) =>
        categoryIndex === index ? { ...category, ...changes } : category,
      ),
    }));
  }

  function removeCategory(index: number) {
    setForm((current) => ({
      ...current,
      categories: current.categories.filter((_, categoryIndex) => categoryIndex !== index),
    }));
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (kind === 'services' && form.tests.length === 0) {
      setValidationError('Selecciona al menos un ensayo para el servicio.');
      return;
    }
    if (kind === 'result-templates' && form.fields.length === 0) {
      setValidationError('Agrega al menos un campo a la plantilla.');
      return;
    }
    if (
      kind === 'result-templates' &&
      form.fields.some(
        (field) => !/^[a-z][a-z0-9_]{0,79}$/.test(field.fieldKey.trim().toLowerCase()),
      )
    ) {
      setValidationError(
        'Cada clave debe iniciar con una letra y usar solo minúsculas, números o guion bajo.',
      );
      return;
    }
    if (
      kind === 'result-templates' &&
      new Set(form.fields.map((field) => field.fieldKey.trim().toLowerCase())).size !==
        form.fields.length
    ) {
      setValidationError('Cada campo debe tener una clave estable diferente.');
      return;
    }
    const incompleteAdvancedField = form.fields.find(
      (field) =>
        (field.fieldKind === 'CALCULATED' && !field.formula?.trim()) ||
        (field.fieldKind === 'INFORMATIONAL' && !field.informationText?.trim()) ||
        (field.fieldKind === 'CAPTURED' && field.readOnly && !field.defaultValue?.trim()),
    );
    if (kind === 'result-templates' && incompleteAdvancedField) {
      setValidationError(
        `Completa la configuración avanzada de «${incompleteAdvancedField.label}».`,
      );
      return;
    }
    if (
      kind === 'result-templates' &&
      form.resultMode === 'MATRIX' &&
      form.fields.some((field) => field.fieldKind === 'CALCULATED')
    ) {
      setValidationError(
        'Los cálculos se configuran en plantillas escalares o repetibles; las matrices usan su cuadrícula especializada.',
      );
      return;
    }
    if (
      kind === 'result-templates' &&
      form.resultMode === 'MATRIX' &&
      form.categories.length === 0
    ) {
      setValidationError('Agrega al menos una categoría para clasificar las yemas.');
      return;
    }

    const saved = await onSubmit(item?.id ?? null, payloadFor(kind, form));
    if (saved) onClose();
  }

  return (
    <Modal
      description={`Configura los datos de ${label} que estarán disponibles en los procesos futuros.`}
      isOpen={isOpen}
      onClose={onClose}
      title={item ? `Editar ${label}` : `Nuevo ${label}`}
    >
      <form className="grid gap-5 md:grid-cols-2" onSubmit={handleSubmit}>
        {kind !== 'result-templates' ? (
          <div className="space-y-2">
            <label htmlFor="catalog-code">Código</label>
            <input
              id="catalog-code"
              maxLength={50}
              onChange={(event) => updateField('code', event.target.value)}
              required
              value={form.code}
            />
          </div>
        ) : null}
        <div className={`space-y-2 ${kind === 'result-templates' ? 'md:col-span-2' : ''}`}>
          <label htmlFor="catalog-name">Nombre</label>
          <input
            id="catalog-name"
            maxLength={200}
            onChange={(event) => updateField('name', event.target.value)}
            required
            value={form.name}
          />
        </div>

        {kind === 'units' ? (
          <div className="space-y-2">
            <label htmlFor="catalog-symbol">Símbolo</label>
            <input
              id="catalog-symbol"
              maxLength={20}
              onChange={(event) => updateField('symbol', event.target.value)}
              required
              value={form.symbol}
            />
          </div>
        ) : null}

        {kind === 'sample-types' ? (
          <>
            <div className="space-y-2">
              <label htmlFor="catalog-prefix">Prefijo de código</label>
              <input
                id="catalog-prefix"
                maxLength={20}
                onChange={(event) => updateField('laboratoryCodePrefix', event.target.value)}
                required
                value={form.laboratoryCodePrefix}
              />
            </div>
            <div className="space-y-2">
              <label htmlFor="catalog-result-mode">Modo de resultado</label>
              <select
                id="catalog-result-mode"
                onChange={(event) =>
                  updateField('resultMode', event.target.value as CatalogFormState['resultMode'])
                }
                value={form.resultMode}
              >
                <option value="SCALAR">Escalar</option>
                <option value="REPEATABLE">Repetible</option>
                <option value="MATRIX">Matriz</option>
              </select>
            </div>
          </>
        ) : null}

        {kind === 'tests' ? (
          <>
            <div className="space-y-2">
              <label htmlFor="catalog-result-type">Tipo de resultado</label>
              <select
                id="catalog-result-type"
                onChange={(event) =>
                  updateField('resultType', event.target.value as CatalogFormState['resultType'])
                }
                value={form.resultType}
              >
                <option value="NUMERIC">Numérico</option>
                <option value="TEXT">Texto</option>
                <option value="QUALITATIVE">Cualitativo</option>
              </select>
            </div>
            <div className="space-y-2">
              <label htmlFor="catalog-result-unit">Unidad de resultado</label>
              <select
                disabled={form.resultType !== 'NUMERIC'}
                id="catalog-result-unit"
                onChange={(event) => updateField('resultUnitId', event.target.value)}
                value={form.resultUnitId}
              >
                <option value="">Sin unidad predeterminada</option>
                {catalog?.units.map((unit) => (
                  <option key={unit.id} value={unit.id}>
                    {unit.name} ({unit.symbol})
                  </option>
                ))}
              </select>
            </div>
          </>
        ) : null}

        {kind === 'methods' || kind === 'result-templates' ? (
          <div className="space-y-2">
            <label htmlFor="catalog-test">Ensayo</label>
            <select
              disabled={Boolean(item)}
              id="catalog-test"
              onChange={(event) => updateField('testDefinitionId', event.target.value)}
              required
              value={form.testDefinitionId}
            >
              <option value="">Selecciona un ensayo</option>
              {catalog?.tests.map((test) => (
                <option key={test.id} value={test.id}>
                  {test.name}
                </option>
              ))}
            </select>
          </div>
        ) : null}

        {kind === 'result-templates' ? (
          <>
            <div className="space-y-2">
              <label htmlFor="template-sample-type">Tipo de muestra</label>
              <select
                disabled={Boolean(item)}
                id="template-sample-type"
                onChange={(event) => updateField('sampleTypeId', event.target.value)}
                value={form.sampleTypeId}
              >
                <option value="">Plantilla general para el ensayo</option>
                {catalog?.sampleTypes.map((sampleType) => (
                  <option key={sampleType.id} value={sampleType.id}>
                    {sampleType.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-2">
              <label htmlFor="template-result-mode">Estructura del resultado</label>
              <select
                id="template-result-mode"
                onChange={(event) =>
                  updateField('resultMode', event.target.value as CatalogFormState['resultMode'])
                }
                value={form.resultMode}
              >
                <option value="SCALAR">Resultado escalar</option>
                <option value="REPEATABLE">Varias lecturas</option>
                <option value="MATRIX">Yemas y cargadores</option>
              </select>
            </div>
          </>
        ) : null}

        {kind === 'methods' ? (
          <div className="space-y-2">
            <label htmlFor="catalog-version">Versión o norma</label>
            <input
              id="catalog-version"
              maxLength={60}
              onChange={(event) => updateField('versionLabel', event.target.value)}
              value={form.versionLabel}
            />
          </div>
        ) : null}

        {kind === 'services' ? (
          <>
            <div className="space-y-2 md:col-span-2">
              <label htmlFor="catalog-area">Área de laboratorio</label>
              <select
                id="catalog-area"
                onChange={(event) => updateField('laboratoryAreaId', event.target.value)}
                required
                value={form.laboratoryAreaId}
              >
                <option value="">Selecciona un área</option>
                {catalog?.areas.map((area) => (
                  <option key={area.id} value={area.id}>
                    {area.name}
                  </option>
                ))}
              </select>
            </div>
            <fieldset className="md:col-span-2">
              <legend className="text-sm font-semibold text-slate-700">Ensayos incluidos</legend>
              <div className="mt-3 grid gap-2 sm:grid-cols-2">
                {catalog?.tests.map((test) => {
                  const selected = form.tests.some(
                    (itemTest) => itemTest.testDefinitionId === test.id,
                  );
                  return (
                    <label
                      className={`flex cursor-pointer items-center gap-3 rounded-xl border px-3.5 py-3 text-sm transition ${
                        selected
                          ? 'border-lab-300 bg-lab-50 text-lab-900'
                          : 'border-slate-200 text-slate-700 hover:border-slate-300'
                      }`}
                      key={test.id}
                    >
                      <input
                        checked={selected}
                        onChange={() => toggleTest(test.id)}
                        type="checkbox"
                      />
                      {test.name}
                    </label>
                  );
                })}
              </div>
            </fieldset>
          </>
        ) : null}

        {kind === 'varieties' ? (
          <div className="space-y-2 md:col-span-2">
            <label htmlFor="catalog-product">Producto</label>
            <select
              disabled={Boolean(item)}
              id="catalog-product"
              onChange={(event) => updateField('productId', event.target.value)}
              required
              value={form.productId}
            >
              <option value="">Selecciona un producto</option>
              {catalog?.products.map((product) => (
                <option key={product.id} value={product.id}>
                  {product.name}
                </option>
              ))}
            </select>
          </div>
        ) : null}

        {kind === 'result-templates' ? (
          <>
            <div className="space-y-2 md:col-span-2">
              <label htmlFor="catalog-description">Descripción</label>
              <textarea
                id="catalog-description"
                maxLength={500}
                onChange={(event) => updateField('description', event.target.value)}
                rows={2}
                value={form.description}
              />
            </div>
            {form.resultMode === 'REPEATABLE' ? (
              <section className="grid gap-4 rounded-2xl border border-sky-200 bg-sky-50/60 p-4 md:col-span-2 sm:grid-cols-2">
                <div className="space-y-2">
                  <label htmlFor="template-minimum-repetitions">Lecturas iniciales</label>
                  <input
                    id="template-minimum-repetitions"
                    min={1}
                    onChange={(event) =>
                      updateField('minimumRepetitions', Number(event.target.value))
                    }
                    required
                    type="number"
                    value={form.minimumRepetitions}
                  />
                </div>
                <div className="space-y-2">
                  <label htmlFor="template-maximum-repetitions">Máximo de lecturas</label>
                  <input
                    id="template-maximum-repetitions"
                    min={form.minimumRepetitions}
                    onChange={(event) => updateField('maximumRepetitions', event.target.value)}
                    placeholder="Sin límite"
                    type="number"
                    value={form.maximumRepetitions}
                  />
                </div>
              </section>
            ) : null}
            {form.resultMode === 'MATRIX' ? (
              <section className="space-y-4 rounded-2xl border border-emerald-200 bg-emerald-50/60 p-4 md:col-span-2">
                <div>
                  <h3 className="font-semibold text-slate-900">
                    Dimensiones de yemas y cargadores
                  </h3>
                  <p className="mt-1 text-xs text-slate-600">
                    Cada fila representa una yema y cada columna un cargador con sus metadatos.
                  </p>
                </div>
                <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                  <div className="space-y-2">
                    <label htmlFor="template-default-rows">Yemas iniciales</label>
                    <input
                      id="template-default-rows"
                      min={1}
                      onChange={(event) => updateField('defaultRows', Number(event.target.value))}
                      required
                      type="number"
                      value={form.defaultRows}
                    />
                  </div>
                  <div className="space-y-2">
                    <label htmlFor="template-maximum-rows">Máximo de yemas</label>
                    <input
                      id="template-maximum-rows"
                      min={form.defaultRows}
                      onChange={(event) => updateField('maximumRows', event.target.value)}
                      placeholder="Sin límite"
                      type="number"
                      value={form.maximumRows}
                    />
                  </div>
                  <div className="space-y-2">
                    <label htmlFor="template-default-columns">Cargadores iniciales</label>
                    <input
                      id="template-default-columns"
                      min={1}
                      onChange={(event) =>
                        updateField('defaultColumns', Number(event.target.value))
                      }
                      required
                      type="number"
                      value={form.defaultColumns}
                    />
                  </div>
                  <div className="space-y-2">
                    <label htmlFor="template-maximum-columns">Máximo de cargadores</label>
                    <input
                      id="template-maximum-columns"
                      min={form.defaultColumns}
                      onChange={(event) => updateField('maximumColumns', event.target.value)}
                      placeholder="Sin límite"
                      type="number"
                      value={form.maximumColumns}
                    />
                  </div>
                </div>
                <div className="flex items-center justify-between gap-3 border-t border-emerald-200 pt-4">
                  <div>
                    <h3 className="font-semibold text-slate-900">Categorías de clasificación</h3>
                    <p className="mt-1 text-xs text-slate-600">
                      Define las opciones que podrá seleccionar el analista en cada yema.
                    </p>
                  </div>
                  <Button onClick={addCategory} type="button" variant="secondary">
                    <Plus size={15} />
                    Categoría
                  </Button>
                </div>
                <div className="grid gap-3 sm:grid-cols-2">
                  {form.categories.map((category, index) => (
                    <div
                      className="grid grid-cols-[1fr_1.5fr_auto] gap-2 rounded-xl border border-emerald-200 bg-white p-3"
                      key={category.id ?? index}
                    >
                      <input
                        aria-label="Código de categoría"
                        onChange={(event) => updateCategory(index, { code: event.target.value })}
                        placeholder="Código"
                        required
                        value={category.code}
                      />
                      <input
                        aria-label="Nombre de categoría"
                        onChange={(event) => updateCategory(index, { label: event.target.value })}
                        placeholder="Nombre"
                        required
                        value={category.label}
                      />
                      <Button
                        aria-label="Eliminar categoría"
                        onClick={() => removeCategory(index)}
                        type="button"
                        variant="ghost"
                      >
                        <Trash2 size={16} />
                      </Button>
                    </div>
                  ))}
                </div>
              </section>
            ) : null}
            <section className="space-y-3 md:col-span-2">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <h3 className="font-semibold text-slate-800">Estructura de resultados</h3>
                  <p className="text-xs text-slate-500">
                    Combina entradas, instrucciones y cálculos sin exponer la lógica al navegador.
                  </p>
                </div>
                <div className="flex flex-wrap gap-2">
                  {form.fields.some((field) => field.fieldKind === 'CALCULATED') ? (
                    <Button
                      disabled={isPreviewing}
                      onClick={previewFormulas}
                      type="button"
                      variant="secondary"
                    >
                      <Calculator size={15} />
                      {isPreviewing ? 'Calculando…' : 'Probar fórmulas'}
                    </Button>
                  ) : null}
                  <Button onClick={addTemplateField} type="button" variant="secondary">
                    <Plus size={15} />
                    Campo
                  </Button>
                </div>
              </div>
              <div className="space-y-4">
                {form.fields.map((field, index) => (
                  <TemplateFieldEditor
                    allowCalculated={form.resultMode !== 'MATRIX'}
                    field={field}
                    index={index}
                    key={field.id ?? index}
                    onChange={(changes) => updateTemplateField(index, changes)}
                    onRemove={() => removeTemplateField(index)}
                    removable={form.fields.length > 1}
                    units={catalog?.units ?? []}
                  />
                ))}
              </div>
              {formulaPreview.length ? (
                <div className="rounded-2xl border border-emerald-200 bg-emerald-50/70 p-4">
                  <div className="flex items-start gap-3">
                    <CheckCircle2 className="mt-0.5 text-emerald-700" size={18} />
                    <div className="min-w-0 flex-1">
                      <h4 className="text-sm font-bold text-emerald-950">
                        Fórmulas válidas · vista previa del servidor
                      </h4>
                      <p className="mt-1 text-xs leading-5 text-emerald-800">
                        Se usaron valores de ejemplo para comprobar referencias, ciclos y cálculos.
                      </p>
                      <div className="mt-3 grid gap-2 sm:grid-cols-2">
                        {formulaPreview.map((preview) => (
                          <div
                            className="rounded-xl border border-emerald-200 bg-white px-3 py-2"
                            key={`${preview.fieldKey}:${preview.repetitionIndex}`}
                          >
                            <p className="text-xs font-semibold text-slate-500">
                              {preview.fieldKey}
                              {form.resultMode === 'REPEATABLE'
                                ? ` · lectura ${preview.repetitionIndex + 1}`
                                : ''}
                            </p>
                            <p className="mt-1 font-mono text-sm font-bold text-emerald-800">
                              {preview.value}
                            </p>
                          </div>
                        ))}
                      </div>
                    </div>
                  </div>
                </div>
              ) : null}
            </section>
          </>
        ) : null}

        {validationError ? (
          <p className="text-sm text-rose-700 md:col-span-2">{validationError}</p>
        ) : null}
        <ModalFooter className="md:col-span-2">
          <Button onClick={onClose} variant="secondary">
            Cancelar
          </Button>
          <Button disabled={isSaving} type="submit">
            {isSaving ? 'Guardando…' : item ? 'Guardar cambios' : `Crear ${label}`}
          </Button>
        </ModalFooter>
      </form>
    </Modal>
  );
}
