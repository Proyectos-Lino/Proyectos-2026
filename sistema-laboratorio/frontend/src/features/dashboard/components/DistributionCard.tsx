import { Card, EmptyState } from '../../../shared/components/ui';
import type { DashboardSummary } from '../../../shared/types/domain';

interface DistributionCardProps {
  distribution: DashboardSummary['distributions'][number];
}

export function DistributionCard({ distribution }: DistributionCardProps) {
  const maximum = Math.max(1, ...distribution.items.map((item) => item.value));
  const total = distribution.items.reduce((sum, item) => sum + item.value, 0);

  return (
    <Card className="min-w-0 p-5">
      <div className="mb-5 flex items-start justify-between gap-3">
        <div>
          <h3 className="font-bold text-slate-950">{distribution.title}</h3>
          <p className="mt-1 text-xs text-slate-500">Distribución del período seleccionado</p>
        </div>
        <span className="rounded-lg bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-600">
          {total}
        </span>
      </div>
      {distribution.items.length ? (
        <div className="space-y-3.5">
          {distribution.items.slice(0, 6).map((item) => (
            <div key={item.key}>
              <div className="mb-1.5 flex justify-between gap-3 text-xs">
                <span className="truncate font-medium text-slate-600">{item.label}</span>
                <span className="font-bold text-slate-900">{item.value}</span>
              </div>
              <div className="h-2 overflow-hidden rounded-full bg-slate-100">
                <div
                  className="h-full min-w-1 rounded-full bg-gradient-to-r from-lab-400 to-lab-700"
                  style={{ width: `${(item.value / maximum) * 100}%` }}
                />
              </div>
            </div>
          ))}
        </div>
      ) : (
        <EmptyState message="Sin información para este período." />
      )}
    </Card>
  );
}
