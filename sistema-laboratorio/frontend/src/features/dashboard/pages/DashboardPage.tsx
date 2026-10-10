import {
  AlertTriangle,
  BriefcaseBusiness,
  ClipboardCheck,
  ClipboardList,
  Factory,
  FileCheck2,
  FlaskConical,
  PackageCheck,
  ShieldCheck,
  TestTubeDiagonal,
  UsersRound,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { Card, EmptyState, ErrorNotice, PageHeader } from '../../../shared/components/ui';
import { formatDate } from '../../../shared/lib/format';
import { ActivityTrendChart } from '../components/ActivityTrendChart';
import { CycleTimesPanel } from '../components/CycleTimesPanel';
import { DashboardFilters } from '../components/DashboardFilters';
import { DistributionCard } from '../components/DistributionCard';
import { MetricCard } from '../components/MetricCard';
import { StatusBarChart } from '../components/StatusBarChart';
import { useDashboard } from '../hooks/useDashboard';

const metricPresentation = {
  activePersonnel: { icon: BriefcaseBusiness, tone: 'bg-blue-50 text-blue-700' },
  clients: { icon: UsersRound, tone: 'bg-sky-50 text-sky-700' },
  producers: { icon: Factory, tone: 'bg-fuchsia-50 text-fuchsia-700' },
  receivedOrders: { icon: ClipboardList, tone: 'bg-violet-50 text-violet-700' },
  receivedSamples: { icon: PackageCheck, tone: 'bg-teal-50 text-teal-700' },
  activeServices: { icon: TestTubeDiagonal, tone: 'bg-indigo-50 text-indigo-700' },
  technicalOrders: { icon: ClipboardCheck, tone: 'bg-blue-50 text-blue-700' },
  pendingResults: { icon: FlaskConical, tone: 'bg-amber-50 text-amber-700' },
  assignedTests: { icon: TestTubeDiagonal, tone: 'bg-violet-50 text-violet-700' },
  corrections: { icon: AlertTriangle, tone: 'bg-rose-50 text-rose-700' },
  recordedResults: { icon: ClipboardCheck, tone: 'bg-emerald-50 text-emerald-700' },
  validationQueue: { icon: ShieldCheck, tone: 'bg-cyan-50 text-cyan-700' },
  issuedReports: { icon: FileCheck2, tone: 'bg-emerald-50 text-emerald-700' },
};

export function DashboardPage() {
  const { data, error, filters, isLoading, setFilters } = useDashboard();

  return (
    <div className="space-y-7">
      <PageHeader
        description="Indicadores, tendencias, productividad y vencimientos según los permisos de tu usuario."
        eyebrow="Centro de control"
        title="Operación del laboratorio"
      />
      <ErrorNotice message={error} />

      <DashboardFilters
        areas={data?.availableAreas ?? []}
        filters={filters}
        isLoading={isLoading}
        onApply={setFilters}
      />

      <section
        aria-label="Indicadores principales"
        className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-5"
      >
        {isLoading && !data ? (
          <p className="text-sm text-slate-500">Cargando indicadores…</p>
        ) : (
          data?.metrics.map((metric) => {
            const presentation = metricPresentation[
              metric.key as keyof typeof metricPresentation
            ] ?? {
              icon: ClipboardList,
              tone: 'bg-slate-100 text-slate-700',
            };
            return (
              <MetricCard
                context={metric.context}
                icon={presentation.icon}
                key={metric.key}
                label={metric.label}
                path={metric.path}
                tone={presentation.tone}
                trend={metric.trend}
                value={metric.value}
              />
            );
          })
        )}
      </section>

      <div className="grid gap-6 2xl:grid-cols-[minmax(0,1.7fr)_minmax(20rem,0.8fr)]">
        <Card className="p-5">
          <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
            <div>
              <h2 className="font-bold text-slate-950">
                {data?.activityTrendLabel ?? 'Actividad diaria'}
              </h2>
              <p className="mt-1 text-xs text-slate-500">
                Evolución dentro del período seleccionado.
              </p>
            </div>
            <span className="rounded-lg bg-lab-50 px-3 py-1.5 text-xs font-bold text-lab-700">
              {data?.activityTrend.reduce((sum, point) => sum + point.value, 0) ?? 0} total
            </span>
          </div>
          {data?.activityTrend.length ? (
            <ActivityTrendChart points={data.activityTrend} />
          ) : (
            <EmptyState message="No hay actividad registrada en el período." />
          )}
        </Card>

        <Card className="overflow-hidden">
          <div className="border-b border-slate-100 px-5 py-4">
            <h2 className="font-bold text-slate-950">Servicios más solicitados</h2>
            <p className="mt-1 text-xs text-slate-500">Demanda acumulada por muestra.</p>
          </div>
          {data?.topServices.length ? (
            <div className="divide-y divide-slate-100">
              {data.topServices.map((service, index) => (
                <Link
                  className="flex items-center gap-4 px-5 py-3.5 transition hover:bg-slate-50"
                  key={service.id}
                  to={service.path}
                >
                  <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-lab-50 text-xs font-bold text-lab-700">
                    {index + 1}
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-semibold text-slate-800">{service.label}</p>
                    <p className="truncate text-xs text-slate-500">{service.context}</p>
                  </div>
                  <span className="text-lg font-bold text-slate-950">{service.value}</span>
                </Link>
              ))}
            </div>
          ) : (
            <EmptyState message="Aún no hay servicios solicitados en este período." />
          )}
        </Card>
      </div>

      {data?.distributions.length ? (
        <section
          aria-label="Distribuciones operativas"
          className="grid gap-5 xl:grid-cols-2 2xl:grid-cols-4"
        >
          {data.distributions.map((distribution) => (
            <DistributionCard distribution={distribution} key={distribution.key} />
          ))}
        </section>
      ) : null}

      <div className="grid gap-6 xl:grid-cols-2">
        {data?.serviceOrderStatuses.length ? (
          <Card className="p-5">
            <div className="mb-6">
              <h2 className="font-bold text-slate-950">Estados de órdenes de servicio</h2>
              <p className="mt-1 text-xs text-slate-500">Distribución comercial del período.</p>
            </div>
            <StatusBarChart items={data.serviceOrderStatuses} />
          </Card>
        ) : null}
        {data?.workOrderStatuses.length ? (
          <Card className="p-5">
            <div className="mb-6">
              <h2 className="font-bold text-slate-950">Estados de órdenes de trabajo</h2>
              <p className="mt-1 text-xs text-slate-500">Carga técnica visible para tu rol.</p>
            </div>
            <StatusBarChart items={data.workOrderStatuses} />
          </Card>
        ) : null}
      </div>

      {data?.cycleTimes.length ? <CycleTimesPanel items={data.cycleTimes} /> : null}

      <Card className="overflow-hidden">
        <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-100 px-5 py-4">
          <div>
            <h2 className="flex items-center gap-2 font-bold text-slate-950">
              <AlertTriangle className="text-amber-600" size={18} />
              Alertas operativas
            </h2>
            <p className="mt-1 text-xs text-slate-500">
              Trabajos vencidos o próximos durante las siguientes 48 horas.
            </p>
          </div>
          <p className="text-xs text-slate-400">
            Actualizado {data ? formatDate(data.generatedAt) : '—'}
          </p>
        </div>
        {data?.alerts.length ? (
          <div className="divide-y divide-slate-100">
            {data.alerts.map((alert) => (
              <Link
                className="grid gap-3 px-5 py-4 transition hover:bg-slate-50 sm:grid-cols-[auto_minmax(0,1fr)_auto]"
                key={`${alert.type}-${alert.resourceId}`}
                to={alert.path}
              >
                <span
                  className={`mt-1 size-2.5 rounded-full ${alert.severity === 'CRITICAL' ? 'bg-rose-500' : 'bg-amber-500'}`}
                />
                <div>
                  <p className="font-semibold text-slate-900">
                    {alert.code} · {alert.title}
                  </p>
                  <p className="mt-1 text-sm text-slate-500">{alert.description}</p>
                </div>
                <time className="text-xs font-semibold text-slate-500">
                  {formatDate(alert.dueAt)}
                </time>
              </Link>
            ))}
          </div>
        ) : (
          <EmptyState message="No hay vencimientos críticos ni próximos." />
        )}
      </Card>
    </div>
  );
}
