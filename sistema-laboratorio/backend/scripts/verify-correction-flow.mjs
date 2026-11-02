import assert from "node:assert/strict";

const base = process.env.LABFLOW_TEST_API ?? "http://127.0.0.1:8081/api/v1";
assert(
  ["127.0.0.1", "localhost"].includes(new URL(base).hostname),
  "This verification creates fictional data and must run against a local test database.",
);
const suffix = Date.now().toString(36);
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
      ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  assert.equal(response.status, expected, `${method} ${path}: ${text}`);
  checks++;
  return text ? JSON.parse(text) : null;
}
assert(
  process.env.LABFLOW_TEST_PASSWORD,
  "Define LABFLOW_TEST_PASSWORD con la contraseña del administrador de la base local de pruebas.",
);
const admin = await call("/auth/login", null, {
  email: "admin@labcore.local",
  password: process.env.LABFLOW_TEST_PASSWORD,
});
const token = admin.accessToken;
const catalog = await call("/catalogs", token);
const area = catalog.areas[0];
const sampleType = catalog.sampleTypes[0];
const service = catalog.services.find(
  (item) => item.laboratoryAreaId === area.id,
);
const serviceTests = catalog.serviceTests.filter(
  (item) => item.serviceId === service.id,
);
const definition = catalog.tests.find(
  (item) => item.id === serviceTests[0].testDefinitionId,
);
const method = catalog.methods.find(
  (item) => item.testDefinitionId === definition.id,
);
assert(
  serviceTests.length >= 2,
  "Use the standard bootstrap service with at least two tests.",
);
const actor = async (name, roles) => {
  const email = `${name}-${suffix}@example.test`;
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
};
const analyst = await actor("analista", ["ANALYST"]);
const manager = await actor("responsable", ["TECHNICAL_MANAGER"]);
const outsider = await actor("responsable-no-asignado", ["TECHNICAL_MANAGER"]);
const validator = await actor("validador", ["VALIDATOR"]);
const commercial = await actor("comercial", ["COMMERCIAL"]);
const dualAnalyst = await actor("analista-validador", ["ANALYST", "VALIDATOR"]);
const product = await call("/catalogs/products", commercial.token, {
  code: `UVA-${suffix}`,
  name: `Uva prueba ${suffix}`,
});
const variety = await call("/catalogs/varieties", commercial.token, {
  productId: product.id,
  code: "RED",
  name: "Red Globe",
});
const otherProduct = await call("/catalogs/products", token, {
  code: `MAN-${suffix}`,
  name: `Manzana prueba ${suffix}`,
});
const wrongVariety = await call("/catalogs/varieties", token, {
  productId: otherProduct.id,
  code: "GALA",
  name: "Gala",
});
const client = await call(
  "/clients",
  commercial.token,
  {
    participantType: "BOTH",
    documentType: "OTHER",
    documentNumber: suffix,
    legalName: `Cliente ficticio ${suffix}`,
    email: "evaluacion@example.test",
  },
  201,
);
const requested = [
  {
    serviceId: service.id,
    testDefinitionId: definition.id,
    testMethodId: method.id,
  },
];
const sample = {
  clientIdentification: "Muestra ficticia 1",
  productId: product.id,
  productName: product.name,
  varietyId: variety.id,
  varietyName: variety.name,
  sampleTypeId: sampleType.id,
  budCount: 10,
  carrierCount: 2,
  counterSampleInCustody: false,
  requestedTests: requested,
};
const input = {
  clientId: client.id,
  laboratoryAreaId: area.id,
  deliveryMethod: "EMAIL",
  plannedDeliveryAt: new Date(Date.now() + 7 * 86400000).toISOString(),
  postAnalysisCustody: "RETURNED_TO_CLIENT",
  samples: [sample],
};
await call(
  "/service-orders",
  commercial.token,
  { ...input, samples: [{ ...sample, budCount: 1.5 }] },
  400,
);
await call(
  "/service-orders",
  commercial.token,
  { ...input, samples: [{ ...sample, budCount: -1 }] },
  400,
);
await call(
  "/service-orders",
  commercial.token,
  {
    ...input,
    samples: [
      { ...sample, varietyId: wrongVariety.id, varietyName: wrongVariety.name },
    ],
  },
  422,
);
let order = await call("/service-orders", commercial.token, input, 201);
assert.equal(order.samples[0].productId, product.id);
assert.equal(
  order.samples[0].requestedTests.length,
  1,
  "Other configured service tests remain optional for this sample.",
);
await call(`/service-orders/${order.id}/confirm`, commercial.token, {}, 204);
order = await call(`/service-orders/${order.id}`, token);
const workOrder = await call(
  "/work-orders",
  manager.token,
  {
    serviceOrderId: order.id,
    laboratoryAreaId: area.id,
    responsibleId: manager.id,
    laboratoryDueAt: new Date(Date.now() + 3 * 86400000).toISOString(),
    sampleClassifications: [
      {
        sourceSampleId: order.samples[0].id,
        technicalSampleTypeId: sampleType.id,
      },
    ],
    assignments: [
      {
        sourceSampleTestId: order.samples[0].requestedTests[0].id,
        analystId: analyst.id,
      },
    ],
  },
  201,
);
const testId = workOrder.samples[0].tests[0].id;
const resultPayload = (number) => ({
  analystObservation: "Observación ficticia de verificación",
  values: [
    {
      fieldKey: "resultado",
      label: definition.name,
      repetitionIndex: 0,
      valueNumber: number,
    },
  ],
});
await call(
  `/work-order-tests/${testId}/result`,
  analyst.token,
  resultPayload(7),
);
await call(
  `/work-order-tests/${testId}/result/validate`,
  analyst.token,
  {},
  403,
);
await call(`/work-order-tests/${testId}/result/validate`, validator.token, {
  comment: "Validación de prueba",
});
await call(
  `/work-orders/${workOrder.id}/return-to-previous`,
  outsider.token,
  { reason: "No debe permitirse" },
  403,
);
await call(
  `/work-order-tests/${testId}/result/return-to-previous`,
  commercial.token,
  { reason: "No debe permitirse" },
  403,
);
await call(`/work-orders/${workOrder.id}/close`, manager.token, {}, 204);
await call(`/service-orders/${order.id}/finalize`, token, {}, 204);
let report = await call(`/reports/service-orders/${order.id}`, token, {});
report = await call(`/reports/${report.id}/issue`, token, {});
assert.equal(report.status, "ISSUED");
await call(`/reports/${report.id}/return-to-previous`, manager.token, {}, 400);
await call(
  `/reports/${report.id}/return-to-previous`,
  outsider.token,
  { reason: "No corresponde" },
  403,
);
await call(
  `/reports/${report.id}/return-to-previous`,
  manager.token,
  { reason: "Revisar presentación del informe" },
  204,
);
assert.equal((await call(`/reports/${report.id}`, token)).status, "CANCELLED");
const reports = await call("/reports", token);
const draft = reports.content.find(
  (item) => item.serviceOrderId === order.id && item.status === "DRAFT",
);
assert(draft, "Returning issued report creates a separate draft.");
await call(`/reports/${draft.id}/issue`, token, {});
await call(
  `/work-order-tests/${testId}/result/return-to-previous`,
  manager.token,
  { reason: "Revisar valor validado" },
);
let result = await call(`/work-order-tests/${testId}/result`, analyst.token);
assert.equal(result.status, "RECORDED");
assert.equal(
  result.validation,
  null,
  "An earlier approval cannot approve the current revision.",
);
await call(
  `/work-order-tests/${testId}/result/return-to-previous`,
  manager.token,
  { reason: "Solicitar corrección de lectura" },
);
result = await call(`/work-order-tests/${testId}/result`, analyst.token);
assert.equal(result.status, "CORRECTION_REQUESTED");
await call(`/work-order-tests/${testId}/result`, analyst.token, {
  ...resultPayload(7.2),
  revisionReason: "Se corrigió la lectura original",
});
await call(`/work-order-tests/${testId}/result/validate`, validator.token, {
  comment: "Segunda validación",
});
result = await call(`/work-order-tests/${testId}/result`, token);
assert.equal(result.status, "VALIDATED");
assert.equal(result.values[0].valueNumber, 7.2);
const versions = await call(
  `/operation-versions/RESULT/${testId}`,
  analyst.token,
);
assert(
  versions.content.some((version) =>
    version.snapshot.values?.some((value) => value.valueNumber === 7),
  ),
  "The original result is still visible.",
);
await call(
  `/operation-versions/SERVICE_ORDER/${order.id}`,
  manager.token,
  undefined,
  403,
);
await call(`/work-orders/${workOrder.id}/return-to-previous`, manager.token, {
  reason: "Revisar aprobación",
});
await call(`/work-orders/${workOrder.id}/return-to-previous`, manager.token, {
  reason: "Corregir registro",
});
await call(`/work-orders/${workOrder.id}/return-to-previous`, manager.token, {
  reason: "Corregir asignación",
});
let updatedWorkOrder = await call(
  `/work-orders/${workOrder.id}`,
  manager.token,
);
assert.equal(updatedWorkOrder.status, "PENDING");
await call(`/work-orders/${workOrder.id}/correct-assignments`, manager.token, {
  reason: "Ajuste de fecha y analista de prueba",
  laboratoryDueAt: new Date(Date.now() + 2 * 86400000).toISOString(),
  assignments: [{ testId, analystId: dualAnalyst.id }],
});
await call(`/work-order-tests/${testId}/result`, dualAnalyst.token, {
  ...resultPayload(7.3),
  revisionReason: "Lectura final comprobada",
});
await call(
  `/work-order-tests/${testId}/result/validate`,
  dualAnalyst.token,
  {},
  422,
);
await call(`/work-order-tests/${testId}/result/validate`, validator.token, {});
await call(`/work-orders/${workOrder.id}/close`, manager.token, {}, 204);
await call(`/service-orders/${order.id}/finalize`, token, {}, 204);
await call(
  `/service-orders/${order.id}/return-to-commercial`,
  manager.token,
  { reason: "Ajustar descripción comercial" },
  204,
);
order = await call(`/service-orders/${order.id}`, commercial.token);
assert.equal(order.status, "COMMERCIAL_CORRECTION");
await call(`/service-orders/${order.id}/submit-correction`, commercial.token, {
  changeNote: "Revisado para prueba",
});
await call(
  `/service-orders/${order.id}/return-to-commercial`,
  manager.token,
  { reason: "Completar aclaración pendiente" },
  204,
);
order = await call(`/service-orders/${order.id}`, commercial.token);
assert.equal(order.status, "COMMERCIAL_CORRECTION");
assert.equal(order.statusBeforeCorrection, "FINALIZED");
await call(`/service-orders/${order.id}/submit-correction`, commercial.token, {
  changeNote: "Aclaración completada",
});
await call(
  `/service-orders/${order.id}/approve-correction`,
  manager.token,
  {},
  204,
);
order = await call(`/service-orders/${order.id}`, token);
assert.equal(order.status, "FINALIZED");
assert.equal(
  (await call(`/work-order-tests/${testId}/result`, token)).values[0]
    .valueNumber,
  7.3,
);
const workVersions = await call(
  `/operation-versions/WORK_ORDER/${workOrder.id}`,
  manager.token,
);
assert(workVersions.totalElements > 5);

