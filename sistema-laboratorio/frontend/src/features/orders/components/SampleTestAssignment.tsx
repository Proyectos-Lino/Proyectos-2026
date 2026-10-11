import { CheckSquare } from 'lucide-react';
import type { CatalogSnapshot } from '../../../shared/types/domain';
import type { WizardSampleForm } from '../model/order-wizard';

interface SampleTestAssignmentProps {
  catalog: CatalogSnapshot;
  index: number;
  laboratoryAreaId: string;
  onMethodChange: (testDefinitionId: string, testMethodId: string) => void;
  onServiceChange: (serviceId: string) => void;
  onTestToggle: (testDefinitionId: string) => void;
  sample: WizardSampleForm;
}

export function SampleTestAssignment({
  catalog,
  index,
  laboratoryAreaId,
  onMethodChange,
  onServiceChange,
  onTestToggle,
  sample,
}: SampleTestAssignmentProps) {
  const availableServices = catalog.services.filter(
    (service) => service.laboratoryAreaId === laboratoryAreaId,
  );
  const serviceTests = catalog.serviceTests
    .filter((serviceTest) => serviceTest.serviceId === sample.serviceId)
    .sort((first, second) => first.displayOrder - second.displayOrder);
  const selectedTestIds = new Set(sample.requestedTests.map((test) => test.testDefinitionId));

  return (
    <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <header className="flex items-center gap-3">
        <span className="grid size-9 place-items-center rounded-xl bg-lab-50 text-lab-700">
          <CheckSquare size={18} />
        </span>
        <div>
          <h3 className="font-bold text-slate-900">
            Muestra {index + 1}: {sample.productName || 'Sin producto'}
          </h3>
          <p className="mt-1 text-xs text-slate-500">
            Selecciona el servicio, los ensayos necesarios y su método.
          </p>
        </div>
      </header>

      <div className="mt-5 space-y-4">
        <div className="space-y-2">
          <label htmlFor={`sample-${index}-service`}>Servicio o paquete</label>
          <select
            id={`sample-${index}-service`}
            onChange={(event) => onServiceChange(event.target.value)}
            required
            value={sample.serviceId}
          >
            <option value="">Seleccionar servicio</option>
            {availableServices.map((service) => (
              <option key={service.id} value={service.id}>
                {service.name}
              </option>
            ))}
          </select>
        </div>

        {!laboratoryAreaId ? (
          <p className="rounded-xl bg-amber-50 p-3 text-sm text-amber-800">
            Primero selecciona el área de laboratorio en Datos generales.
          </p>
        ) : null}
        {laboratoryAreaId && availableServices.length === 0 ? (
          <p className="rounded-xl bg-amber-50 p-3 text-sm text-amber-800">
            No hay servicios activos configurados para el área seleccionada.
          </p>
        ) : null}
        {sample.serviceId && serviceTests.length === 0 ? (
          <p className="rounded-xl bg-amber-50 p-3 text-sm text-amber-800">
            El servicio seleccionado no tiene ensayos configurados.
          </p>
        ) : null}

        {serviceTests.map((serviceTest) => {
          const test = catalog.tests.find((item) => item.id === serviceTest.testDefinitionId);
          const selected = sample.requestedTests.find(
            (item) => item.testDefinitionId === serviceTest.testDefinitionId,
          );
          const methods = catalog.methods.filter(
            (method) => method.testDefinitionId === serviceTest.testDefinitionId,
          );

          return (
            <div
              className="grid gap-3 rounded-xl border border-slate-200 p-3.5 md:grid-cols-[minmax(0,1fr)_minmax(12rem,0.8fr)]"
              key={serviceTest.testDefinitionId}
            >
              <label className="flex cursor-pointer items-start gap-3 text-sm text-slate-700">
                <input
                  checked={selectedTestIds.has(serviceTest.testDefinitionId)}
                  disabled={serviceTest.required}
                  onChange={() => onTestToggle(serviceTest.testDefinitionId)}
                  type="checkbox"
                />
                <span>
                  <span className="block font-semibold text-slate-800">
                    {test?.name ?? 'Ensayo'}
                  </span>
                  {serviceTest.required ? (
                    <span className="mt-1 block text-xs text-slate-500">
                      Obligatorio en el servicio
                    </span>
                  ) : null}
                </span>
              </label>
              <select
                aria-label={`Método de ${test?.name ?? 'ensayo'}`}
                disabled={!selected}
                onChange={(event) =>
                  onMethodChange(serviceTest.testDefinitionId, event.target.value)
                }
                value={selected?.testMethodId ?? ''}
              >
                <option value="">Seleccionar método</option>
                {methods.map((method) => (
                  <option key={method.id} value={method.id}>
                    {method.name}
                    {method.versionLabel ? ` · ${method.versionLabel}` : ''}
                  </option>
                ))}
              </select>
            </div>
          );
        })}
      </div>
    </article>
  );
}
