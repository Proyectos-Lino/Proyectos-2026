import { createContext } from 'react';
import type { OrganizationBranding } from '../../../shared/types/domain';

export interface OrganizationContextValue {
  organization: OrganizationBranding;
  refreshOrganization: () => Promise<void>;
  setOrganization: (organization: OrganizationBranding) => void;
}

export const OrganizationContext = createContext<OrganizationContextValue | null>(null);
