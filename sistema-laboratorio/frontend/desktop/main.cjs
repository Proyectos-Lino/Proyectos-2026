const { app, BrowserWindow, net, protocol, session } = require('electron');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');

const APP_ORIGIN = 'app://labcore';
const API_ORIGIN = 'https://api-laboratorio-amcdbpgzgyf4avgu.canadacentral-01.azurewebsites.net';
const BUNDLE_DIRECTORY = path.join(__dirname, '..', 'dist');
const CONTENT_SECURITY_POLICY = [
  "default-src 'self'",
  "script-src 'self'",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob:",
  "font-src 'self' data:",
  "connect-src 'self'",
  "frame-src 'self' blob:",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
].join('; ');

protocol.registerSchemesAsPrivileged([
  {
    scheme: 'app',
    privileges: { standard: true, secure: true, supportFetchAPI: true, stream: true },
  },
]);

function textResponse(message, status) {
  return new Response(message, {
    status,
    headers: { 'Content-Type': 'text/plain; charset=utf-8' },
  });
}

function isAppUrl(value) {
  const url = new URL(value);
  return url.protocol === 'app:' && url.host === 'labcore';
}

async function proxyApi(request, url) {
  if (request.initiatorOrigin && request.initiatorOrigin !== APP_ORIGIN) {
    return textResponse('Origen no autorizado.', 403);
  }

  const allowedMethods = new Set(['GET', 'HEAD', 'POST', 'PUT', 'PATCH', 'DELETE']);
  if (!allowedMethods.has(request.method)) {
    return textResponse('Método no permitido.', 405);
  }

  const headers = new Headers(request.headers);
  for (const name of ['host', 'origin', 'referer', 'content-length']) {
    headers.delete(name);
  }
  for (const name of Array.from(headers.keys())) {
    if (name.startsWith('sec-')) headers.delete(name);
  }

  try {
    const hasBody = !['GET', 'HEAD'].includes(request.method);
    return await net.fetch(new URL(`${url.pathname}${url.search}`, API_ORIGIN).toString(), {
      method: request.method,
      headers,
      body: hasBody ? request.body : undefined,
      duplex: hasBody ? 'half' : undefined,
      redirect: 'manual',
    });
  } catch (error) {
    console.error('Falló la comunicación con la API:', error);
    return textResponse('No se pudo conectar con el servidor del laboratorio.', 502);
  }
}

async function serveBundle(url) {
  const requestedPath = path.resolve(BUNDLE_DIRECTORY, `.${decodeURIComponent(url.pathname)}`);
  const relativePath = path.relative(BUNDLE_DIRECTORY, requestedPath);
  if (relativePath.startsWith('..') || path.isAbsolute(relativePath)) {
    return textResponse('Ruta no permitida.', 403);
  }

  const isFile = fs.existsSync(requestedPath) && fs.statSync(requestedPath).isFile();
  if (!isFile && path.extname(requestedPath)) {
    return textResponse('Archivo no encontrado.', 404);
  }

  const filename = isFile ? requestedPath : path.join(BUNDLE_DIRECTORY, 'index.html');
  const response = await net.fetch(pathToFileURL(filename).toString());
  if (path.extname(filename) !== '.html') return response;

  const headers = new Headers(response.headers);
  headers.set('Content-Security-Policy', CONTENT_SECURITY_POLICY);
  return new Response(response.body, { status: response.status, headers });
}

function registerAppProtocol() {
  protocol.handle('app', (request) => {
    const url = new URL(request.url);
    if (!isAppUrl(request.url)) return textResponse('Destino no permitido.', 403);
    if (url.pathname.startsWith('/api/v1/')) return proxyApi(request, url);
    if (request.method !== 'GET') return textResponse('Método no permitido.', 405);
    return serveBundle(url);
  });
}

function createWindow() {
  const window = new BrowserWindow({
    width: 1360,
    height: 900,
    minWidth: 840,
    minHeight: 600,
    title: 'LabCore Laboratorio',
    backgroundColor: '#f6f8fc',
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      sandbox: true,
    },
  });

  window.webContents.on('will-navigate', (event, destination) => {
    if (!isAppUrl(destination)) event.preventDefault();
  });

  window.webContents.setWindowOpenHandler(({ url }) => {
    const allowed = url === 'about:blank' || url.startsWith(`blob:${APP_ORIGIN}/`);
    return allowed
      ? {
          action: 'allow',
          overrideBrowserWindowOptions: {
            webPreferences: { nodeIntegration: false, contextIsolation: true, sandbox: true },
          },
        }
      : { action: 'deny' };
  });

  void window.loadURL(`${APP_ORIGIN}/`);
}

app.whenReady().then(() => {
  session.defaultSession.setPermissionRequestHandler((_webContents, _permission, callback) => {
    callback(false);
  });
  registerAppProtocol();
  createWindow();
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
