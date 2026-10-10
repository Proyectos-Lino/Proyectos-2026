/**
 * Helper to call the Gemini API
 * @param {Array} parts - Formatted parts for the Gemini prompt
 * @param {number} temperature - Generation temperature
 * @param {number} timeoutMs - Timeout in milliseconds
 * @returns {Promise<string>}
 */
async function callGemini(parts, temperature = 0.2, timeoutMs = 60000) {
  if (!process.env.GEMINI_API_KEY) {
    throw new Error('GEMINI_API_KEY no configurada');
  }

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs);
  
  const res = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=${process.env.GEMINI_API_KEY}`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ contents: [{ parts }], generationConfig: { temperature } }),
      signal: controller.signal
    }
  );
  
  clearTimeout(timeoutId);
  const json = await res.json();
  if (json.error) {
    throw new Error('Gemini API Error: ' + json.error.message);
  }
  return json?.candidates?.[0]?.content?.parts?.[0]?.text?.trim() || '';
}

/**
 * Uploads a file buffer to Gemini's File API for processing (like videos).
 * @param {Buffer} buffer - The file buffer
 * @param {string} mimeType - The file MIME type
 * @returns {Promise<string>} The uploaded file URI
 */
async function uploadToGemini(buffer, mimeType) {
  if (!process.env.GEMINI_API_KEY) throw new Error('GEMINI_API_KEY no configurada');

  const res = await fetch(
    `https://generativelanguage.googleapis.com/upload/v1beta/files?uploadType=media&key=${process.env.GEMINI_API_KEY}`,
    {
      method: 'POST',
      headers: {
        'Content-Type': mimeType,
      },
      body: buffer
    }
  );

  const json = await res.json();
  if (!json.file || !json.file.uri) {
    throw new Error('No se pudo subir el archivo a Gemini: ' + JSON.stringify(json));
  }
  return json.file.uri;
}

module.exports = {
  callGemini,
  uploadToGemini
};
