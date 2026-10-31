'use strict';
const {app, BrowserWindow, dialog, session} = require('electron');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const {spawn} = require('node:child_process');

function option(name) {
  const index = process.argv.indexOf(name);
  return index >= 0 ? process.argv[index + 1] : undefined;
}
const dataRoot = path.resolve(option('--data-dir') || path.join(process.env.LOCALAPPDATA || path.join(app.getPath('home'), 'AppData', 'Local'), 'BIOTRACK'));
app.setPath('userData', path.join(dataRoot, 'portal'));
app.setAppUserModelId('pe.biotrack.portal');
const runtimePath = path.join(dataRoot, 'runtime.json');
let window, runtime, timer, busy = false, lastFocus = -1, failures = 0;
const testing = option('--test-output');
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

function descriptor() {
  const value = JSON.parse(fs.readFileSync(runtimePath, 'utf8'));
  const url = new URL(value.url);
  if (value.app !== 'BIOTRACK-WINDOWS-1' || url.protocol !== 'http:' || url.hostname !== '127.0.0.1' || !/^\d+$/.test(url.port) || url.pathname !== '/' || url.username || url.password || !/^[a-f0-9]{64}$/.test(value.token)) throw new Error('Conexión local no válida.');
  return value;
}

function request(value, operation) {
  return new Promise((resolve, reject) => {
    const req = http.request(new URL('__biotrack__/' + operation, value.url), {method: 'POST', headers: {'X-BIOTRACK-Token': value.token}, timeout: 2000}, res => {
      let content = '';
      res.on('data', part => {content += part; if (content.length > 10000) req.destroy(new Error('Respuesta demasiado grande.'));});
      res.on('end', () => {try {if (res.statusCode !== 200) throw new Error('Conexión no disponible.'); resolve(JSON.parse(content));} catch (error) {reject(error);}});
    });
    req.on('timeout', () => req.destroy(new Error('Tiempo de conexión agotado.')));
    req.on('error', reject);
    req.end();
  });
}

async function connect() {
  try {
    const value = descriptor();
    const status = await request(value, 'status');
    if (status.app === value.app && status.instance === value.instance && !status.stopping) return value;
  } catch (_) {}
  return null;
}

async function ensureBackend() {
  let value = await connect();
  if (value) return value;
  const home = process.env.BIOTRACK_PORTAL_HOME || process.env.PORTABLE_EXECUTABLE_DIR || path.dirname(process.execPath);
  const desktop = path.join(home, 'BIOTRACK_Escritorio.exe');
  if (!fs.existsSync(desktop)) throw new Error('Coloque BIOTRACK_Escritorio.exe junto a BIOTRACK_Portal.exe y vuelva a abrir el portal.');
  const child = spawn(desktop, ['--data-dir', dataRoot], {cwd: home, detached: true, windowsHide: true, stdio: 'ignore'});
  let launchError;
  child.on('error', error => {launchError = error;});
  child.unref();
  for (let count = 0; count < 240; count++) {
    if (launchError) throw launchError;
    await delay(500);
    value = await connect();
    if (value) return value;
  }
  throw new Error('No se pudo iniciar BIOTRACK. Abra la aplicación de escritorio y vuelva a intentarlo.');
}

async function poll() {
  if (busy || !runtime || !window || window.isDestroyed()) return;
  busy = true;
  try {
    const current = descriptor();
    if (current.instance !== runtime.instance) return app.quit();
    const result = await request(runtime, 'portal');
    if (result.instance !== runtime.instance || result.stopping) return app.quit();
    if (result.focus > lastFocus) {
      lastFocus = result.focus;
      if (window.isMinimized()) window.restore();
      window.show(); window.focus();
    }
    failures = 0;
  } catch (_) {
    if (++failures >= 2) app.quit();
  } finally {busy = false;}
}

