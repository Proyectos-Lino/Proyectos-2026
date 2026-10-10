import { useCallback, useEffect, useRef, useState } from 'react';
import { request } from '../../../shared/api/http-client';
import { getErrorMessage } from '../../../shared/lib/errors';
import type { CatalogSnapshot } from '../../../shared/types/domain';
import {
  createCatalogItem,
  getCatalogItems,
  updateCatalogItem,
  updateCatalogItemStatus,
  type CatalogKind,
  type CatalogPayload,
  type CatalogQuery,
  type ManagedCatalogItem,
} from '../api/catalog-management-api';

const initialPagination = { page: 0, totalElements: 0, totalPages: 0 };

export function useCatalogManagement(kind: CatalogKind) {
  const [catalog, setCatalog] = useState<CatalogSnapshot | null>(null);
  const [items, setItems] = useState<ManagedCatalogItem[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [pagination, setPagination] = useState(initialPagination);
  const lastQuery = useRef<CatalogQuery>({});

  const loadItems = useCallback(
    async (query: CatalogQuery = {}) => {
      setIsLoading(true);
      lastQuery.current = query;

      try {
        const [page, snapshot] = await Promise.all([
          getCatalogItems(kind, query),
          request<CatalogSnapshot>('/catalogs'),
        ]);
        setItems(page.content);
        setCatalog(snapshot);
        setPagination({
          page: page.page,
          totalElements: page.totalElements,
          totalPages: page.totalPages,
        });
        setError(null);
      } catch (reason) {
        setError(getErrorMessage(reason, 'No se pudo cargar el catálogo.'));
      } finally {
        setIsLoading(false);
      }
    },
    [kind],
  );

  useEffect(() => {
    let isMounted = true;

    void Promise.all([getCatalogItems(kind), request<CatalogSnapshot>('/catalogs')])
      .then(([page, snapshot]) => {
        if (!isMounted) return;
        setItems(page.content);
        setCatalog(snapshot);
        setPagination({
          page: page.page,
          totalElements: page.totalElements,
          totalPages: page.totalPages,
        });
        setError(null);
      })
      .catch((reason: unknown) => {
        if (isMounted) {
          setError(getErrorMessage(reason, 'No se pudo cargar el catálogo.'));
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
  }, [kind]);

  const saveItem = useCallback(
    async (id: string | null, payload: CatalogPayload) => {
      setIsSaving(true);
      try {
        if (id) {
          await updateCatalogItem(kind, id, payload);
        } else {
          await createCatalogItem(kind, payload);
        }
        await loadItems(lastQuery.current);
        return true;
      } catch (reason) {
        setError(getErrorMessage(reason, 'No fue posible guardar el registro.'));
        return false;
      } finally {
        setIsSaving(false);
      }
    },
    [kind, loadItems],
  );

  const changeStatus = useCallback(
    async (item: ManagedCatalogItem) => {
      try {
        await updateCatalogItemStatus(kind, item.id, !item.active);
        await loadItems(lastQuery.current);
      } catch (reason) {
        setError(getErrorMessage(reason, 'No fue posible actualizar el estado.'));
      }
    },
    [kind, loadItems],
  );

  return {
    catalog,
    changeStatus,
    error,
    isLoading,
    isSaving,
    items,
    loadItems,
    pagination,
    saveItem,
  };
}
