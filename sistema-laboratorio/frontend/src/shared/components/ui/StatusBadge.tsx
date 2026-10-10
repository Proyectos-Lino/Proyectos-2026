const palette = {
  completed: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  pending: 'bg-sky-50 text-sky-700 ring-sky-600/20',
  recorded: 'bg-amber-50 text-amber-700 ring-amber-600/20',
  cancelled: 'bg-rose-50 text-rose-700 ring-rose-600/20',
};

function getPalette(value: string): string {
  const normalized = value.toUpperCase();

  if (
    normalized.includes('VALID') ||
    normalized.includes('FINAL') ||
    normalized.includes('CLOSED') ||
    normalized.includes('EMIT')
  ) {
    return palette.completed;
  }

  if (normalized.includes('CANCEL') || normalized.includes('ANUL')) {
    return palette.cancelled;
  }

  if (normalized.includes('RECORDED') || normalized.includes('REGISTERED')) {
    return palette.recorded;
  }

  return palette.pending;
}

export function StatusBadge({ value }: { value: string }) {
  return (
    <span
      className={`inline-flex whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-bold ring-1 ring-inset ${getPalette(value)}`}
    >
      {value.replaceAll('_', ' ')}
    </span>
  );
}
