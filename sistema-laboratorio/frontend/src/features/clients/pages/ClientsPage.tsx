import { Download, Plus, Search } from 'lucide-react';
import { useMemo, useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Button, ErrorNotice, PageHeader, Pagination } from '../../../shared/components/ui';
import type { Client, DocumentType, ParticipantType } from '../../../shared/types/domain';
import { ClientFormModal } from '../components/ClientFormModal';
import { ClientHistoryModal } from '../components/ClientHistoryModal';
import { ClientsTable } from '../components/ClientsTable';
import { useClients } from '../hooks/useClients';
import { exportClients, type ClientFilters } from '../api/clients-api';
import { downloadBlob } from '../../../shared/lib/downloads';
import { documentTypeLabels } from '../../../shared/lib/labels';

const documentTypes: DocumentType[] = ['RUC', 'DNI', 'PASSPORT', 'OTHER'];

export function ClientsPage() {
  const [searchParams] = useSearchParams();
  const initialFilters = useMemo<ClientFilters>(
    () => ({
      participantType: (searchParams.get('participantType') as ParticipantType | null) ?? '',
    }),
    [searchParams],
  );
  const {
    changeClientStatus,
    clients,
    error,
    isLoading,
    isSaving,
    loadClients,
    pageResponse,
    saveClient,
  } = useClients(initialFilters);
  const [search, setSearch] = useState('');
  const [documentType, setDocumentType] = useState<DocumentType | ''>('');
  const [participantType, setParticipantType] = useState<ParticipantType | ''>(
    initialFilters.participantType ?? '',
  );
  const [active, setActive] = useState<'true' | 'false' | ''>('');
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingClient, setEditingClient] = useState<Client | null>(null);
  const [historyClient, setHistoryClient] = useState<Client | null>(null);

  function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void loadClients({ search, documentType, participantType, active });
  }

  function openNewClientForm() {
    setEditingClient(null);
    setIsFormOpen(true);
  }

  function openEditClientForm(client: Client) {
    setEditingClient(client);
    setIsFormOpen(true);
  }

  function loadPage(page: number) {
    void loadClients({ search, documentType, participantType, active, page });
  }

  async function exportDirectory() {
    const blob = await exportClients({ search, documentType, participantType, active });
    downloadBlob(blob, 'clientes-productores.csv');
  }

  return (
    <div className="space-y-7">
      <PageHeader
        actions={
          <div className="flex flex-wrap gap-2">
            <Button onClick={() => void exportDirectory()} variant="secondary">
              <Download size={17} />
              Exportar CSV
            </Button>
            <Button onClick={openNewClientForm}>
              <Plus size={17} />
              Nuevo registro
            </Button>
          </div>
        }
        description="Datos reutilizables para órdenes de servicio e informes de ensayo."
        eyebrow="Directorio comercial"
        title="Clientes y productores"
      />

      <ErrorNotice message={error} />

      <section className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
        <div>
          <h2 className="font-bold text-slate-900">Directorio de participantes</h2>
          <p className="mt-1 text-xs text-slate-500">
            Busca por RUC, DNI, nombre, correo o persona de contacto.
          </p>
        </div>
        <form
          className="grid w-full gap-3 lg:grid-cols-[minmax(14rem,1fr)_11rem_12rem_10rem_auto]"
          onSubmit={handleSearch}
        >
          <div className="relative">
            <Search
              aria-hidden="true"
              className="pointer-events-none absolute left-3.5 top-3 text-slate-400"
              size={16}
            />
            <input
              aria-label="Buscar participante"
              className="w-full pl-9"
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Buscar cliente o productor"
              value={search}
            />
          </div>
          <select
            aria-label="Filtrar por tipo de documento"
            onChange={(event) => setDocumentType(event.target.value as DocumentType | '')}
            value={documentType}
          >
            <option value="">Todos los documentos</option>
            {documentTypes.map((type) => (
              <option key={type} value={type}>
                {documentTypeLabels[type]}
              </option>
            ))}
          </select>
          <select
            aria-label="Filtrar por clasificación"
            onChange={(event) => setParticipantType(event.target.value as ParticipantType | '')}
            value={participantType}
          >
            <option value="">Todas las clasificaciones</option>
            <option value="CLIENT">Clientes</option>
            <option value="PRODUCER">Productores</option>
            <option value="BOTH">Solo ambos</option>
          </select>
          <select
            aria-label="Filtrar por estado"
            onChange={(event) => setActive(event.target.value as 'true' | 'false' | '')}
            value={active}
          >
            <option value="">Todos los estados</option>
            <option value="true">Activos</option>
            <option value="false">Inactivos</option>
          </select>
          <Button type="submit" variant="secondary">
            Buscar
          </Button>
        </form>
      </section>

      <ClientsTable
        clients={clients}
        isLoading={isLoading}
        onEdit={openEditClientForm}
        onHistory={setHistoryClient}
        onStatusChange={(client) => void changeClientStatus(client)}
      />
      {pageResponse ? (
        <Pagination
          onPageChange={loadPage}
          page={pageResponse.page}
          totalElements={pageResponse.totalElements}
          totalPages={pageResponse.totalPages}
        />
      ) : null}
      <ClientFormModal
        client={editingClient}
        isOpen={isFormOpen}
        isSaving={isSaving}
        key={editingClient?.id ?? 'new-client'}
        onClose={() => {
          setIsFormOpen(false);
          setEditingClient(null);
        }}
        onSubmit={saveClient}
      />
      <ClientHistoryModal
        client={historyClient}
        key={historyClient?.id ?? 'no-client-history'}
        onClose={() => setHistoryClient(null)}
      />
    </div>
  );
}
