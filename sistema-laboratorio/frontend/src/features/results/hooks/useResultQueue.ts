import { useCallback, useEffect, useState } from 'react';
import { request } from '../../../shared/api/http-client';
import { getErrorMessage } from '../../../shared/lib/errors';
import type {
  CatalogSnapshot,
  PageResponse,
  TechnicalResultTask,
} from '../../../shared/types/domain';
import {
  getBudResultQueue,
  getResultEntryQueue,
  getResultValidationQueue,
} from '../api/results-api';

export type ResultQueueMode = 'BUDS' | 'ENTRY' | 'VALIDATION';

const queueLoaders = {
  BUDS: getBudResultQueue,
  ENTRY: getResultEntryQueue,
  VALIDATION: getResultValidationQueue,
};

export function useResultQueue(mode: ResultQueueMode) {
  const [catalog, setCatalog] = useState<CatalogSnapshot | null>(null);
  const [queue, setQueue] = useState<PageResponse<TechnicalResultTask> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const loadQueue = useCallback(
    async (page = 0) => {
      setIsLoading(true);

      try {
        const resultQueue = await queueLoaders[mode](page);
        setQueue(resultQueue);
        setError(null);
      } catch (reason) {
        setError(
          getErrorMessage(
            reason,
            mode !== 'VALIDATION'
              ? 'No se pudo cargar la bandeja de resultados.'
              : 'No se pudo cargar la bandeja de validación.',
          ),
        );
      } finally {
        setIsLoading(false);
      }
    },
    [mode],
  );

  useEffect(() => {
    let isMounted = true;

    void Promise.all([queueLoaders[mode](0), request<CatalogSnapshot>('/catalogs')])
      .then(([resultQueue, catalogSnapshot]) => {
        if (isMounted) {
          setQueue(resultQueue);
          setCatalog(catalogSnapshot);
          setError(null);
        }
      })
      .catch((reason: unknown) => {
        if (isMounted) {
          setError(
            getErrorMessage(
              reason,
              mode !== 'VALIDATION'
                ? 'No se pudo cargar la bandeja de resultados.'
                : 'No se pudo cargar la bandeja de validación.',
            ),
          );
        }
      })
      .finally(() => {
        if (isMounted) {
          setIsLoading(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, [mode]);

  return {
    catalog,
    error,
    isLoading,
    loadQueue,
    queue,
  };
}
