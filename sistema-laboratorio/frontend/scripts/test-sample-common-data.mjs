import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';

const source = await readFile(
  new URL('../src/features/orders/model/sample-common-data.ts', import.meta.url),
  'utf8',
);
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.ESNext },
}).outputText;
const { copyCommonSampleData } = await import(
  `data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`
);
const sourceSample = {
  productId: 'uva',
  productName: 'Uva',
  varietyId: 'red',
  varietyName: 'Red Globe',
  sampledAt: '2026-11-02T10:00',
  origin: 'Ica',
  clientIdentification: 'M-01',
  quantity: '3',
  requestedTests: ['ph'],
};

test('fills empty common fields without overwriting existing data or sample identity', () => {
  const target = {
    productId: '',
    varietyId: '',
    sampledAt: '2026-11-01T09:00',
    origin: '',
    clientIdentification: 'M-10',
    quantity: '7',
    requestedTests: ['acidez'],
  };
  const result = copyCommonSampleData(sourceSample, target, ['date', 'origin', 'product'], false);
  assert.equal(result.sampledAt, target.sampledAt);
  assert.equal(result.origin, 'Ica');
  assert.equal(result.productId, 'uva');
  assert.equal(result.varietyId, 'red');
  assert.equal(result.clientIdentification, 'M-10');
  assert.equal(result.quantity, '7');
  assert.deepEqual(result.requestedTests, ['acidez']);
  assert.equal(target.origin, '');
});

test('replaces only explicitly selected groups and keeps product and variety together', () => {
  const target = { productId: 'manzana', varietyId: 'gala', sampledAt: '', origin: 'Lima' };
  assert.equal(copyCommonSampleData(sourceSample, target, ['product'], false).varietyId, 'gala');
  const result = copyCommonSampleData(sourceSample, target, ['date', 'product'], true);
  assert.equal(result.productId, 'uva');
  assert.equal(result.varietyId, 'red');
  assert.equal(result.sampledAt, sourceSample.sampledAt);
  assert.equal(result.origin, 'Lima');
});
