import { ClipboardPenLine, FlaskConical } from 'lucide-react';
import { useState } from 'react';
import { useAuth } from '../../auth/hooks/useAuth';
import { Card, ErrorNotice, PageHeader } from '../../../shared/components/ui';
import type { TechnicalResultTask } from '../../../shared/types/domain';
import { ResultEditorModal } from '../components/ResultEditorModal';
import { ResultQueueTable } from '../components/ResultQueueTable';
import { useResultQueue } from '../hooks/useResultQueue';
import { toWorkOrderTest } from '../model/result-task';

export function ResultEntryPage() {
  const { hasRole } = useAuth();
  const { catalog, error, isLoading, loadQueue, queue } = useResultQueue('ENTRY');
  const [selectedTask, setSelectedTask] = useState<TechnicalResultTask | null>(null);
  const selectedDefinition = catalog?.tests.find(
    (definition) => definition.id === selectedTask?.testDefinitionId,
  );

  function refreshQueue() {
    void loadQueue(queue?.page ?? 0);
  }

  return (
    <div className="space-y-7">
      <PageHeader
        description="Registra resultados de los ensayos que te fueron asignados, con observaciones y correcciones auditadas."
        eyebrow="Operación analítica"
        title="Registro de resultados"
      />

      <ErrorNotice message={error} />

      <section className="grid gap-4 sm:grid-cols-2">
        <Card className="flex items-center gap-4 p-5">
          <span className="grid size-11 place-items-center rounded-2xl bg-lab-50 text-lab-700">
            <ClipboardPenLine size={21} />
          </span>
          <div>
            <p className="text-2xl font-bold text-slate-950">{queue?.totalElements ?? 0}</p>
            <p className="mt-1 text-sm text-slate-500">Ensayos pendientes o corregibles</p>
          </div>
        </Card>
        <Card className="flex items-center gap-4 p-5">
          <span className="grid size-11 place-items-center rounded-2xl bg-sky-50 text-sky-700">
            <FlaskConical size={21} />
          </span>
          <p className="text-sm leading-6 text-slate-600">
            Esta vista utiliza únicamente códigos técnicos, sin información comercial del cliente.
          </p>
        </Card>
      </section>

      <Card className="p-0">
        <header className="border-b border-slate-100 px-5 py-4">
          <h2 className="font-bold text-slate-900">Bandeja de registro</h2>
          <p className="mt-1 text-sm text-slate-500">
            Selecciona un ensayo para registrar o corregir sus valores.
          </p>
        </header>
        <div className="p-5">
          <ResultQueueTable
            action="RECORD"
            isLoading={isLoading}
            onOpen={setSelectedTask}
            onPageChange={(page) => void loadQueue(page)}
            queue={queue}
          />
        </div>
      </Card>

      <ResultEditorModal
        canRecord={hasRole('ADMIN', 'ANALYST')}
        canValidate={false}
        definition={selectedDefinition}
        isOpen={selectedTask !== null}
        onClose={() => setSelectedTask(null)}
        onSaved={refreshQueue}
        test={selectedTask ? toWorkOrderTest(selectedTask) : null}
        testName={selectedTask?.testName ?? ''}
        units={catalog?.units ?? []}
      />
    </div>
  );
}
