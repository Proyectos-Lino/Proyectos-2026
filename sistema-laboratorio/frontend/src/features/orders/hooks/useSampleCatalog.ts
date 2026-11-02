import { useMemo, useState } from 'react';
import { request } from '../../../shared/api/http-client';
import type {
  CatalogSnapshot,
  CatalogItem as Product,
  ProductVariety,
} from '../../../shared/types/domain';

export function useSampleCatalog(catalog: CatalogSnapshot | null) {
  const [products, setProducts] = useState<Product[]>([]);
  const [varieties, setVarieties] = useState<ProductVariety[]>([]);
  const merged = useMemo(
    () =>
      catalog
        ? {
            ...catalog,
            products: [
              ...catalog.products.filter((item) => !products.some((added) => added.id === item.id)),
              ...products,
            ],
            varieties: [
              ...catalog.varieties.filter(
                (item) => !varieties.some((added) => added.id === item.id),
              ),
              ...varieties,
            ],
          }
        : null,
    [catalog, products, varieties],
  );

  async function createProduct(input: { code: string; name: string }) {
    const item = await request<Product>('/catalogs/products', {
      method: 'POST',
      body: JSON.stringify(input),
    });
    setProducts((current) => [...current, item]);
    return item;
  }

  async function createVariety(input: { productId: string; code: string; name: string }) {
    const item = await request<ProductVariety>('/catalogs/varieties', {
      method: 'POST',
      body: JSON.stringify(input),
    });
    setVarieties((current) => [...current, item]);
    return item;
  }

  return { catalog: merged, createProduct, createVariety };
}
