const path = require('path');

let pdfParse = null;
let mammoth = null;

try { 
  pdfParse = require('pdf-parse'); 
} catch(e) { 
  console.warn('[DocumentService] pdf-parse no disponible'); 
}

try { 
  mammoth = require('mammoth'); 
} catch(e) { 
  console.warn('[DocumentService] mammoth no disponible'); 
}

/**
 * Extrae texto de un archivo PDF, DOCX o TXT a partir de su base64.
 * @param {string} fileBase64 - Contenido del archivo en base64
 * @param {string} fileName - Nombre del archivo original (para detectar la extension)
 * @returns {Promise<{ textoExtraido: string, caracteres: number, truncado: boolean }>}
 */
async function extractTextFromFile(fileBase64, fileName) {
  if (!fileBase64 || !fileName) throw new Error('Proporcione fileBase64 y fileName');

  const buffer = Buffer.from(fileBase64, 'base64');
  if (buffer.length > 10 * 1024 * 1024) {
    throw new Error('El archivo supera el limite de 10 MB');
  }

  const ext = path.extname(fileName).toLowerCase();
  let textoExtraido = '';

  if (ext === '.pdf') {
    if (!pdfParse) throw new Error('pdf-parse no disponible');
    // PDF.js requiere la semántica de copia de Uint8Array.slice; Buffer.slice comparte memoria.
    const result = await pdfParse(new Uint8Array(buffer));
    textoExtraido = result.text || '';
  } else if (ext === '.docx') {
    if (!mammoth) throw new Error('mammoth no disponible');
    const result = await mammoth.extractRawText({ buffer });
    textoExtraido = result.value || '';
  } else if (ext === '.txt') {
    textoExtraido = buffer.toString('utf-8');
  } else {
    throw new Error('Formato no soportado. Use PDF, DOCX o TXT.');
  }

  textoExtraido = textoExtraido.replace(/\r\n/g, '\n').replace(/\n{3,}/g, '\n\n').trim();
  if (!textoExtraido) {
    throw new Error('No se pudo extraer texto. El archivo puede estar protegido o ser una imagen.');
  }

  return {
    textoExtraido: textoExtraido,
    caracteres: textoExtraido.length,
    truncado: false
  };
}

module.exports = {
  extractTextFromFile
};
