import { ChevronLeft, ChevronRight, Plus } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Button, Modal, ModalFooter } from '../../../shared/components/ui';
import type { CatalogSnapshot, Client, ServiceOrderDetail } from '../../../shared/types/domain';
import { ClientFormModal } from '../../clients/components/ClientFormModal';
import type { CreateClientInput } from '../../clients/api/clients-api';
import type {
  CommercialDetailsInput,
  CorrectionImpact,
  CreateServiceOrderInput,
  UpdateOrderSamplesInput,
} from '../api/orders-api';
import { CommercialDetailsFields } from './CommercialDetailsFields';
import { SampleTestAssignment } from './SampleTestAssignment';
import { SampleTraceabilityFields } from './SampleTraceabilityFields';
import type { CommercialDetailsFormValues } from '../model/order-commercial-details';
import {
  emptyOrderWizard,
  emptyWizardSample,
  orderToWizardForm,
  toOrderSamplesInput,
  type OrderWizardForm,
  type WizardSampleForm,
} from '../model/order-wizard';

interface OrderWizardModalProps {
  catalog: CatalogSnapshot | null;
  clients: Client[];
  producers: Client[];
  editingOrder: ServiceOrderDetail | null;
  isCreatingClient: boolean;
  isOpen: boolean;
  isSaving: boolean;
  onClose: () => void;
  onCreateClient: (input: CreateClientInput) => Promise<Client | null>;
  onSubmitCreate: (input: CreateServiceOrderInput) => Promise<boolean>;
  onSubmitSamplesUpdate: (orderId: string, input: UpdateOrderSamplesInput) => Promise<boolean>;
  onSubmitCorrection: (
    orderId: string,
    commercialDetails: CommercialDetailsInput,
    input: UpdateOrderSamplesInput,
    confirmResultInvalidation?: boolean,
  ) => Promise<{ saved: boolean; impact?: CorrectionImpact }>;
}

const stepTitles = ['Datos generales', 'Muestras', 'Ensayos'];

function initialForm(
  order: ServiceOrderDetail | null,
  catalog: CatalogSnapshot | null,
): OrderWizardForm {
  return order ? orderToWizardForm(order) : emptyOrderWizard(catalog?.sampleTypes[0]?.id ?? '');
}

