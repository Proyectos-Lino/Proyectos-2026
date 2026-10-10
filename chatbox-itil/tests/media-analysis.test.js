const { test } = require('node:test');
const assert = require('node:assert/strict');
const { analyzeVideo, youtubeUrl, validateAccess, withDeadline } = require('../lib/videoService');
const { extractTextFromFile } = require('../lib/documentService');
test('PDF válido conserva texto con el lector basado en Uint8Array', async () => {
  const pdf = require('node:fs').readFileSync(require('node:path').join(__dirname, 'fixtures', 'pdf-texto.pdf'));
  const result = await extractTextFromFile(pdf.toString('base64'), 'documento.pdf');
  assert.ok(result.textoExtraido.includes('PDF QA VERIFICADO'));
});
const valid = () => ({ suggestion: { contenido: 'Procedimiento comprobado' }, validacion: {
  visual: { estado: 'procesado', evidencia: 'Pantalla de configuración en 00:10' },
  audio: { estado: 'procesado', evidencia: 'Narración de los pasos' },
  habla: { estado: 'procesado', evidencia: 'Abrir configuración' },
  temporal: { estado: 'procesado', evidencia: 'Paso en 00:10' }, duracionSegundos: 30, timestamps: ['00:10']
} });
function mock(states = ['ACTIVE'], outputs = [valid()]) {
  const calls = [];
  let stateIndex = 0, outputIndex = 0;
  const file = () => ({ name: 'files/test', uri: 'https://example.test/file', mimeType: 'video/mp4', state: states[stateIndex++] || states.at(-1) });
  return { calls, ai: { files: {
    upload: async args => { calls.push(['upload', args]); return file(); },
    get: async args => { calls.push(['get', args]); return file(); },
    delete: async args => { calls.push(['delete', args]); }
  }, interactions: { create: async args => { calls.push(['create', args]); return { output_text: JSON.stringify(outputs[outputIndex++] || outputs.at(-1)) }; } } } };
}
test('YouTube acepta formatos públicos y rechaza dominios falsos', () => {
  for (const url of ['https://youtu.be/b3ZFwi6vRNk', 'https://www.youtube.com/watch?v=b3ZFwi6vRNk&list=x', 'https://m.youtube.com/shorts/b3ZFwi6vRNk']) assert.equal(youtubeUrl(url), 'https://www.youtube.com/watch?v=b3ZFwi6vRNk');
  for (const url of ['https://youtube.com.evil.test/watch?v=b3ZFwi6vRNk', 'https://evil.test/youtu.be/b3ZFwi6vRNk', 'ftp://youtu.be/b3ZFwi6vRNk', 'https://youtube.com/playlist?list=x']) assert.throws(() => youtubeUrl(url));
});
test('Files API procesa videos mayores de 25 MB sin inlineData y elimina el temporal', async () => {
  const m = mock(['PROCESSING', 'PROCESSING', 'ACTIVE']);
  const buffer = Buffer.alloc(26 * 1024 * 1024); buffer.write('ftyp', 4);
  await analyzeVideo({ buffer, mime: 'video/mp4', prompt: 'Prompt original' }, { ai: m.ai, sleep: async () => {} });
  assert.deepEqual(m.calls.map(c => c[0]), ['upload', 'get', 'get', 'create', 'delete']);
  const request = m.calls.find(c => c[0] === 'create')[1];
  assert.equal(request.model, 'gemini-3.5-flash-lite');
  assert.deepEqual(request.input[0], { type: 'video', uri: 'https://example.test/file', mime_type: 'video/mp4', processing: 'agentic' });
  assert.ok(request.input[1].text.startsWith('Prompt original'));
});
test('FAILED rechaza el video y limpia sin analizar', async () => {
  const m = mock(['PROCESSING', 'FAILED']);
  await assert.rejects(analyzeVideo({ buffer: Buffer.from('0000ftyp'), mime: 'video/mp4', prompt: '' }, { ai: m.ai, sleep: async () => {} }), /FAILED/);
  assert.deepEqual(m.calls.map(c => c[0]), ['upload', 'get', 'delete']);
});
test('timeout de procesamiento limpia el archivo', async () => {
  const m = mock(['PROCESSING']);
  await assert.rejects(analyzeVideo({ buffer: Buffer.from('0000ftyp'), mime: 'video/mp4', prompt: '' }, { ai: m.ai, timeoutMs: 5, sleep: () => new Promise(r => setTimeout(r, 10)) }));
  assert.equal(m.calls.at(-1)[0], 'delete');
});
test('YouTube viaja como video y reintenta acceso incompleto', async () => {
  const bad = valid(); bad.validacion.audio.estado = 'no_accesible';
  const m = mock([], [bad, valid()]);
  await analyzeVideo({ url: 'https://youtu.be/b3ZFwi6vRNk', prompt: 'Analiza' }, { ai: m.ai });
  assert.deepEqual(m.calls.map(c => c[0]), ['create', 'create']);
  assert.equal(m.calls[0][1].input[0].processing, 'agentic');
});
test('rechaza resultados incompletos tras reintento y permite silencio verificado', async () => {
  const bad = valid(); bad.validacion.visual.estado = 'no_accesible';
  const m = mock([], [bad]);
  await assert.rejects(analyzeVideo({ url: 'https://youtu.be/b3ZFwi6vRNk', prompt: '' }, { ai: m.ai }), /No se pudo verificar/);
  assert.equal(m.calls.length, 2);
  const silent = valid(); silent.validacion.audio.estado = silent.validacion.habla.estado = 'ausente';
  assert.equal(validateAccess(silent), silent);
});
test('no trunca texto más allá del índice', async () => {
  const text = 'Índice\n' + 'Contenido completo. '.repeat(1000) + '\nANEXO FINAL';
  const result = await extractTextFromFile(Buffer.from(text).toString('base64'), 'documento.txt');
  assert.equal(result.textoExtraido, text);
  assert.equal(result.truncado, false);
});
test('DOCX conserva cuerpo y anexos después de 8000 caracteres', async () => {
  const JSZip = require('jszip');
  const zip = new JSZip();
  zip.file('[Content_Types].xml', '<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>');
  zip.file('_rels/.rels', '<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>');
  zip.file('word/document.xml', '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>INDICE</w:t></w:r></w:p><w:p><w:r><w:t>' + 'Desarrollo del documento. '.repeat(500) + '</w:t></w:r></w:p><w:p><w:r><w:t>ANEXO FINAL COMPLETO</w:t></w:r></w:p></w:body></w:document>');
  const result = await extractTextFromFile(await zip.generateAsync({ type: 'base64' }), 'prueba.docx');
  assert.ok(result.textoExtraido.endsWith('ANEXO FINAL COMPLETO'));
  assert.ok(result.caracteres > 8000);
});
test('timeout real aunque el SDK ignore AbortSignal', async () => {
  const start = Date.now();
  await assert.rejects(withDeadline(() => new Promise(() => {}), 15), /Tiempo de análisis agotado/);
  assert.ok(Date.now() - start < 1000);
});
test('archivo local usa análisis directo con esquema y progreso', async () => {
  const m = mock();
  let request;
  m.ai.models = { generateContent: async args => { request = args; return { text: JSON.stringify(valid()) }; } };
  const messages = [];
  await analyzeVideo({ buffer: Buffer.from('0000ftyp'), mime: 'video/mp4', prompt: 'Prompt original' }, { ai: m.ai, onProgress: message => messages.push(message) });
  assert.equal(request.contents[0].parts[0].fileData.fileUri, 'https://example.test/file');
  assert.equal(request.config.responseJsonSchema.properties.suggestion.properties.contenido.type, 'string');
  assert.equal(m.calls.filter(c => c[0] === 'create').length, 0);
  assert.ok(messages.length >= 3);
});
test('YouTube abandona agentic bloqueado y usa alternativa directa', async () => {
  const m = mock();
  m.ai.interactions.create = () => new Promise(() => {});
  m.ai.models = { generateContent: async () => ({ text: JSON.stringify(valid()) }) };
  const result = await analyzeVideo({ url: 'https://youtu.be/b3ZFwi6vRNk', prompt: 'Analiza' }, { ai: m.ai, agenticTimeoutMs: 10 });
  assert.equal(result.suggestion.contenido, 'Procedimiento comprobado');
});
