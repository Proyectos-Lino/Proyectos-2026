import type { PostAnalysisCustody } from '../../../shared/types/domain';

export interface CommercialDetailsFormValues {
  reportName: string;
  reportEmail: string;
  reportAddress: string;
  deliveryMethod: 'EMAIL' | 'PICKUP';
  pickupPerson: string;
  plannedDeliveryAt: string;
  proformaNumber: string;
  technicalProposalNumber: string;
  developmentDays: string;
  outsourced: boolean;
  postAnalysisCustody: PostAnalysisCustody;
  generalObservations: string;
}

export const custodyLabels: Record<PostAnalysisCustody, string> = {
  RETAINED: 'Mantener en custodia',
  RETURNED_TO_CLIENT: 'Devolver al cliente',
  DISPOSED: 'Desechar según procedimiento',
  OTHER: 'Otra condición',
};

export const postAnalysisCustodyOptions = Object.keys(custodyLabels) as PostAnalysisCustody[];

export function defaultDueDate(): string {
  const date = new Date(Date.now() + 3 * 24 * 60 * 60 * 1000);
  return date.toISOString().slice(0, 16);
}

export function emptyCommercialDetails(): CommercialDetailsFormValues {
  return {
    reportName: '',
    reportEmail: '',
    reportAddress: '',
    deliveryMethod: 'EMAIL',
    pickupPerson: '',
    plannedDeliveryAt: defaultDueDate(),
    proformaNumber: '',
    technicalProposalNumber: '',
    developmentDays: '',
    outsourced: false,
    postAnalysisCustody: 'RETAINED',
    generalObservations: '',
  };
}
