import { Pencil, Power } from 'lucide-react';
import { Button, Card, EmptyState } from '../../../shared/components/ui';
import { resultModeLabels, resultTypeLabels } from '../../../shared/lib/labels';
import type { CatalogKind, ManagedCatalogItem } from '../api/catalog-management-api';

interface CatalogTableProps {
  isLoading: boolean;
  items: ManagedCatalogItem[];
  kind: CatalogKind;
  onEdit: (item: ManagedCatalogItem) => void;
  onStatusChange: (item: ManagedCatalogItem) => void;
}

function detailsFor(kind: CatalogKind, item: ManagedCatalogItem): string {
  switch (kind) {
    case 'units':
      return item.symbol ?? 'Sin símbolo';
    case 'sample-types':
      return `${item.laboratoryCodePrefix ?? '—'} · ${resultModeLabels[item.resultMode ?? 'SCALAR']}`;
    case 'tests':
      return `${item.resultType ? resultTypeLabels[item.resultType] : '—'}${item.resultUnitId ? ' · con unidad' : ''}`;
    case 'methods':
      return item.versionLabel ?? 'Sin versión declarada';
    case 'result-templates':
      return [
        item.testDefinitionName ?? 'Ensayo',
        item.sampleTypeName ?? 'Todas las muestras',
        item.resultMode === 'MATRIX'
          ? `${resultModeLabels.MATRIX} · ${item.categories?.length ?? 0} categorías`
          : resultModeLabels[item.resultMode ?? 'SCALAR'],
      ].join(' · ');
    case 'services':
      return `${item.laboratoryAreaName ?? 'Sin área'} · ${item.tests?.length ?? 0} ensayo(s)`;
    case 'products':
      return 'Producto o matriz de muestra';
    case 'varieties':
      return item.productName ?? 'Sin producto asociado';
  }
}

export function CatalogTable({
  isLoading,
  items,
  kind,
  onEdit,
  onStatusChange,
}: CatalogTableProps) {
  return (
    <Card>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[44rem] text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50/80 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-5 py-3.5">Código</th>
              <th className="px-5 py-3.5">Nombre</th>
              <th className="px-5 py-3.5">Configuración</th>
              <th className="px-5 py-3.5">Estado</th>
              <th className="px-5 py-3.5 text-right">Acciones</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {items.map((item) => (
              <tr className="hover:bg-slate-50/70" key={item.id}>
                <td className="px-5 py-4 font-mono text-xs font-bold text-lab-700">
                  {item.code ?? '—'}
                </td>
                <td className="px-5 py-4 font-semibold text-slate-800">{item.name}</td>
                <td className="px-5 py-4 text-slate-500">{detailsFor(kind, item)}</td>
                <td className="px-5 py-4">
                  <span
                    className={
                      item.active
                        ? 'rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-bold text-emerald-700'
                        : 'rounded-full bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-600'
                    }
                  >
                    {item.active ? 'ACTIVO' : 'INACTIVO'}
                  </span>
                </td>
                <td className="px-5 py-4">
                  <div className="flex justify-end gap-2">
                    <Button onClick={() => onEdit(item)} variant="secondary">
                      <Pencil size={15} />
                      Editar
                    </Button>
                    <Button onClick={() => onStatusChange(item)} variant="ghost">
                      <Power size={15} />
                      {item.active ? 'Desactivar' : 'Activar'}
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {isLoading ? <p className="px-5 py-8 text-sm text-slate-500">Cargando catálogo…</p> : null}
        {!isLoading && items.length === 0 ? (
          <EmptyState message="No se encontraron registros." />
        ) : null}
      </div>
    </Card>
  );
}
