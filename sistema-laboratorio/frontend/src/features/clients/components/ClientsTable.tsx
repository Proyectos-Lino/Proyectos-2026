import { BadgeCheck, History, Pencil, Power } from 'lucide-react';
import { Card, EmptyState } from '../../../shared/components/ui';
import { documentTypeLabels } from '../../../shared/lib/labels';
import type { Client } from '../../../shared/types/domain';

interface ClientsTableProps {
  clients: Client[];
  isLoading: boolean;
  onEdit: (client: Client) => void;
  onHistory: (client: Client) => void;
  onStatusChange: (client: Client) => void;
}

export function ClientsTable({
  clients,
  isLoading,
  onEdit,
  onHistory,
  onStatusChange,
}: ClientsTableProps) {
  return (
    <Card>
      <div className="overflow-x-auto">
        <table className="w-full min-w-180 text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50/80 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-5 py-3.5 font-bold">Cliente</th>
              <th className="px-5 py-3.5 font-bold">Clasificación</th>
              <th className="px-5 py-3.5 font-bold">Documento</th>
              <th className="px-5 py-3.5 font-bold">Contacto</th>
              <th className="px-5 py-3.5 font-bold">Estado</th>
              <th className="px-5 py-3.5 text-right font-bold">Acciones</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {clients.map((client) => (
              <tr className="transition hover:bg-slate-50/70" key={client.id}>
                <td className="px-5 py-4">
                  <p className="font-semibold text-slate-800">{client.legalName}</p>
                  <p className="mt-1 text-xs text-slate-500">
                    {client.reportName || 'Sin nombre de informe'}
                  </p>
                </td>
                <td className="px-5 py-4">
                  <span className="inline-flex rounded-full bg-sky-50 px-2.5 py-1 text-xs font-bold text-sky-700">
                    {participantLabel(client.participantType)}
                  </span>
                </td>
                <td className="px-5 py-4 text-slate-600">
                  {documentTypeLabels[client.documentType]} {client.documentNumber}
                </td>
                <td className="px-5 py-4 text-slate-600">
                  <p>{client.contactName || 'Sin contacto asignado'}</p>
                  <p className="mt-1 text-xs text-slate-500">
                    {client.email || client.phone || '—'}
                  </p>
                </td>
                <td className="px-5 py-4">
                  <span
                    className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-bold ${
                      client.active
                        ? 'bg-emerald-50 text-emerald-700'
                        : 'bg-slate-100 text-slate-600'
                    }`}
                  >
                    <BadgeCheck size={13} />
                    {client.active ? 'ACTIVO' : 'INACTIVO'}
                  </span>
                </td>
                <td className="px-5 py-4">
                  <div className="flex justify-end gap-1.5">
                    <button
                      aria-label={`Ver historial de ${client.legalName}`}
                      className="rounded-lg p-2 text-slate-500 transition hover:bg-lab-50 hover:text-lab-700"
                      onClick={() => onHistory(client)}
                      title="Historial de órdenes"
                      type="button"
                    >
                      <History size={16} />
                    </button>
                    <button
                      aria-label={`Editar ${client.legalName}`}
                      className="rounded-lg p-2 text-slate-500 transition hover:bg-lab-50 hover:text-lab-700"
                      onClick={() => onEdit(client)}
                      title="Editar"
                      type="button"
                    >
                      <Pencil size={16} />
                    </button>
                    <button
                      aria-label={`${client.active ? 'Desactivar' : 'Activar'} ${client.legalName}`}
                      className={`rounded-lg p-2 transition ${
                        client.active
                          ? 'text-rose-600 hover:bg-rose-50'
                          : 'text-emerald-700 hover:bg-emerald-50'
                      }`}
                      onClick={() => onStatusChange(client)}
                      title={client.active ? 'Desactivar' : 'Activar'}
                      type="button"
                    >
                      <Power size={16} />
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {isLoading ? <p className="px-5 py-9 text-sm text-slate-500">Cargando clientes…</p> : null}
        {!isLoading && clients.length === 0 ? (
          <EmptyState message="No se encontraron clientes." />
        ) : null}
      </div>
    </Card>
  );
}

function participantLabel(value: Client['participantType']) {
  if (value === 'BOTH') return 'CLIENTE Y PRODUCTOR';
  return value === 'CLIENT' ? 'CLIENTE' : 'PRODUCTOR';
}