async function testWindow() {
  // Solo se activa mediante argumento explícito de verificación, con perfil aislado.
  const checks = [];
  const evaluate = code => window.webContents.executeJavaScript(code);
  async function waitFor(code) {
    for (let count=0; count<160; count++) {if (await evaluate(code)) return; await delay(100);}
    throw new Error('La interfaz no respondió durante la prueba.');
  }
  await waitFor("!!document.querySelector('#login-form')");
  await evaluate("document.querySelector('#username').value='admin';document.querySelector('#password').value='BioTrack2026!';document.querySelector('#login-form').requestSubmit();");
  await waitFor("!document.querySelector('#app-view').hidden");
  checks.push('Ingreso en Electron');
  for (const page of ['personal','horarios','predicciones','historial','reportes','incidencias','configuracion','auditoria','resumen']) {
    await evaluate(`document.querySelector('[data-page="${page}"]').click()`);
    await waitFor("!document.querySelector('#page-content .loading') && document.querySelector('#page-content').textContent.trim().length>0");
    if (await evaluate("!!document.querySelector('#retry-page')")) throw new Error('Error en la vista '+page);
    checks.push('Vista '+page);
  }
  const downloaded = [];
  const destination = path.dirname(path.resolve(testing));
  window.webContents.session.on('will-download', (_event, item) => {
    item.setSavePath(path.join(destination, item.getFilename()));
    item.once('done', (_e, state) => {if (state==='completed') downloaded.push(item.getFilename());});
  });
  await evaluate("document.querySelector('[data-page=reportes]').click()");
  await waitFor("!!document.querySelector('[data-format=xlsx]')");
  for (const format of ['xlsx','pdf','csv']) await evaluate(`document.querySelector('[data-format=${format}]').click()`);
  for (let count=0; count<160 && downloaded.length<3; count++) await delay(100);
  if (downloaded.length!==3) throw new Error('No se completaron las descargas en Electron.');
  checks.push('Descargas Excel, PDF y CSV');
  fs.writeFileSync(testing, JSON.stringify({correcto:true,checks,downloaded,electron:process.versions.electron,chrome:process.versions.chrome},null,2));
  app.quit();
}

if (!app.requestSingleInstanceLock()) app.quit();
else {
  app.on('second-instance', () => {if (window) {if (window.isMinimized()) window.restore();window.show();window.focus();}});
  app.whenReady().then(async () => {
    window = new BrowserWindow({width:1280,height:860,minWidth:800,minHeight:600,title:'BIOTRACK — Portal administrativo',autoHideMenuBar:true,webPreferences:{nodeIntegration:false,contextIsolation:true,sandbox:true,webSecurity:true,devTools:false}});
    window.removeMenu();
    await window.loadFile(path.join(__dirname,'loading.html'));
    try {
      runtime = await ensureBackend();
      const origin = new URL(runtime.url).origin;
      const allowed = target => {try {return new URL(target).origin===origin;} catch (_) {return false;}};
      window.webContents.setWindowOpenHandler(() => ({action:'deny'}));
      window.webContents.on('will-navigate',(event,url)=>{if(!allowed(url))event.preventDefault();});
      window.webContents.session.setPermissionRequestHandler((_contents,_permission,callback)=>callback(false));
      window.webContents.session.webRequest.onBeforeRequest((details,callback)=>callback({cancel:!allowed(details.url)&&!details.url.startsWith('blob:'+origin+'/')&&!details.url.startsWith('data:')}));
      await window.loadURL(runtime.url);
      await poll();
      timer = setInterval(poll,1500);
      if (testing) await testWindow();
    } catch (error) {
      if (testing) fs.writeFileSync(testing,JSON.stringify({correcto:false,error:String(error)},null,2));
      else dialog.showErrorBox('BIOTRACK',String(error.message || error));
      app.quit();
    }
  });
  app.on('window-all-closed',()=>app.quit());
  app.on('before-quit',()=>{if(timer)clearInterval(timer);});
}
