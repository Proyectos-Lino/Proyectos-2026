import type { DashboardSummary } from '../../../shared/types/domain';

interface ActivityTrendChartProps {
  points: DashboardSummary['activityTrend'];
}

export function ActivityTrendChart({ points }: ActivityTrendChartProps) {
  const maximum = Math.max(1, ...points.map((point) => point.value));
  const visibleLabels = points.length <= 14 ? points : points.filter((_, index) => index % 5 === 0);

  return (
    <div>
      <div
        aria-label={`Actividad diaria. Máximo ${maximum}.`}
        className="flex h-48 items-end gap-1.5 rounded-2xl bg-gradient-to-b from-lab-50/70 to-white px-4 pb-3 pt-5"
        role="img"
      >
        {points.map((point) => (
          <div className="group relative flex h-full min-w-0 flex-1 items-end" key={point.date}>
            <div
              className="w-full min-w-1 rounded-t-md bg-gradient-to-t from-lab-700 to-lab-400 transition group-hover:from-lab-800 group-hover:to-lab-500"
              style={{ height: `${Math.max(point.value ? 8 : 2, (point.value / maximum) * 100)}%` }}
            />
            <span className="pointer-events-none absolute -top-1 left-1/2 z-10 hidden -translate-x-1/2 -translate-y-full whitespace-nowrap rounded-lg bg-slate-950 px-2 py-1 text-[11px] font-semibold text-white group-hover:block">
              {new Intl.DateTimeFormat('es-PE', { day: 'numeric', month: 'short' }).format(
                new Date(`${point.date}T12:00:00`),
              )}{' '}
              · {point.value}
            </span>
          </div>
        ))}
      </div>
      <div className="mt-2 flex justify-between text-[11px] font-medium text-slate-400">
        {visibleLabels.map((point) => (
          <span key={point.date}>
            {new Intl.DateTimeFormat('es-PE', { day: 'numeric', month: 'short' }).format(
              new Date(`${point.date}T12:00:00`),
            )}
          </span>
        ))}
      </div>
    </div>
  );
}
