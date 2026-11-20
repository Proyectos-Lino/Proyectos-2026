import assert from "node:assert/strict";
import { writeFile, mkdir } from "node:fs/promises";
import { fileURLToPath } from "node:url";

const base = "http://127.0.0.1:8081/api/v1";
const suffix = Date.now().toString(36);
const output = fileURLToPath(
  new URL("../../tmp/labflow-adjustments-check/", import.meta.url),
);
if (!process.env.LABFLOW_TEST_PASSWORD) {
  throw new Error(
    "Define LABFLOW_TEST_PASSWORD con la contraseña del administrador local de pruebas.",
  );
}
let checks = 0;
async function call(
  path,
  token,
  body,
  expected = 200,
  method = body === undefined ? "GET" : "POST",
) {
  const response = await fetch(base + path, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body === undefined ? {} : { "Content-Type": "application/json" }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const content = await response.text();
  assert.equal(response.status, expected, `${method} ${path}: ${content}`);
  checks++;
  return content ? JSON.parse(content) : null;
}
const admin = await call("/auth/login", null, {
  email: "admin@labcore.local",
  password: process.env.LABFLOW_TEST_PASSWORD,
});
const token = admin.accessToken;
const catalog = await call("/catalogs", token);
const area = catalog.areas[0];
async function actor(name, roles) {
  const email = `${roles.join("-").toLowerCase()}-${suffix}@example.test`;
  const user = await call(
    "/users",
    token,
    {
      fullName: name,
      email,
      password: "PruebasLocales2026!",
      roles,
      laboratoryAreaId: area.id,
    },
    201,
  );
  const auth = await call("/auth/login", null, {
    email,
    password: "PruebasLocales2026!",
  });
  return { ...user, token: auth.accessToken };
}
const manager = await actor("Laura Medina", ["TECHNICAL_MANAGER"]);
const analyst = await actor("Diego Salazar", ["ANALYST"]);
const validator = await actor("Ana Torres", ["VALIDATOR"]);
const commercial = await actor("Lucía Rojas", ["COMMERCIAL"]);
const dual = await actor("Elena Ruiz", ["ANALYST", "VALIDATOR"]);
const buyer = await call(
  "/clients",
  commercial.token,
  {
    participantType: "CLIENT",
    documentType: "OTHER",
    documentNumber: "C-" + suffix,
    legalName: "Agroindustrial Valle Verde " + suffix,
  },
  201,
);
const producer = await call(
  "/clients",
  commercial.token,
  {
    participantType: "PRODUCER",
    documentType: "OTHER",
    documentNumber: "P-" + suffix,
    legalName: "Fundo Santa Elena " + suffix,
  },
  201,
);
const product = await call("/catalogs/products", commercial.token, {
  code: "UVA-" + suffix,
  name: "Uva de mesa " + suffix,
});
const variety = await call("/catalogs/varieties", commercial.token, {
  productId: product.id,
  code: "RG-" + suffix,
  name: "Red Globe",
});
const matrixType = await call("/catalog-management/sample-types", token, {
  code: "YEM-" + suffix,
  name: "Yemas de vid " + suffix,
  laboratoryCodePrefix: "QA",
  resultMode: "MATRIX",
});
const otherType = await call("/catalog-management/sample-types", token, {
  code: "AGU-" + suffix,
  name: "Agua de riego " + suffix,
  laboratoryCodePrefix: "QA",
  resultMode: "SCALAR",
});
const test = await call("/catalog-management/tests", token, {
  code: "YEMA-" + suffix,
  name: "Viabilidad de yemas " + suffix,
  resultType: "QUALITATIVE",
});
const method = await call("/catalog-management/methods", token, {
  testDefinitionId: test.id,
  code: "MET-" + suffix,
  name: "Evaluación visual por sección longitudinal",
  versionLabel: "1",
});
const service = await call("/catalog-management/services", token, {
  code: "VID-" + suffix,
  name: "Evaluación agronómica " + suffix,
  laboratoryAreaId: area.id,
  tests: [{ testDefinitionId: test.id, required: true }],
});
const templateInput = {
  testDefinitionId: test.id,
  sampleTypeId: matrixType.id,
  name: "Matriz de yemas " + suffix,
  resultMode: "MATRIX",
  minimumRepetitions: 1,
  defaultRows: 2,
  maximumRows: 10,
  defaultColumns: 1,
  maximumColumns: 5,
  fields: [
    {
      fieldKey: "clasificacion",
      label: "Clasificación",
      resultType: "QUALITATIVE",
      required: true,
      displayOrder: 1,
      fieldKind: "CAPTURED",
      readOnly: false,
      calculationScope: "PER_REPETITION",
    },
  ],
  categories: [
    {
      code: "V",
      label: "Viable",
      legend: "Tejido verde y estructuras conservadas.",
      displayOrder: 1,
    },
    {
      code: "NV",
      label: "No viable",
      legend: "Tejido necrótico o estructuras dañadas.",
      displayOrder: 2,
    },
  ],
  carrierShapes: ["Recto", "Curvo"],
};
await call(
  "/catalog-management/result-templates",
  commercial.token,
  templateInput,
  403,
);
await call(
  "/catalog-management/result-templates",
  token,
  { ...templateInput, carrierShapes: ["Recto", " recto "] },
  422,
);
await call(
  "/catalog-management/result-templates",
  token,
  {
    ...templateInput,
    categories: [{ ...templateInput.categories[0], legend: "x".repeat(501) }],
  },
  400,
);
const template = await call(
  "/catalog-management/result-templates",
  token,
  templateInput,
);
assert.deepEqual(template.carrierShapes, ["Recto", "Curvo"]);
assert.equal(template.categories[0].legend, templateInput.categories[0].legend);
const organization = await call("/organization-profile", token);
const organizationInput = {
  ...organization,
  contacts: organization.contacts,
  authorizedSignerIds: organization.authorizedSigners.map(
    (signer) => signer.id,
  ),
  serviceOrderFormat: {
    code: "LAB-OS-01",
    version: "02",
    approvedOn: "2026-11-17",
  },
  workOrderFormat: {
    code: "LAB-OT-01",
    version: "03",
    approvedOn: "2026-11-16",
  },
};
await call(
  "/organization-profile",
  manager.token,
  organizationInput,
  403,
  "PUT",
);
await call("/organization-profile", token, organizationInput, 200, "PUT");
const sampleInput = {
  clientIdentification: "LOTE-UVA-01",
  productId: product.id,
  productName: product.name,
  varietyId: variety.id,
  varietyName: variety.name,
  sampleTypeId: matrixType.id,
  producerName: "Productor eventual no registrado",
  budCount: 2,
  carrierCount: 1,
  quantity: 1,
  counterSampleInCustody: false,
  requestedTests: [
    {
      serviceId: service.id,
      testDefinitionId: test.id,
      testMethodId: method.id,
    },
  ],
};
const commercialInput = {
  clientId: buyer.id,
  laboratoryAreaId: area.id,
  deliveryMethod: "EMAIL",
  plannedDeliveryAt: new Date(Date.now() + 7 * 86400000).toISOString(),
  postAnalysisCustody: "RETURNED_TO_CLIENT",
};
async function orderFor(sample = sampleInput) {
  return call(
    "/service-orders",
    commercial.token,
    { ...commercialInput, samples: [sample] },
    201,
  );
}
let order = await orderFor();
assert.equal(order.samples[0].producerName, sampleInput.producerName);
assert.equal(order.samples[0].producerId, null);
await call(
  `/service-orders/${order.id}/samples`,
  commercial.token,
  {
    samples: [
      {
        ...sampleInput,
        sourceSampleId: order.samples[0].id,
        producerId: producer.id,
        producerName: producer.legalName,
      },
    ],
  },
  200,
  "PUT",
);
order = await call(`/service-orders/${order.id}`, token);
assert.equal(order.samples[0].producerId, producer.id);
await call(
  `/service-orders/${order.id}/samples`,
  commercial.token,
  {
    samples: [
      {
        ...sampleInput,
        sourceSampleId: order.samples[0].id,
        producerName: "Fundo El Olivar, propietario declarado",
      },
    ],
  },
  200,
  "PUT",
);
order = await call(`/service-orders/${order.id}`, token);
assert.equal(
  order.samples[0].producerName,
  "Fundo El Olivar, propietario declarado",
);
assert.equal(order.samples[0].producerId, null);
const linked = await orderFor({
  ...sampleInput,
  producerId: producer.id,
  producerName: undefined,
});
assert.equal(linked.samples[0].producerName, producer.legalName);
await call(`/service-orders/${order.id}/confirm`, commercial.token, {}, 204);
const orderDocument = await call(`/service-orders/${order.id}/document`, token);
assert.equal(
  orderDocument.samples[0].producerName,
  order.samples[0].producerName,
);
assert.equal(orderDocument.organization.serviceOrderFormat.code, "LAB-OS-01");
const laterFormat = {
  ...organizationInput,
  serviceOrderFormat: {
    code: "LAB-OS-02",
    version: "09",
    approvedOn: "2026-11-18",
  },
  workOrderFormat: {
    code: "LAB-OT-02",
    version: "04",
    approvedOn: "2026-11-18",
  },
};
await call("/organization-profile", token, laterFormat, 200, "PUT");
assert.equal(
  (await call(`/service-orders/${order.id}/document`, token)).organization
    .serviceOrderFormat.version,
  "02",
);
async function workFor(order, classification = matrixType.id) {
  return call(
    "/work-orders",
    manager.token,
    {
      serviceOrderId: order.id,
      laboratoryAreaId: area.id,
      responsibleId: manager.id,
      laboratoryDueAt: new Date(Date.now() + 3 * 86400000).toISOString(),
      sampleClassifications: order.samples.map((sample) => ({
        sourceSampleId: sample.id,
        technicalSampleTypeId: classification,
      })),
      assignments: order.samples.flatMap((sample) =>
        sample.requestedTests.map((requested) => ({
          sourceSampleTestId: requested.id,
          analystId: analyst.id,
        })),
      ),
    },
    201,
  );
}
const workOrder = await workFor(order);
assert.match(workOrder.code, /^OT-\d{4}-\d+$/);
assert.match(workOrder.samples[0].laboratoryCode, /^QA-\d+$/);
assert.equal(workOrder.samples[0].productName, product.name);
assert.equal(workOrder.samples[0].varietyName, variety.name);
assert.ok(!("serviceOrderCode" in workOrder));
const document = await call(
  `/work-orders/${workOrder.id}/document`,
  manager.token,
);
assert.equal(document.samples[0].productName, product.name);
assert.equal(document.samples[0].varietyName, variety.name);
assert.ok(!("serviceOrderCode" in document));
assert.equal(document.organization.workOrderFormat.version, "04");
await call("/organization-profile", token, organizationInput, 200, "PUT");
assert.equal(
  (await call(`/work-orders/${workOrder.id}/document`, token)).organization
    .workOrderFormat.version,
  "04",
);
assert.ok(
  !JSON.stringify(await call("/work-orders?size=100", manager.token)).includes(
    "serviceOrderCode",
  ),
);
const versions = await call(
  `/operation-versions/WORK_ORDER/${workOrder.id}`,
  manager.token,
);
assert.ok(!JSON.stringify(versions).includes(order.code));
const workTestId = workOrder.samples[0].tests[0].id;
await call(
  `/work-order-tests/${workTestId}/result/matrix-execution/start`,
  analyst.token,
  { declaredRowCount: 2, declaredColumnCount: 1 },
);
const initialTemplate = await call(
  `/work-order-tests/${workTestId}/result/template`,
  analyst.token,
);
assert.deepEqual(initialTemplate.carrierShapes, ["Recto", "Curvo"]);
const changedTemplate = await call(
  `/catalog-management/result-templates/${template.id}`,
  token,
  {
    ...templateInput,
    carrierShapes: ["Espiral"],
    categories: [
      {
        ...templateInput.categories[0],
        label: "Otra interpretación",
        legend: "Leyenda posterior",
        displayOrder: 1,
      },
    ],
    maximumRows: 4,
  },
  200,
  "PUT",
);
assert.deepEqual(changedTemplate.carrierShapes, ["Espiral"]);
const frozen = await call(
  `/work-order-tests/${workTestId}/result/template`,
  analyst.token,
);
assert.deepEqual(frozen.carrierShapes, ["Recto", "Curvo"]);
assert.equal(frozen.categories[0].legend, templateInput.categories[0].legend);
assert.equal(frozen.maximumRows, 10);
const matrix = {
  rowCount: 2,
  columnCount: 1,
  columns: [
    {
      columnIndex: 0,
      carrierLabel: "Cargador 1",
      distance: 12,
      diameter: 8,
      shape: "Recto",
    },
  ],
  cells: [
    { rowIndex: 0, columnIndex: 0, categoryCode: "V" },
    { rowIndex: 1, columnIndex: 0, categoryCode: "NV" },
  ],
};
await call(
  `/work-order-tests/${workTestId}/result/matrix-execution/draft`,
  analyst.token,
  {
    matrix: {
      ...matrix,
      columns: [{ ...matrix.columns[0], shape: "Forma inventada" }],
    },
  },
  422,
);
const partial = {
  ...matrix,
  cells: [matrix.cells[0], { rowIndex: 1, columnIndex: 0 }],
};
const progress = await call(
  `/work-order-tests/${workTestId}/result/matrix-execution/draft`,
  analyst.token,
  { matrix: partial },
);
assert.equal(progress.matrix.completionPercentage, 50);
await call(
  `/work-order-tests/${workTestId}/result`,
  analyst.token,
  { values: [], matrix: partial },
  422,
);
await call(`/work-order-tests/${workTestId}/result`, analyst.token, {
  values: [],
  matrix,
  analystObservation: "Evaluación de viabilidad por sección longitudinal.",
});
await call(
  `/work-order-tests/${workTestId}/result/validate`,
  analyst.token,
  {},
  403,
);
await call(`/work-order-tests/${workTestId}/result/validate`, validator.token, {
  comment: "Resultado cotejado con el registro.",
});
await call(`/work-orders/${workOrder.id}/close`, manager.token, {}, 204);
await call(`/service-orders/${order.id}/finalize`, commercial.token, {}, 204);
const report = await call(
  `/reports/service-orders/${order.id}`,
  validator.token,
  {},
);
await call(`/reports/${report.id}/issue`, manager.token, {});
const reportResponse = await fetch(`${base}/reports/${report.id}/pdf`, {
  headers: { Authorization: `Bearer ${token}` },
});
assert.equal(reportResponse.status, 200);
await mkdir(output, { recursive: true });
await writeFile(
  output + "/informe-matriz.pdf",
  Buffer.from(await reportResponse.arrayBuffer()),
);
// Concurrent classifications with two sample types sharing an abbreviation must remain unique.
const concurrentOrders = await Promise.all(
  Array.from({ length: 6 }, (_, index) =>
    orderFor({
      ...sampleInput,
      sampleTypeId: index % 2 ? otherType.id : matrixType.id,
    }),
  ),
);
await Promise.all(
  concurrentOrders.map((order) =>
    call(`/service-orders/${order.id}/confirm`, commercial.token, {}, 204),
  ),
);
const concurrentWorks = await Promise.all(
  concurrentOrders.map((order, index) =>
    workFor(order, index % 2 ? otherType.id : matrixType.id),
  ),
);
const codes = [workOrder, ...concurrentWorks].map(
  (work) => work.samples[0].laboratoryCode,
);
assert.equal(new Set(codes).size, codes.length);
const numbers = codes
  .map((code) => Number(code.split("-").at(-1)))
  .sort((first, second) => first - second);
assert.equal(numbers.at(-1) - numbers[0], numbers.length - 1);
// New matrices use the edited parameters, while the one already started retains its own.
const nextTest = concurrentWorks[0].samples[0].tests[0];
const nextTemplate = await call(
  `/work-order-tests/${nextTest.id}/result/template`,
  analyst.token,
);
assert.deepEqual(nextTemplate.carrierShapes, ["Espiral"]);
await call(
  `/catalog-management/result-templates/${template.id}`,
  token,
  templateInput,
  200,
  "PUT",
);
for (const user of [
  token,
  manager.token,
  analyst.token,
  validator.token,
  commercial.token,
  dual.token,
]) {
  await call("/dashboard/summary", user);
}
const navigationOrder = await call(
  "/service-orders",
  commercial.token,
  {
    ...commercialInput,
    samples: Array.from({ length: 10 }, (_, index) => ({
      ...sampleInput,
      clientIdentification: `LOTE-VID-${String(index + 1).padStart(2, "0")}`,
    })),
  },
  201,
);
const context = {
  passed: true,
  checks,
  suffix,
  area,
  product,
  variety,
  service,
  test,
  method,
  matrixType,
  otherType,
  template,
  templateInput,
  producer,
  buyer,
  order,
  linked,
  workOrder,
  concurrentWorks,
  navigationOrder,
  report,
  users: Object.fromEntries(
    Object.entries({ manager, analyst, validator, commercial, dual }).map(
      ([key, user]) => [key, { id: user.id, email: user.email }],
    ),
  ),
};
await writeFile(output + "/qa-context.json", JSON.stringify(context, null, 2));
console.log(
  JSON.stringify(
    {
      passed: true,
      checks,
      continuousCodes: codes,
      historicalParametersPreserved: true,
      producerNamesEditable: true,
      documentsVersioned: true,
      localOnly: true,
    },
    null,
    2,
  ),
);
