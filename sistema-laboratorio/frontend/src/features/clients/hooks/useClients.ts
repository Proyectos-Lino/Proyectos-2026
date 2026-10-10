import { useCallback, useEffect, useRef, useState } from 'react';
import { getErrorMessage } from '../../../shared/lib/errors';
import type { Client, PageResponse } from '../../../shared/types/domain';
import {
  createClient,
  getClients,
  updateClient,
  updateClientStatus,
  type ClientFilters,
  type CreateClientInput,
} from '../api/clients-api';

export function useClients(initialFilters: ClientFilters = {}) {
  const [clients, setClients] = useState<Client[]>([]);
  const [pageResponse, setPageResponse] = useState<PageResponse<Client> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const lastFilters = useRef<ClientFilters>(initialFilters);

  const loadClients = useCallback(async (filters: ClientFilters = {}) => {
    setIsLoading(true);
    lastFilters.current = filters;

    try {
      const page = await getClients(filters);
      setClients(page.content);
      setPageResponse(page);
      setError(null);
    } catch (reason) {
      setError(getErrorMessage(reason, 'No se pudieron cargar los participantes.'));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    let isMounted = true;

    void getClients(initialFilters)
      .then((page) => {
        if (isMounted) {
          setClients(page.content);
          setPageResponse(page);
          setError(null);
        }
      })
      .catch((reason: unknown) => {
        if (isMounted) {
          setError(getErrorMessage(reason, 'No se pudieron cargar los participantes.'));
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
  }, [initialFilters]);

  const saveClient = useCallback(
    async (clientId: string | null, input: CreateClientInput) => {
      setIsSaving(true);

      try {
        if (clientId) {
          await updateClient(clientId, input);
        } else {
          await createClient(input);
        }
        await loadClients(lastFilters.current);
        return true;
      } catch (reason) {
        setError(getErrorMessage(reason, 'No fue posible guardar el participante.'));
        return false;
      } finally {
        setIsSaving(false);
      }
    },
    [loadClients],
  );

  const changeClientStatus = useCallback(
    async (client: Client) => {
      try {
        await updateClientStatus(client.id, !client.active);
        await loadClients(lastFilters.current);
        return true;
      } catch (reason) {
        setError(getErrorMessage(reason, 'No fue posible actualizar el estado del cliente.'));
        return false;
      }
    },
    [loadClients],
  );

  return {
    changeClientStatus,
    clients,
    error,
    isLoading,
    isSaving,
    loadClients,
    pageResponse,
    saveClient,
  };
}
