import { Plus, Trash2 } from 'lucide-react';
import { Button } from '../../../shared/components/ui';
import type { TemplateCategory } from '../api/catalog-management-api';

interface Props {
  categories: TemplateCategory[];
  carrierShapes: string[];
  onChangeCategories: (categories: TemplateCategory[]) => void;
  onChangeShapes: (shapes: string[]) => void;
}

export function MatrixClassificationEditor({
  categories,
  carrierShapes,
  onChangeCategories,
  onChangeShapes,
}: Props) {
  function updateCategory(index: number, changes: Partial<TemplateCategory>) {
    onChangeCategories(
      categories.map((category, position) =>
        position === index ? { ...category, ...changes } : category,
      ),
    );
  }

  return (
    <div className="space-y-5 border-t border-emerald-200 pt-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h3 className="font-semibold text-slate-900">Categorías y leyendas</h3>
        <Button
          onClick={() =>
            onChangeCategories([
              ...categories,
              {
                code: `CAT_${categories.length + 1}`,
                label: '',
                displayOrder: categories.length + 1,
              },
            ])
          }
          type="button"
          variant="secondary"
        >
          <Plus size={15} /> Categoría
        </Button>
      </div>
      {categories.map((category, index) => (
        <div
          className="space-y-3 rounded-xl border border-emerald-200 bg-white p-3"
          key={category.id ?? index}
        >
          <div className="grid grid-cols-[minmax(0,1fr)_minmax(0,2fr)_auto] gap-2">
            <label className="space-y-1 text-sm">
              Código
              <input
                aria-label={`Código de categoría ${index + 1}`}
                maxLength={50}
                required
                value={category.code}
                onChange={(event) => updateCategory(index, { code: event.target.value })}
              />
            </label>
            <label className="space-y-1 text-sm">
              Nombre
              <input
                aria-label={`Nombre de categoría ${index + 1}`}
                maxLength={120}
                required
                value={category.label}
                onChange={(event) => updateCategory(index, { label: event.target.value })}
              />
            </label>
            <Button
              aria-label={`Eliminar categoría ${index + 1}`}
              onClick={() =>
                onChangeCategories(categories.filter((_, position) => position !== index))
              }
              type="button"
              variant="ghost"
            >
              <Trash2 size={16} />
            </Button>
          </div>
          <label className="block space-y-1 text-sm">
            Leyenda o significado
            <textarea
              aria-label={`Leyenda de categoría ${index + 1}`}
              maxLength={500}
              rows={2}
              value={category.legend ?? ''}
              onChange={(event) => updateCategory(index, { legend: event.target.value })}
            />
          </label>
        </div>
      ))}
      <label className="block space-y-2">
        <span className="font-semibold text-slate-900">Formas de cargador permitidas</span>
        <textarea
          aria-label="Formas de cargador permitidas"
          rows={4}
          value={carrierShapes.join('\n')}
          onChange={(event) => onChangeShapes(event.target.value.split('\n'))}
        />
        <span className="block text-xs text-slate-600">
          Escribe una forma por línea, hasta 50 opciones de 120 caracteres. Una lista vacía permite
          escribir la forma libremente.
        </span>
      </label>
    </div>
  );
}
