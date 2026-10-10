// Native browser checks with isolated API fixtures. Does not touch application data.
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const http = require('node:http');
const { spawn } = require('node:child_process');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '../public');
const artifactDir = fs.mkdtempSync(path.join(os.tmpdir(), 'itil-redesign-'));
const browserPath = process.env.BROWSER_PATH || ['C:/Program Files/Google/Chrome/Application/chrome.exe','C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'].find(fs.existsSync);
if (!browserPath) throw new Error('Set BROWSER_PATH to a Chromium browser executable.');
const tickets = [
 {id:'ticket-001',titulo:'[Red] + [VPN] + Acceso remoto interrumpido',categoria:'Conectividad',reportadoPor:'Ana Torres',atendidoPor:'agente_01',equipoAsignado:'mesa_de_ayuda',turno:'ma\u00f1ana',estado:'en_progreso',fechaCreacion:'2026-09-18T10:00:00Z',bitacora:[{autor:'Ana Torres',texto:'No puedo conectarme a la VPN desde mi equipo.',fecha:'2026-09-18T10:00:00Z',minutosInvertidos:0},{autor:'agente_01',texto:'Se verificaron las credenciales.\nLa conexi\u00f3n sigue sin responder; revisar el certificado del cliente.',fecha:'2026-09-18T10:08:00Z',minutosInvertidos:8}]},
 {id:'ticket-002',titulo:'[Correo] + [Cuenta] + Mensajes no sincronizados',categoria:'Correo electr\u00f3nico',reportadoPor:'Luis Ramos',equipoAsignado:'area_tecnica',turno:'tarde',estado:'derivado',fechaCreacion:'2026-09-17T10:00:00Z',bitacora:[{autor:'agente_02',texto:'Se deriva para revisar la configuraci\u00f3n del servidor.',fecha:'2026-09-17T10:00:00Z',minutosInvertidos:12}]},
 {id:'ticket-003',titulo:'[Equipo] + [Impresora] + Cola de impresi\u00f3n detenida',categoria:'Equipos',reportadoPor:'Mar\u00eda Soto',equipoAsignado:'mesa_de_ayuda',turno:'tarde',estado:'cerrado',fechaCreacion:'2026-09-16T10:00:00Z',tfrMinutos:3,ttrMinutos:18,resueltoEnPrimerContacto:true,bitacora:[{autor:'agente_01',texto:'Se reinici\u00f3 el servicio y se valid\u00f3 la impresi\u00f3n.',fecha:'2026-09-16T10:18:00Z',minutosInvertidos:18}]}
];
const knowledge = [{id:'kb-1',titulo:'Restablecer la conexi\u00f3n VPN',tipo:'procedimiento',categoriaITIL:'Gestion de Incidentes',estado:'publicado',servicio:'Acceso remoto',descripcion:'Pasos de diagn\u00f3stico para recuperar el acceso a la red interna.',contenido:'1. Verificar conectividad.\n2. Revisar el certificado.\n3. Validar el acceso con el agente.',etiquetas:['VPN','Red'],fuente:'Manual de soporte',fechaCreacion:'2026-09-12T12:00:00Z',metadatos:{usoContador:8}},{id:'kb-2',titulo:'Gu\u00eda de configuraci\u00f3n de correo',tipo:'documento',categoriaITIL:'Procedimientos de Soporte',estado:'pendiente_revision',descripcion:'Configuraci\u00f3n y validaci\u00f3n de la cuenta institucional.',contenido:'Contenido de prueba.',etiquetas:['Correo'],fechaCreacion:'2026-09-10T12:00:00Z',metadatos:{}}];
// Fixture escape sequences are normalized for readable screenshots.
const normalize = obj => JSON.parse(JSON.stringify(obj).replace(/\\\\u([0-9a-f]{4})/gi, (_,code) => String.fromCharCode(parseInt(code,16))));
const fixtureTickets = normalize(tickets), fixtureKnowledge = normalize(knowledge);
const writes = [];
let emptyTickets = false, emptyKnowledge = false, failedMetrics = false;
const server = http.createServer(async (req,res) => {
 const url = new URL(req.url,'http://localhost');
 if (url.pathname.startsWith('/api/')) {
  let body=''; for await(const chunk of req) body+=chunk;
  if (req.method !== 'GET') writes.push({url:url.pathname,method:req.method,body:body ? JSON.parse(body) : {}});
  let value={};
  if(url.pathname==='/api/config') value={teams:['mesa_de_ayuda','area_tecnica','supervisor','otro'],shifts:['ma\u00f1ana','tarde','noche'],tiposTicket:['Incidencia'],prioridades:['Alta','Media'],subcategoriasTecnicas:['Red']};
  else if(url.pathname==='/api/tickets') value=req.method==='POST'?{ticket:fixtureTickets[0],response:'Consulta registrada.'}:emptyTickets?[]:fixtureTickets.filter(t=>!url.searchParams.get('estado')||t.estado===url.searchParams.get('estado'));
  else if(url.pathname==='/api/metrics') value=failedMetrics?{}:{totalTickets:128,fcrPorcentaje:76,tfrPromedioMinutos:8,ttrPromedioMinutos:42,pendientesEscalados:6,satisfaccionPromedio:4.6,distribucionPorCategoria:{Conectividad:42,Equipos:36,Correo:28,Aplicaciones:22}};
  else if(url.pathname==='/api/knowledge/stats') value={total:2,byTipo:{procedimiento:1,documento:1}};
  else if(url.pathname==='/api/knowledge') value=emptyKnowledge?[]:fixtureKnowledge;
  else if(url.pathname==='/api/knowledge/kb-1') value=fixtureKnowledge[0];
  else if(url.pathname==='/api/asistente-tecnico/mensaje') value={respuesta:'Revisa la conectividad y valida el certificado del cliente VPN.',kiFuentes:[{id:'kb-1',titulo:'Restablecer la conexi\u00f3n VPN',categoriaITIL:'Gestion de Incidentes'}]};
  else if(url.pathname.endsWith('/close-conversation')) value={};
  else value={ticket:fixtureTickets[0],response:'Acci\u00f3n registrada.'};
  res.setHeader('Content-Type','application/json');res.end(JSON.stringify(value));return;
 }
 const file=path.join(root,url.pathname==='/'?'index.html':url.pathname);
 if(!file.startsWith(root)||!fs.existsSync(file)){res.writeHead(404);res.end();return;}
 const mime={'.html':'text/html; charset=utf-8','.js':'application/javascript','.css':'text/css','.woff2':'text/plain','.svg':'image/svg+xml'};
 res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');fs.createReadStream(file).pipe(res);
});
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
let browser, socket;
(async()=>{
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 const base='http://127.0.0.1:'+server.address().port;
 browser=spawn(browserPath,['--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check','--remote-debugging-port=0','--user-data-dir='+path.join(artifactDir,'profile'),'about:blank'],{windowsHide:true,stdio:'ignore'});
 const portFile=path.join(artifactDir,'profile','DevToolsActivePort');
 for(let i=0;i<100&&!fs.existsSync(portFile);i++)await delay(100);
 const port=fs.readFileSync(portFile,'utf8').split('\n')[0];
 const targets=await(await fetch('http://127.0.0.1:'+port+'/json')).json();
 socket=new WebSocket(targets.find(t=>t.type==='page').webSocketDebuggerUrl);
 await new Promise((resolve,reject)=>{socket.onopen=resolve;socket.onerror=reject;});
 let id=0;const pending=new Map(),errors=[];
 socket.onmessage=e=>{const msg=JSON.parse(e.data);if(msg.id){const p=pending.get(msg.id);pending.delete(msg.id);if(msg.error)p.reject(msg.error);else p.resolve(msg.result);}else if(msg.method==='Runtime.exceptionThrown')errors.push(msg.params.exceptionDetails);};
 function cdp(method,params={}){return new Promise((resolve,reject)=>{const key=++id;pending.set(key,{resolve,reject});socket.send(JSON.stringify({id:key,method,params}));});}
 async function evaluate(expression){const r=await cdp('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(r.exceptionDetails)throw new Error(JSON.stringify(r.exceptionDetails));return r.result.value;}
 async function waitFor(expression){for(let i=0;i<100;i++){if(await evaluate(expression))return;await delay(50);}throw new Error('Timeout: '+expression);}
 async function click(selector){await evaluate(`document.querySelector(${JSON.stringify(selector)}).focus();document.querySelector(${JSON.stringify(selector)}).click()`);await delay(80);}
 async function shot(name){await delay(250);const r=await cdp('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});fs.writeFileSync(path.join(artifactDir,name+'.png'),Buffer.from(r.data,'base64'));}
 async function dimensions(width,height){await cdp('Emulation.setDeviceMetricsOverride',{width,height,deviceScaleFactor:1,mobile:false});}
 async function overflow(label){const bad=await evaluate(`Array.from(document.querySelectorAll('body *')).filter(e=>{const s=getComputedStyle(e);const r=e.getBoundingClientRect();return s.visibility!=='hidden' && s.display!=='none' && r.width>0 && (r.right>innerWidth+2 || r.left < -2) && !e.closest('.sidebar:not(.sidebar-open)') && !e.closest('svg') && !e.matches('.skip-link,.sr-only')}).map(e=>({tag:e.tagName,id:e.id,class:e.className})).slice(0,8)`);assert.deepEqual(bad,[],label+' overflow');}
 await cdp('Page.enable');await cdp('Runtime.enable');
 await dimensions(1366,900);await cdp('Page.navigate',{url:base});
 await waitFor("document.querySelectorAll('.ticket-general').length===3");await evaluate('document.fonts.ready');
 assert.equal(await evaluate("document.fonts.check('16px Inter')"),true);
 await shot('tickets-desktop');
 await click('.ticket-row-actions .ticket-view-link');
 await waitFor("document.querySelectorAll('.bitacora-item').length===2");
 assert.equal(await evaluate("document.querySelectorAll('.bitacora-item').length"),2);
 assert.ok(await evaluate("document.querySelector('.bitacora-text').textContent.includes('VPN')"));
 await shot('ticket-detail-desktop');
 await click('#btn-back-to-list');await waitFor("document.querySelectorAll('.ticket-general').length===3");
 await click('#btn-new-ticket');await waitFor("document.activeElement.closest('#modal-new-ticket') !== null");
 assert.equal(await evaluate("document.querySelector('.app-container').inert"),true);
 await shot('new-ticket-desktop');
 await cdp('Input.dispatchKeyEvent',{type:'keyDown',key:'Escape',code:'Escape'});await delay(80);
 assert.equal(await evaluate("document.querySelector('#modal-new-ticket').style.display"),'none');
 assert.equal(await evaluate('document.activeElement.id'),'btn-new-ticket');
 for(const [width,height] of [[1920,1080],[1366,900],[1024,768],[768,1024],[360,800]]){
  await dimensions(width,height);
  for(const [nav,selector,name] of [['#nav-tickets','.ticket-general','tickets'],['#nav-dashboard','.report-metrics','dashboard'],['#nav-asistente-ia','.asistente-chat-container','assistant'],['#nav-knowledge-mgmt','.ki-item-card','knowledge']]){
   await click(nav);await waitFor(`document.querySelector(${JSON.stringify(selector)}) !== null`);await delay(100);await overflow(name+' '+width);
   if(name==='tickets') assert.ok(await evaluate("Array.from(document.querySelectorAll('.ticket-status-badge')).every(e=>e.getBoundingClientRect().height<65)"),'Status badges must remain readable');
   if(width===1366||width===360)await shot(name+'-'+width);
  }
 }
 await click('.mobile-menu');assert.equal(await evaluate("document.body.classList.contains('sidebar-open')"),true);
 await cdp('Input.dispatchKeyEvent',{type:'keyDown',key:'Escape',code:'Escape'});await delay(80);
 assert.equal(await evaluate("document.body.classList.contains('sidebar-open')"),false);
 await click('.btn-del');await waitFor("document.querySelector('.confirm-dialog[open]') !== null");await click('.confirm-dialog .btn-cancel');assert.equal(writes.filter(r=>r.method==='DELETE').length,0);
 await click('#btn-add-knowledge');await waitFor("document.querySelector('#modal-ki-type').style.display==='flex'");await overflow('type modal mobile');
 await click('#btn-tipo-texto');await waitFor("document.querySelector('#modal-ki-form').style.display==='flex'");await overflow('knowledge form mobile');await shot('knowledge-form-mobile');
 await cdp('Input.dispatchKeyEvent',{type:'keyDown',key:'Escape',code:'Escape'});await delay(80);
 await click('#nav-tickets');await click('.btn-ticket-menu');await click('.ticket-dropdown-item:nth-child(2)');
 await evaluate("document.querySelector('#comment-text').value='Visual smoke test';document.querySelector('#comment-mins').value='3';document.querySelector('#form-comment').requestSubmit()");await delay(150);
 assert.ok(writes.some(r=>r.url==='/api/tickets/ticket-001/log'&&r.body.texto==='Visual smoke test'&&r.body.minutosInvertidos===3));
 await click('#nav-asistente-ia');await evaluate("document.querySelector('#asistente-input').value='Consulta de prueba';document.querySelector('#asistente-form').requestSubmit()");await waitFor("document.querySelector('.ki-source-box') !== null");
 await overflow('assistant response mobile');await click('.ki-source-item a');await waitFor("document.querySelector('.knowledge-article') !== null");await overflow('knowledge detail mobile');
 await cdp('Input.dispatchKeyEvent',{type:'keyDown',key:'Escape',code:'Escape'});await delay(80);
 emptyTickets=true;await click('#nav-tickets');await waitFor("document.querySelector('#all-tickets-list .empty-state') !== null");await shot('empty-tickets-mobile');
 emptyKnowledge=true;await click('#nav-knowledge-mgmt');await waitFor("document.querySelector('.ki-empty') !== null");
 await cdp('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'reduce'}]});
 assert.equal(await evaluate("getComputedStyle(document.querySelector('.active-view')).animationName"),'none');
 assert.equal(errors.length,0,JSON.stringify(errors));
 if (process.env.CAPTURE_MANUAL === '1') {
  emptyTickets=false;emptyKnowledge=false;
  await dimensions(1366,900);await cdp('Page.navigate',{url:base});
  await waitFor("document.querySelectorAll('.ticket-general').length===3");
  await evaluate("window.openCommentModal('ticket-001');document.getElementById('comment-text').value='Se verificaron las credenciales. El acceso continúa bloqueado; se revisará el certificado.';document.getElementById('comment-mins').value='8'");await shot('manual-bitacora');
  await evaluate("document.getElementById('modal-comment').style.display='none';window.openDerivateModal('ticket-001');document.getElementById('derivate-reason').value='Requiere revisión del certificado de acceso.'");await shot('manual-derivacion');
  await evaluate("document.getElementById('modal-derivate').style.display='none'");
  await click('#nav-asistente-ia');await evaluate("document.getElementById('asistente-input').value='La VPN no conecta. ¿Qué verificaciones puedo realizar?' ");await shot('manual-voz');
  await click('#nav-knowledge-mgmt');await click('#btn-add-knowledge');await shot('manual-tipos');
  await evaluate("document.getElementById('modal-ki-type').style.display='none';openKIForm('video')");await shot('manual-video');
  await evaluate("openKIForm('solucion');document.getElementById('ki-titulo').value='Restablecer conexión VPN';document.getElementById('ki-contenido').value='1. Verificar conectividad.\\n2. Renovar el certificado.\\n3. Validar el acceso con el usuario.'");await shot('manual-solucion');
  const captureDir=path.resolve(__dirname,'../deliverables/capturas');fs.mkdirSync(captureDir,{recursive:true});
  for(const name of fs.readdirSync(artifactDir).filter(n=>n.endsWith('.png')))fs.copyFileSync(path.join(artifactDir,name),path.join(captureDir,name));
 }
 console.log(JSON.stringify({result:'PASS',viewports:5,views:4,checks:['layout overflow','local font','full timeline','modal focus and Escape','mobile menu','knowledge forms','comment payload','assistant sources','empty states','reduced motion'],screenshots:artifactDir,isolatedWrites:writes.length},null,2));
})().catch(error=>{console.error(error);process.exitCode=1;}).finally(async()=>{if(socket)socket.close();if(browser)browser.kill();server.close();});
