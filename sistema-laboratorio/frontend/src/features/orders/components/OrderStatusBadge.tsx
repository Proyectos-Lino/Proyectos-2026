import { StatusBadge } from '../../../shared/components/ui';
import type { ServiceOrderStatus } from '../../../shared/types/domain';
import { serviceOrderStatusLabels } from '../model/order-labels';

export function OrderStatusBadge({ status }: { status: ServiceOrderStatus }) {
  return <StatusBadge value={serviceOrderStatusLabels[status]} />;
}
