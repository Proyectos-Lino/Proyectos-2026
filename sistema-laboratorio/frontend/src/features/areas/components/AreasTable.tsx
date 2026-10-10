import { Pencil, Power } from 'lucide-react';
import { Button, Card, EmptyState } from '../../../shared/components/ui';
import type { LaboratoryArea } from '../../../shared/types/domain';

interface AreasTableProps {
  areas: LaboratoryArea[];
  isLoading: boolean;
  onEdit: (area: LaboratoryArea) => void;
  onStatusChange: (area: LaboratoryArea) => void;
}

export function AreasTable({ areas, isLoading, onEdit, onStatusChange }: AreasTableProps) {
  return (
    <Card>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[44rem] text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50/80 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-5 py-3.5">Código</th>
              <th className="px-5 py-3.5">Área</th>
              <th className="px-5 py-3.5">Estado</th>
              <th className="px-5 py-3.5 text-right">Acciones</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {areas.map((area) => (
              <tr className="hover:bg-slate-50/70" key={area.id}>
                <td className="px-5 py-4 font-mono text-xs font-bold text-lab-700">{area.code}</td>
                <td className="px-5 py-4 font-semibold text-slate-800">{area.name}</td>
                <td className="px-5 py-4">
                  <span
                    className={
                      area.active
                        ? 'rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-bold text-emerald-700'
                        : 'rounded-full bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-600'
                    }
                  >
                    {area.active ? 'ACTIVA' : 'INACTIVA'}
                  </span>
                </td>
                <td className="px-5 py-4">
                  <div className="flex justify-end gap-2">
                    <Button onClick={() => onEdit(area)} variant="secondary">
                      <Pencil size={15} />
                      Editar
                    </Button>
                    <Button onClick={() => onStatusChange(area)} variant="ghost">
                      <Power size={15} />
                      {area.active ? 'Desactivar' : 'Activar'}
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {isLoading ? <p className="px-5 py-8 text-sm text-slate-500">Cargando áreas…</p> : null}
        {!isLoading && areas.length === 0 ? (
          <EmptyState message="No se encontraron áreas de laboratorio." />
        ) : null}
      </div>
    </Card>
  );
}
