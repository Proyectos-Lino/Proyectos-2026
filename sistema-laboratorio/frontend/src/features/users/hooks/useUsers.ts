import { useCallback, useEffect, useRef, useState } from 'react';
import { getErrorMessage } from '../../../shared/lib/errors';
import type { LaboratoryAreaOption, UserSummary } from '../../../shared/types/domain';
import { getActiveAreaOptions } from '../../areas/api/laboratory-areas-api';
import {
  createUser,
  getUsers,
  updateUser,
  updateUserStatus,
  type CreateUserInput,
  type UpdateUserInput,
  type UsersQuery,
} from '../api/users-api';

const initialPagination = { page: 0, totalElements: 0, totalPages: 0 };

export function useUsers() {
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [areas, setAreas] = useState<LaboratoryAreaOption[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [pagination, setPagination] = useState(initialPagination);
  const lastQuery = useRef<UsersQuery>({});

  const loadUsers = useCallback(async (query: UsersQuery = {}) => {
    setIsLoading(true);
    lastQuery.current = query;

    try {
      const [page, activeAreas] = await Promise.all([getUsers(query), getActiveAreaOptions()]);
      setUsers(page.content);
      setAreas(activeAreas);
      setPagination({
        page: page.page,
        totalElements: page.totalElements,
        totalPages: page.totalPages,
      });
      setError(null);
    } catch (reason) {
      setError(getErrorMessage(reason, 'No se pudieron cargar los usuarios.'));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    let isMounted = true;

    void Promise.all([getUsers(), getActiveAreaOptions()])
      .then(([page, activeAreas]) => {
        if (!isMounted) return;
        setUsers(page.content);
        setAreas(activeAreas);
        setPagination({
          page: page.page,
          totalElements: page.totalElements,
          totalPages: page.totalPages,
        });
        setError(null);
      })
      .catch((reason: unknown) => {
        if (isMounted) {
          setError(getErrorMessage(reason, 'No se pudieron cargar los usuarios.'));
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

  const saveUser = useCallback(
    async (userId: string | null, input: CreateUserInput | UpdateUserInput) => {
      setIsSaving(true);

      try {
        if (userId) await updateUser(userId, input as UpdateUserInput);
        else await createUser(input as CreateUserInput);
        await loadUsers(lastQuery.current);
        return true;
      } catch (reason) {
        setError(getErrorMessage(reason, 'No se pudo crear el usuario.'));
        return false;
      } finally {
        setIsSaving(false);
      }
    },
    [loadUsers],
  );

  const changeStatus = useCallback(
    async (user: UserSummary) => {
      try {
        await updateUserStatus(user.id, !user.active);
        await loadUsers(lastQuery.current);
      } catch (reason) {
        setError(getErrorMessage(reason, 'No fue posible actualizar el estado del usuario.'));
      }
    },
    [loadUsers],
  );

  return {
    areas,
    changeStatus,
    error,
    isLoading,
    isSaving,
    loadUsers,
    pagination,
    saveUser,
    users,
  };
}
