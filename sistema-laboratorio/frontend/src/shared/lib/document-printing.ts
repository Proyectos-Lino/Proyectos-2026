import type {
  OrganizationIdentity,
  ServiceOrderDocument,
  WorkOrderDocument,
} from '../types/domain';
import { formatDate } from './format';

type DocumentField = readonly [label: string, value?: string | number | null];

const postAnalysisCustodyLabels = {
  DISPOSED: 'Desechar según procedimiento',
  OTHER: 'Otra condición registrada',
  RETAINED: 'Mantener en custodia',
  RETURNED_TO_CLIENT: 'Devolver al cliente',
};

const documentStyles = `
  @page {
    size: Letter;
    margin: 12mm 13mm 15mm;
  }

  :root {
    color: #172033;
    font-family: Arial, Helvetica, sans-serif;
  }

  * {
    box-sizing: border-box;
  }

  body {
    margin: 0;
    background: #ffffff;
    color: #172033;
    font-size: 10pt;
    line-height: 1.45;
  }

  .document {
    width: 100%;
  }

  .document-header {
    display: grid;
    grid-template-columns: 30% minmax(0, 1fr);
    gap: 0;
    align-items: stretch;
    border: 1px solid #202020;
  }

  .brand {
    display: flex;
    align-items: center;
    justify-content: center;
    border-right: 1px solid #202020;
    padding: 10px;
    color: #111827;
    font-size: 10pt;
    font-weight: 800;
    letter-spacing: 0.13em;
    text-transform: uppercase;
  }

  .brand-logo {
    display: inline-block;
    max-width: 150px;
    max-height: 54px;
    margin-right: 8px;
    object-fit: contain;
  }

  .document-title {
    margin: 0;
    color: #0f172a;
    font-size: 19pt;
    line-height: 1.15;
  }

  .document-subtitle,
  .document-meta {
    margin: 5px 0 0;
    color: #64748b;
    font-size: 8.5pt;
  }

  .document-code {
    padding: 10px 14px;
    text-align: center;
  }

  .document-code span {
    display: block;
    color: #334155;
    font-size: 8pt;
    font-weight: 800;
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }

  .document-code strong {
    display: block;
    margin-top: 2px;
    color: #0f172a;
    font-size: 11pt;
  }

  .laboratory-band,
  .report-band {
    border: 1px solid #202020;
    border-top: 0;
    padding: 6px 10px;
    text-align: center;
    font-weight: 800;
  }

  .laboratory-band {
    background: #e3e3e3;
    font-size: 11pt;
    text-transform: uppercase;
  }

  .report-band {
    background: #fff;
    font-size: 12pt;
  }

  .section {
    margin-top: 18px;
  }

  .section-heading {
    margin: 0;
    border: 1px solid #202020;
    background: #e3e3e3;
    padding: 6px 10px;
    color: #0f172a;
    font-size: 10pt;
    text-align: center;
    text-transform: uppercase;
  }

  .section-note {
    margin: -2px 0 9px;
    color: #64748b;
    font-size: 8.5pt;
  }

  .field-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    overflow: hidden;
    border: 1px solid #202020;
    border-top: 0;
  }

  .field {
    min-width: 0;
    border-bottom: 1px solid #a8a8a8;
    padding: 9px 10px;
  }

  .field:nth-last-child(-n + 2) {
    border-bottom: 0;
  }

  .field:nth-child(odd) {
    border-right: 1px solid #a8a8a8;
  }

  .field-label {
    color: #64748b;
    font-size: 7.5pt;
    font-weight: 800;
    letter-spacing: 0.055em;
    text-transform: uppercase;
  }

  .field-value {
    margin-top: 3px;
    color: #1e293b;
    font-size: 9pt;
    overflow-wrap: anywhere;
  }

  .notice {
    border-left: 3px solid #0f766e;
    background: #f8fafc;
    padding: 10px 12px;
    color: #334155;
    white-space: pre-wrap;
  }

  .sample {
    margin-top: 13px;
    border: 1px solid #202020;
    overflow: hidden;
  }

  .sample-header {
    display: flex;
    justify-content: space-between;
    gap: 14px;
    background: #e3e3e3;
    border-bottom: 1px solid #202020;
    padding: 10px 12px;
  }

  .sample-title {
    margin: 0;
    color: #0f172a;
    font-size: 10.5pt;
  }

  .sample-caption {
    margin: 3px 0 0;
    color: #64748b;
    font-size: 8.5pt;
  }

  .sample-identifier {
    color: #0f766e;
    font-size: 8.5pt;
    font-weight: 700;
    text-align: right;
  }

  .sample-content {
    padding: 12px;
  }

  .sample .field-grid {
    border: 0;
    border-radius: 0;
  }

  .sample .field {
    padding: 7px 8px;
  }

  .table-wrap {
    margin-top: 12px;
  }

  table {
    width: 100%;
    border-collapse: collapse;
    font-size: 8.5pt;
  }

  th {
    background: #d8d8d8;
    color: #111827;
    font-size: 7.5pt;
    letter-spacing: 0.045em;
    text-align: left;
    text-transform: uppercase;
  }

  th,
  td {
    border: 1px solid #555;
    padding: 7px 8px;
    vertical-align: top;
  }

  td {
    color: #334155;
  }

  .signature-row {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 34px;
    margin-top: 42px;
  }

  .signature {
    border-top: 1px solid #64748b;
    padding-top: 6px;
    color: #475569;
    font-size: 8.5pt;
    text-align: center;
  }

  .document-footer {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    border-top: 1px solid #cbd5e1;
    margin-top: 22px;
    padding-top: 8px;
    color: #64748b;
    font-size: 7.5pt;
  }

  .restricted {
    color: #0f766e;
    font-size: 8pt;
    font-weight: 700;
  }

  @media print {
    body {
      -webkit-print-color-adjust: exact;
      print-color-adjust: exact;
    }

    .no-break {
      break-inside: avoid-page;
      page-break-inside: avoid;
    }

    thead {
      display: table-header-group;
    }

    tr {
      break-inside: avoid-page;
      page-break-inside: avoid;
    }
  }
`;

