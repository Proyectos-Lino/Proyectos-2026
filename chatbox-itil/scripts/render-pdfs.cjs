const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { spawn } = require('node:child_process');
const root = path.resolve(__dirname, '..');
const out = path.join(root, 'deliverables');
const browserPath = ['C:/Program Files/Google/Chrome/Application/chrome.exe', 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'].find(fs.existsSync);
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
let browser, socket;
(async () => {
  const profile = fs.mkdtempSync(path.join(root, '.tmp', 'pdf-browser-'));
  browser = spawn(browserPath, ['--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check', '--remote-debugging-port=0', '--user-data-dir=' + profile, 'about:blank'], { windowsHide: true, stdio: 'ignore' });
  const portFile = path.join(profile, 'DevToolsActivePort');
  for (let i = 0; i < 100 && !fs.existsSync(portFile); i++) await delay(100);
  const port = fs.readFileSync(portFile, 'utf8').split('\n')[0];
  const targets = await (await fetch('http://127.0.0.1:' + port + '/json')).json();
  socket = new WebSocket(targets.find(t => t.type === 'page').webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject; });
  let id = 0; const pending = new Map();
  socket.onmessage = event => { const message = JSON.parse(event.data); if (!message.id) return; const p = pending.get(message.id); pending.delete(message.id); message.error ? p.reject(message.error) : p.resolve(message.result); };
  const cdp = (method, params = {}) => new Promise((resolve, reject) => { const key = ++id; pending.set(key, { resolve, reject }); socket.send(JSON.stringify({ id: key, method, params })); });
  async function evaluate(expression) { const r = await cdp('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true }); if (r.exceptionDetails) throw new Error(JSON.stringify(r.exceptionDetails)); return r.result.value; }
  await cdp('Page.enable'); await cdp('Runtime.enable');
  const result = [];
  for (const name of ['manual-usuario', 'memoria-descriptiva', 'anexos-informe']) {
    await cdp('Page.navigate', { url: pathToFileURL(path.join(out, name + '.html')).href });
    for (let i = 0; i < 100; i++) { if (await evaluate("document.readyState === 'complete' && document.querySelectorAll('.page').length > 0 && [...document.images].every(i=>i.complete&&i.naturalWidth>0)")) break; await delay(100); }
    const overflow = await evaluate("[...document.querySelectorAll('.page')].map((p,i)=>({page:i+1,content:p.querySelector('.content')?.getBoundingClientRect().bottom||0,footer:p.querySelector('.footer').getBoundingClientRect().top})).filter(p=>p.content>p.footer-8)");
    if (overflow.length) throw new Error(name + ' contenido excede página: ' + JSON.stringify(overflow));
    const pdf = await cdp('Page.printToPDF', { printBackground: true, preferCSSPageSize: true, displayHeaderFooter: false });
    fs.writeFileSync(path.join(out, name + '.pdf'), Buffer.from(pdf.data, 'base64'));
    result.push({ file: name + '.pdf', pages: await evaluate("document.querySelectorAll('.page').length"), overflow: false });
  }
  fs.writeFileSync(path.join(out, 'verificacion-pdf.json'), JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result, null, 2));
})().catch(error => { console.error(error); process.exitCode = 1; }).finally(() => { socket?.close(); browser?.kill(); });
