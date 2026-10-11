import { Plus, Search } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Button, ErrorNotice, PageHeader, Pagination } from '../../../shared/components/ui';
import { roleLabels } from '../../../shared/lib/labels';
import type { RoleCode, UserSummary } from '../../../shared/types/domain';
import { UserFormModal } from '../components/UserFormModal';
import { UsersList } from '../components/UsersList';
import { useUsers } from '../hooks/useUsers';

export function UsersPage() {
  const {
    areas,
    changeStatus,
    error,
    isLoading,
    isSaving,
    loadUsers,
    pagination,
    saveUser,
    users,
  } = useUsers();
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [selectedUser, setSelectedUser] = useState<UserSummary | null>(null);
  const [search, setSearch] = useState('');
  const [active, setActive] = useState('');
  const [role, setRole] = useState('');

  function openCreate() {
    setSelectedUser(null);
    setIsFormOpen(true);
  }
  function openEdit(user: UserSummary) {
    setSelectedUser(user);
    setIsFormOpen(true);
  }

  function closeForm() {
    setIsFormOpen(false);
    setSelectedUser(null);
  }

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void loadUsers({
      active: active === '' ? undefined : active === 'true',
      role: role === '' ? undefined : (role as RoleCode),
      search,
    });
  }

  function changePage(page: number) {
    void loadUsers({
      active: active === '' ? undefined : active === 'true',
      page,
      role: role === '' ? undefined : (role as RoleCode),
      search,
    });
  }

  return (
    <div className="space-y-7">
      <PageHeader
        actions={
          <Button onClick={openCreate}>
            <Plus size={17} />
            Nuevo usuario
          </Button>
        }
        description="Define quién puede registrar, analizar y validar los resultados."
        eyebrow="Administración"
        title="Usuarios y roles"
      />
      <ErrorNotice message={error} />
      <form
        className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_11rem_13rem_auto]"
        onSubmit={submitSearch}
      >
        <div className="relative">
          <Search
            className="pointer-events-none absolute left-3.5 top-3 text-slate-400"
            size={16}
          />
          <input
            aria-label="Buscar usuario"
            className="pl-9"
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Buscar por nombre o correo"
            value={search}
          />
        </div>
        <select
          aria-label="Filtrar por estado"
          onChange={(event) => setActive(event.target.value)}
          value={active}
        >
          <option value="">Todos los estados</option>
          <option value="true">Activos</option>
          <option value="false">Inactivos</option>
        </select>
        <select
          aria-label="Filtrar por rol"
          onChange={(event) => setRole(event.target.value)}
          value={role}
        >
          <option value="">Todos los roles</option>
          {(Object.keys(roleLabels) as RoleCode[]).map((roleCode) => (
            <option key={roleCode} value={roleCode}>
              {roleLabels[roleCode]}
            </option>
          ))}
        </select>
        <Button type="submit" variant="secondary">
          Filtrar
        </Button>
      </form>
      <UsersList
        isLoading={isLoading}
        onEdit={openEdit}
        onStatusChange={(user) => void changeStatus(user)}
        users={users}
      />
      <Pagination {...pagination} onPageChange={changePage} />
      <UserFormModal
        areas={areas}
        isOpen={isFormOpen}
        isSaving={isSaving}
        key={selectedUser?.id ?? 'new'}
        onClose={closeForm}
        onSubmit={saveUser}
        user={selectedUser}
      />
    </div>
  );
}
