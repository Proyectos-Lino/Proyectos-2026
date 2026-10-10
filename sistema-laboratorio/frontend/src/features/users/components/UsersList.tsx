import { Pencil, Power, ShieldCheck } from 'lucide-react';
import { Button, Card, EmptyState } from '../../../shared/components/ui';
import { roleLabels } from '../../../shared/lib/labels';
import type { UserSummary } from '../../../shared/types/domain';

interface UsersListProps {
  isLoading: boolean;
  users: UserSummary[];
  onEdit: (user: UserSummary) => void;
  onStatusChange: (user: UserSummary) => void;
}

export function UsersList({ isLoading, onEdit, onStatusChange, users }: UsersListProps) {
  return (
    <Card>
      <header className="border-b border-slate-100 px-5 py-4">
        <h2 className="font-bold text-slate-900">Accesos configurados</h2>
      </header>
      <div className="divide-y divide-slate-100">
        {users.map((user) => (
          <article
            className="flex flex-wrap items-center justify-between gap-4 px-5 py-4"
            key={user.id}
          >
            <div className="flex items-center gap-3">
              <span className="grid size-10 place-items-center rounded-xl bg-lab-50 text-lab-700">
                <ShieldCheck size={19} />
              </span>
              <div>
                <p className="font-semibold text-slate-800">{user.fullName}</p>
                <p className="mt-1 text-sm text-slate-500">{user.email}</p>
                <p className="mt-1 text-xs text-slate-400">
                  {user.laboratoryAreaName ?? 'Sin área asignada'}
                </p>
              </div>
            </div>
            <div className="flex flex-wrap items-center justify-end gap-2">
              <span
                className={
                  user.active
                    ? 'rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-bold text-emerald-700'
                    : 'rounded-full bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-600'
                }
              >
                {user.active ? 'ACTIVO' : 'INACTIVO'}
              </span>
              {user.roles.map((role) => (
                <span
                  className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-600"
                  key={role}
                >
                  {roleLabels[role]}
                </span>
              ))}
              <Button onClick={() => onEdit(user)} variant="secondary">
                <Pencil size={15} />
                Editar
              </Button>
              <Button onClick={() => onStatusChange(user)} variant="ghost">
                <Power size={15} />
                {user.active ? 'Desactivar' : 'Activar'}
              </Button>
            </div>
          </article>
        ))}
        {isLoading ? <p className="px-5 py-8 text-sm text-slate-500">Cargando usuarios…</p> : null}
        {!isLoading && users.length === 0 ? (
          <EmptyState message="Aún no hay usuarios configurados." />
        ) : null}
      </div>
    </Card>
  );
}
