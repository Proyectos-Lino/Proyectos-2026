export type RoleCode = 'ADMIN' | 'COMMERCIAL' | 'TECHNICAL_MANAGER' | 'ANALYST' | 'VALIDATOR';

export type DocumentType = 'RUC' | 'DNI' | 'PASSPORT' | 'OTHER';

export type ParticipantType = 'CLIENT' | 'PRODUCER' | 'BOTH';

export interface OrganizationContact {
  id?: string;
  type: 'EMAIL' | 'PHONE';
  label?: string;
  value: string;
  displayOrder?: number;
}

export interface OrganizationSigner {
  id: string;
  fullName: string;
  email: string;
  displayOrder: number;
}

export interface OrganizationIdentity {
  commercialName: string;
  legalName: string;
  documentLabel?: string;
  address?: string;
  logoDataUrl?: string;
  primaryColor: string;
  secondaryColor: string;
  timeZone: string;
  legalText?: string;
  accreditationName?: string;
  accreditationCode?: string;
  accreditationScope?: string;
  contacts: OrganizationContact[];
  authorizedSigners: OrganizationSigner[];
}

export interface OrganizationBranding {
  commercialName: string;
  logoDataUrl?: string;
  primaryColor: string;
  secondaryColor: string;
  timeZone: string;
}

export interface OrganizationProfile
  extends Omit<OrganizationIdentity, 'documentLabel' | 'authorizedSigners'>, OrganizationBranding {
  id: string;
  documentType?: string;
  documentNumber?: string;
  serviceOrderPrefix: string;
  workOrderPrefix: string;
  reportPrefix: string;
  authorizedSigners: OrganizationSigner[];
  updatedAt: string;
}

