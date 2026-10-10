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
  if (url.pathname === '/api/knowledge/analyze-video') {
    res.writeHead(200, {'Content-Type':'application/x-ndjson'});
    res.write(JSON.stringify({type:'progress',message:'Analizando el video',segundos:1})+'\n');
    await new Promise(resolve=>setTimeout(resolve,700));
    res.end(JSON.stringify({type:'result',suggestion:{titulo:'Video analizado',contenido:'Resultado audiovisual verificado'}})+'\n');return;
  }
  if (req.method !== 'GET') writes.push({url:url.pathname,method:req.method,body:body ? JSON.parse(body) : {}});
  let value={};
  if(url.pathname==='/api/config') value={teams:['mesa_de_ayuda','area_tecnica','supervisor','otro'],shifts:['ma\u00f1ana','tarde','noche'],tiposTicket:['Incidencia'],prioridades:['Alta','Media'],subcategoriasTecnicas:['Red']};
  else if(url.pathname==='/api/tickets') value=req.method==='POST'?{ticket:fixtureTickets[0],response:'Consulta registrada.'}:emptyTickets?[]:fixtureTickets.filter(t=>!url.searchParams.get('estado')||t.estado===url.searchParams.get('estado'));
  else if(url.pathname==='/api/metrics') value=failedMetrics?{}:{totalTickets:128,fcrPorcentaje:76,tfrPromedioMinutos:8,ttrPromedioMinutos:42,pendientesEscalados:6,satisfaccionPromedio:4.6,distribucionPorCategoria:{Conectividad:42,Equipos:36,Correo:28,Aplicaciones:22}};
  else if(url.pathname==='/api/knowledge/stats') value={total:24,byTipo:{procedimiento:10,solucion:7,documento:5,video:2}};
  else if(url.pathname==='/api/knowledge') value=fixtureKnowledge.filter(i=>!url.searchParams.get('tipo')||i.tipo===url.searchParams.get('tipo'));
  else if(url.pathname==='/api/knowledge/extract-file') value={textoExtraido:'Texto completo. '.repeat(1000)+'ANEXO FINAL',truncado:false};
  else if(url.pathname==='/api/knowledge/analyze') value={suggestion:{titulo:'Analizado',contenido:'Contenido de IA completo'}};
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
fixtureTickets.forEach((t,i)=>t.prioridad=['P1 (Alta)','P2 (Media)','P3 (Normal)'][i]); fixtureTickets[2].ttrMinutos=5585;
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
 await cdp('Page.addScriptToEvaluateOnNewDocument', {source: `
 window.SpeechRecognition = class {
   constructor() { window.testRecognition = this; }
   start() { this.started = true; }
   abort() { this.aborted = true; this.onend?.(); }
 };
 `});
 await dimensions(1366,900);await cdp('Page.navigate',{url:base});
 await waitFor("document.querySelectorAll('.ticket-general').length===3");await evaluate('document.fonts.ready');
 assert.equal(await evaluate("document.fonts.check('16px Inter')"),true);

 assert.equal(await evaluate("formatTicketMinutes(5585)"),'93 h 5 min');
 for (const [status,color] of [['closed','rgb(185, 28, 28)'],['derived','rgb(250, 204, 21)'],['open','rgb(21, 128, 61)']]) {
   assert.equal(await evaluate("getComputedStyle(document.querySelector('#view-tickets .status-"+status+"')).backgroundColor"),color);
 }
 const colors=await evaluate("[...document.querySelectorAll('#view-tickets .priority-badge')].map(e=>getComputedStyle(e).backgroundColor)");
 assert.equal(new Set(colors).size,3);
 await shot('ticket-colors');
 await click('#nav-knowledge-mgmt');
 await waitFor("document.querySelectorAll('.ki-item-card').length===2");
 await evaluate("document.getElementById('ki-filter-tipo').value='documento';document.getElementById('ki-filter-tipo').dispatchEvent(new Event('change'))");
 await waitFor("document.querySelectorAll('.ki-item-card').length===1");
 await evaluate("openKIForm('documento');const dt=new DataTransfer();dt.items.add(new File(['sample'],'prueba.docx',{type:'application/vnd.openxmlformats-officedocument.wordprocessingml.document'}));document.getElementById('ki-file-input').files=dt.files;document.getElementById('ki-file-input').dispatchEvent(new Event('change'))");
 await waitFor("document.getElementById('ki-file-preview').textContent.includes('ANEXO FINAL')");
 assert.equal(await evaluate("document.getElementById('ki-contenido').value"),'');
 await click('#btn-extract-file');
 await waitFor("kiDocumentText.size===1");
 assert.equal(await evaluate("document.getElementById('ki-contenido').value"),'');
 await click('#btn-ki-analyze');
 await waitFor("document.getElementById('ki-contenido').value==='Contenido de IA completo'");
 assert.ok(writes.find(r=>r.url==='/api/knowledge/analyze').body.contenido.includes('ANEXO FINAL'));
 assert.ok(writes.find(r=>r.url==='/api/knowledge/analyze').body.contenido.length>8000);
 await shot('word-preview-and-analysis');
 await evaluate("openKIForm('video');const dt2=new DataTransfer();dt2.items.add(new File(['0000ftyp'],'prueba.mp4',{type:'video/mp4'}));document.getElementById('ki-video-input').files=dt2.files;");
 await click('#btn-ki-analyze');
 await waitFor("document.getElementById('ki-processing-text').textContent.includes('Analizando el video')");
 await waitFor("document.getElementById('ki-contenido').value==='Resultado audiovisual verificado'");
 assert.equal(await evaluate("document.getElementById('btn-ki-analyze').disabled"),false);
 await shot('video-analysis-complete');
 for (const nextType of ['solucion', 'imagen']) {
   await evaluate("{openKIForm('video');const files=new DataTransfer();files.items.add(new File(['video'],'cancel.mp4',{type:'video/mp4'}));document.getElementById('ki-video-input').files=files.files;}");
   await click('#btn-ki-analyze');
   await waitFor("document.getElementById('ki-processing-text').textContent.includes('Analizando el video')");
   await click('#modal-ki-form .btn-cancel');
   await evaluate(`openKIForm('${nextType}');document.getElementById('ki-titulo').value='Formulario nuevo';`);
   await delay(900);
   assert.equal(await evaluate("document.getElementById('ki-titulo').value"),'Formulario nuevo');
   assert.equal(await evaluate("document.getElementById('ki-contenido').value"),'');
   assert.equal(await evaluate("document.getElementById('ki-processing-bar').style.display"),'none');
   assert.equal(await evaluate("document.getElementById('btn-ki-analyze').disabled"),false);
 }
 const current = new Date();
 fixtureTickets[0].fechaCreacion = new Date(current.getFullYear(), current.getMonth(), 2, 12).toISOString();
 fixtureTickets[1].fechaCreacion = new Date(current.getFullYear(), current.getMonth()-1, 2, 12).toISOString();
 fixtureTickets[2].fechaCreacion = new Date(current.getFullYear()-1, current.getMonth(), 2, 12).toISOString();
 await evaluate("document.getElementById('modal-ki-form').style.display='none';renderDashboard()");
 await waitFor("document.querySelectorAll('.report-metric').length===6");
 assert.deepEqual(await evaluate("[...document.querySelectorAll('.report-metric')].slice(2,4).map(e=>e.querySelector('strong').textContent)"),['1','1']);
 const counts=await evaluate("[...document.querySelector('.report-chart').querySelectorAll('rect title')].map(e=>Number(e.textContent.split(': ')[1].split(' ')[0]))");
 assert.equal(counts.length,1);
 assert.equal(await evaluate("document.querySelector('.report-chart rect title').textContent"),'Día 2: 1 casos');
 assert.equal(await evaluate("document.querySelector('.report-chart').querySelectorAll('circle').length"),0);
 assert.equal(counts.reduce((a,b)=>a+b,0),1);


 await evaluate("document.getElementById('asistente-input').value='Consulta previa';document.querySelector('#asistente-input + .voice-button').click();window.testRecognition.onresult({results:[[{transcript:'necesito ayuda con la red'}]]});");
 assert.equal(await evaluate("document.getElementById('asistente-input').value"),'Consulta previa necesito ayuda con la red');
 await evaluate("document.querySelector('#asistente-input + .voice-button').click();window.testRecognition.onresult({results:[[{transcript:'resultado tardio'}]]});");
 assert.equal(await evaluate("document.getElementById('asistente-input').value"),'Consulta previa necesito ayuda con la red');
 assert.equal(await evaluate("window.testRecognition.aborted"),true);
 await evaluate("document.querySelector('#asistente-input + .voice-button').click();window.testRecognition.onerror({error:'not-allowed'});");
 assert.ok(await evaluate("document.getElementById('asistente-input-voice-status').textContent.includes('Permite')"));
 assert.equal(await evaluate("document.querySelector('#asistente-input + .voice-button').getAttribute('aria-pressed')"),'false');
 assert.equal(errors.length,0,JSON.stringify(errors));
 console.log(JSON.stringify({result:'PASS',checks:['status colors','priority contrast','hours and minutes','automatic filters','Word preview','extraction separate from Content','full document sent to AI','cancel video before solution or image','separate pending and derived counts','current month daily chart','voice transcription and cancellation','microphone permission error'],screenshots:artifactDir},null,2));
})().catch(error=>{console.error(error);process.exitCode=1;}).finally(async()=>{if(socket)socket.close();if(browser)browser.kill();server.close();});
