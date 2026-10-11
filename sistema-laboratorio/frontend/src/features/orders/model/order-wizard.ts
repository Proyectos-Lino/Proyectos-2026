import type { ServiceOrderDetail, ServiceOrderSample } from '../../../shared/types/domain';
import type { CreateServiceOrderInput } from '../api/orders-api';
import {
  emptyCommercialDetails,
  type CommercialDetailsFormValues,
} from './order-commercial-details';

export interface WizardTestSelection {
  sourceRequestedTestId?: string;
  testDefinitionId: string;
  testMethodId: string;
}

export interface WizardSampleForm {
  sourceSampleId?: string;
  clientKey: string;
  clientIdentification: string;
  producerId: string;
  productName: string;
  varietyName: string;
  sampleTypeId: string;
  physicalDescription: string;
  samplingLocation: string;
  origin: string;
  samplerName: string;
  sampledAt: string;
  quantity: string;
  measurementUnitId: string;
  containerDescription: string;
  batchNumber: string;
  productionYear: string;
  brand: string;
  originCertificate: string;
  sanitaryRegistration: string;
  cropAge: string;
  carrierCount: string;
  budCount: string;
  counterSampleInCustody: boolean;
  observations: string;
  serviceId: string;
  requestedTests: WizardTestSelection[];
}

export interface OrderWizardForm extends CommercialDetailsFormValues {
  clientId: string;
  laboratoryAreaId: string;
  samples: WizardSampleForm[];
}

function createClientKey(): string {
  return crypto.randomUUID();
}

export function emptyWizardSample(sampleTypeId = ''): WizardSampleForm {
  return {
    sourceSampleId: undefined,
    clientKey: createClientKey(),
    clientIdentification: '',
    producerId: '',
    productName: '',
    varietyName: '',
    sampleTypeId,
    physicalDescription: '',
    samplingLocation: '',
    origin: '',
    samplerName: '',
    sampledAt: '',
    quantity: '',
    measurementUnitId: '',
    containerDescription: '',
    batchNumber: '',
    productionYear: '',
    brand: '',
    originCertificate: '',
    sanitaryRegistration: '',
    cropAge: '',
    carrierCount: '',
    budCount: '',
    counterSampleInCustody: false,
    observations: '',
    serviceId: '',
    requestedTests: [],
  };
}

export function emptyOrderWizard(sampleTypeId = ''): OrderWizardForm {
  return {
    ...emptyCommercialDetails(),
    clientId: '',
    laboratoryAreaId: '',
    samples: [emptyWizardSample(sampleTypeId)],
  };
}

function toLocalDateTime(value?: string): string {
  if (!value) {
    return '';
  }

  const date = new Date(value);
  const localDate = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return localDate.toISOString().slice(0, 16);
}

function sampleToWizardForm(sample: ServiceOrderSample): WizardSampleForm {
  return {
    sourceSampleId: sample.id,
    clientKey: createClientKey(),
    clientIdentification: sample.clientIdentification ?? '',
    producerId: sample.producerId ?? '',
    productName: sample.productName ?? '',
    varietyName: sample.varietyName ?? '',
    sampleTypeId: sample.sampleTypeId,
    physicalDescription: sample.physicalDescription ?? '',
    samplingLocation: sample.samplingLocation ?? '',
    origin: sample.origin ?? '',
    samplerName: sample.samplerName ?? '',
    sampledAt: toLocalDateTime(sample.sampledAt),
    quantity: sample.quantity?.toString() ?? '',
    measurementUnitId: sample.measurementUnitId ?? '',
    containerDescription: sample.containerDescription ?? '',
    batchNumber: sample.batchNumber ?? '',
    productionYear: sample.productionYear?.toString() ?? '',
    brand: sample.brand ?? '',
    originCertificate: sample.originCertificate ?? '',
    sanitaryRegistration: sample.sanitaryRegistration ?? '',
    cropAge: sample.cropAge ?? '',
    carrierCount: sample.carrierCount?.toString() ?? '',
    budCount: sample.budCount?.toString() ?? '',
    counterSampleInCustody: sample.counterSampleInCustody,
    observations: sample.observations ?? '',
    serviceId: sample.requestedTests[0]?.serviceId ?? '',
    requestedTests: sample.requestedTests.map((test) => ({
      sourceRequestedTestId: test.id,
      testDefinitionId: test.testDefinitionId,
      testMethodId: test.testMethodId,
    })),
  };
}

export function orderToWizardForm(order: ServiceOrderDetail): OrderWizardForm {
  return {
    reportName: order.reportName ?? '',
    reportEmail: order.reportEmail ?? '',
    reportAddress: order.reportAddress ?? '',
    deliveryMethod: order.deliveryMethod,
    pickupPerson: order.pickupPerson ?? '',
    plannedDeliveryAt: toLocalDateTime(order.plannedDeliveryAt),
    proformaNumber: order.proformaNumber ?? '',
    technicalProposalNumber: order.technicalProposalNumber ?? '',
    developmentDays: order.developmentDays?.toString() ?? '',
    outsourced: order.outsourced,
    postAnalysisCustody: order.postAnalysisCustody,
    generalObservations: order.generalObservations ?? '',
    clientId: order.clientId,
    laboratoryAreaId: order.laboratoryAreaId,
    samples: order.samples.map(sampleToWizardForm),
  };
}

function optionalNumber(value: string): number | undefined {
  return value ? Number(value) : undefined;
}

function optionalDate(value: string): string | undefined {
  return value ? new Date(value).toISOString() : undefined;
}

export function toOrderSamplesInput(
  samples: WizardSampleForm[],
): CreateServiceOrderInput['samples'] {
  return samples.map((sample) => ({
    sourceSampleId: sample.sourceSampleId,
    clientIdentification: sample.clientIdentification || undefined,
    producerId: sample.producerId || undefined,
    productName: sample.productName,
    varietyName: sample.varietyName || undefined,
    sampleTypeId: sample.sampleTypeId,
    physicalDescription: sample.physicalDescription || undefined,
    samplingLocation: sample.samplingLocation || undefined,
    origin: sample.origin || undefined,
    samplerName: sample.samplerName || undefined,
    sampledAt: optionalDate(sample.sampledAt),
    quantity: optionalNumber(sample.quantity),
    measurementUnitId: sample.measurementUnitId || undefined,
    containerDescription: sample.containerDescription || undefined,
    batchNumber: sample.batchNumber || undefined,
    productionYear: optionalNumber(sample.productionYear),
    brand: sample.brand || undefined,
    originCertificate: sample.originCertificate || undefined,
    sanitaryRegistration: sample.sanitaryRegistration || undefined,
    cropAge: sample.cropAge || undefined,
    carrierCount: optionalNumber(sample.carrierCount),
    budCount: optionalNumber(sample.budCount),
    counterSampleInCustody: sample.counterSampleInCustody,
    observations: sample.observations || undefined,
    requestedTests: sample.requestedTests.map((test) => ({
      sourceRequestedTestId: test.sourceRequestedTestId,
      serviceId: sample.serviceId,
      testDefinitionId: test.testDefinitionId,
      testMethodId: test.testMethodId,
    })),
  }));
}
