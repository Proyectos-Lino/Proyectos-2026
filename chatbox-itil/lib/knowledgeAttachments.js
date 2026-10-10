const path = require('node:path');
const crypto = require('node:crypto');

const MB = 1024 * 1024;
const FORMATS = {
  '.pdf': ['application/pdf', 10], '.docx': ['application/vnd.openxmlformats-officedocument.wordprocessingml.document', 10],
  '.txt': ['text/plain', 10], '.png': ['image/png', 4], '.jpg': ['image/jpeg', 4],
  '.jpeg': ['image/jpeg', 4], '.gif': ['image/gif', 4], '.webp': ['image/webp', 4],
  '.mp4': ['video/mp4', 40], '.webm': ['video/webm', 40], '.ogg': ['video/ogg', 40],
  '.mp3': ['audio/mpeg', 40], '.wav': ['audio/wav', 40], '.m4a': ['audio/mp4', 40]
};

// Validate every new file before writing anything. Client paths/MIME are not trusted.
function prepareAttachments(input, existing = []) {
  if (input === undefined) return existing.map(metadata => ({ metadata }));
  if (!Array.isArray(input) || input.length > 12) throw new Error('Adjunta como máximo 12 archivos.');
  const seen = new Set();
  let total = 0;
  return input.map(file => {
    if (!file || typeof file !== 'object') throw new Error('Adjunto inválido.');
    if (file.id && !file.base64) {
      const metadata = existing.find(a => a.id === file.id);
      if (!metadata || seen.has(file.id)) throw new Error('El adjunto no pertenece a este conocimiento o está repetido.');
      seen.add(file.id);
      total += metadata.tamano;
      if (total > 40 * MB) throw new Error('Los adjuntos no pueden superar 40 MB en total.');
      return { metadata };
    }
    const nombre = path.basename(String(file.nombre || '').replace(/\\/g, '/')).replace(/[\x00-\x1f\x7f]/g, '').slice(0, 180);
    const ext = path.extname(nombre).toLowerCase();
    const format = FORMATS[ext];
    if (!format) throw new Error('Formato de archivo no permitido.');
    if (typeof file.base64 !== 'string' || file.base64.length > Math.ceil(format[1] * MB / 3) * 4 || !/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(file.base64)) throw new Error('Archivo vacío, inválido o demasiado grande.');
    const buffer = Buffer.from(file.base64, 'base64');
    if (!buffer.length || buffer.length > format[1] * MB) throw new Error(`El archivo supera el límite de ${format[1]} MB o está vacío.`);
    total += buffer.length;
    if (total > 40 * MB) throw new Error('Los adjuntos no pueden superar 40 MB en total.');
    const hex = buffer.subarray(0, 12).toString('hex');
    const ascii = buffer.subarray(0, 12).toString('ascii');
    const valid = {
      '.pdf': ascii.startsWith('%PDF-'), '.docx': hex.startsWith('504b0304'),
      '.png': hex.startsWith('89504e470d0a1a0a'), '.jpg': hex.startsWith('ffd8ff'), '.jpeg': hex.startsWith('ffd8ff'),
      '.gif': ascii.startsWith('GIF87a') || ascii.startsWith('GIF89a'),
      '.webp': ascii.startsWith('RIFF') && ascii.slice(8) === 'WEBP',
      '.mp4': ascii.slice(4,8) === 'ftyp', '.webm': hex.startsWith('1a45dfa3'), '.ogg': ascii.startsWith('OggS'),
      '.mp3': hex.startsWith('494433') || hex.startsWith('fffa') || hex.startsWith('fffb'), 
      '.wav': ascii.startsWith('RIFF') && ascii.slice(8, 12) === 'WAVE',
      '.m4a': ascii.slice(4,8) === 'ftyp',
      '.txt': !buffer.includes(0)
    }[ext];
    if (!valid) throw new Error('El contenido del archivo no coincide con su formato.');
    return { metadata: { id: crypto.randomUUID(), nombre, mime: format[0], tamano: buffer.length }, buffer };
  });
}

module.exports = { prepareAttachments };
