import { request, requestBlob, toQuery } from '../../../shared/api/http-client';
import type { PageResponse } from '../../../shared/types/domain';

export type CatalogKind =
  | 'units'
  | 'sample-types'
  | 'tests'
  | 'methods'
  | 'result-templates'
  | 'services'
  | 'products'
  | 'varieties';

export interface TemplateField {
  id?: string;
  fieldKey: string;
  label: string;
  resultType: 'NUMERIC' | 'TEXT' | 'QUALITATIVE';
  measurementUnitId?: string;
  required: boolean;
  displayOrder: number;
  fieldKind: 'CAPTURED' | 'CALCULATED' | 'INFORMATIONAL';
  readOnly: boolean;
  formula?: string;
  calculationScope: 'PER_REPETITION' | 'AGGREGATE';
  decimalPrecision?: number;
  informationText?: string;
  defaultValue?: string;
}

export interface FormulaPreviewValue {
  fieldKey: string;
  repetitionIndex: number;
  formula: string;
  inputs: Record<string, number[]>;
  value: number;
}

export interface TemplateCategory {
  id?: string;
  code: string;
  label: string;
  displayOrder: number;
}

export interface ServiceTestConfiguration {
  testDefinitionId: string;
  testDefinitionName?: string;
  sampleTypeId?: string;
  sampleTypeName?: string;
  displayOrder?: number;
  required: boolean;
}

export interface ManagedCatalogItem {
  id: string;
  active: boolean;
  createdAt: string;
  code?: string;
  name: string;
  description?: string;
  symbol?: string;
  laboratoryCodePrefix?: string;
  resultMode?: 'SCALAR' | 'REPEATABLE' | 'MATRIX';
  resultType?: 'NUMERIC' | 'TEXT' | 'QUALITATIVE';
  resultUnitId?: string;
  sampleTypeId?: string;
  sampleTypeName?: string;
  testDefinitionId?: string;
  testDefinitionName?: string;
  versionLabel?: string;
  laboratoryAreaId?: string;
  laboratoryAreaName?: string;
  productId?: string;
  productName?: string;
  tests?: ServiceTestConfiguration[];
  fields?: TemplateField[];
  categories?: TemplateCategory[];
  minimumRepetitions?: number;
  maximumRepetitions?: number;
  defaultRows?: number;
  maximumRows?: number;
  defaultColumns?: number;
  maximumColumns?: number;
}

export interface CatalogQuery {
  active?: boolean;
  page?: number;
  search?: string;
}

export type CatalogPayload = Record<string, unknown>;

export function getCatalogItems(
  kind: CatalogKind,
  query: CatalogQuery = {},
): Promise<PageResponse<ManagedCatalogItem>> {
  return request<PageResponse<ManagedCatalogItem>>(
    `/catalog-management/${kind}${toQuery({
      active: query.active === undefined ? undefined : String(query.active),
      page: query.page ?? 0,
      search: query.search,
      size: 10,
    })}`,
  );
}

export function createCatalogItem(
  kind: CatalogKind,
  payload: CatalogPayload,
): Promise<ManagedCatalogItem> {
  return request<ManagedCatalogItem>(`/catalog-management/${kind}`, {
    body: JSON.stringify(payload),
    method: 'POST',
  });
}

export function updateCatalogItem(
  kind: CatalogKind,
  id: string,
  payload: CatalogPayload,
): Promise<ManagedCatalogItem> {
  return request<ManagedCatalogItem>(`/catalog-management/${kind}/${id}`, {
    body: JSON.stringify(payload),
    method: 'PUT',
  });
}

export function updateCatalogItemStatus(
  kind: CatalogKind,
  id: string,
  active: boolean,
): Promise<ManagedCatalogItem> {
  return request<ManagedCatalogItem>(`/catalog-management/${kind}/${id}/status`, {
    body: JSON.stringify({ active }),
    method: 'PATCH',
  });
}

export function previewTemplateFormulas(
  fields: TemplateField[],
  sampleValues: Record<string, number[]>,
): Promise<{ values: FormulaPreviewValue[] }> {
  return request<{ values: FormulaPreviewValue[] }>(
    '/catalog-management/result-templates/formula-preview',
    {
      body: JSON.stringify({ fields, sampleValues }),
      method: 'POST',
    },
  );
}

export async function exportCatalog(kind: CatalogKind): Promise<void> {
  const blob = await requestBlob(`/catalog-management/export${toQuery({ type: kind })}`);
  const downloadUrl = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = downloadUrl;
  anchor.download = `catalogo-${kind}.csv`;
  anchor.click();
  URL.revokeObjectURL(downloadUrl);
}
