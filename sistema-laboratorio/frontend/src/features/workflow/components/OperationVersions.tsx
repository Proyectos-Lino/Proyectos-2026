import { useEffect, useState } from 'react';
import { getOperationVersions } from '../api/workflow-api';
import { Button, Pagination } from '../../../shared/components/ui';
import { formatDate } from '../../../shared/lib/format';
import { getErrorMessage } from '../../../shared/lib/errors';
import { labelForAction, labelForSystemCode } from '../../../shared/lib/labels';
import type { PageResponse } from '../../../shared/types/domain';

interface Version {
  id: string;
  revision: number;
  action: string;
  reason?: string;
  performedBy: string;
  performedByRole: string;
  capturedAt: string;
  snapshot: unknown;
}
interface Props {
  type: 'SERVICE_ORDER' | 'WORK_ORDER' | 'RESULT';
  id: string;
  refreshKey?: string | number;
}

const labels: Record<string, string> = {
  requestedTests: 'Ensayos solicitados',
  lastRecordedByName: 'Última corrección registrada por',
  lastRecordedAt: 'Fecha de la última corrección',
  sampleTypeName: 'Tipo de muestra',
  measurementUnitName: 'Unidad de medida',
  technicalIdentification: 'Identificación técnica',
  classifiedBy: 'Clasificado por',
  classifiedAt: 'Fecha de clasificación',
  serviceName: 'Servicio',
  carrierLabel: 'Cargador',
  executionStatus: 'Estado de ejecución',
  categorySummary: 'Resumen por categoría',
  calculationSnapshots: 'Cálculos conservados',
  calculatedValue: 'Valor calculado',
  calculatedAt: 'Fecha de cálculo',
  calculationInputs: 'Entradas utilizadas en el cálculo',
  calculatedByRole: 'Rol que calculó',
  code: 'Código',
  status: 'Estado',
  clientName: 'Cliente',
  clientDocument: 'Documento',
  laboratoryAreaName: 'Área',
  reportName: 'Nombre en el informe',
  reportEmail: 'Correo del informe',
  reportAddress: 'Dirección del informe',
  deliveryMethod: 'Entrega',
  pickupPerson: 'Persona que recoge',
  plannedDeliveryAt: 'Entrega programada',
  proformaNumber: 'Proforma',
  technicalProposalNumber: 'Propuesta técnica',
  developmentDays: 'Días de desarrollo',
  outsourced: 'Subcontratado',
  postAnalysisCustody: 'Custodia',
  generalObservations: 'Observaciones generales',
  responsibleName: 'Responsable técnico',
  laboratoryDueAt: 'Fecha técnica',
  serviceOrderCode: 'Orden de servicio',
  samples: 'Muestras',
  commercialSequence: 'Número comercial',
  clientIdentification: 'Identificación del cliente',
  producerName: 'Productor',
  productName: 'Producto',
  varietyName: 'Variedad',
  physicalDescription: 'Descripción física',
  samplingLocation: 'Lugar de muestreo',
  origin: 'Procedencia',
  samplerName: 'Muestreador',
  sampledAt: 'Fecha de muestreo',
  quantity: 'Cantidad',
  containerDescription: 'Envase',
  batchNumber: 'Lote',
  productionYear: 'Año de producción',
  brand: 'Marca',
  cropAge: 'Edad del cultivo',
  carrierCount: 'Cargadores',
  budCount: 'Yemas',
  counterSampleInCustody: 'Contramuestra en custodia',
  observations: 'Observaciones',
  laboratoryCode: 'Código interno',
  technicalSampleTypeName: 'Clasificación técnica',
  tests: 'Ensayos',
  testName: 'Ensayo',
  testMethodName: 'Método',
  assignedAnalystName: 'Analista asignado',
  analystObservation: 'Observación técnica',
  recordedByName: 'Registrado por',
  recordedAt: 'Fecha de registro',
  resultRevision: 'Versión del resultado',
  resultMode: 'Estructura',
  values: 'Valores',
  label: 'Campo',
  valueText: 'Valor',
  valueNumber: 'Valor numérico',
  fieldKey: 'Clave del campo',
  repetitionIndex: 'Índice de lectura',
  fieldKind: 'Comportamiento',
  formulaUsed: 'Fórmula',
  matrix: 'Matriz',
  rows: 'Filas',
  columns: 'Cargadores',
  cells: 'Celdas',
  rowIndex: 'Índice de fila (desde cero)',
  columnIndex: 'Índice de cargador (desde cero)',
  categoryCode: 'Categoría',
  categoryLabel: 'Categoría',
  distance: 'Distancia',
  diameter: 'Diámetro',
  shape: 'Forma',
  rowCount: 'Cantidad de yemas',
  columnCount: 'Cantidad de cargadores',
  execution: 'Ejecución',
  startedAt: 'Inicio',
  completedAt: 'Término',
  completionPercentage: 'Avance (%)',
  correctionRequestReason: 'Motivo de devolución',
  correctionReason: 'Motivo de corrección',
  correctionChangeNote: 'Cambios realizados',
  correctionImpactSummary: 'Impacto técnico',
  correctionRequestedAt: 'Fecha de devolución',
  correctionRequestedByName: 'Devuelto por',
  validation: 'Validación',
  validatorName: 'Validado por',
  validatedAt: 'Fecha de validación',
  comment: 'Comentario',
  createdAt: 'Creación',
  updatedAt: 'Última actualización',
  name: 'Nombre',
  title: 'Nombre',
  declaredRowCount: 'Yemas declaradas',
  declaredColumnCount: 'Cargadores declarados',
  lastModifiedAt: 'Última modificación',
  lastModifiedByName: 'Modificado por',
  filledRequiredCells: 'Celdas completas',
  requiredCellCount: 'Celdas requeridas',
  statistics: 'Estadísticas',
  count: 'Cantidad',
  percentage: 'Porcentaje',
  categories: 'Categorías',
  columnLabel: 'Cargador',
  startedByName: 'Iniciado por',
  completedByName: 'Finalizado por',
  correctionSubmittedBy: 'Corrección enviada por',
  correctionReviewedBy: 'Revisado por',
  correctionSubmittedAt: 'Fecha de envío',
  correctionReviewedAt: 'Fecha de revisión',
  confirmedAt: 'Confirmación',
  finalizedAt: 'Finalización',
  cancelledAt: 'Anulación',
  cancellationReason: 'Motivo de anulación',
  closedAt: 'Cierre',
  sanitaryRegistration: 'Registro sanitario',
  originCertificate: 'Certificado de origen',
  technicalProgress: 'Avance técnico',
  totalRequestedTests: 'Ensayos solicitados',
  validatedTests: 'Ensayos validados',
  pendingAssignmentTests: 'Ensayos por asignar',
  pendingValidationTests: 'Ensayos por validar',
  totalWorkOrders: 'Órdenes de trabajo',
  openWorkOrders: 'Órdenes abiertas',
};

