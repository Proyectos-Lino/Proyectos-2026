// Ejecuta el backend real sobre una copia aislada; nunca modifica data/ del proyecto.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { spawn } = require('node:child_process');
const net = require('node:net');
const root = path.resolve(__dirname, '..');
const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
let child;
const checks = [];
(async () => {
  fs.mkdirSync(path.join(root, '.tmp'), { recursive: true });
  const packaged = process.env.PACKAGED_EXECUTABLE;
  const isolated = fs.mkdtempSync(path.join(packaged ? require('node:os').tmpdir() : path.join(root, '.tmp'), 'release-qa-'));
  for (const name of ['server.js', 'lib', 'models', 'public']) fs.cpSync(path.join(root, name), path.join(isolated, name), { recursive: true });
  if (packaged) {
    fs.copyFileSync(packaged, path.join(isolated, 'chatbox-itil.exe'));
    fs.cpSync(path.join(path.dirname(packaged), 'node_modules'), path.join(isolated, 'node_modules'), { recursive: true });
  }
  fs.mkdirSync(path.join(isolated, 'data'));
  fs.copyFileSync(path.join(root, 'data/knowledge-base.json'), path.join(isolated, 'data/knowledge-base.json'));
  for (const name of ['tickets', 'knowledge-items']) fs.writeFileSync(path.join(isolated, 'data', name + '.json'), '[]');
  const probe = net.createServer();
  await new Promise(resolve => probe.listen(0, '127.0.0.1', resolve));
  const port = probe.address().port;
  await new Promise(resolve => probe.close(resolve));
  const base = 'http://127.0.0.1:' + port;
  let output = '';
  const executable = packaged ? path.join(isolated, 'chatbox-itil.exe') : process.execPath;
  child = spawn(executable, packaged ? [] : [path.join(isolated, 'server.js')], { cwd: root, windowsHide: true, env: { ...process.env, ...(packaged ? { PATH: '' } : {}), PORT: String(port), OPEN_BROWSER: '0', GEMINI_API_KEY: '' }, stdio: ['ignore', 'pipe', 'pipe'] });
  child.stdout.on('data', data => output += data);
  child.stderr.on('data', data => output += data);
  for (let i = 0; i < 100; i++) {
    try { if ((await fetch(base + '/api/config')).ok) break; } catch (_) {}
    if (child.exitCode !== null) throw new Error(output);
    await wait(100);
  }
  async function api(method, route, body, expected = 200) {
    const response = await fetch(base + route, { method, headers: { 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body) });
    const result = await response.json();
    assert.equal(response.status, expected, route + ': ' + JSON.stringify(result));
    return result;
  }
  for (const route of ['/', '/app.js', '/voice-input.js', '/voice-input.css', '/assets/fonts/InterVariable.woff2']) assert.equal((await fetch(base + route)).status, 200);
  assert.equal((await fetch(base + '/%2e%2e%2fdata/tickets.json')).status, 404);
  for (const route of ['/empleado.html', '/empleado.css', '/widget.js', '/widget.css']) assert.equal((await fetch(base + route)).status, 404);
  await api('POST', '/api/survey', {}, 404);
  checks.push('archivos estáticos, dictado y aislamiento de carpetas');
  const config = await api('GET', '/api/config');
  await api('POST', '/api/config/add', { listKey: 'sedes', value: 'Sede QA' });
  assert.ok((await api('GET', '/api/config')).sedes.includes('Sede QA'));
  await api('POST', '/api/config/remove', { listKey: 'sedes', value: 'Sede QA' });
  assert.ok(!(await api('GET', '/api/config')).sedes.includes('Sede QA'));
  await api('POST', '/api/kb/reload', {});
  checks.push('configuración persiste con directorio de trabajo distinto y recarga KB');
  await api('POST', '/api/tickets', { message: ' ' }, 400);
  const created = await api('POST', '/api/tickets', { message: 'Caso QA conexión de red especial', usuario: 'Operador QA', titulo: '[Red] + [VPN] + Diagnóstico de prueba' }, 201);
  const id = created.ticket.id;
  const originalLog = created.ticket.bitacora;
  const log = await api('POST', `/api/tickets/${id}/log`, { autor: 'Agente QA', texto: 'Se verifica conexión y cableado.', minutosInvertidos: 3 });
  assert.deepEqual(log.ticket.bitacora.slice(0, originalLog.length), originalLog);
  await api('POST', `/api/tickets/${id}/log`, { autor: 'Agente QA', texto: 'Inválido', minutosInvertidos: -2 }, 400);
  await api('POST', `/api/tickets/${id}/derivate`, { autor: 'Agente QA', nuevoEquipo: 'inexistente', motivo: 'Prueba' }, 400);
  const derived = await api('POST', `/api/tickets/${id}/derivate`, { autor: 'Agente QA', nuevoEquipo: 'area_tecnica', motivo: 'Revisar el enlace.' });
  assert.equal(derived.ticket.estado, 'derivado');
  assert.deepEqual(derived.ticket.bitacora.slice(0, originalLog.length), originalLog);
  assert.equal((await api('GET', '/api/tickets?estado=derivado&equipoAsignado=area_tecnica')).length, 1);
  await api('POST', `/api/tickets/${id}/close`, { autor: 'Agente QA', texto: 'Solución' }, 400);
  await api('POST', `/api/tickets/${id}/close`, { autor: 'Agente QA', subcategoriaTecnica: config.subcategoriasTecnicas[0], texto: '' }, 400);
  const closed = await api('POST', `/api/tickets/${id}/close`, { autor: 'Agente QA', texto: 'Se reemplazó el cable y validó la red.', subcategoriaTecnica: config.subcategoriasTecnicas[0], minutosInvertidos: 5 });
  assert.equal(closed.ticket.estado, 'cerrado');
  assert.equal(closed.ticket.resueltoEnPrimerContacto, false);
  assert.ok(Number.isFinite(closed.ticket.tfrMinutos) && Number.isFinite(closed.ticket.ttrMinutos));
  assert.equal(closed.ticket.bitacora.length, originalLog.length + 3);
  await api('POST', `/api/tickets/${id}/derivate`, { nuevoEquipo: 'mesa_de_ayuda' }, 409);
  const similar = await api('GET', '/api/tickets/similar?query=cable&equipoAsignado=area_tecnica');
  assert.equal(similar[0].id, id);
  assert.deepEqual(similar[0].bitacora, closed.ticket.bitacora);
  const second = await api('POST', '/api/tickets', { message: 'Otra consulta QA', usuario: 'Operador QA' }, 201);
  await api('POST', `/api/tickets/${second.ticket.id}/escalate`, {});
  const metrics = await api('GET', '/api/metrics');
  assert.equal(metrics.totalTickets, 2);
  assert.equal(metrics.satisfaccionPromedio, undefined);
  checks.push('registro, validación, bitácora íntegra, derivación, cierre, filtros, similares, métricas y ausencia del portal retirado');
  const content = 'Procedimiento QA para restablecer conexión de red.';
  const knowledge = await api('POST', '/api/knowledge', { titulo: 'Guía QA de red', contenido: content, tipo: 'documento', adjuntos: [{ nombre: 'guia.txt', base64: Buffer.from(content).toString('base64') }] }, 201);
  const item = knowledge.item;
  await api('POST', '/api/knowledge', { titulo: item.titulo, contenido: content }, 409);
  const download = await fetch(base + `/api/knowledge/${item.id}/attachments/${item.adjuntos[0].id}`);
  assert.equal(await download.text(), content);
  assert.equal((await api('GET', '/api/knowledge?q=guia')).length, 1);
  await api('PUT', '/api/knowledge/' + item.id, { titulo: 'Guía QA revisada', contenido: content + ' Revisado.' });
  assert.equal((await api('GET', '/api/knowledge/' + item.id)).adjuntos.length, 1);
  assert.equal((await api('GET', '/api/knowledge/stats')).total, 1);
  const extracted = await api('POST', '/api/knowledge/extract-file', { fileName: 'guia.txt', fileBase64: Buffer.from(content).toString('base64') });
  assert.equal(extracted.textoExtraido, content);
  const pdfFile = path.join(root, 'tests', 'fixtures', 'pdf-texto.pdf');
  if (fs.existsSync(pdfFile)) {
    const pdf = await api('POST', '/api/knowledge/extract-file', { fileName: 'prueba.pdf', fileBase64: fs.readFileSync(pdfFile).toString('base64') });
    assert.ok(pdf.textoExtraido.includes('PDF QA VERIFICADO'));
  }
  const zip = new (require('jszip'))();
  zip.file('[Content_Types].xml', '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>');
  zip.file('_rels/.rels', '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>');
  zip.file('word/document.xml', '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>DOCX QA VERIFICADO</w:t></w:r></w:p></w:body></w:document>');
  const docx = await api('POST', '/api/knowledge/extract-file', { fileName: 'prueba.docx', fileBase64: await zip.generateAsync({ type: 'base64' }) });
  assert.ok(docx.textoExtraido.includes('DOCX QA VERIFICADO'));
  const assistant = await api('POST', '/api/asistente-tecnico/mensaje', { mensaje: 'red', historial: [] });
  assert.ok(assistant.respuesta.includes('no configurada'));
  await api('DELETE', '/api/knowledge/' + item.id);
  assert.equal((await api('GET', '/api/knowledge')).length, 0);
  assert.equal((await fetch(base + `/api/knowledge/${item.id}/attachments/${item.adjuntos[0].id}`)).status, 404);
  checks.push('CRUD conocimiento, duplicados, búsqueda, adjuntos, extracción TXT/PDF/DOCX y respuesta sin clave IA');
  fs.mkdirSync(path.join(root, 'deliverables'), { recursive: true });
  const report = { result: 'PASS', date: new Date().toISOString(), checks, isolated };
  fs.writeFileSync(path.join(root, 'deliverables', packaged ? 'verificacion-exe.json' : 'verificacion-backend.json'), JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report, null, 2));
})().catch(error => { console.error(error); process.exitCode = 1; }).finally(() => child?.kill());
