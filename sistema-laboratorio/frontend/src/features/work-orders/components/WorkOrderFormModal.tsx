import { CheckSquare, Fingerprint, LockKeyhole, UsersRound } from 'lucide-react';
import { useMemo, useState, type FormEvent } from 'react';
import { Button, Modal, ModalFooter } from '../../../shared/components/ui';
import type {
  EligibleServiceOrderDetail,
  EligibleWorkOrderSample,
  EligibleWorkOrderTest,
  SampleType,
  UserSummary,
} from '../../../shared/types/domain';
import type { CreateWorkOrderInput } from '../api/work-orders-api';

interface WorkOrderFormModalProps {
  analysts: UserSummary[];
  eligibleServiceOrders: Array<{
    id: string;
    code: string;
    pendingSampleCount: number;
    pendingTestCount: number;
    plannedDeliveryAt: string;
  }>;
  isOpen: boolean;
  isSaving: boolean;
  onClose: () => void;
  onLoadEligibleSource: (orderId: string) => Promise<EligibleServiceOrderDetail | null>;
  onSubmit: (input: CreateWorkOrderInput) => Promise<boolean>;
  responsibleUsers: UserSummary[];
  sampleTypes: SampleType[];
}

interface TestAssignmentSelection {
  analystId: string;
  selected: boolean;
}

function defaultDueDate(): string {
  return new Date(Date.now() + 2 * 24 * 60 * 60 * 1000).toISOString().slice(0, 16);
}

function toLocalDateTime(value: string): string {
  const date = new Date(value);
  const localDate = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return localDate.toISOString().slice(0, 16);
}

function testsForArea(sourceOrder: EligibleServiceOrderDetail, areaId: string) {
  return sourceOrder.samples.flatMap((sample) =>
    sample.pendingTests
      .filter((test) => test.laboratoryAreaId === areaId)
      .map((test) => ({ sample, test })),
  );
}

function samplesForArea(
  sourceOrder: EligibleServiceOrderDetail,
  areaId: string,
): EligibleWorkOrderSample[] {
  return sourceOrder.samples.filter((sample) =>
    sample.pendingTests.some((test) => test.laboratoryAreaId === areaId),
  );
}

function initialClassifications(
  sourceOrder: EligibleServiceOrderDetail,
  areaId: string,
): Record<string, string> {
  return Object.fromEntries(
    samplesForArea(sourceOrder, areaId).map((sample) => [
      sample.sourceSampleId,
      sample.technicalSampleTypeId ?? sample.declaredSampleTypeId,
    ]),
  );
}

function initialAssignments(
  sourceOrder: EligibleServiceOrderDetail,
  areaId: string,
  analystId = '',
): Record<string, TestAssignmentSelection> {
  return Object.fromEntries(
    testsForArea(sourceOrder, areaId).map(({ test }) => [
      test.sourceSampleTestId,
      { analystId, selected: true },
    ]),
  );
}

function isUserAvailableForArea(user: UserSummary, areaId: string): boolean {
  return user.roles.includes('ADMIN') || user.laboratoryAreaId === areaId;
}