export function OrderWizardModal({
  catalog,
  clients,
  editingOrder,
  isCreatingClient,
  isOpen,
  isSaving,
  onClose,
  onCreateClient,
  onSubmitCreate,
  onSubmitCorrection,
  onSubmitSamplesUpdate,
  producers,
}: OrderWizardModalProps) {
  const [form, setForm] = useState<OrderWizardForm>(() => initialForm(editingOrder, catalog));
  const [step, setStep] = useState(1);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [isQuickClientOpen, setIsQuickClientOpen] = useState(false);
  const isEditingSamples = Boolean(editingOrder);
  const isCorrection = editingOrder?.status === 'COMMERCIAL_CORRECTION';
  const [correctionImpact, setCorrectionImpact] = useState<CorrectionImpact | null>(null);

  function updateForm(updater: (current: OrderWizardForm) => OrderWizardForm) {
    setCorrectionImpact(null);
    setForm(updater);
  }

  function closeModal() {
    setForm(initialForm(editingOrder, catalog));
    setStep(1);
    setValidationError(null);
    setCorrectionImpact(null);
    onClose();
  }

  function updateCommercialField(
    field: keyof CommercialDetailsFormValues,
    value: string | boolean,
  ) {
    updateForm((current) => ({ ...current, [field]: value }) as OrderWizardForm);
  }

  function updateSample(index: number, field: keyof WizardSampleForm, value: string | boolean) {
    updateForm((current) => ({
      ...current,
      samples: current.samples.map((sample, sampleIndex) =>
        sampleIndex === index ? ({ ...sample, [field]: value } as WizardSampleForm) : sample,
      ),
    }));
  }

  function addSample() {
    updateForm((current) => ({
      ...current,
      samples: [...current.samples, emptyWizardSample(catalog?.sampleTypes[0]?.id ?? '')],
    }));
  }

  function removeSample(index: number) {
    updateForm((current) => ({
      ...current,
      samples: current.samples.filter((_, sampleIndex) => sampleIndex !== index),
    }));
  }

  function changeService(index: number, serviceId: string) {
    const serviceTests = catalog?.serviceTests.filter((item) => item.serviceId === serviceId) ?? [];
    const requestedTests = serviceTests.map((serviceTest) => ({
      testDefinitionId: serviceTest.testDefinitionId,
      testMethodId:
        catalog?.methods.find((method) => method.testDefinitionId === serviceTest.testDefinitionId)
          ?.id ?? '',
    }));

    updateForm((current) => ({
      ...current,
      samples: current.samples.map((sample, sampleIndex) =>
        sampleIndex === index ? { ...sample, serviceId, requestedTests } : sample,
      ),
    }));
  }

  function toggleTest(index: number, testDefinitionId: string) {
    updateForm((current) => ({
      ...current,
      samples: current.samples.map((sample, sampleIndex) => {
        if (sampleIndex !== index) {
          return sample;
        }

        const isSelected = sample.requestedTests.some(
          (test) => test.testDefinitionId === testDefinitionId,
        );
        if (isSelected) {
          return {
            ...sample,
            requestedTests: sample.requestedTests.filter(
              (test) => test.testDefinitionId !== testDefinitionId,
            ),
          };
        }

        const method = catalog?.methods.find((item) => item.testDefinitionId === testDefinitionId);
        return {
          ...sample,
          requestedTests: [
            ...sample.requestedTests,
            { testDefinitionId, testMethodId: method?.id ?? '' },
          ],
        };
      }),
    }));
  }

  function changeMethod(index: number, testDefinitionId: string, testMethodId: string) {
    updateForm((current) => ({
      ...current,
      samples: current.samples.map((sample, sampleIndex) =>
        sampleIndex === index
          ? {
              ...sample,
              requestedTests: sample.requestedTests.map((test) =>
                test.testDefinitionId === testDefinitionId ? { ...test, testMethodId } : test,
              ),
            }
          : sample,
      ),
    }));
  }

  function validateCurrentStep(): boolean {
    if (step === 1 && (!isEditingSamples || isCorrection)) {
      if (!form.clientId || !form.laboratoryAreaId) {
        setValidationError('Selecciona el cliente y el área de laboratorio de la orden.');
        return false;
      }
      if (form.deliveryMethod === 'PICKUP' && !form.pickupPerson.trim()) {
        setValidationError('Indica la persona que recogerá el informe.');
        return false;
      }
    }

    if (step === 2) {
      const incompleteSample = form.samples.some(
        (sample) => !sample.productName.trim() || !sample.sampleTypeId,
      );
      if (incompleteSample) {
        setValidationError('Cada muestra debe tener producto o matriz y tipo de muestra.');
        return false;
      }
    }

    if (step === 3) {
      const incompleteTests = form.samples.some(
        (sample) =>
          !sample.serviceId ||
          sample.requestedTests.length === 0 ||
          sample.requestedTests.some((test) => !test.testMethodId),
      );
      if (incompleteTests) {
        setValidationError('Cada muestra debe tener un servicio, al menos un ensayo y un método.');
        return false;
      }
    }

    setValidationError(null);
    return true;
  }

  function goNext() {
    if (validateCurrentStep()) {
      setStep((current) => Math.min(3, current + 1));
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (step < 3) {
      goNext();
      return;
    }
    if (!validateCurrentStep()) {
      return;
    }

    const samples = toOrderSamplesInput(form.samples);
    let wasSaved: boolean;
    if (editingOrder && isCorrection) {
      const commercialDetails: CommercialDetailsInput = {
        clientId: form.clientId,
        reportName: form.reportName,
        reportEmail: form.reportEmail,
        reportAddress: form.reportAddress,
        deliveryMethod: form.deliveryMethod,
        pickupPerson: form.deliveryMethod === 'PICKUP' ? form.pickupPerson : undefined,
        plannedDeliveryAt: new Date(form.plannedDeliveryAt).toISOString(),
        proformaNumber: form.proformaNumber,
        technicalProposalNumber: form.technicalProposalNumber,
        developmentDays: form.developmentDays ? Number(form.developmentDays) : undefined,
        outsourced: form.outsourced,
        postAnalysisCustody: form.postAnalysisCustody,
        generalObservations: form.generalObservations,
      };
      const result = await onSubmitCorrection(
        editingOrder.id,
        commercialDetails,
        {
          samples,
          changeReason: editingOrder.correctionReason,
          expectedAffectedResultCount: correctionImpact?.affectedResultCount,
        },
        correctionImpact?.requiresResultInvalidation === true,
      );
      setCorrectionImpact(result.impact ?? null);
      wasSaved = result.saved;
    } else if (editingOrder) {
      wasSaved = await onSubmitSamplesUpdate(editingOrder.id, { samples });
    } else {
      wasSaved = await onSubmitCreate({
        clientId: form.clientId,
        laboratoryAreaId: form.laboratoryAreaId,
        reportName: form.reportName,
        reportEmail: form.reportEmail,
        reportAddress: form.reportAddress,
        deliveryMethod: form.deliveryMethod,
        pickupPerson: form.deliveryMethod === 'PICKUP' ? form.pickupPerson : undefined,
        plannedDeliveryAt: new Date(form.plannedDeliveryAt).toISOString(),
        proformaNumber: form.proformaNumber,
        technicalProposalNumber: form.technicalProposalNumber,
        developmentDays: form.developmentDays ? Number(form.developmentDays) : undefined,
        outsourced: form.outsourced,
        postAnalysisCustody: form.postAnalysisCustody,
        generalObservations: form.generalObservations,
        samples,
      });
    }

    if (wasSaved) {
      closeModal();
    }
  }

  async function saveQuickClient(input: CreateClientInput) {
    const client = await onCreateClient(input);
    if (client) {
      updateForm((current) => ({
        ...current,
        clientId: client.id,
        reportName: current.reportName || client.reportName || '',
        reportEmail: current.reportEmail || client.email || '',
        reportAddress: current.reportAddress || client.address || '',
      }));
      return true;
    }

    return false;
  }

  return (
    <>
      <Modal
        description={
          isCorrection
            ? 'Corrige los datos solicitados. El sistema conservará las OT y evaluará el impacto sobre los resultados.'
            : isEditingSamples
              ? 'Modifica muestras y ensayos antes de iniciar el procesamiento técnico.'
            : 'Completa los tres pasos para registrar una orden de servicio trazable.'
        }
        isOpen={isOpen}
        onClose={closeModal}
        title={
          isCorrection
            ? `Corrección comercial · ${editingOrder?.code}`
            : editingOrder
              ? `Editar muestras · ${editingOrder.code}`
              : 'Nueva orden de servicio'
        }
      >
        <div className="mb-6 grid grid-cols-3 gap-2" role="tablist">
          {stepTitles.map((title, index) => {
            const stepNumber = index + 1;
            return (
              <button
                aria-selected={step === stepNumber}
                className={`rounded-xl px-3 py-2 text-left text-xs font-bold transition sm:text-sm ${
                  step === stepNumber
                    ? 'bg-lab-700 text-white shadow-sm'
                    : step > stepNumber
                      ? 'bg-lab-50 text-lab-800'
                      : 'bg-slate-100 text-slate-500'
                }`}
                key={title}
                onClick={() => {
                  if (stepNumber < step) {
                    setStep(stepNumber);
                    setValidationError(null);
                  }
                }}
                role="tab"
                type="button"
              >
                {stepNumber}. {title}
              </button>
            );
          })}
        </div>

        <form onSubmit={handleSubmit}>
          {step === 1 ? (
            <div className="grid gap-5 md:grid-cols-2">
              {isEditingSamples && !isCorrection ? (
                <div className="rounded-xl bg-slate-50 p-4 text-sm text-slate-700 md:col-span-2">
                  Esta edición conserva los datos comerciales. Puedes modificarlos desde el botón
                  “Editar” del detalle comercial.
                </div>
              ) : (
                <>
                  <div className="space-y-2">
                    <div className="flex items-center justify-between gap-3">
                      <label htmlFor="wizard-client">Cliente</label>
                      <button
                        className="inline-flex items-center gap-1 text-xs font-bold text-lab-700 hover:text-lab-900"
                        onClick={() => setIsQuickClientOpen(true)}
                        type="button"
                      >
                        <Plus size={14} />
                        Nuevo cliente
                      </button>
                    </div>
                    <select
                      id="wizard-client"
                      onChange={(event) => {
                        const client = clients.find((item) => item.id === event.target.value);
                        updateForm((current) => ({
                          ...current,
                          clientId: event.target.value,
                          reportName: current.reportName || client?.reportName || '',
                          reportEmail: current.reportEmail || client?.email || '',
                          reportAddress: current.reportAddress || client?.address || '',
                        }));
                      }}
                      required
                      value={form.clientId}
                    >
                      <option value="">Seleccionar cliente</option>
                      {clients.map((client) => (
                        <option key={client.id} value={client.id}>
                          {client.legalName}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div className="space-y-2">
                    <label htmlFor="wizard-area">Área de laboratorio</label>
                    <select
                      id="wizard-area"
                      disabled={isCorrection}
                      onChange={(event) =>
                        updateForm((current) => ({
                          ...current,
                          laboratoryAreaId: event.target.value,
                          samples: current.samples.map((sample) => ({
                            ...sample,
                            serviceId: '',
                            requestedTests: [],
                          })),
                        }))
                      }
                      required
                      value={form.laboratoryAreaId}
                    >
                      <option value="">Seleccionar área</option>
                      {catalog?.areas.map((area) => (
                        <option key={area.id} value={area.id}>
                          {area.name}
                        </option>
                      ))}
                    </select>
                    {isCorrection ? (
                      <p className="text-xs text-slate-500">
                        El área técnica se conserva para no romper las órdenes de trabajo emitidas.
                      </p>
                    ) : null}
                  </div>
                  <fieldset className="grid gap-5 rounded-2xl border border-slate-200 p-4 md:col-span-2 md:grid-cols-2">
                    <legend className="px-2 text-sm font-bold text-slate-800">
                      Datos comerciales e informe
                    </legend>
                    <CommercialDetailsFields onChange={updateCommercialField} values={form} />
                  </fieldset>
                </>
              )}
            </div>
          ) : null}

          {step === 2 && catalog ? (
            <div className="space-y-5">
              {form.samples.map((sample, index) => (
                <SampleTraceabilityFields
                  catalog={catalog}
                  index={index}
                  isRemovable={form.samples.length > 1}
                  key={sample.clientKey}
                  onChange={(field, value) => updateSample(index, field, value)}
                  onRemove={() => removeSample(index)}
                  producers={producers}
                  sample={sample}
                />
              ))}
              <Button onClick={addSample} variant="secondary">
                <Plus size={16} />
                Agregar muestra
              </Button>
            </div>
          ) : null}

          {step === 3 && catalog ? (
            <div className="space-y-5">
              {form.samples.map((sample, index) => (
                <SampleTestAssignment
                  catalog={catalog}
                  index={index}
                  key={sample.clientKey}
                  laboratoryAreaId={form.laboratoryAreaId}
                  onMethodChange={(testDefinitionId, testMethodId) =>
                    changeMethod(index, testDefinitionId, testMethodId)
                  }
                  onServiceChange={(serviceId) => changeService(index, serviceId)}
                  onTestToggle={(testDefinitionId) => toggleTest(index, testDefinitionId)}
                  sample={sample}
                />
              ))}
            </div>
          ) : null}

          {validationError ? (
            <p className="mt-5 rounded-xl bg-rose-50 p-3 text-sm text-rose-700">
              {validationError}
            </p>
          ) : null}

          {correctionImpact?.requiresResultInvalidation ? (
            <section className="mt-5 rounded-2xl border border-amber-300 bg-amber-50 p-4 text-sm text-amber-950">
              <p className="font-bold">Confirmación técnica necesaria</p>
              <p className="mt-2 leading-6">{correctionImpact.warningMessage}</p>
              <ul className="mt-3 space-y-1 pl-5 text-xs">
                {correctionImpact.affectedItems.map((item) => (
                  <li key={`${item.sourceRequestedTestId}-${item.reason}`}>
                    {item.sampleLabel}: {item.testName} · {item.reason}
                  </li>
                ))}
              </ul>
              <p className="mt-3 text-xs font-semibold">
                Al confirmar, los resultados anteriores quedarán conservados como históricos e
                invalidados; nunca se eliminarán de la auditoría.
              </p>
            </section>
          ) : null}

          <ModalFooter className="justify-between">
            <Button
              disabled={step === 1}
              onClick={() => setStep((current) => current - 1)}
              variant="secondary"
            >
              <ChevronLeft size={16} />
              Anterior
            </Button>
            {step < 3 ? (
              <Button onClick={goNext} type="button">
                Siguiente
                <ChevronRight size={16} />
              </Button>
            ) : (
              <Button disabled={isSaving} type="submit">
                {isSaving
                  ? 'Guardando…'
                  : isCorrection
                    ? correctionImpact?.requiresResultInvalidation
                      ? 'Confirmar cambios e invalidación'
                      : 'Guardar corrección'
                    : isEditingSamples
                      ? 'Guardar muestras y ensayos'
                    : 'Registrar orden'}
              </Button>
            )}
          </ModalFooter>
        </form>
      </Modal>
      <ClientFormModal
        allowedParticipantTypes={['CLIENT', 'BOTH']}
        client={null}
        isOpen={isQuickClientOpen}
        isSaving={isCreatingClient}
        onClose={() => setIsQuickClientOpen(false)}
        onSubmit={async (_clientId, input) => saveQuickClient(input)}
      />
    </>
  );
}
