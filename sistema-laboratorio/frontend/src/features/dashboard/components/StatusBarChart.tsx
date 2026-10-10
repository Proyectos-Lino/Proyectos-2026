interface StatusItem {
  status: string;
  label: string;
  value: number;
}

export function StatusBarChart({ items }: { items: StatusItem[] }) {
  const maximum = Math.max(1, ...items.map((item) => item.value));

  return (
    <div className="space-y-4">
      {items.map((item) => (
        <div key={item.status}>
          <div className="mb-1.5 flex items-center justify-between gap-3 text-sm">
            <span className="font-medium text-slate-600">{item.label}</span>
            <span className="font-bold text-slate-900">{item.value}</span>
          </div>
          <div className="h-2.5 overflow-hidden rounded-full bg-slate-100">
            <div
              aria-label={`${item.label}: ${item.value}`}
              className="h-full min-w-1 rounded-full bg-gradient-to-r from-lab-500 to-lab-700"
              role="img"
              style={{ width: `${(item.value / maximum) * 100}%` }}
            />
          </div>
        </div>
      ))}
    </div>
  );
}
