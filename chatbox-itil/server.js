const http = require('http');
const url = require('url');
const fs = require('fs');
const path = require('path');

// Carga automatica de .env
const { APP_ROOT } = require('./lib/paths');
const envPath = path.join(APP_ROOT, '.env');
if (fs.existsSync(envPath)) {
  fs.readFileSync(envPath, 'utf-8').split('\n').forEach(line => {
    const match = line.match(/^([^=:#]+)=(.*)$/);
    if (match) {
      const key = match[1].trim();
      const value = match[2].trim();
      if (!process.env[key]) process.env[key] = value;
    }
  });
}

const { handleApiRequest } = require('./lib/router');
const { sendJson } = require('./lib/utils');

const PORT = process.env.PORT || 3000;

// ─────────────────────────────────────────────────────────────
// HTTP SERVER
// ─────────────────────────────────────────────────────────────
const server = http.createServer(async (req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS, PUT, PATCH, DELETE');
  res.setHeader('Access-Control-Allow-Headers', 'X-Requested-With,content-type');

  if (req.method === 'OPTIONS') { res.writeHead(200); res.end(); return; }

  const parsedUrl = url.parse(req.url, true);
  const pathName = parsedUrl.pathname;

  try {
    // Bloquear recursos retirados incluso si una actualización conserva archivos antiguos.
    if (['/empleado.html', '/empleado.css', '/widget.js', '/widget.css'].includes(decodeURIComponent(pathName).toLowerCase())) {
      return sendJson(res, 404, { error: 'Not Found' });
    }
    // Intentar manejar la ruta de la API
    const handled = await handleApiRequest(req, res, parsedUrl, pathName);
    if (handled !== null) return;

    // ═══════════════════════════════════════════════════════════
    // STATIC FILES
    // ═══════════════════════════════════════════════════════════
    const publicRoot = path.join(APP_ROOT, 'public');
    const staticPath = path.resolve(publicRoot, '.' + decodeURIComponent(pathName === '/' ? '/index.html' : pathName));
    const relativePath = path.relative(publicRoot, staticPath);
    if (relativePath.startsWith('..') || path.isAbsolute(relativePath)) return sendJson(res, 404, { error: 'Not Found' });
    if (fs.existsSync(staticPath) && fs.statSync(staticPath).isFile()) {
      const ext = path.extname(staticPath);
      let contentType = 'text/plain';
      if (ext === '.html') contentType = 'text/html';
      if (ext === '.css') contentType = 'text/css';
      if (ext === '.js') contentType = 'application/javascript';
      if (ext === '.svg') contentType = 'image/svg+xml';
      if (ext === '.woff2') contentType = 'font/woff2';
      if (ext === '.png') contentType = 'image/png';
      res.writeHead(200, { 'Content-Type': contentType });
      return fs.createReadStream(staticPath).pipe(res);
    }

    sendJson(res, 404, { error: 'Not Found' });

  } catch (error) {
    console.error(error);
    sendJson(res, error instanceof SyntaxError || error instanceof URIError ? 400 : 500, { error: error.message });
  }
});

server.listen(PORT, () => {
  console.log(`Motor ITIL ejecutandose en http://localhost:${PORT}`);
  
  // La apertura del navegador es opcional; el servidor debe seguir activo si falla.
  if (process.env.OPEN_BROWSER === '1') {
    const { exec } = require('child_process');
    exec(`start http://localhost:${PORT}`, () => {});
  }
});
