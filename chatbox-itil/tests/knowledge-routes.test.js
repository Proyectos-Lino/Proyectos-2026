const { test } = require('node:test');
const assert = require('node:assert/strict');
const { Readable } = require('node:stream');
const { EventEmitter } = require('node:events');
const videoService = require('../lib/videoService');
let received;
const realAnalyze = videoService.analyzeVideo;
videoService.analyzeVideo = async input => {
  received = input;
  return { suggestion: { contenido: 'Verificado', categoriaITIL: 'General' }, validacion: { visual: { estado: 'procesado' } } };
};
const { handleApiRequest } = require('../lib/router');
videoService.analyzeVideo = realAnalyze;

async function request(path, payload, mime = 'application/json') {
  const req = Readable.from([Buffer.isBuffer(payload) ? payload : Buffer.from(JSON.stringify(payload))]);
  req.method = 'POST'; req.headers = { 'content-type': mime }; req.setTimeout = () => {};
  const res = new EventEmitter();
  res.writeHead = status => { res.status = status; };
  res.end = body => { res.body = JSON.parse(body); res.emit('finish'); };
  await handleApiRequest(req, res, { query: {} }, path);
  return res;
}
test('ruta binaria envía el archivo sin Base64 y conserva el prompt', async () => {
  const previous = process.env.GEMINI_API_KEY;
  process.env.GEMINI_API_KEY = 'test-no-network';
  try {
    const buffer = Buffer.from('0000ftyp');
    const res = await request('/api/knowledge/analyze-video', buffer, 'video/mp4');
    assert.equal(res.status, 200);
    assert.deepEqual(received.buffer, buffer);
    assert.match(received.prompt, /Analiza este archivo multimedia/);
    assert.equal(res.body.suggestion.contenido, 'Verificado');
    const youtube = await request('/api/knowledge/analyze', { videoUrl: 'https://youtu.be/b3ZFwi6vRNk' });
    assert.equal(youtube.status, 200);
    assert.equal(received.url, 'https://youtu.be/b3ZFwi6vRNk');
  } finally {
    if (previous === undefined) delete process.env.GEMINI_API_KEY; else process.env.GEMINI_API_KEY = previous;
  }
});
test('ruta de extracción devuelve el texto completo para vista previa y memoria', async () => {
  const text = 'Contenido. '.repeat(1000) + 'Anexo final';
  const res = await request('/api/knowledge/extract-file', { fileName: 'documento.txt', fileBase64: Buffer.from(text).toString('base64') });
  assert.equal(res.status, 200);
  assert.equal(res.body.textoExtraido, text);
});
test('gestión filtra texto con acentos sin ocultar registros pendientes', async () => {
  const { knowledgeService } = require('../lib/knowledgeService');
  const original = knowledgeService.getAll;
  let filters;
  knowledgeService.getAll = async value => {
    filters = value;
    return [{ titulo: 'Configuración', contenido: 'Impresora', estado: 'pendiente' }, { titulo: 'Red', contenido: 'Router' }];
  };
  try {
    const res = new EventEmitter();
    res.writeHead = status => { res.status = status; };
    res.end = body => { res.body = JSON.parse(body); };
    await handleApiRequest({ method: 'GET' }, res, { query: { tipo: 'documento', categoriaITIL: 'General', q: 'configuracion impresora' } }, '/api/knowledge');
    assert.deepEqual(filters, { tipo: 'documento', categoriaITIL: 'General' });
    assert.equal(res.body.length, 1);
    assert.equal(res.body[0].estado, 'pendiente');
  } finally { knowledgeService.getAll = original; }
});