export function WorkOrderFormModal({
  analysts,
  eligibleServiceOrders,
  isOpen,
  isSaving,
  onClose,
  onLoadEligibleSource,
  onSubmit,
  responsibleUsers,
  sampleTypes,
}: WorkOrderFormModalProps) {
  const [sourceOrder, setSourceOrder] = useState<EligibleServiceOrderDetail | null>(null);
  const [sourceOrderId, setSourceOrderId] = useState('');
  const [laboratoryAreaId, setLaboratoryAreaId] = useState('');
  const [responsibleId, setResponsibleId] = useState('');
  const [defaultAnalystId, setDefaultAnalystId] = useState('');
  const [assignmentsByTest, setAssignmentsByTest] = useState<
    Record<string, TestAssignmentSelection>
  >({});
  const [technicalTypeBySample, setTechnicalTypeBySample] = useState<Record<string, string>>({});
  const [laboratoryDueAt, setLaboratoryDueAt] = useState(defaultDueDate);
  const [validationError, setValidationError] = useState<string | null>(null);

  const pendingTests = useMemo(
    () => (sourceOrder ? testsForArea(sourceOrder, laboratoryAreaId) : []),
    [laboratoryAreaId, sourceOrder],
  );
  const availableAnalysts = analysts.filter((analyst) =>
    isUserAvailableForArea(analyst, laboratoryAreaId),
  );
  const pendingSamples = useMemo(
    () => (sourceOrder ? samplesForArea(sourceOrder, laboratoryAreaId) : []),
    [laboratoryAreaId, sourceOrder],
  );
  const availableResponsibleUsers = responsibleUsers.filter((user) =>
    isUserAvailableForArea(user, laboratoryAreaId),
  );

  function closeModal() {
    setSourceOrder(null);
    setSourceOrderId('');
    setLaboratoryAreaId('');
    setResponsibleId('');
    setDefaultAnalystId('');
    setAssignmentsByTest({});
    setTechnicalTypeBySample({});
    setLaboratoryDueAt(defaultDueDate());
    setValidationError(null);
    onClose();
  }

  async function selectSourceOrder(orderId: string) {
    setSourceOrderId(orderId);
    setSourceOrder(null);
    setLaboratoryAreaId('');
    setAssignmentsByTest({});
    setTechnicalTypeBySample({});
    setResponsibleId('');
    setDefaultAnalystId('');
    setValidationError(null);

    if (!orderId) {
      return;
    }

    const loadedOrder = await onLoadEligibleSource(orderId);
    if (!loadedOrder) {
      return;
    }

    const initialAreaId = loadedOrder.laboratoryAreas[0]?.id ?? '';
    setSourceOrder(loadedOrder);
    setLaboratoryAreaId(initialAreaId);
    setAssignmentsByTest(initialAssignments(loadedOrder, initialAreaId));
    setTechnicalTypeBySample(initialClassifications(loadedOrder, initialAreaId));
  }

  function selectLaboratoryArea(areaId: string) {
    if (!sourceOrder) {
      return;
    }

    setLaboratoryAreaId(areaId);
    setResponsibleId('');
    setDefaultAnalystId('');
    setAssignmentsByTest(initialAssignments(sourceOrder, areaId));
    setTechnicalTypeBySample(initialClassifications(sourceOrder, areaId));
  }

  function applyDefaultAnalyst(analystId: string) {
    setDefaultAnalystId(analystId);
    setAssignmentsByTest((current) =>
      Object.fromEntries(
        Object.entries(current).map(([testId, assignment]) => [
          testId,
          assignment.selected ? { ...assignment, analystId } : assignment,
        ]),
      ),
    );
  }

  function toggleTest(test: EligibleWorkOrderTest) {
    setAssignmentsByTest((current) => {
      const assignment = current[test.sourceSampleTestId] ?? {
        analystId: defaultAnalystId,
        selected: false,
      };
      return {
        ...current,
        [test.sourceSampleTestId]: { ...assignment, selected: !assignment.selected },
      };
    });
  }

  function assignAnalyst(testId: string, analystId: string) {
    setAssignmentsByTest((current) => ({
      ...current,
      [testId]: { ...current[testId], analystId, selected: true },
    }));
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!sourceOrder || !laboratoryAreaId || !responsibleId) {
      setValidationError('Selecciona la orden de servicio, el área técnica y el responsable.');
      return;
    }
    if (new Date(laboratoryDueAt).getTime() > new Date(sourceOrder.plannedDeliveryAt).getTime()) {
      setValidationError(
        'La fecha interna no puede ser posterior a la fecha prometida al cliente.',
      );
      return;
    }

    const assignments = pendingTests
      .filter(({ test }) => assignmentsByTest[test.sourceSampleTestId]?.selected)
      .map(({ test }) => ({
        sourceSampleTestId: test.sourceSampleTestId,
        analystId: assignmentsByTest[test.sourceSampleTestId]?.analystId ?? '',
      }));
    if (assignments.length === 0) {
      setValidationError('Selecciona al menos un ensayo para emitir la orden técnica.');
      return;
    }
    if (assignments.some((assignment) => !assignment.analystId)) {
      setValidationError('Asigna un analista a cada ensayo seleccionado.');
      return;
    }

    const selectedSampleIds = new Set(
      pendingTests
        .filter(({ test }) => assignmentsByTest[test.sourceSampleTestId]?.selected)
        .map(({ sample }) => sample.sourceSampleId),
    );
    const sampleClassifications = [...selectedSampleIds].map((sourceSampleId) => ({
      sourceSampleId,
      technicalSampleTypeId: technicalTypeBySample[sourceSampleId] ?? '',
    }));
    if (sampleClassifications.some((classification) => !classification.technicalSampleTypeId)) {
      setValidationError('Confirma el tipo técnico de cada muestra incluida en la OT.');
      return;
    }

    const wasSaved = await onSubmit({
      serviceOrderId: sourceOrder.id,
      laboratoryAreaId,
      responsibleId,
      laboratoryDueAt: new Date(laboratoryDueAt).toISOString(),
      sampleClassifications,
      assignments,
    });
    if (wasSaved) {
      closeModal();
    }
  }

  return (
    <Modal
      description="Selecciona los ensayos pendientes que procesará esta orden. Los ensayos restantes pueden emitirse en otra OT."
      isOpen={isOpen}
      onClose={closeModal}
      title="Emitir orden de trabajo"
    >
      <form className="space-y-5" onSubmit={handleSubmit}>
        <div className="grid gap-5 md:grid-cols-2">
          <div className="space-y-2 md:col-span-2">
            <label htmlFor="work-order-source">Orden de servicio elegible</label>
            <select
              id="work-order-source"
              onChange={(event) => void selectSourceOrder(event.target.value)}
              required
              value={sourceOrderId}
            >
              <option value="">Seleccionar orden</option>
              {eligibleServiceOrders.map((order) => (
                <option key={order.id} value={order.id}>
                  {order.code} · {order.pendingSampleCount} muestra(s) · {order.pendingTestCount}{' '}
                  ensayo(s) pendiente(s)
                </option>
              ))}
            </select>
            {eligibleServiceOrders.length === 0 ? (
              <p className="text-xs text-slate-500">
                No hay órdenes en proceso con ensayos pendientes de asignación.
              </p>
            ) : null}
          </div>

          <div className="space-y-2">
            <label htmlFor="work-order-area">Área técnica</label>
            <select
              disabled={!sourceOrder}
              id="work-order-area"
              onChange={(event) => selectLaboratoryArea(event.target.value)}
              required
              value={laboratoryAreaId}
            >
              <option value="">Seleccionar área</option>
              {sourceOrder?.laboratoryAreas.map((area) => (
                <option key={area.id} value={area.id}>
                  {area.name} · {area.pendingTestCount} ensayo(s)
                </option>
              ))}
            </select>
          </div>

          <div className="space-y-2">
            <label htmlFor="work-order-due">Fecha límite de laboratorio</label>
            <input
              id="work-order-due"
              max={sourceOrder ? toLocalDateTime(sourceOrder.plannedDeliveryAt) : undefined}
              onChange={(event) => setLaboratoryDueAt(event.target.value)}
              required
              type="datetime-local"
              value={laboratoryDueAt}
            />
            {sourceOrder ? (
              <p className="text-xs text-slate-500">
                Entrega prometida: {new Date(sourceOrder.plannedDeliveryAt).toLocaleString('es-PE')}
              </p>
            ) : null}
          </div>

          <div className="space-y-2">
            <label htmlFor="work-order-responsible">Responsable técnico</label>
            <select
              disabled={!laboratoryAreaId}
              id="work-order-responsible"
              onChange={(event) => setResponsibleId(event.target.value)}
              required
              value={responsibleId}
            >
              <option value="">Seleccionar responsable</option>
              {availableResponsibleUsers.map((user) => (
                <option key={user.id} value={user.id}>
                  {user.fullName}
                </option>
              ))}
            </select>
          </div>

          <div className="space-y-2">
            <label htmlFor="work-order-default-analyst">Analista predeterminado</label>
            <select
              disabled={!laboratoryAreaId}
              id="work-order-default-analyst"
              onChange={(event) => applyDefaultAnalyst(event.target.value)}
              value={defaultAnalystId}
            >
              <option value="">Asignar por ensayo</option>
              {availableAnalysts.map((user) => (
                <option key={user.id} value={user.id}>
                  {user.fullName}
                </option>
              ))}
            </select>
          </div>
        </div>

        {sourceOrder ? (
          <section className="overflow-hidden rounded-2xl border border-lab-100 bg-lab-50/30">
            <header className="flex items-center gap-3 border-b border-lab-100 bg-white px-4 py-3">
              <span className="grid size-8 place-items-center rounded-lg bg-lab-50 text-lab-700">
                <Fingerprint size={16} />
              </span>
              <div>
                <h3 className="text-sm font-bold text-slate-900">
                  Clasificación e identidad técnica
                </h3>
                <p className="mt-0.5 text-xs text-slate-500">
                  Confirma el tipo real antes de generar el código interno de laboratorio.
                </p>
              </div>
            </header>
            <div className="grid gap-3 p-4 lg:grid-cols-2">
              {pendingSamples.map((sample) => (
                <article
                  className="rounded-xl border border-slate-200 bg-white p-4"
                  key={sample.sourceSampleId}
                >
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <p className="text-sm font-bold text-slate-900">
                        Muestra comercial {sample.commercialSequence}
                      </p>
                      <p className="mt-1 text-xs text-slate-500">
                        Tipo declarado: {sample.declaredSampleTypeName}
                      </p>
                    </div>
                    {sample.laboratoryCode ? (
                      <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-bold text-emerald-700">
                        <LockKeyhole size={13} />
                        {sample.laboratoryCode}
                      </span>
                    ) : null}
                  </div>
                  <label
                    className="mt-3 block text-xs font-bold text-slate-700"
                    htmlFor={`technical-type-${sample.sourceSampleId}`}
                  >
                    Tipo técnico confirmado
                  </label>
                  <select
                    disabled={Boolean(sample.technicalSampleTypeId)}
                    id={`technical-type-${sample.sourceSampleId}`}
                    onChange={(event) =>
                      setTechnicalTypeBySample((current) => ({
                        ...current,
                        [sample.sourceSampleId]: event.target.value,
                      }))
                    }
                    required
                    value={technicalTypeBySample[sample.sourceSampleId] ?? ''}
                  >
                    <option value="">Seleccionar tipo técnico</option>
                    {sample.technicalSampleTypeId &&
                    !sampleTypes.some(
                      (sampleType) => sampleType.id === sample.technicalSampleTypeId,
                    ) ? (
                      <option value={sample.technicalSampleTypeId}>
                        {sample.technicalSampleTypeName} · clasificación conservada
                      </option>
                    ) : null}
                    {sampleTypes.map((sampleType) => (
                      <option key={sampleType.id} value={sampleType.id}>
                        {sampleType.name} · prefijo {sampleType.laboratoryCodePrefix}
                      </option>
                    ))}
                  </select>
                  <p className="mt-2 text-xs leading-5 text-slate-500">
                    {sample.technicalSampleTypeId
                      ? 'Clasificación ya fijada; se reutilizará el mismo código en futuras OT.'
                      : 'El código se asignará al emitir y quedará bloqueado para esta muestra.'}
                  </p>
                </article>
              ))}
            </div>
          </section>
        ) : null}

        {sourceOrder ? (
          <section className="overflow-hidden rounded-2xl border border-slate-200">
            <header className="flex items-center gap-3 bg-slate-50 px-4 py-3">
              <span className="grid size-8 place-items-center rounded-lg bg-lab-50 text-lab-700">
                <UsersRound size={16} />
              </span>
              <div>
                <h3 className="text-sm font-bold text-slate-900">Asignaciones por ensayo</h3>
                <p className="mt-0.5 text-xs text-slate-500">
                  Cada selección se convertirá en una asignación técnica independiente.
                </p>
              </div>
            </header>
            <div className="divide-y divide-slate-100">
              {pendingTests.map(({ sample, test }) => {
                const assignment = assignmentsByTest[test.sourceSampleTestId];
                return (
                  <article
                    className="grid gap-3 px-4 py-4 md:grid-cols-[minmax(0,1fr)_minmax(12rem,0.7fr)]"
                    key={test.sourceSampleTestId}
                  >
                    <label className="flex cursor-pointer items-start gap-3">
                      <input
                        checked={assignment?.selected ?? false}
                        onChange={() => toggleTest(test)}
                        type="checkbox"
                      />
                      <span>
                        <span className="flex items-center gap-1.5 text-sm font-bold text-slate-800">
                          <CheckSquare className="text-lab-700" size={15} />
                          Muestra {sample.commercialSequence} · {test.testName}
                        </span>
                        <span className="mt-1 block text-xs text-slate-500">
                          {test.serviceName} · {test.testMethodName}
                        </span>
                      </span>
                    </label>
                    <select
                      aria-label={`Analista para ${test.testName}`}
                      disabled={!assignment?.selected}
                      onChange={(event) =>
                        assignAnalyst(test.sourceSampleTestId, event.target.value)
                      }
                      value={assignment?.analystId ?? ''}
                    >
                      <option value="">Seleccionar analista</option>
                      {availableAnalysts.map((user) => (
                        <option key={user.id} value={user.id}>
                          {user.fullName}
                        </option>
                      ))}
                    </select>
                  </article>
                );
              })}
              {pendingTests.length === 0 ? (
                <p className="px-4 py-5 text-sm text-slate-500">
                  No hay ensayos pendientes para el área seleccionada.
                </p>
              ) : null}
            </div>
          </section>
        ) : null}

        {validationError ? (
          <p className="rounded-xl bg-rose-50 p-3 text-sm text-rose-700">{validationError}</p>
        ) : null}

        <ModalFooter>
          <Button onClick={closeModal} variant="secondary">
            Cancelar
          </Button>
          <Button disabled={isSaving || pendingTests.length === 0} type="submit">
            {isSaving ? 'Emitiendo…' : 'Emitir orden técnica'}
          </Button>
        </ModalFooter>
      </form>
    </Modal>
  );
}
