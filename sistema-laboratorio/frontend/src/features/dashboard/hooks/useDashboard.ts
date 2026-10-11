import { useEffect, useState } from 'react';
import { getErrorMessage } from '../../../shared/lib/errors';
import { getDashboardData, type DashboardFilters } from '../api/dashboard-api';
import type { DashboardSummary } from '../../../shared/types/domain';

function isoDate(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function initialFilters(): DashboardFilters {
  const to = new Date();
  const from = new Date(to);
  from.setDate(to.getDate() - 29);
  return { from: isoDate(from), to: isoDate(to), laboratoryAreaId: '' };
}

export function useDashboard() {
  const [data, setData] = useState<DashboardSummary | null>(null);
  const [filters, setFilters] = useState<DashboardFilters>(initialFilters);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let isMounted = true;

    async function loadDashboard() {
      try {
        const dashboard = await getDashboardData(filters);

        if (isMounted) {
          setData(dashboard);
          setError(null);
        }
      } catch (reason) {
        if (isMounted) {
          setError(getErrorMessage(reason, 'No se pudieron cargar los indicadores.'));
        }
      } finally {
        if (isMounted) {
          setIsLoading(false);
        }
      }
    }

    void loadDashboard();

    return () => {
      isMounted = false;
    };
  }, [filters]);

  return { data, error, filters, isLoading, setFilters };
}
