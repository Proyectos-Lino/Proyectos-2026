import { ClipboardPaste, X } from 'lucide-react';
import { useMemo, useState } from 'react';
import { Button } from '../../../shared/components/ui';
import type { InitialResultTemplate } from '../../../shared/types/domain';

interface MatrixBulkPastePanelProps {
  categories: InitialResultTemplate['categories'];
  columnCount: number;
  disabled: boolean;
  onApply: (values: string[][]) => void;
  rowCount: number;
}

export function MatrixBulkPastePanel({
  categories,
  columnCount,
  disabled,
  onApply,
  rowCount,
}: MatrixBulkPastePanelProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [source, setSource] = useState('');
  const [error, setError] = useState<string | null>(null);
  const categoryByInput = useMemo(
    () =>
      new Map<string, string>(
        categories.flatMap((category): Array<[string, string]> => [
          [category.code.trim().toLocaleLowerCase(), category.code],
          [category.label.trim().toLocaleLowerCase(), category.code],
        ]),
      ),
    [categories],
  );

  function applyPaste() {
    const rows = source
      .replace(/\r/g, '')
      .split('\n')
      .filter((row, index, values) => row.length > 0 || index < values.length - 1)
      .map((row) => row.split('\t').map((value) => value.trim()));
    if (!rows.length || rows.every((row) => row.every((value) => !value))) {
      setError('Pega al menos una celda de la tabla.');
      return;
    }
    if (rows.length > rowCount || rows.some((row) => row.length > columnCount)) {
      setError(`La tabla pegada no puede superar ${rowCount} filas y ${columnCount} columnas.`);
      return;
    }
    const normalized = categories.length
      ? rows.map((row) =>
          row.map((value) => (value ? categoryByInput.get(value.toLocaleLowerCase()) : '')),
        )
      : rows;
    if (normalized.some((row) => row.some((value) => value === undefined))) {
      setError('Una o más categorías no coinciden con los códigos o nombres configurados.');
      return;
    }
    onApply(normalized as string[][]);
    setError(null);
    setSource('');
    setIsOpen(false);
  }

  if (!isOpen) {
    return (
      <Button disabled={disabled} onClick={() => setIsOpen(true)} type="button" variant="secondary">
        <ClipboardPaste size={15} />
        Pegar tabla
      </Button>
    );
  }

  return (
    <section className="basis-full rounded-2xl border border-sky-200 bg-sky-50/70 p-4">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h4 className="text-sm font-bold text-sky-950">Pegado masivo desde Excel</h4>
          <p className="mt-1 text-xs leading-5 text-sky-800">
            Copia únicamente las celdas de resultados, sin encabezados. Se aceptan hasta {rowCount}{' '}
            filas por {columnCount} columnas.
          </p>
        </div>
        <button
          aria-label="Cerrar pegado masivo"
          className="rounded-lg p-1 text-sky-700 hover:bg-sky-100"
          onClick={() => setIsOpen(false)}
          type="button"
        >
          <X size={17} />
        </button>
      </div>
      <textarea
        aria-label="Datos tabulares para pegar en la matriz"
        autoFocus
        className="mt-3 min-h-28 bg-white font-mono text-sm"
        onChange={(event) => setSource(event.target.value)}
        onPaste={() => setError(null)}
        placeholder={'Categoría A\tCategoría B\nCategoría C\tCategoría A'}
        value={source}
      />
      {error ? (
        <p aria-live="polite" className="mt-2 text-xs font-semibold text-rose-700">
          {error}
        </p>
      ) : null}
      <div className="mt-3 flex justify-end gap-2">
        <Button onClick={() => setIsOpen(false)} type="button" variant="ghost">
          Cancelar
        </Button>
        <Button onClick={applyPaste} type="button">
          Aplicar a la matriz
        </Button>
      </div>
    </section>
  );
}