// An incompatible commercial edit keeps the previous result available but read-only.
const replacementMethod = await call("/catalog-management/methods", token, {
  testDefinitionId: definition.id,
  code: `CAMBIO-${suffix}`,
  name: "Método alternativo de verificación",
  versionLabel: "2.0",
});
await call(
  `/service-orders/${order.id}/return-to-commercial`,
  manager.token,
  { reason: "Cambiar método conservando el resultado histórico" },
  204,
);
const changedSamples = {
  samples: [
    {
      ...sample,
      sourceSampleId: order.samples[0].id,
      requestedTests: [
        {
          ...requested[0],
          sourceRequestedTestId: order.samples[0].requestedTests[0].id,
          testMethodId: replacementMethod.id,
        },
      ],
    },
  ],
  changeReason: "Aplicar el método alternativo",
};
const impact = await call(
  `/service-orders/${order.id}/correction-impact`,
  commercial.token,
  changedSamples,
);
assert.equal(impact.requiresResultInvalidation, true);
assert.equal(impact.affectedResultCount, 1);
await call(
  `/service-orders/${order.id}/samples`,
  commercial.token,
  changedSamples,
  422,
  "PUT",
);
await call(
  `/service-orders/${order.id}/samples`,
  commercial.token,
  {
    ...changedSamples,
    confirmResultInvalidation: true,
    expectedAffectedResultCount: 0,
  },
  422,
  "PUT",
);
await call(
  `/service-orders/${order.id}/samples`,
  commercial.token,
  {
    ...changedSamples,
    confirmResultInvalidation: true,
    expectedAffectedResultCount: impact.affectedResultCount,
  },
  200,
  "PUT",
);
await call(`/service-orders/${order.id}/submit-correction`, commercial.token, {
  changeNote: "Método sustituido con confirmación del impacto",
});
await call(
  `/service-orders/${order.id}/approve-correction`,
  manager.token,
  {},
  204,
);
const archivedResult = await call(`/work-order-tests/${testId}/result`, token);
assert.equal(archivedResult.status, "INVALIDATED");
assert.equal(archivedResult.values[0].valueNumber, 7.3);
await call(
  `/work-order-tests/${testId}/result`,
  dualAnalyst.token,
  {
    ...resultPayload(8),
    revisionReason: "No debe reescribir la copia histórica",
  },
  422,
);
await call(
  `/work-order-tests/${testId}/result/validate`,
  validator.token,
  {},
  422,
);
assert.equal(
  (await call(`/work-orders/${workOrder.id}`, manager.token)).id,
  workOrder.id,
);

