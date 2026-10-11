import { useCallback, useEffect, useRef, useState } from 'react';
import { getErrorMessage } from '../../../shared/lib/errors';
import type { LaboratoryArea } from '../../../shared/types/domain';
import {
  createArea,
  getAreas,
  updateArea,
  updateAreaStatus,
  type AreaInput,
  type AreasQuery,
} from '../api/laboratory-areas-api';

const initialPagination = { page: 0, totalElements: 0, totalPages: 0 };

export function useAreas() {
  const [areas, setAreas] = useState<LaboratoryArea[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [pagination, setPagination] = useState(initialPagination);
  const lastQuery = useRef<AreasQuery>({});

  const loadAreas = useCallback(async (query: AreasQuery = {}) => {
    setIsLoading(true);
    lastQuery.current = query;
    try {
      const page = await getAreas(query);
      setAreas(page.content);
      setPagination({
        page: page.page,
        totalElements: page.totalElements,
        totalPages: page.totalPages,
      });
      setError(null);
    } catch (reason) {
      setError(getErrorMessage(reason, 'No se pudieron cargar las áreas.'));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    let isMounted = true;

    void getAreas()
      .then((page) => {
        if (!isMounted) return;
        setAreas(page.content);
        setPagination({
          page: page.page,
          totalElements: page.totalElements,
          totalPages: page.totalPages,
        });
        setError(null);
      })
      .catch((reason: unknown) => {
        if (isMounted) {
          setError(getErrorMessage(reason, 'No se pudieron cargar las áreas.'));
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
  }, []);

  async function saveArea(areaId: string | null, input: AreaInput) {
    setIsSaving(true);
    try {
      if (areaId) {
        await updateArea(areaId, input);
      } else {
        await createArea(input);
      }
      await loadAreas(lastQuery.current);
      return true;
    } catch (reason) {
      setError(getErrorMessage(reason, 'No fue posible guardar el área.'));
      return false;
    } finally {
      setIsSaving(false);
    }
  }

  async function changeStatus(area: LaboratoryArea) {
    try {
      await updateAreaStatus(area.id, !area.active);
      await loadAreas(lastQuery.current);
    } catch (reason) {
      setError(getErrorMessage(reason, 'No fue posible actualizar el estado del área.'));
    }
  }

  return { areas, changeStatus, error, isLoading, isSaving, loadAreas, pagination, saveArea };
}