function escapeHtml(value: string | number | null | undefined): string {
  const text = String(value ?? 'No especificado');

  return text
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;')
    .replaceAll('\n', '<br />');
}

function fieldGrid(fields: DocumentField[]): string {
  return `<div class="field-grid">${fields
    .map(
      ([label, value]) => `
        <div class="field">
          <div class="field-label">${escapeHtml(label)}</div>
          <div class="field-value">${escapeHtml(value)}</div>
        </div>`,
    )
    .join('')}</div>`;
}

function pageHeader(
  type: string,
  code: string,
  generatedAt: string,
  version: number,
  subtitle: string,
  organization?: OrganizationIdentity,
): string {
  const institutionalIdentity = [
    organization?.documentLabel,
    organization?.address,
    organization?.contacts.map((contact) => contact.value).join(' · '),
  ]
    .filter(Boolean)
    .map((value) => escapeHtml(value))
    .join('<br />');
  return `
    <header class="document-header no-break">
      <div class="brand">
        ${
          organization?.logoDataUrl
            ? `<img alt="" class="brand-logo" src="${escapeHtml(organization.logoDataUrl)}" />`
            : ''
        }
        ${escapeHtml(organization?.commercialName ?? 'Gestión de laboratorio')}
      </div>
      <div class="document-code">
        <strong>${escapeHtml(organization?.legalName ?? organization?.commercialName ?? 'Laboratorio')}</strong>
        <span>${institutionalIdentity || 'Documento institucional de laboratorio'}</span>
      </div>
    </header>
    <div class="laboratory-band">${escapeHtml(subtitle)}</div>
    <div class="report-band no-break">
      <h1 class="document-title">${escapeHtml(type)} · ${escapeHtml(code)}</h1>
      <p class="document-meta">Versión ${escapeHtml(version)} · Emitido el ${escapeHtml(
        formatDate(generatedAt),
      )}</p>
    </div>`;
}

function signatures(leftLabel: string, rightLabel: string): string {
  return `
    <div class="signature-row no-break">
      <div class="signature">${escapeHtml(leftLabel)}</div>
      <div class="signature">${escapeHtml(rightLabel)}</div>
    </div>`;
}

function authorizedSignerName(organization?: OrganizationIdentity): string {
  return (
    [...(organization?.authorizedSigners ?? [])].sort(
      (first, second) => first.displayOrder - second.displayOrder,
    )[0]?.fullName ?? 'Responsable autorizado del laboratorio'
  );
}