function Snapshot({ value }: { value: unknown }) {
  if (value === null || value === undefined) return <span>—</span>;
  if (Array.isArray(value))
    return (
      <div className="space-y-3">
        {value.map((item, index) => (
          <div key={index} className="rounded-lg border border-slate-200 p-3">
            <Snapshot value={item} />
          </div>
        ))}
      </div>
    );
  if (typeof value === 'object')
    return (
      <dl className="grid gap-3 sm:grid-cols-2">
        {Object.entries(value)
          .filter(([key, item]) => labels[key] && item !== null)
          .map(([key, item]) => (
            <div
              className={typeof item === 'object' ? 'min-w-0 sm:col-span-2' : 'min-w-0'}
              key={key}
            >
              <dt className="mb-1 text-xs font-semibold text-slate-500">{labels[key]}</dt>
              <dd className="break-words text-sm text-slate-800">
                <Snapshot value={item} />
              </dd>
            </div>
          ))}
      </dl>
    );
  if (typeof value === 'boolean') return <span>{value ? 'Sí' : 'No'}</span>;
  if (typeof value === 'string' && /^\d{4}-\d\d-\d\dT/.test(value))
    return <span>{formatDate(value)}</span>;
  return (
    <span>
      {typeof value === 'string' && /^[A-Z_]+$/.test(value)
        ? labelForSystemCode(value)
        : String(value)}
    </span>
  );
}

export function OperationVersions({ type, id, refreshKey }: Props) {
  const [open, setOpen] = useState(false);
  const [page, setPage] = useState(0);
  const [data, setData] = useState<PageResponse<Version> | null>(null);
  const [selected, setSelected] = useState<Version | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  useEffect(() => {
    if (!open) return;
    let active = true;
    void getOperationVersions(type, id, page)
      .then((result) => {
        if (active) {
          setData(result);
          setError(null);
        }
      })
      .catch((reason) => {
        if (active) setError(getErrorMessage(reason, 'No se pudieron consultar las versiones.'));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [open, type, id, page, refreshKey]);
  return (
    <section className="space-y-3 rounded-xl border border-slate-200 p-4">
      <Button
        variant="ghost"
        onClick={() => {
          if (!open) setLoading(true);
          setOpen((current) => !current);
        }}
      >
        Historial de versiones · solo consulta
      </Button>
      {open ? (
        <>
          {loading ? <p className="text-sm text-slate-500">Cargando versiones…</p> : null}
          {error ? (
            <p role="alert" className="text-sm text-rose-700">
              {error}
            </p>
          ) : null}
          {data?.content.map((version) => (
            <article key={version.id} className="space-y-2 rounded-lg bg-slate-50 p-3 text-sm">
              <p className="font-semibold">
                Versión conservada {version.revision} · {labelForAction(version.action)}
              </p>
              <p className="text-xs text-slate-600">
                {version.performedBy} ·{' '}
                {version.performedByRole.split(',').map(labelForSystemCode).join(', ')} ·{' '}
                {formatDate(version.capturedAt)}
              </p>
              {version.reason ? <p className="text-slate-700">{version.reason}</p> : null}
              <Button
                variant="secondary"
                onClick={() =>
                  setSelected((current) => (current?.id === version.id ? null : version))
                }
              >
                Ver esta versión
              </Button>
              {selected?.id === version.id ? (
                <div className="space-y-3 rounded-lg border border-slate-200 bg-white p-4">
                  <p className="text-xs font-semibold text-lab-800">
                    Copia histórica de solo lectura. La operación vigente se modifica desde su flujo
                    de trabajo.
                  </p>
                  <Snapshot value={selected.snapshot} />
                </div>
              ) : null}
            </article>
          ))}
          {data && !data.content.length ? (
            <p className="text-sm text-slate-500">
              Las versiones se conservarán a partir del próximo avance o corrección.
            </p>
          ) : null}
          {data && data.totalPages > 1 ? (
            <Pagination
              page={data.page}
              totalPages={data.totalPages}
              totalElements={data.totalElements}
              onPageChange={(next) => {
                setLoading(true);
                setPage(next);
                setSelected(null);
              }}
            />
          ) : null}
        </>
      ) : null}
    </section>
  );
}
