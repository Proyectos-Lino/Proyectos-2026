import { Clock3 } from 'lucide-react';
import { Card } from '../../../shared/components/ui';
import type { DashboardSummary } from '../../../shared/types/domain';

function durationLabel(hours?: number) {
  if (hours === undefined || hours === null) return 'Sin datos';
  if (hours < 1) return `${Math.round(hours * 60)} min`;
  if (hours < 48) return `${hours.toFixed(1)} h`;
  return `${(hours / 24).toFixed(1)} días`;
}

export function CycleTimesPanel({ items }: { items: DashboardSummary['cycleTimes'] }) {
  return (
    <Card className="overflow-hidden">
      <div className="border-b border-slate-100 px-5 py-4">
        <h2 className="flex items-center gap-2 font-bold text-slate-950">
          <Clock3 className="text-lab-600" size={18} />
          Tiempos promedio del proceso
        </h2>
        <p className="mt-1 text-xs text-slate-500">
          Velocidad entre los principales hitos operativos.
        </p>
      </div>
      <div className="grid sm:grid-cols-2">
        {items.map((item) => (
          <div className="border-b border-slate-100 p-5 odd:sm:border-r" key={item.key}>
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
              {item.label}
            </p>
            <p className="mt-2 text-2xl font-bold text-slate-950">
              {durationLabel(item.averageHours)}
            </p>
            <p className="mt-1 text-xs text-slate-500">{item.context}</p>
          </div>
        ))}
      </div>
    </Card>
  );
}
