import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Button } from './Button';

interface PaginationProps {
  page: number;
  totalElements: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}

export function Pagination({ page, totalElements, totalPages, onPageChange }: PaginationProps) {
  if (totalPages <= 1) {
    return totalElements > 0 ? (
      <p className="text-sm text-slate-500">{totalElements} registro(s) encontrado(s).</p>
    ) : null;
  }

  return (
    <nav aria-label="Paginación" className="flex flex-wrap items-center justify-between gap-3">
      <p className="text-sm text-slate-500">
        Página {page + 1} de {totalPages} · {totalElements} registros
      </p>
      <div className="flex gap-2">
        <Button disabled={page === 0} onClick={() => onPageChange(page - 1)} variant="secondary">
          <ChevronLeft size={16} />
          Anterior
        </Button>
        <Button
          disabled={page >= totalPages - 1}
          onClick={() => onPageChange(page + 1)}
          variant="secondary"
        >
          Siguiente
          <ChevronRight size={16} />
        </Button>
      </div>
    </nav>
  );
}
