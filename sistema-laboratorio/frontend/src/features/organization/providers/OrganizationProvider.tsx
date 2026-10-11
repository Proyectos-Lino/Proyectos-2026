import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import type { OrganizationBranding } from '../../../shared/types/domain';
import { getOrganizationBranding } from '../api/organization-api';
import { OrganizationContext } from '../model/organization-context';
import { setApplicationTimeZone } from '../../../shared/lib/format';

const fallbackOrganization: OrganizationBranding = {
  commercialName: 'Gestión de laboratorio',
  primaryColor: '#0F766E',
  secondaryColor: '#0F172A',
  timeZone: 'America/Lima',
};

export function OrganizationProvider({ children }: { children: ReactNode }) {
  const [organization, setOrganization] = useState<OrganizationBranding>(fallbackOrganization);

  const refreshOrganization = useCallback(async () => {
    try {
      setOrganization(await getOrganizationBranding());
    } catch {
      setOrganization((current) => current ?? fallbackOrganization);
    }
  }, []);

  useEffect(() => {
    let isMounted = true;
    void getOrganizationBranding()
      .then((profile) => {
        if (isMounted) setOrganization(profile);
      })
      .catch(() => {
        if (isMounted) setOrganization(fallbackOrganization);
      });
    return () => {
      isMounted = false;
    };
  }, []);

  useEffect(() => {
    const profile = organization;
    document.documentElement.style.setProperty('--organization-primary', profile.primaryColor);
    document.documentElement.style.setProperty('--organization-secondary', profile.secondaryColor);
    document.documentElement.style.setProperty('--color-lab-950', profile.secondaryColor);
    document.documentElement.style.setProperty('--color-lab-900', profile.secondaryColor);
    document.documentElement.style.setProperty('--color-lab-800', profile.primaryColor);
    document.documentElement.style.setProperty('--color-lab-700', profile.primaryColor);
    document.documentElement.style.setProperty('--color-lab-600', profile.primaryColor);
    document.documentElement.style.setProperty('--color-lab-500', profile.primaryColor);
    document.documentElement.style.setProperty(
      '--color-lab-300',
      `color-mix(in srgb, ${profile.primaryColor} 38%, white)`,
    );
    document.documentElement.style.setProperty(
      '--color-lab-100',
      `color-mix(in srgb, ${profile.primaryColor} 16%, white)`,
    );
    document.documentElement.style.setProperty(
      '--color-lab-50',
      `color-mix(in srgb, ${profile.primaryColor} 7%, white)`,
    );
    document.title = `${profile.commercialName} · Gestión de laboratorio`;
    setApplicationTimeZone(profile.timeZone);
  }, [organization]);

  const value = useMemo(
    () => ({
      organization,
      refreshOrganization,
      setOrganization,
    }),
    [organization, refreshOrganization],
  );

  return <OrganizationContext.Provider value={value}>{children}</OrganizationContext.Provider>;
}
