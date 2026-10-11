import { Plus, Search } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Button, ErrorNotice, PageHeader, Pagination } from '../../../shared/components/ui';
import type { LaboratoryArea } from '../../../shared/types/domain';
import { AreaFormModal } from '../components/AreaFormModal';
import { AreasTable } from '../components/AreasTable';
import { useAreas } from '../hooks/useAreas';

export function AreasPage() {
  const { areas, changeStatus, error, isLoading, isSaving, loadAreas, pagination, saveArea } =
    useAreas();
  const [search, setSearch] = useState('');
  const [selectedArea, setSelectedArea] = useState<LaboratoryArea | null>(null);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [active, setActive] = useState('');

  function openCreate() {
    setSelectedArea(null);
    setIsFormOpen(true);
  }
  function openEdit(area: LaboratoryArea) {
    setSelectedArea(area);
    setIsFormOpen(true);
  }

  function closeForm() {
    setIsFormOpen(false);
    setSelectedArea(null);
  }

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void loadAreas({ active: active === '' ? undefined : active === 'true', search });
  }

  function changePage(page: number) {
    void loadAreas({ active: active === '' ? undefined : active === 'true', page, search });
  }

  return (
    <div className="space-y-7">
      <PageHeader
        actions={
          <Button onClick={openCreate}>
            <Plus size={17} />
            Nueva área
          </Button>
        }
        description="Configura los laboratorios o áreas técnicas que recibirán muestras y usuarios."
        eyebrow="Administración"
        title="Áreas de laboratorio"
      />
      <ErrorNotice message={error} />
      <form className="grid gap-3 md:grid-cols-[minmax(0,1fr)_12rem_auto]" onSubmit={submitSearch}>
        <div className="relative">
          <Search
            className="pointer-events-none absolute left-3.5 top-3 text-slate-400"
            size={16}
          />
          <input
            aria-label="Buscar área"
            className="pl-9"
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Buscar por código o nombre"
            value={search}
          />
        </div>
        <select
          aria-label="Filtrar por estado"
          onChange={(event) => setActive(event.target.value)}
          value={active}
        >
          <option value="">Todas las áreas</option>
          <option value="true">Activas</option>
          <option value="false">Inactivas</option>
        </select>
        <Button type="submit" variant="secondary">
          Filtrar
        </Button>
      </form>
      <AreasTable
        areas={areas}
        isLoading={isLoading}
        onEdit={openEdit}
        onStatusChange={(area) => void changeStatus(area)}
      />
      <Pagination {...pagination} onPageChange={changePage} />
      <AreaFormModal
        area={selectedArea}
        isOpen={isFormOpen}
        isSaving={isSaving}
        key={selectedArea?.id ?? 'new'}
        onClose={closeForm}
        onSubmit={saveArea}
      />
    </div>
  );
}
