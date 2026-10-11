import { Trash2 } from 'lucide-react';
import { Button } from '../../../shared/components/ui';
import type { CatalogSnapshot, Client } from '../../../shared/types/domain';
import type { WizardSampleForm } from '../model/order-wizard';

interface SampleTraceabilityFieldsProps {
  catalog: CatalogSnapshot;
  index: number;
  isRemovable: boolean;
  onChange: (field: keyof WizardSampleForm, value: string | boolean) => void;
  onRemove: () => void;
  producers: Client[];
  sample: WizardSampleForm;
}

export function SampleTraceabilityFields({
  catalog,
  index,
  isRemovable,
  onChange,
  onRemove,
  producers,
  sample,
}: SampleTraceabilityFieldsProps) {
  const fieldId = (field: string) => `sample-${index}-${field}`;

  return (
    <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <header className="mb-5 flex items-center justify-between gap-3">
        <div>
          <h3 className="font-bold text-slate-900">Muestra {index + 1}</h3>
          <p className="mt-1 text-xs text-slate-500">Identificación y condiciones de recepción.</p>
        </div>
        {isRemovable ? (
          <Button onClick={onRemove} variant="ghost">
            <Trash2 size={16} />
            Quitar
          </Button>
        ) : null}
      </header>

      <div className="grid gap-5 md:grid-cols-2">
        <div className="space-y-2">
          <label htmlFor={fieldId('client-identification')}>Identificación del cliente</label>
          <input
            id={fieldId('client-identification')}
            onChange={(event) => onChange('clientIdentification', event.target.value)}
            placeholder="Lote, código o referencia"
            value={sample.clientIdentification}
          />
        </div>
        <div className="space-y-2">
          <label htmlFor={fieldId('producer')}>Productor o propietario</label>
          <select
            id={fieldId('producer')}
            onChange={(event) => onChange('producerId', event.target.value)}
            value={sample.producerId}
          >
            <option value="">No especificado</option>
            {producers.map((producer) => (
              <option key={producer.id} value={producer.id}>
                {producer.legalName} · {producer.documentNumber}
              </option>
            ))}
          </select>
          <p className="text-xs text-slate-500">Puede ser diferente del solicitante de la orden.</p>
        </div>
        <div className="space-y-2">
          <label htmlFor={fieldId('sample-type')}>Tipo de muestra</label>
          <select
            id={fieldId('sample-type')}
            onChange={(event) => onChange('sampleTypeId', event.target.value)}
            required
            value={sample.sampleTypeId}
          >
            <option value="">Seleccionar tipo</option>
            {catalog.sampleTypes.map((sampleType) => (
              <option key={sampleType.id} value={sampleType.id}>
                {sampleType.name}
              </option>
            ))}
          </select>
        </div>
        <div className="space-y-2">
          <label htmlFor={fieldId('product')}>Producto o matriz</label>
          <input
            id={fieldId('product')}
            list="catalog-products"
            onChange={(event) => onChange('productName', event.target.value)}
            required
            value={sample.productName}
          />
        </div>
        <div className="space-y-2">
          <label htmlFor={fieldId('variety')}>Variedad</label>
          <input
            id={fieldId('variety')}
            list="catalog-varieties"
            onChange={(event) => onChange('varietyName', event.target.value)}
            value={sample.varietyName}
          />
        </div>
        <div className="space-y-2 md:col-span-2">
          <label htmlFor={fieldId('physical-description')}>Descripción física / condición</label>
          <textarea
            id={fieldId('physical-description')}
            onChange={(event) => onChange('physicalDescription', event.target.value)}
            rows={2}
            value={sample.physicalDescription}
          />
        </div>
        <div className="space-y-2">
          <label htmlFor={fieldId('sampling-location')}>Lugar de muestreo</label>
          <input
            id={fieldId('sampling-location')}
            onChange={(event) => onChange('samplingLocation', event.target.value)}
            value={sample.samplingLocation}
          />
        </div>
        <div className="space-y-2">
          <label htmlFor={fieldId('origin')}>Procedencia / origen</label>
          <input
            id={fieldId('origin')}
            onChange={(event) => onChange('origin', event.target.value)}
            value={sample.origin}
          />
        </div>
        <div className="space-y-2">
          <label htmlFor={fieldId('sampler')}>Persona que muestreó</label>
          <input
            id={fieldId('sampler')}
            onChange={(event) => onChange('samplerName', event.target.value)}
            value={sample.samplerName}
          />
        </div>
        <div className="space-y-2">
          <label htmlFor={fieldId('sampled-at')}>Fecha y hora de muestreo</label>
          <input
            id={fieldId('sampled-at')}
            onChange={(event) => onChange('sampledAt', event.target.value)}
            type="datetime-local"
            value={sample.sampledAt}
          />
        </div>
        <div className="space-y-2">
          <label htmlFor={fieldId('quantity')}>Cantidad recibida</label>
          <input
            id={fieldId('quantity')}
            min="0.0001"
            onChange={(event) => onChange('quantity', event.target.value)}
            step="0.0001"
            type="number"
            value={sample.quantity}
          />
        </div>
        <div className="space-y-2">
          <label htmlFor={fieldId('unit')}>Unidad de medida</label>
          <select
            id={fieldId('unit')}
            onChange={(event) => onChange('measurementUnitId', event.target.value)}
            value={sample.measurementUnitId}
          >
            <option value="">Sin unidad</option>
            {catalog.units.map((unit) => (
              <option key={unit.id} value={unit.id}>
                {unit.name} ({unit.symbol})
              </option>
            ))}
          </select>
        </div>
        <div className="space-y-2 md:col-span-2">
          <label htmlFor={fieldId('container')}>Envase o recipiente</label>
          <input
            id={fieldId('container')}
            onChange={(event) => onChange('containerDescription', event.target.value)}
            value={sample.containerDescription}
          />
        </div>
      </div>

      <details className="mt-5 rounded-xl bg-slate-50 p-4">
        <summary className="cursor-pointer text-sm font-bold text-slate-700">
          Trazabilidad adicional
        </summary>
        <div className="mt-4 grid gap-5 md:grid-cols-2">
          <div className="space-y-2">
            <label htmlFor={fieldId('batch')}>Lote</label>
            <input
              id={fieldId('batch')}
              onChange={(event) => onChange('batchNumber', event.target.value)}
              value={sample.batchNumber}
            />
          </div>
          <div className="space-y-2">
            <label htmlFor={fieldId('production-year')}>Año de producción</label>
            <input
              id={fieldId('production-year')}
              min="1900"
              onChange={(event) => onChange('productionYear', event.target.value)}
              type="number"
              value={sample.productionYear}
            />
          </div>
          <div className="space-y-2">
            <label htmlFor={fieldId('brand')}>Marca</label>
            <input
              id={fieldId('brand')}
              onChange={(event) => onChange('brand', event.target.value)}
              value={sample.brand}
            />
          </div>
          <div className="space-y-2">
            <label htmlFor={fieldId('origin-certificate')}>Certificado de origen</label>
            <input
              id={fieldId('origin-certificate')}
              onChange={(event) => onChange('originCertificate', event.target.value)}
              value={sample.originCertificate}
            />
          </div>
          <div className="space-y-2">
            <label htmlFor={fieldId('sanitary-registration')}>Registro sanitario</label>
            <input
              id={fieldId('sanitary-registration')}
              onChange={(event) => onChange('sanitaryRegistration', event.target.value)}
              value={sample.sanitaryRegistration}
            />
          </div>
          <div className="space-y-2">
            <label htmlFor={fieldId('crop-age')}>Edad del cultivo</label>
            <input
              id={fieldId('crop-age')}
              onChange={(event) => onChange('cropAge', event.target.value)}
              value={sample.cropAge}
            />
          </div>
          <div className="space-y-2">
            <label htmlFor={fieldId('carrier-count')}>Número de cargadores</label>
            <input
              id={fieldId('carrier-count')}
              min="0"
              onChange={(event) => onChange('carrierCount', event.target.value)}
              type="number"
              value={sample.carrierCount}
            />
          </div>
          <div className="space-y-2">
            <label htmlFor={fieldId('bud-count')}>Número de yemas</label>
            <input
              id={fieldId('bud-count')}
              min="0"
              onChange={(event) => onChange('budCount', event.target.value)}
              type="number"
              value={sample.budCount}
            />
          </div>
          <label className="flex cursor-pointer items-center gap-3 rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm font-semibold text-slate-700">
            <input
              checked={sample.counterSampleInCustody}
              onChange={(event) => onChange('counterSampleInCustody', event.target.checked)}
              type="checkbox"
            />
            Contramuestra en custodia
          </label>
          <div className="space-y-2 md:col-span-2">
            <label htmlFor={fieldId('observations')}>Observaciones de la muestra</label>
            <textarea
              id={fieldId('observations')}
              onChange={(event) => onChange('observations', event.target.value)}
              rows={3}
              value={sample.observations}
            />
          </div>
        </div>
      </details>
      {index === 0 ? (
        <>
          <datalist id="catalog-products">
            {catalog.products.map((product) => (
              <option key={product.id} value={product.name} />
            ))}
          </datalist>
          <datalist id="catalog-varieties">
            {catalog.varieties.map((variety) => (
              <option key={variety.id} value={variety.name} />
            ))}
          </datalist>
        </>
      ) : null}
    </article>
  );
}
