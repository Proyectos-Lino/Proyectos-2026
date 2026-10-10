import { useContext } from 'react';
import { OrganizationContext } from '../model/organization-context';

export function useOrganization() {
  const context = useContext(OrganizationContext);
  if (!context) {
    throw new Error('useOrganization debe utilizarse dentro de OrganizationProvider.');
  }
  return context;
}