function footer(code: string, organization?: OrganizationIdentity): string {
  const legal = [
    organization?.legalText,
    organization?.accreditationName,
    organization?.accreditationCode,
  ]
    .filter(Boolean)
    .join(' · ');
  const identity = [
    organization?.legalName,
    organization?.documentLabel,
    organization?.address,
    organization?.contacts.map((contact) => contact.value).join(' · '),
  ]
    .filter(Boolean)
    .join(' · ');
  return `
    <footer class="document-footer">
      <span>Documento controlado · ${escapeHtml(organization?.commercialName ?? 'Laboratorio')}${
        identity ? `<br />${escapeHtml(identity)}` : ''
      }${legal ? `<br />${escapeHtml(legal)}` : ''}</span>
      <span>${escapeHtml(code)}</span>
    </footer>`;
}

function openPrintWindow(
  title: string,
  documentBody: string,
  organization?: OrganizationIdentity,
): boolean {
  const printWindow = window.open('', '_blank', 'popup,width=980,height=760');

  if (!printWindow) {
    return false;
  }

  printWindow.document.open();
  printWindow.document.write(`
    <!doctype html>
    <html lang="es">
      <head>
        <meta charset="utf-8" />
        <meta content="width=device-width, initial-scale=1" name="viewport" />
        <title>${escapeHtml(title)}</title>
        <style>
          :root { --document-primary: ${organization?.primaryColor ?? '#0f766e'}; }
          ${documentStyles.replaceAll('#0f766e', 'var(--document-primary)')}
        </style>
      </head>
      <body>${documentBody}</body>
    </html>`);
  printWindow.document.close();

  window.setTimeout(() => {
    printWindow.focus();
    printWindow.print();
  }, 120);

  return true;
}

export function printServiceOrderDocument(document: ServiceOrderDocument): boolean {
  const samples = document.samples
    .map((sample) => {
      const quantity =
        sample.quantity === undefined
          ? undefined
          : `${sample.quantity} ${sample.measurementUnitName ?? ''}`.trim();
      const tests = sample.requestedTests
        .map(
          (test) => `
            <tr>
              <td>${escapeHtml(test.serviceName)}</td>
              <td>${escapeHtml(test.testName)}</td>
              <td>${escapeHtml(test.methodName)}</td>
            </tr>`,
        )
        .join('');

      return `
        <section class="sample">
          <header class="sample-header">
            <div>
              <h3 class="sample-title">Muestra ${escapeHtml(sample.commercialSequence)} · ${escapeHtml(
                sample.productName,
              )}</h3>
              <p class="sample-caption">${escapeHtml(sample.sampleTypeName)}${
                sample.varietyName ? ` · ${escapeHtml(sample.varietyName)}` : ''
              }</p>
            </div>
            <div class="sample-identifier">Identificación: ${escapeHtml(
              sample.clientIdentification,
            )}</div>
          </header>
          <div class="sample-content">
            ${fieldGrid([
              ['Descripción física', sample.physicalDescription],
              ['Productor / propietario', sample.producerName],
              ['Documento del productor', sample.producerDocument],
              ['Lugar de muestreo', sample.samplingLocation],
              ['Origen / procedencia', sample.origin],
              ['Muestreado por', sample.samplerName],
              ['Fecha de muestreo', sample.sampledAt ? formatDate(sample.sampledAt) : undefined],
              ['Cantidad recibida', quantity],
              ['Recipiente', sample.containerDescription],
              ['Lote', sample.batchNumber],
              ['Año de producción', sample.productionYear],
              ['Marca', sample.brand],
              ['Certificado de origen', sample.originCertificate],
              ['Registro sanitario', sample.sanitaryRegistration],
              ['Edad del cultivo', sample.cropAge],
              ['Número de cargadores', sample.carrierCount],
              ['Número de yemas', sample.budCount],
              ['Contramuestra', sample.counterSampleInCustody ? 'En custodia' : 'No registrada'],
            ])}
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Servicio</th>
                    <th>Ensayo solicitado</th>
                    <th>Método</th>
                  </tr>
                </thead>
                <tbody>${tests}</tbody>
              </table>
            </div>
            ${
              sample.observations
                ? `<div class="notice"><strong>Observaciones de la muestra:</strong><br />${escapeHtml(
                    sample.observations,
                  )}</div>`
                : ''
            }
          </div>
        </section>`;
    })
    .join('');

  const delivery =
    document.deliveryMethod === 'EMAIL' ? 'Correo electrónico' : 'Recojo en laboratorio';
  const body = `
    <main class="document">
      ${pageHeader(
        'Orden de servicio',
        document.code,
        document.generatedAt,
        document.version,
        `${document.laboratoryAreaName} · Gestión comercial y recepción`,
        document.organization,
      )}
      <section class="section no-break">
        <h2 class="section-heading">Solicitante y datos de informe</h2>
        ${fieldGrid([
          ['Cliente / razón social', document.clientName],
          ['Documento', document.clientDocument],
          ['Nombre para informe', document.reportName],
          ['Correo de informe', document.reportEmail],
          ['Dirección de informe', document.reportAddress],
          ['Área de laboratorio', document.laboratoryAreaName],
        ])}
      </section>
      <section class="section no-break">
        <h2 class="section-heading">Condiciones del servicio</h2>
        ${fieldGrid([
          ['Entrega programada', formatDate(document.plannedDeliveryAt)],
          ['Modalidad de entrega', delivery],
          ['Persona que recoge', document.pickupPerson],
          ['Número de proforma', document.proformaNumber],
          ['Propuesta técnica', document.technicalProposalNumber],
          [
            'Plazo de desarrollo',
            document.developmentDays === undefined
              ? undefined
              : `${document.developmentDays} día(s)`,
          ],
          ['Modalidad del servicio', document.outsourced ? 'Subcontratado' : 'Interno'],
          ['Custodia posensayo', postAnalysisCustodyLabels[document.postAnalysisCustody]],
        ])}
      </section>
      ${
        document.generalObservations
          ? `<section class="section no-break">
              <h2 class="section-heading">Observaciones generales</h2>
              <div class="notice">${escapeHtml(document.generalObservations)}</div>
            </section>`
          : ''
      }
      <section class="section">
        <h2 class="section-heading">Muestras y ensayos solicitados</h2>
        <p class="section-note">La información de cada muestra conserva la trazabilidad registrada al finalizar la orden.</p>
        ${samples}
      </section>
      ${signatures(
        `Recepción y conformidad · ${authorizedSignerName(document.organization)}`,
        'Conformidad del solicitante',
      )}
      ${footer(document.code, document.organization)}
    </main>`;

  return openPrintWindow(`Orden de servicio ${document.code}`, body, document.organization);
}

