import type { CatalogKind } from '../api/catalog-management-api';

export const catalogLabels: Record<CatalogKind, { singular: string; plural: string }> = {
  units: { singular: 'unidad de medida', plural: 'Unidades de medida' },
  'sample-types': { singular: 'tipo de muestra', plural: 'Tipos de muestra' },
  tests: { singular: 'ensayo', plural: 'Ensayos' },
  methods: { singular: 'método', plural: 'Métodos' },
  'result-templates': { singular: 'plantilla de resultados', plural: 'Plantillas de resultados' },
  services: { singular: 'servicio', plural: 'Servicios' },
  products: { singular: 'producto', plural: 'Productos' },
  varieties: { singular: 'variedad', plural: 'Variedades' },
};
