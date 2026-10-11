import type { LucideIcon } from 'lucide-react';
import { ArrowUpRight } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Card } from '../../../shared/components/ui';

interface MetricCardProps {
  icon: LucideIcon;
  label: string;
  context: string;
  path: string;
  tone: string;
  trend: string;
  value: number;
}

export function MetricCard({
  context,
  icon: Icon,
  label,
  path,
  tone,
  trend,
  value,
}: MetricCardProps) {
  return (
    <Link className="group block min-w-0" to={path}>
      <Card className="h-full p-5 transition duration-200 group-hover:-translate-y-0.5 group-hover:border-lab-200 group-hover:shadow-lg">
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            <p className="text-sm font-semibold text-slate-600">{label}</p>
            <p className="mt-2 text-3xl font-bold tracking-tight text-slate-950">{value}</p>
          </div>
          <span className={`grid size-11 shrink-0 place-items-center rounded-xl ${tone}`}>
            <Icon size={20} />
          </span>
        </div>
        <div className="mt-4 flex items-end justify-between gap-3 border-t border-slate-100 pt-3">
          <div className="min-w-0">
            <p className="truncate text-xs text-slate-500">{context}</p>
            <p className="mt-0.5 text-[11px] font-semibold text-lab-700">{trend}</p>
          </div>
          <ArrowUpRight className="shrink-0 text-slate-300 group-hover:text-lab-600" size={16} />
        </div>
      </Card>
    </Link>
  );
}