export function printWorkOrderDocument(document: WorkOrderDocument): boolean {
  const samples = document.samples
    .map(
      (sample) => `
        <section class="sample no-break">
          <header class="sample-header">
            <div>
              <h3 class="sample-title">Muestra técnica ${escapeHtml(sample.laboratoryCode)}</h3>
              <p class="sample-caption">${escapeHtml(sample.sampleTypeName)}</p>
            </div>
            <div class="sample-identifier">${escapeHtml(sample.tests.length)} ensayo(s) asignado(s)</div>
          </header>
          <div class="sample-content">
            <table>
              <thead>
                <tr>
                  <th>Ensayo</th>
                  <th>Método</th>
                  <th>Analista asignado</th>
                </tr>
              </thead>
              <tbody>
                ${sample.tests
                  .map(
                    (test) => `
                      <tr>
                        <td>${escapeHtml(test.testName)}</td>
                        <td>${escapeHtml(test.methodName)}</td>
                        <td>${escapeHtml(test.analystName)}</td>
                      </tr>`,
                  )
                  .join('')}
              </tbody>
            </table>
          </div>
        </section>`,
    )
    .join('');

  const body = `
    <main class="document">
      ${pageHeader(
        'Orden de trabajo',
        document.code,
        document.generatedAt,
        document.version,
        document.laboratoryAreaName,
        document.organization,
      )}
      <section class="section no-break">
        <p class="restricted">Documento técnico restringido. No contiene datos personales ni comerciales del solicitante.</p>
        ${fieldGrid([
          ['Orden de servicio de referencia', document.serviceOrderCode],
          ['Área técnica', document.laboratoryAreaName],
          ['Responsable técnico', document.responsibleName],
          ['Fecha límite de laboratorio', formatDate(document.laboratoryDueAt)],
        ])}
      </section>
      <section class="section">
        <h2 class="section-heading">Muestras, ensayos y asignaciones</h2>
        <p class="section-note">Utilice exclusivamente los códigos técnicos consignados en este documento durante el procesamiento.</p>
        ${samples}
      </section>
      ${signatures(
        `Responsable técnico · ${document.responsibleName}`,
        'Confirmación del analista asignado',
      )}
      ${footer(document.code, document.organization)}
    </main>`;

  return openPrintWindow(`Orden de trabajo ${document.code}`, body, document.organization);
}
