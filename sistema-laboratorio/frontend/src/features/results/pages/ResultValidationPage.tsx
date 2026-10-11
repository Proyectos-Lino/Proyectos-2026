import { BadgeCheck, ShieldCheck } from 'lucide-react';
import { useState } from 'react';
import { useAuth } from '../../auth/hooks/useAuth';
import { Card, ErrorNotice, PageHeader } from '../../../shared/components/ui';
import type { TechnicalResultTask } from '../../../shared/types/domain';
import { ResultEditorModal } from '../components/ResultEditorModal';
import { ResultQueueTable } from '../components/ResultQueueTable';
import { useResultQueue } from '../hooks/useResultQueue';
import { toWorkOrderTest } from '../model/result-task';

export function ResultValidationPage() {
  const { hasRole } = useAuth();
  const { catalog, error, isLoading, loadQueue, queue } = useResultQueue('VALIDATION');
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
        description="Revisa resultados registrados por otro usuario, consulta sus correcciones y valida solo cuando estén conformes."
        eyebrow="Aseguramiento técnico"
        title="Validación de resultados"
      />

      <ErrorNotice message={error} />

      <section className="grid gap-4 sm:grid-cols-2">
        <Card className="flex items-center gap-4 p-5">
          <span className="grid size-11 place-items-center rounded-2xl bg-violet-50 text-violet-700">
            <BadgeCheck size={21} />
          </span>
          <div>
            <p className="text-2xl font-bold text-slate-950">{queue?.totalElements ?? 0}</p>
            <p className="mt-1 text-sm text-slate-500">Resultados pendientes de validación</p>
          </div>
        </Card>
        <Card className="flex items-center gap-4 p-5">
          <span className="grid size-11 place-items-center rounded-2xl bg-emerald-50 text-emerald-700">
            <ShieldCheck size={21} />
          </span>
          <p className="text-sm leading-6 text-slate-600">
            La validación está separada del registro: el backend impide validar un resultado
            ingresado por el mismo usuario.
          </p>
        </Card>
      </section>

      <Card className="p-0">
        <header className="border-b border-slate-100 px-5 py-4">
          <h2 className="font-bold text-slate-900">Bandeja de revisión</h2>
          <p className="mt-1 text-sm text-slate-500">
            Abre un resultado para revisar sus valores, observaciones y correcciones antes de
            validarlo.
          </p>
        </header>
        <div className="p-5">
          <ResultQueueTable
            action="VALIDATE"
            isLoading={isLoading}
            onOpen={setSelectedTask}
            onPageChange={(page) => void loadQueue(page)}
            queue={queue}
          />
        </div>
      </Card>

      <ResultEditorModal
        canRecord={false}
        canValidate={hasRole('ADMIN', 'VALIDATOR')}
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
