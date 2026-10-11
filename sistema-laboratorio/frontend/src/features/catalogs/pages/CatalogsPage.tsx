import { Download, Plus, Search } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Button, ErrorNotice, PageHeader, Pagination } from '../../../shared/components/ui';
import type { CatalogKind, ManagedCatalogItem } from '../api/catalog-management-api';
import { exportCatalog } from '../api/catalog-management-api';
import { CatalogFormModal } from '../components/CatalogFormModal';
import { CatalogTable } from '../components/CatalogTable';
import { useCatalogManagement } from '../hooks/useCatalogManagement';
import { catalogLabels } from '../model/catalog-labels';

const catalogKinds: CatalogKind[] = [
  'services',
  'tests',
  'methods',
  'result-templates',
  'sample-types',
  'units',
  'products',
  'varieties',
];

export function CatalogsPage() {
  const [searchParams] = useSearchParams();
  const requestedKind = searchParams.get('tab') as CatalogKind | null;
  const [kind, setKind] = useState<CatalogKind>(
    requestedKind && catalogKinds.includes(requestedKind) ? requestedKind : 'services',
  );
  const [search, setSearch] = useState('');
  const [active, setActive] = useState('');
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [selectedItem, setSelectedItem] = useState<ManagedCatalogItem | null>(null);
  const {
    catalog,
    changeStatus,
    error,
    isLoading,
    isSaving,
    items,
    loadItems,
    pagination,
    saveItem,
  } = useCatalogManagement(kind);
  const label = catalogLabels[kind];

  function selectKind(nextKind: CatalogKind) {
    setKind(nextKind);
    setSearch('');
    setActive('');
    setSelectedItem(null);
  }

  function openCreate() {
    setSelectedItem(null);
    setIsFormOpen(true);
  }

  function openEdit(item: ManagedCatalogItem) {
    setSelectedItem(item);
    setIsFormOpen(true);
  }

  function closeForm() {
    setIsFormOpen(false);
    setSelectedItem(null);
  }

  function query(page = 0) {
    return { active: active === '' ? undefined : active === 'true', page, search };
  }

  function handleFilter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void loadItems(query());
  }

  async function handleExport() {
    try {
      await exportCatalog(kind);
    } catch {
      void loadItems(query());
    }
  }

  return (
    <div className="space-y-7">
      <PageHeader
        actions={
          <div className="flex flex-wrap gap-3">
            <Button onClick={() => void handleExport()} variant="secondary">
              <Download size={16} />
              Exportar CSV
            </Button>
            <Button onClick={openCreate}>
              <Plus size={17} />
              Nuevo registro
            </Button>
          </div>
        }
        description="Configura los catálogos comerciales y técnicos que utilizarán las órdenes y resultados."
        eyebrow="Administración"
        title="Catálogos del laboratorio"
      />
      <div className="flex flex-wrap gap-2" role="tablist">
        {catalogKinds.map((catalogKind) => (
          <button
            aria-selected={kind === catalogKind}
            className={`rounded-xl px-3.5 py-2 text-sm font-semibold transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-lab-500 ${
              kind === catalogKind
                ? 'bg-lab-700 text-white shadow-sm'
                : 'bg-white text-slate-600 ring-1 ring-slate-200 hover:bg-slate-50'
            }`}
            key={catalogKind}
            onClick={() => selectKind(catalogKind)}
            role="tab"
            type="button"
          >
            {catalogLabels[catalogKind].plural}
          </button>
        ))}
      </div>
      <ErrorNotice message={error} />
      <form className="grid gap-3 md:grid-cols-[minmax(0,1fr)_12rem_auto]" onSubmit={handleFilter}>
        <div className="relative">
          <Search
            className="pointer-events-none absolute left-3.5 top-3 text-slate-400"
            size={16}
          />
          <input
            aria-label={`Buscar ${label.plural.toLowerCase()}`}
            className="pl-9"
            onChange={(event) => setSearch(event.target.value)}
            placeholder={`Buscar ${label.plural.toLowerCase()}`}
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
        <Button type="submit" variant="secondary">
          Filtrar
        </Button>
      </form>
      <CatalogTable
        isLoading={isLoading}
        items={items}
        kind={kind}
        onEdit={openEdit}
        onStatusChange={(item) => void changeStatus(item)}
      />
      <Pagination {...pagination} onPageChange={(page) => void loadItems(query(page))} />
      <CatalogFormModal
        catalog={catalog}
        isOpen={isFormOpen}
        isSaving={isSaving}
        item={selectedItem}
        key={`${kind}-${selectedItem?.id ?? 'new'}`}
        kind={kind}
        onClose={closeForm}
        onSubmit={saveItem}
      />
    </div>
  );
}
