import type { WizardSampleForm } from './order-wizard';

export type CommonDataGroup = 'date' | 'origin' | 'product';

export function copyCommonSampleData(
  source: WizardSampleForm,
  target: WizardSampleForm,
  groups: CommonDataGroup[],
  overwrite: boolean,
): WizardSampleForm {
  const updated = { ...target };
  if (groups.includes('date') && source.sampledAt && (overwrite || !target.sampledAt))
    updated.sampledAt = source.sampledAt;
  if (groups.includes('origin')) {
    for (const field of ['samplingLocation', 'origin', 'samplerName'] as const) {
      if (source[field] && (overwrite || !target[field])) updated[field] = source[field];
    }
  }
  if (
    groups.includes('origin') &&
    (source.producerId || source.producerName) &&
    (overwrite || !(target.producerId || target.producerName))
  ) {
    updated.producerId = source.producerId;
    updated.producerName = source.producerName;
  }
  if (groups.includes('product') && source.productId && (overwrite || !target.productId)) {
    updated.productId = source.productId;
    updated.productName = source.productName;
    updated.varietyId = source.varietyId;
    updated.varietyName = source.varietyName;
  }
  return updated;
}