await call(
  `/catalog-management/methods/${replacementMethod.id}/status`,
  token,
  { active: false },
  200,
  "PATCH",
);
assert.equal(
  (await call(`/service-orders/${order.id}`, token)).samples[0]
    .requestedTests[0].testMethodName,
  replacementMethod.name,
);

for (const mode of ["REPEATABLE", "MATRIX"]) {
  const advancedTest = await call("/catalog-management/tests", token, {
    code: `${mode}-${suffix}`,
    name: `Ensayo ${mode} de verificación`,
    resultType: "NUMERIC",
  });
  const advancedMethod = await call("/catalog-management/methods", token, {
    testDefinitionId: advancedTest.id,
    code: `MET-${suffix}`,
    name: "Método de verificación",
    versionLabel: "1.0",
  });
  const advancedService = await call("/catalog-management/services", token, {
    code: `${mode}-${suffix}`,
    name: `Servicio ${mode}`,
    laboratoryAreaId: area.id,
    tests: [{ testDefinitionId: advancedTest.id, required: true }],
  });
  const fields = [
    {
      fieldKey: "lectura",
      label: "Lectura",
      resultType: "NUMERIC",
      required: true,
      displayOrder: 1,
      fieldKind: "CAPTURED",
      readOnly: false,
      calculationScope: "PER_REPETITION",
    },
  ];
  if (mode === "REPEATABLE")
    fields.push({
      fieldKey: "promedio",
      label: "Promedio",
      resultType: "NUMERIC",
      required: true,
      displayOrder: 2,
      fieldKind: "CALCULATED",
      readOnly: true,
      formula: "ROUND(AVG(lectura),2)",
      calculationScope: "AGGREGATE",
      decimalPrecision: 2,
    });
  await call("/catalog-management/result-templates", token, {
    testDefinitionId: advancedTest.id,
    sampleTypeId: sampleType.id,
    name: `Plantilla ${mode}`,
    resultMode: mode,
    minimumRepetitions: 1,
    maximumRepetitions: 5,
    defaultRows: 2,
    maximumRows: 10,
    defaultColumns: 2,
    maximumColumns: 5,
    fields,
    categories:
      mode === "MATRIX"
        ? [
            { code: "A", label: "Categoría A", displayOrder: 1 },
            { code: "B", label: "Categoría B", displayOrder: 2 },
          ]
        : [],
  });
  let advancedOrder = await call(
    "/service-orders",
    token,
    {
      ...input,
      samples: [
        {
          ...sample,
          budCount: 2,
          carrierCount: 2,
          requestedTests: [
            {
              serviceId: advancedService.id,
              testDefinitionId: advancedTest.id,
              testMethodId: advancedMethod.id,
            },
          ],
        },
      ],
    },
    201,
  );
  await call(`/service-orders/${advancedOrder.id}/confirm`, token, {}, 204);
  advancedOrder = await call(`/service-orders/${advancedOrder.id}`, token);
  const advancedWork = await call(
    "/work-orders",
    manager.token,
    {
      serviceOrderId: advancedOrder.id,
      laboratoryAreaId: area.id,
      responsibleId: manager.id,
      laboratoryDueAt: new Date(Date.now() + 3 * 86400000).toISOString(),
      sampleClassifications: [
        {
          sourceSampleId: advancedOrder.samples[0].id,
          technicalSampleTypeId: sampleType.id,
        },
      ],
      assignments: [
        {
          sourceSampleTestId: advancedOrder.samples[0].requestedTests[0].id,
          analystId: analyst.id,
        },
      ],
    },
    201,
  );
  const advancedId = advancedWork.samples[0].tests[0].id;
  const matrix = {
    rowCount: 2,
    columnCount: 2,
    columns: [0, 1].map((columnIndex) => ({
      columnIndex,
      carrierLabel: `Cargador ${columnIndex + 1}`,
      distance: 5,
      diameter: 7,
      shape: "Recta",
    })),
    cells: [0, 1, 2, 3].map((index) => ({
      rowIndex: Math.floor(index / 2),
      columnIndex: index % 2,
      categoryCode: index % 2 ? "B" : "A",
    })),
  };
  const readings = [10, 14].map((valueNumber, index) => ({
    fieldKey: "lectura",
    label: "Lectura",
    repetitionIndex: index + 1,
    valueNumber,
  }));
  if (mode === "MATRIX") {
    await call(
      `/work-order-tests/${advancedId}/result/matrix-execution/start`,
      analyst.token,
      { declaredRowCount: 1.5, declaredColumnCount: 2 },
      400,
    );
    await call(
      `/work-order-tests/${advancedId}/result/matrix-execution/start`,
      analyst.token,
      { declaredRowCount: 2, declaredColumnCount: 2 },
    );
    const partial = {
      ...matrix,
      cells: matrix.cells.map((cell, index) =>
        index === 0 ? cell : { ...cell, categoryCode: null },
      ),
    };
    const saved = await call(
      `/work-order-tests/${advancedId}/result/matrix-execution/draft`,
      analyst.token,
      { matrix: partial, analystObservation: "Avance parcial" },
    );
    assert.equal(saved.matrix.completionPercentage, 25);
    await call(
      `/work-order-tests/${advancedId}/result`,
      analyst.token,
      { values: [], matrix: partial },
      422,
    );
  }
  const payload =
    mode === "MATRIX" ? { values: [], matrix } : { values: readings };
  let advancedResult = await call(
    `/work-order-tests/${advancedId}/result`,
    analyst.token,
    payload,
  );
  if (mode === "REPEATABLE")
    assert.equal(
      advancedResult.values.find((value) => value.fieldKey === "promedio")
        .valueNumber,
      12,
    );
  await call(
    `/work-order-tests/${advancedId}/result/validate`,
    validator.token,
    {},
  );
  await call(
    `/work-order-tests/${advancedId}/result/return-to-previous`,
    manager.token,
    { reason: "Revisar la versión aprobada" },
  );
  await call(
    `/work-order-tests/${advancedId}/result/return-to-previous`,
    manager.token,
    { reason: "Corregir el resultado avanzado" },
  );
  if (mode === "MATRIX")
    matrix.cells[0] = { ...matrix.cells[0], categoryCode: "B" };
  else readings[0].valueNumber = 12;
  advancedResult = await call(
    `/work-order-tests/${advancedId}/result`,
    analyst.token,
    { ...payload, revisionReason: "Corrección avanzada documentada" },
  );
  if (mode === "REPEATABLE")
    assert.equal(
      advancedResult.values.find((value) => value.fieldKey === "promedio")
        .valueNumber,
      13,
    );
  await call(
    `/work-order-tests/${advancedId}/result/validate`,
    validator.token,
    {},
  );
  const preserved = await call(
    `/operation-versions/RESULT/${advancedId}`,
    manager.token,
  );
  assert(preserved.totalElements >= 6);
  await call(`/work-orders/${advancedWork.id}/close`, manager.token, {}, 204);
  await call(`/service-orders/${advancedOrder.id}/finalize`, token, {}, 204);
  const advancedReport = await call(
    `/reports/service-orders/${advancedOrder.id}`,
    token,
    {},
  );
  const pdfResponse = await fetch(base + `/reports/${advancedReport.id}/pdf`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  assert.equal(pdfResponse.status, 200);
  assert.equal(
    Buffer.from(await pdfResponse.arrayBuffer())
      .subarray(0, 5)
      .toString(),
    "%PDF-",
  );
  checks++;
  await call(
    `/reports/${advancedReport.id}/return-to-previous`,
    manager.token,
    { reason: "Revisar resultados antes de emitir" },
    204,
  );
  assert.equal(
    (await call(`/reports/${advancedReport.id}`, token)).status,
    "CANCELLED",
  );
  assert.equal(
    (await call(`/work-order-tests/${advancedId}/result`, token)).status,
    "RECORDED",
  );
}
await call("/dashboard/summary", token);
console.log(
  JSON.stringify(
    {
      passed: true,
      checks,
      order: order.code,
      workOrder: workOrder.code,
      resultVersions: versions.totalElements,
      workOrderVersions: workVersions.totalElements,
      localOnly: true,
    },
    null,
    2,
  ),
);
