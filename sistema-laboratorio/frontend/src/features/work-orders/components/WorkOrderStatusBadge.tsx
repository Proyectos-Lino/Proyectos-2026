import type { WorkOrderStatus } from '../../../shared/types/domain';
import { workOrderStatusLabels } from '../model/work-order-labels';

interface WorkOrderStatusBadgeProps {
  status: WorkOrderStatus;
}

const tones: Record<WorkOrderStatus, string> = {
  PENDING: 'bg-sky-50 text-sky-700 ring-sky-600/20',
  IN_PROGRESS: 'bg-amber-50 text-amber-700 ring-amber-600/20',
  COMMERCIAL_CORRECTION: 'bg-orange-50 text-orange-700 ring-orange-600/20',
  RESULTS_RECORDED: 'bg-violet-50 text-violet-700 ring-violet-600/20',
  VALIDATED: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  CLOSED: 'bg-slate-100 text-slate-700 ring-slate-500/20',
  CANCELLED: 'bg-rose-50 text-rose-700 ring-rose-600/20',
};

export function WorkOrderStatusBadge({ status }: WorkOrderStatusBadgeProps) {
  return (
    <span
      className={`inline-flex whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-bold ring-1 ring-inset ${tones[status]}`}
    >
      {workOrderStatusLabels[status]}
    </span>
  );
}
