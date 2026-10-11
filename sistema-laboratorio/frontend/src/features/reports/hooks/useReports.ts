import { useCallback, useEffect, useRef, useState } from 'react';
import { getErrorMessage } from '../../../shared/lib/errors';
import type {
  LaboratoryReportDetail,
  LaboratoryReportHistoryEntry,
  LaboratoryReportSummary,
  PageResponse,
  ReportPreparationCandidate,
} from '../../../shared/types/domain';
import {
  cancelReport,
  createReportDraft,
  getReport,
  getReportHistory,
  getReportPreparationCandidates,
  getReports,
  issueReport,
  reissueReport,
  type ReportFilters,
} from '../api/reports-api';

const initialPagination = { page: 0, totalElements: 0, totalPages: 0 };

export function useReports(canPrepare: boolean, initialFilters: ReportFilters = {}) {
  const [reports, setReports] = useState<LaboratoryReportSummary[]>([]);
  const [preparationCandidates, setPreparationCandidates] = useState<ReportPreparationCandidate[]>(
    [],
  );
  const [selectedReport, setSelectedReport] = useState<LaboratoryReportDetail | null>(null);
  const [history, setHistory] = useState<PageResponse<LaboratoryReportHistoryEntry> | null>(null);
  const [pagination, setPagination] = useState(initialPagination);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const filtersRef = useRef<ReportFilters>(initialFilters);

  const load = useCallback(
    async (filters: ReportFilters = {}) => {
      setIsLoading(true);
      filtersRef.current = filters;
      try {
        const [reportPage, candidates] = await Promise.all([
          getReports(filters),
          canPrepare ? getReportPreparationCandidates() : Promise.resolve([]),
        ]);
        setReports(reportPage.content);
        setPreparationCandidates(candidates);
        setPagination({
          page: reportPage.page,
          totalElements: reportPage.totalElements,
          totalPages: reportPage.totalPages,
        });
        setError(null);
      } catch (reason) {
        setError(getErrorMessage(reason, 'No fue posible cargar los informes.'));
      } finally {
        setIsLoading(false);
      }
    },
    [canPrepare],
  );

  useEffect(() => {
    let isMounted = true;
    void Promise.all([
      getReports(initialFilters),
      canPrepare ? getReportPreparationCandidates() : Promise.resolve([]),
    ])
      .then(([reportPage, candidates]) => {
        if (!isMounted) return;
        setReports(reportPage.content);
        setPreparationCandidates(candidates);
        setPagination({
          page: reportPage.page,
          totalElements: reportPage.totalElements,
          totalPages: reportPage.totalPages,
        });
        setError(null);
      })
      .catch((reason: unknown) => {
        if (isMounted) {
          setError(getErrorMessage(reason, 'No fue posible cargar los informes.'));
        }
      })
      .finally(() => {
        if (isMounted) setIsLoading(false);
      });
    return () => {
      isMounted = false;
    };
  }, [canPrepare, initialFilters]);

  const open = useCallback(async (reportId: string) => {
    try {
      const [detail, reportHistory] = await Promise.all([
        getReport(reportId),
        getReportHistory(reportId),
      ]);
      setSelectedReport(detail);
      setHistory(reportHistory);
      setError(null);
    } catch (reason) {
      setError(getErrorMessage(reason, 'No fue posible abrir el informe.'));
    }
  }, []);

  const mutate = useCallback(
    async (action: () => Promise<LaboratoryReportDetail>, fallback: string) => {
      setIsSaving(true);
      try {
        const detail = await action();
        setSelectedReport(detail);
        setHistory(await getReportHistory(detail.id));
        await load(filtersRef.current);
        return true;
      } catch (reason) {
        setError(getErrorMessage(reason, fallback));
        return false;
      } finally {
        setIsSaving(false);
      }
    },
    [load],
  );

  return {
    cancel: (reportId: string, reason: string) =>
      mutate(() => cancelReport(reportId, reason), 'No fue posible anular el informe.'),
    create: (serviceOrderId: string) =>
      mutate(
        () => createReportDraft(serviceOrderId),
        'No fue posible preparar el informe. Comprueba que todos los resultados estén validados.',
      ),
    error,
    preparationCandidates,
    history,
    isLoading,
    isSaving,
    issue: (reportId: string) =>
      mutate(() => issueReport(reportId), 'No fue posible emitir el informe.'),
    load,
    open,
    pagination,
    reissue: (reportId: string) =>
      mutate(() => reissueReport(reportId), 'No fue posible reemitir el informe.'),
    reports,
    selectedReport,
  };
}