export interface CurrentUser {
  id: string;
  fullName: string;
  email: string;
  roles: RoleCode[];
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  user: CurrentUser;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Client {
  id: string;
  participantType: ParticipantType;
  documentType: DocumentType;
  documentNumber: string;
  legalName: string;
  reportName?: string;
  email?: string;
  address?: string;
  contactName?: string;
  phone?: string;
  active: boolean;
  createdAt: string;
}

export interface CatalogItem {
  id: string;
  code: string;
  name: string;
}

export interface LaboratoryArea extends CatalogItem {
  active: boolean;
  createdAt: string;
}

export type LaboratoryAreaOption = CatalogItem;

export interface SampleType extends CatalogItem {
  laboratoryCodePrefix: string;
  resultMode: 'SCALAR' | 'REPEATABLE' | 'MATRIX';
}

export type Product = CatalogItem;

export interface ProductVariety extends CatalogItem {
  productId: string;
}

export interface Unit extends CatalogItem {
  symbol: string;
}

export interface TestDefinition extends CatalogItem {
  resultType: 'NUMERIC' | 'TEXT' | 'QUALITATIVE';
  resultUnitId?: string;
}

export interface LaboratoryService extends CatalogItem {
  laboratoryAreaId: string;
}

export interface TestMethod extends CatalogItem {
  testDefinitionId: string;
  versionLabel?: string;
}

export interface ServiceTest {
  serviceId: string;
  testDefinitionId: string;
  displayOrder: number;
  required: boolean;
}

export interface CatalogSnapshot {
  areas: CatalogItem[];
  sampleTypes: SampleType[];
  units: Unit[];
  tests: TestDefinition[];
  services: LaboratoryService[];
  methods: TestMethod[];
  serviceTests: ServiceTest[];
  products: Product[];
  varieties: ProductVariety[];
}

export type ServiceOrderStatus =
  | 'DRAFT'
  | 'IN_PROGRESS'
  | 'COMMERCIAL_CORRECTION'
  | 'CORRECTION_REVIEW'
  | 'FINALIZED'
  | 'CANCELLED';

export type PostAnalysisCustody = 'RETAINED' | 'RETURNED_TO_CLIENT' | 'DISPOSED' | 'OTHER';

export interface ServiceOrderSummary {
  id: string;
  code: string;
  clientName: string;
  laboratoryAreaName: string;
  status: ServiceOrderStatus;
  plannedDeliveryAt: string;
  createdAt: string;
  proformaNumber?: string;
  outsourced: boolean;
  sampleCount: number;
}

export interface RequestedTest {
  id: string;
  serviceId: string;
  testDefinitionId: string;
  testMethodId: string;
  status: 'PENDING' | 'ASSIGNED' | 'COMPLETED' | 'CANCELLED';
}

export interface ServiceOrderTechnicalProgress {
  totalRequestedTests: number;
  assignedTests: number;
  validatedTests: number;
  pendingAssignmentTests: number;
  pendingValidationTests: number;
  totalWorkOrders: number;
  openWorkOrders: number;
  canFinalize: boolean;
  blockingReasons: string[];
}

export interface ServiceOrderSample {
  id: string;
  commercialSequence: number;
  clientIdentification?: string;
  producerId?: string;
  producerName?: string;
  producerDocument?: string;
  productName?: string;
  varietyName?: string;
  sampleTypeId: string;
  physicalDescription?: string;
  samplingLocation?: string;
  origin?: string;
  samplerName?: string;
  sampledAt?: string;
  quantity?: number;
  measurementUnitId?: string;
  containerDescription?: string;
  batchNumber?: string;
  productionYear?: number;
  brand?: string;
  originCertificate?: string;
  sanitaryRegistration?: string;
  cropAge?: string;
  carrierCount?: number;
  budCount?: number;
  counterSampleInCustody: boolean;
  observations?: string;
  technicalIdentification?: {
    id: string;
    technicalSampleTypeId: string;
    technicalSampleTypeName: string;
    laboratoryCode: string;
    classifiedBy: string;
    classifiedAt: string;
  };
  requestedTests: RequestedTest[];
}

export interface ServiceOrderDetail {
  id: string;
  code: string;
  clientId: string;
  clientName: string;
  clientDocument: string;
  laboratoryAreaId: string;
  laboratoryAreaName: string;
  status: ServiceOrderStatus;
  reportName?: string;
  reportEmail?: string;
  reportAddress?: string;
  deliveryMethod: 'PICKUP' | 'EMAIL';
  pickupPerson?: string;
  plannedDeliveryAt: string;
  proformaNumber?: string;
  technicalProposalNumber?: string;
  developmentDays?: number;
  outsourced: boolean;
  postAnalysisCustody: PostAnalysisCustody;
  generalObservations?: string;
  confirmedAt?: string;
  finalizedAt?: string;
  cancelledAt?: string;
  cancellationReason?: string;
  statusBeforeCorrection?: ServiceOrderStatus;
  correctionReason?: string;
  correctionRequestedBy?: string;
  correctionRequestedAt?: string;
  correctionSubmittedBy?: string;
  correctionSubmittedAt?: string;
  correctionChangeNote?: string;
  correctionReviewedBy?: string;
  correctionReviewedAt?: string;
  correctionImpactSummary?: string;
  correctionRequiresTechnicalWork: boolean;
  createdAt: string;
  updatedAt: string;
  samples: ServiceOrderSample[];
  technicalProgress: ServiceOrderTechnicalProgress;
}

export type ServiceOrderHistoryAction =
  | 'CREATED'
  | 'COMMERCIAL_DETAILS_UPDATED'
  | 'SAMPLES_UPDATED'
  | 'COMMERCIAL_CORRECTION_REQUESTED'
  | 'COMMERCIAL_CORRECTION_SUBMITTED'
  | 'COMMERCIAL_CORRECTION_APPROVED'
  | 'TECHNICAL_DATA_INVALIDATED'
  | 'CONFIRMED'
  | 'PROCESSING_STARTED'
  | 'FINALIZED'
  | 'CANCELLED';

export interface ServiceOrderHistoryEntry {
  id: string;
  action: ServiceOrderHistoryAction;
  previousStatus?: ServiceOrderStatus;
  newStatus?: ServiceOrderStatus;
  changeNote?: string;
  performedBy: string;
  occurredAt: string;
}

export interface ServiceOrderDocument {
  id: string;
  version: number;
  generatedAt: string;
  organization?: OrganizationIdentity;
  code: string;
  clientName: string;
  clientDocument: string;
  laboratoryAreaName: string;
  reportName?: string;
  reportEmail?: string;
  reportAddress?: string;
  deliveryMethod: 'PICKUP' | 'EMAIL';
  pickupPerson?: string;
  plannedDeliveryAt: string;
  proformaNumber?: string;
  technicalProposalNumber?: string;
  developmentDays?: number;
  outsourced: boolean;
  postAnalysisCustody: PostAnalysisCustody;
  generalObservations?: string;
  samples: ServiceOrderDocumentSample[];
}

export interface ServiceOrderDocumentSample {
  commercialSequence: number;
  clientIdentification?: string;
  producerName?: string;
  producerDocument?: string;
  productName: string;
  varietyName?: string;
  sampleTypeName: string;
  physicalDescription?: string;
  samplingLocation?: string;
  origin?: string;
  samplerName?: string;
  sampledAt?: string;
  quantity?: number;
  measurementUnitName?: string;
  containerDescription?: string;
  batchNumber?: string;
  productionYear?: number;
  brand?: string;
  originCertificate?: string;
  sanitaryRegistration?: string;
  cropAge?: string;
  carrierCount?: number;
  budCount?: number;
  counterSampleInCustody: boolean;
  observations?: string;
  requestedTests: Array<{
    serviceName: string;
    testName: string;
    methodName: string;
  }>;
}

export interface UserSummary {
  id: string;
  fullName: string;
  email: string;
  active: boolean;
  roles: RoleCode[];
  laboratoryAreaId?: string;
  laboratoryAreaName?: string;
}

export interface WorkOrderSummary {
  id: string;
  code: string;
  serviceOrderId: string;
  serviceOrderCode: string;
  laboratoryAreaId: string;
  laboratoryAreaName: string;
  responsibleId: string;
  responsibleName: string;
  laboratoryDueAt: string;
  status: WorkOrderStatus;
  createdAt: string;
  sampleCount: number;
}

export type WorkOrderStatus =
  | 'PENDING'
  | 'IN_PROGRESS'
  | 'RESULTS_RECORDED'
  | 'VALIDATED'
  | 'COMMERCIAL_CORRECTION'
  | 'CLOSED'
  | 'CANCELLED';

export type WorkOrderTestStatus =
  | 'PENDING'
  | 'RECORDED'
  | 'CORRECTION_REQUESTED'
  | 'CORRECTED'
  | 'VALIDATED'
  | 'INVALIDATED';

export interface WorkOrderTest {
  id: string;
  sourceSampleTestId: string;
  testDefinitionId: string;
  testName: string;
  testMethodId: string;
  testMethodName: string;
  assignedAnalystId: string;
  assignedAnalystName: string;
  status: WorkOrderTestStatus;
}

export interface WorkOrderSample {
  id: string;
  technicalSampleId: string;
  laboratoryCode: string;
  technicalSampleTypeId: string;
  technicalSampleTypeName: string;
  tests: WorkOrderTest[];
}

export interface WorkOrderDetail {
  id: string;
  code: string;
  serviceOrderId: string;
  serviceOrderCode: string;
  laboratoryAreaId: string;
  laboratoryAreaName: string;
  responsibleId: string;
  responsibleName: string;
  laboratoryDueAt: string;
  status: WorkOrderStatus;
  serviceOrderStatus: ServiceOrderStatus;
  serviceOrderCorrectionReason?: string;
  serviceOrderCorrectionChangeNote?: string;
  serviceOrderCorrectionImpactSummary?: string;
  serviceOrderCorrectionRequiresTechnicalWork: boolean;
  cancelledAt?: string;
  cancellationReason?: string;
  closedAt?: string;
  samples: WorkOrderSample[];
}

export interface EligibleServiceOrder {
  id: string;
  code: string;
  plannedDeliveryAt: string;
  pendingTestCount: number;
  pendingSampleCount: number;
}

export interface EligibleLaboratoryArea extends CatalogItem {
  pendingTestCount: number;
}

export interface EligibleWorkOrderTest {
  sourceSampleTestId: string;
  laboratoryAreaId: string;
  laboratoryAreaName: string;
  serviceName: string;
  testDefinitionId: string;
  testName: string;
  testMethodId: string;
  testMethodName: string;
}

export interface EligibleWorkOrderSample {
  sourceSampleId: string;
  commercialSequence: number;
  declaredSampleTypeId: string;
  declaredSampleTypeName: string;
  technicalSampleTypeId?: string;
  technicalSampleTypeName?: string;
  laboratoryCode?: string;
  pendingTests: EligibleWorkOrderTest[];
}

export interface EligibleServiceOrderDetail {
  id: string;
  code: string;
  plannedDeliveryAt: string;
  laboratoryAreas: EligibleLaboratoryArea[];
  samples: EligibleWorkOrderSample[];
}

export type WorkOrderHistoryAction =
  | 'CREATED'
  | 'SAMPLES_CLASSIFIED'
  | 'STATUS_UPDATED'
  | 'RESULT_RECORDED'
  | 'RESULT_CORRECTED'
  | 'RESULT_CORRECTION_REQUESTED'
  | 'RESULT_RESUBMITTED'
  | 'RESULT_CORRECTED_AND_VALIDATED'
  | 'RESULT_VALIDATED'
  | 'COMMERCIAL_CORRECTION_PAUSED'
  | 'COMMERCIAL_CORRECTION_RESUMED'
  | 'TECHNICAL_DATA_INVALIDATED'
  | 'CANCELLED'
  | 'CLOSED';

export interface WorkOrderHistoryEntry {
  id: string;
  action: WorkOrderHistoryAction;
  previousStatus?: WorkOrderStatus;
  newStatus?: WorkOrderStatus;
  changeNote?: string;
  performedBy: string;
  occurredAt: string;
}

export interface WorkOrderDocument {
  id: string;
  version: number;
  generatedAt: string;
  organization?: OrganizationIdentity;
  code: string;
  serviceOrderCode?: string;
  laboratoryAreaName: string;
  responsibleName: string;
  laboratoryDueAt: string;
  samples: Array<{
    laboratoryCode: string;
    sampleTypeName: string;
    tests: Array<{
      testName: string;
      methodName: string;
      analystName: string;
    }>;
  }>;
}

export interface ResultValue {
  id: string;
  fieldKey: string;
  label: string;
  position: number;
  repetitionIndex: number;
  valueText?: string;
  valueNumber?: number;
  measurementUnitId?: string;
  fieldKind: 'CAPTURED' | 'CALCULATED' | 'INFORMATIONAL';
  formulaUsed?: string;
  calculationInputs?: string;
}

export interface ResultTemplateField {
  id: string;
  fieldKey: string;
  label: string;
  resultType: 'NUMERIC' | 'TEXT' | 'QUALITATIVE';
  measurementUnitId?: string;
  required: boolean;
  displayOrder: number;
  fieldKind: 'CAPTURED' | 'CALCULATED' | 'INFORMATIONAL';
  readOnly: boolean;
  formula?: string;
  calculationScope: 'PER_REPETITION' | 'AGGREGATE';
  decimalPrecision?: number;
  informationText?: string;
  defaultValue?: string;
}

export interface InitialResultTemplate {
  id: string;
  testDefinitionId: string;
  testDefinitionName: string;
  sampleTypeId?: string;
  sampleTypeName?: string;
  name: string;
  description?: string;
  resultMode: 'SCALAR' | 'REPEATABLE' | 'MATRIX';
  minimumRepetitions: number;
  maximumRepetitions?: number;
  defaultRows: number;
  maximumRows?: number;
  defaultColumns: number;
  maximumColumns?: number;
  active: boolean;
  createdAt: string;
  fields: ResultTemplateField[];
  categories: Array<{
    id: string;
    code: string;
    label: string;
    displayOrder: number;
  }>;
}

export interface ResultResponse {
  id: string;
  workOrderTestId: string;
  resultTemplateId?: string;
  resultMode: 'SCALAR' | 'REPEATABLE' | 'MATRIX';
  status: ResultEntryStatus;
  resultRevision: number;
  analystObservation?: string;
  recordedBy: string;
  recordedByName: string;
  recordedAt: string;
  values: ResultValue[];
  corrections: ResultCorrection[];
  matrix?: ResultMatrix;
  matrixCorrections: ResultMatrixCorrection[];
  calculationSnapshots: Array<{
    id: string;
    fieldKey: string;
    repetitionIndex: number;
    resultRevision: number;
    formulaUsed: string;
    calculationInputs: string;
    calculatedValue: number;
    calculatedBy: string;
    calculatedByRole: string;
    calculatedAt: string;
  }>;
  correctionRequestReason?: string;
  correctionRequestedBy?: string;
  correctionRequestedByName?: string;
  correctionRequestedByRole?: string;
  correctionRequestedAt?: string;
  reviewHistory: ResultReviewHistory[];
  validation?: {
    validatorId: string;
    validatorName: string;
    validatorRole: string;
    resultRevision: number;
    validatedAt: string;
    comment?: string;
  };
}

export type ResultEntryStatus =
  | 'DRAFT'
  | 'RECORDED'
  | 'CORRECTION_REQUESTED'
  | 'CORRECTED'
  | 'VALIDATED'
  | 'INVALIDATED';

export interface ResultReviewHistory {
  id: string;
  action:
    'REGISTERED' | 'CORRECTION_REQUESTED' | 'CORRECTED' | 'VALIDATED' | 'CORRECTED_AND_VALIDATED';
  previousStatus?: ResultEntryStatus;
  newStatus: ResultEntryStatus;
  resultRevision: number;
  reason?: string;
  comment?: string;
  performedBy: string;
  performedByName: string;
  performedByRole: string;
  performedAt: string;
}

export interface ResultMatrix {
  id: string;
  rowCount: number;
  columnCount: number;
  declaredRowCount: number;
  declaredColumnCount: number;
  executionStatus: 'IN_PROGRESS' | 'COMPLETED';
  completionPercentage: number;
  startedAt: string;
  completedAt?: string;
  lastModifiedAt: string;
  lastModifiedBy: string;
  lastModifiedByName: string;
  columns: Array<{
    id: string;
    columnIndex: number;
    carrierLabel?: string;
    distance?: number;
    diameter?: number;
    shape?: string;
  }>;
  cells: Array<{
    id: string;
    rowIndex: number;
    columnIndex: number;
    valueText?: string;
    valueNumber?: number;
    categoryCode?: string;
  }>;
  categorySummary: Array<{
    categoryCode: string;
    categoryLabel: string;
    count: number;
    percentage: number;
  }>;
}

export interface ResultMatrixCorrection {
  id: string;
  targetType: string;
  rowIndex?: number;
  columnIndex?: number;
  fieldName: string;
  oldValue?: string;
  newValue?: string;
  reason: string;
  changedBy: string;
  changedByName: string;
  changedByRole: string;
  changedAt: string;
}

export interface ResultCorrection {
  id: string;
  resultValueId: string;
  fieldLabel: string;
  oldValueText?: string;
  oldValueNumber?: number;
  newValueText?: string;
  newValueNumber?: number;
  reason: string;
  changedBy: string;
  changedByName: string;
  changedByRole: string;
  changedAt: string;
}

export interface TechnicalResultTask {
  workOrderTestId: string;
  workOrderId: string;
  workOrderCode: string;
  laboratoryAreaName: string;
  laboratoryDueAt: string;
  laboratorySampleCode: string;
  sampleTypeName: string;
  testName: string;
  methodName: string;
  testDefinitionId: string;
  resultMode: 'SCALAR' | 'REPEATABLE' | 'MATRIX';
  testStatus: WorkOrderTestStatus;
  assignedAnalystName: string;
  resultId?: string;
  resultStatus?: ResultEntryStatus;
  recordedByName?: string;
  recordedAt?: string;
  correctionRequestReason?: string;
  correctionRequestedByName?: string;
  correctionRequestedByRole?: string;
  correctionRequestedAt?: string;
}

export type LaboratoryReportStatus = 'DRAFT' | 'ISSUED' | 'CANCELLED';

export interface LaboratoryReportSummary {
  id: string;
  code: string;
  serviceOrderId: string;
  serviceOrderCode: string;
  clientName?: string;
  version: number;
  status: LaboratoryReportStatus;
  createdAt: string;
  issuedAt?: string;
}

export interface ReportPreparationCandidate {
  id: string;
  code: string;
  plannedDeliveryAt: string;
}

export interface LaboratoryReportSnapshot {
  reportCode: string;
  version: number;
  generatedAt: string;
  organization?: OrganizationIdentity;
  serviceOrderCode: string;
  clientName: string;
  clientDocument: string;
  reportName?: string;
  reportEmail?: string;
  reportAddress?: string;
  laboratoryAreaName: string;
  generalObservations?: string;
  samples: Array<{
    sequence: number;
    laboratoryCode: string;
    clientIdentification?: string;
    producerName?: string;
    producerDocument?: string;
    productName: string;
    varietyName?: string;
    sampleTypeName: string;
    physicalDescription?: string;
    origin?: string;
    samplingLocation?: string;
    sampledAt?: string;
    quantity?: number;
    quantityUnit?: string;
    containerDescription?: string;
    batchNumber?: string;
    brand?: string;
    observations?: string;
    tests: Array<{
      testName: string;
      methodName: string;
      analystName: string;
      analystObservation?: string;
      validatorName: string;
      validatedAt: string;
      values: Array<{
        label: string;
        repetition: number;
        textValue?: string;
        numericValue?: number;
        unitSymbol?: string;
        fieldKind: 'CAPTURED' | 'CALCULATED' | 'INFORMATIONAL';
        formulaUsed?: string;
        calculationInputs?: string;
      }>;
      matrix?: {
        rowCount: number;
        columnCount: number;
        columns: Array<{
          index: number;
          label?: string;
          distance?: number;
          diameter?: number;
          shape?: string;
        }>;
        cells: Array<{
          row: number;
          column: number;
          textValue?: string;
          numericValue?: number;
          category?: string;
        }>;
      };
    }>;
  }>;
}

export interface LaboratoryReportDetail {
  id: string;
  code: string;
  serviceOrderId: string;
  version: number;
  status: LaboratoryReportStatus;
  checksum: string;
  createdBy: string;
  createdAt: string;
  issuedBy?: string;
  issuedAt?: string;
  cancelledBy?: string;
  cancelledAt?: string;
  cancellationReason?: string;
  snapshot: LaboratoryReportSnapshot;
}

export interface LaboratoryReportHistoryEntry {
  id: string;
  action: 'CREATED' | 'ISSUED' | 'CANCELLED' | 'REISSUED';
  previousStatus?: LaboratoryReportStatus;
  newStatus: LaboratoryReportStatus;
  changeNote?: string;
  performedBy: string;
  occurredAt: string;
}

export interface DashboardSummary {
  period: { from: string; to: string; laboratoryAreaId?: string };
  availableAreas: Array<{ id: string; label: string }>;
  metrics: Array<{
    key: string;
    label: string;
    value: number;
    context: string;
    path: string;
    trend: string;
  }>;
  serviceOrderStatuses: Array<{ status: string; label: string; value: number }>;
  workOrderStatuses: Array<{ status: string; label: string; value: number }>;
  activityTrend: Array<{ date: string; value: number }>;
  activityTrendLabel: string;
  topServices: Array<{
    id: string;
    label: string;
    value: number;
    context: string;
    path: string;
  }>;
  distributions: Array<{
    key: string;
    title: string;
    items: Array<{ key: string; label: string; value: number }>;
  }>;
  cycleTimes: Array<{
    key: string;
    label: string;
    averageHours?: number;
    context: string;
  }>;
  alerts: Array<{
    type: string;
    severity: 'WARNING' | 'CRITICAL';
    resourceId: string;
    code: string;
    title: string;
    description: string;
    dueAt: string;
    path: string;
  }>;
  generatedAt: string;
}

export interface AuditLogEntry {
  id: string;
  module: string;
  resourceId: string;
  resourceCode: string;
  action: string;
  description: string;
  actor: string;
  occurredAt: string;
}
