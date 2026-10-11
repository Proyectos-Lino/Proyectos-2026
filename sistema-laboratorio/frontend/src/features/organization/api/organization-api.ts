import { request } from '../../../shared/api/http-client';
import type {
  OrganizationBranding,
  OrganizationContact,
  OrganizationProfile,
} from '../../../shared/types/domain';

export interface UpdateOrganizationInput {
  commercialName: string;
  legalName: string;
  documentType?: string;
  documentNumber?: string;
  address?: string;
  logoDataUrl?: string;
  primaryColor: string;
  secondaryColor: string;
  timeZone: string;
  legalText?: string;
  accreditationName?: string;
  accreditationCode?: string;
  accreditationScope?: string;
  serviceOrderPrefix: string;
  workOrderPrefix: string;
  reportPrefix: string;
  contacts: Array<Omit<OrganizationContact, 'id' | 'displayOrder'>>;
  authorizedSignerIds: string[];
}

export function getOrganizationProfile(): Promise<OrganizationProfile> {
  return request<OrganizationProfile>('/organization-profile');
}

export function getOrganizationBranding(): Promise<OrganizationBranding> {
  return request<OrganizationBranding>('/organization-profile/public');
}

export function updateOrganizationProfile(
  input: UpdateOrganizationInput,
): Promise<OrganizationProfile> {
  return request<OrganizationProfile>('/organization-profile', {
    method: 'PUT',
    body: JSON.stringify(input),
  });
}
