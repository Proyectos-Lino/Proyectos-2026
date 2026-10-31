'use strict';

const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
const esc = value => String(value ?? '').replace(/[&<>"']/g, character => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[character]));
const ROLES = ['Administrador','Jefe de Enfermería','Médico','Enfermero/a'];
const ACTIONS = {entrada:'Entrada',salida_refrigerio:'Salida Refrigerio',entrada_refrigerio:'Entrada Refrigerio',salida:'Salida'};
const ICONS = {
  home:'<path d="m3 10 9-7 9 7M5 9v11h5v-6h4v6h5V9"/>',
  people:'<circle cx="9" cy="8" r="3"/><path d="M3 21v-3a6 6 0 0 1 12 0v3M16 5a3 3 0 0 1 0 6M21 21v-3a6 6 0 0 0-4-5"/>',
  user:'<circle cx="12" cy="7" r="4"/><path d="M5 21v-2a7 7 0 0 1 14 0v2"/>',
  calendar:'<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M7 3v4M17 3v4M3 11h18M7 15h3M14 15h3"/>',
  chart:'<path d="M4 21V11h4v10M10 21V7h4v14M16 21V3h4v18"/>',
  history:'<path d="M3 11a9 9 0 1 1 2 7M3 5v6h6M12 7v5l3 2"/>',
  logout:'<path d="M9 3H4v18h5M9 12h12m-5-5 5 5-5 5"/>',
  clock:'<circle cx="12" cy="12" r="9"/><path d="M12 6v6l4 3"/>',
  bell:'<path d="M5 17h14l-2-4V9a5 5 0 0 0-10 0v4zM10 21h4M12 2v2"/>',
  check:'<path d="m5 12 4 4 10-10"/>',
  close:'<path d="m6 6 12 12M18 6 6 18"/>',
  plus:'<path d="M12 5v14M5 12h14"/>',
  settings:'<path d="M9 3h6l1 3 3 1 2 5-2 5-3 1-1 3H9l-1-3-3-1-2-5 2-5 3-1z"/><circle cx="12" cy="12" r="3"/>',
  report:'<path d="M5 3h10l4 4v14H5zM15 3v5h4M8 12h8M8 16h8"/>',
  camera:'<path d="M3 7h4l2-3h6l2 3h4v14H3z"/><circle cx="12" cy="13" r="4"/>',
  download:'<path d="M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5"/>',
  edit:'<path d="m14 5 5 5M4 20l4-1L21 6l-5-5L3 14z"/>',
  eye:'<path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12"/><circle cx="12" cy="12" r="3"/>',
  menu:'<path d="M4 6h16M4 12h16M4 18h16"/>',
  shield:'<path d="m12 2 8 4v6c0 5-8 10-8 10S4 17 4 12V6zM9 12l2 2 4-4"/>',
  coffee:'<path d="M4 8h12v8a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4zM16 9h2a3 3 0 0 1 0 6h-2M7 2v3M12 2v3"/>'
};
const icon = name => `<svg class="icon" viewBox="0 0 24 24" aria-hidden="true">${ICONS[name] || ICONS.report}</svg>`;
const initials = user => ((user.nombre || '?')[0] + (user.apellido || '')[0]).toUpperCase();
const fullName = user => `${user.nombre} ${user.apellido}`;
function today(offset = 0) {
  const date = new Date(Date.now() + offset * 86400000);
  const parts = new Intl.DateTimeFormat('en-CA',{timeZone:'America/Lima',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(date);
  const get = type => parts.find(part => part.type === type).value;
  return `${get('year')}-${get('month')}-${get('day')}`;
}
function humanDate(value) { if (!value) return ''; const [year,month,day] = value.slice(0,10).split('-'); return `${day}/${month}/${year}`; }
function badge(text, kind='neutral') {return `<span class="badge ${kind}">${esc(text)}</span>`;}
function presenceBadge(text) {return badge(text,text === 'Presente' ? 'good' : text === 'En refrigerio' ? 'warn' : 'neutral');}
function empty(message='No se encontraron registros.', title='Sin registros') {return `<div class="empty">${icon('report')}<strong>${esc(title)}</strong>${esc(message)}</div>`;}
const app = {user:null,csrf:'',page:'resumen',navToken:0,people:[],filters:{desde:today(-7),hasta:today(),empleado_id:'',accion:'',buscar:''}};
const isAdmin = () => app.user?.cargo === ROLES[0];
const isManager = () => ROLES.slice(0,2).includes(app.user?.cargo);

async function api(path, {method='GET', data, binary=false} = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 12000);
  try {
    const options = {method,signal:controller.signal,credentials:'same-origin',headers:{}};
    if (data !== undefined) { options.headers['Content-Type']='application/json'; options.body=JSON.stringify(data); }
    if (method !== 'GET') options.headers['X-CSRF-Token']=app.csrf;
    const response = await fetch(path,options);
    $('.sidebar-footer').classList.remove('offline'); $('#connection-label').textContent='Conectado';
    if (!response.ok) {
      const failure = await response.json().catch(() => ({error:'No se pudo completar la operación.'}));
      if (response.status === 401 && path !== '/api/login') showLogin();
      const error = new Error(failure.error || 'No se pudo completar la operación.'); error.status=response.status; throw error;
    }
    return binary ? response : await response.json();
  } catch (error) {
    if (error.name === 'AbortError' || error instanceof TypeError) {
      $('.sidebar-footer').classList.add('offline'); $('#connection-label').textContent='Sin conexión';
      throw new Error('No se pudo conectar con BIOTRACK. Compruebe que el sistema siga abierto.');
    }
    throw error;
  } finally {clearTimeout(timeout);}
}
function toast(message,error=false) {
  const element=document.createElement('div'); element.className=`toast${error?' error':''}`; element.textContent=message;
  $('#toasts').append(element); setTimeout(() => element.remove(),5000);
}
function showLogin() {
  app.user=null; app.csrf=''; app.navToken++;
  $('#app-view').hidden=true; $('#login-view').hidden=false;
  if ($('#modal').open) $('#modal').close();
  $('#password').value='';
}
function showApplication(user,csrf) {
  app.user=user; app.csrf=csrf;
  $('#login-view').hidden=true; $('#app-view').hidden=false;
  $('#account-name').textContent=fullName(user); $('#account-role').textContent=user.cargo; $('#account-avatar').textContent=initials(user);
  const groups = [
    ['General',[['resumen','Resumen','home'],['personal','Personal','user']]],
    ['Gestión',[['horarios','Horarios','calendar']]],
    ['Análisis',[['predicciones','Predicciones','chart'],['historial','Historial','history']]],
    ['Complementos',[['reportes','Reportes','report'],['incidencias','Incidencias','bell'],...(isAdmin()?[['configuracion','Configuración','settings'],['auditoria','Auditoría','shield']]:[])]]
  ];
  $('#navigation').innerHTML=groups.map(([label,items])=>`<div class="nav-label">${label}</div>${items.map(([id,name,image])=>`<a href="#${id}" data-page="${id}" class="nav-link">${icon(image)}<span>${name}</span></a>`).join('')}`).join('')+`<a href="#cerrar" class="nav-link" id="logout-link">${icon('logout')}<span>Cerrar Sesión</span></a>`;
  $$('.nav-link[data-page]').forEach(link=>link.addEventListener('click',event=>{event.preventDefault();navigate(link.dataset.page);}));
  $('#logout-link').onclick=async event=>{event.preventDefault();try{await api('/api/logout',{method:'POST',data:{}});showLogin();}catch(error){toast(error.message,true);}};
  const target=location.hash.slice(1); navigate(target in pages && (isAdmin() || !['auditoria','configuracion'].includes(target)) ? target : 'resumen');
}
const TITLES={resumen:'Resumen',personal:'Personal',horarios:'Horarios',predicciones:'Predicciones',historial:'Historial',reportes:'Reportes',incidencias:'Incidencias',configuracion:'Configuración',auditoria:'Auditoría'};
async function navigate(page,refresh=false) {
  if (!app.user) return;
  if (!pages[page] || (!isAdmin() && ['configuracion','auditoria'].includes(page))) page='resumen';
  const token=++app.navToken; app.page=page;
  if (!refresh) {
    $('#page-content').innerHTML='<div class="loading">Cargando información…</div>';
    history.replaceState(null,'',`#${page}`); $('#sidebar').classList.remove('open');
  }
  $('#page-title').textContent=TITLES[page];
  $$('.nav-link[data-page]').forEach(link=>{link.classList.toggle('active',link.dataset.page===page);if(link.dataset.page===page)link.setAttribute('aria-current','page');else link.removeAttribute('aria-current');});
  try {
    const view=await pages[page]();
    if (token !== app.navToken || !app.user) return;
    $('#page-content').innerHTML=view.html;
    if (view.bind) view.bind();
  } catch(error) {
    if (token === app.navToken && app.user) {
      $('#page-content').innerHTML=`<section class="panel">${empty(error.message,'No se pudo cargar la información')}<button id="retry-page" class="primary">Intentar nuevamente</button></section>`;
      $('#retry-page').onclick=()=>navigate(page);
    }
  }
}
function tableMarkup(headers,rows) {return `<div class="table-wrap"><table><thead><tr>${headers.map(label=>`<th scope="col">${esc(label)}</th>`).join('')}</tr></thead><tbody>${rows.join('')}</tbody></table></div>`;}
function mountTable(target,headers,rows,renderer) {
  let page=0; const size=20;
  function paint() {
    if (!rows.length) {target.innerHTML=empty();return;}
    const start=page*size,end=Math.min(start+size,rows.length),count=Math.ceil(rows.length/size);
    target.innerHTML=tableMarkup(headers,rows.slice(start,end).map(renderer))+`<div class="pagination"><span>${start+1}–${end} de ${rows.length} registros</span><div class="pagination-controls"><button class="secondary previous" ${page===0?'disabled':''}>Anterior</button><span>${page+1} / ${count}</span><button class="secondary next" ${page+1===count?'disabled':''}>Siguiente</button></div></div>`;
    $('.previous',target).onclick=()=>{page--;paint();}; $('.next',target).onclick=()=>{page++;paint();};
  }
  paint();
}
function employeeOptions(people,selected='',all=false) {return (all?'<option value="">Todo el personal</option>':'<option value="">Seleccione un empleado</option>')+people.map(p=>`<option value="${esc(p.id)}" ${p.id===selected?'selected':''}>${esc(fullName(p))}${p.activo?'':' (baja)'}</option>`).join('');}
function actionOptions(selected='',all=false) {return (all?'<option value="">Todas las acciones</option>':'')+Object.entries(ACTIONS).map(([value,label])=>`<option value="${value}" ${selected===value?'selected':''}>${label}</option>`).join('');}
function field(label,name,value='',type='text',extra='') {return `<div class="field"><label for="field-${name}">${label}</label><input id="field-${name}" name="${name}" value="${esc(value)}" type="${type}" ${extra}></div>`;}
function showDialog(title,html,submit,label='Guardar') {
  $('#dialog-title').textContent=title; $('#dialog-content').innerHTML=html; $('#dialog-error').textContent=''; $('#dialog-save').textContent=label; $('#dialog-save').disabled=false;
  $('#dialog-form').onsubmit=async event=>{
    event.preventDefault(); const button=$('#dialog-save'); button.disabled=true; $('#dialog-error').textContent='';
    try {await submit(new FormData(event.target)); $('#modal').close();}
    catch(error){$('#dialog-error').textContent=error.message;}
    finally{button.disabled=false;}
  };
  $('#modal').showModal();
}
async function download(path,filename) {
  try {
    const response=await api(path,{binary:true}); const url=URL.createObjectURL(await response.blob()); const link=document.createElement('a');
    link.href=url; link.download=filename; document.body.append(link); link.click(); link.remove(); setTimeout(()=>URL.revokeObjectURL(url),10000);
    toast('Archivo generado correctamente.');
  }catch(error){toast(error.message,true);}
}
function historyRow(row) {return `<tr><td class="nowrap">${humanDate(row.fecha)}</td><td>${esc(row.hora)}</td><td><strong>${esc(row.nombre)} ${esc(row.apellido)}</strong></td><td>${esc(row.cargo)}</td><td>${esc(row.accion_texto)}</td><td>${row.origen==='demostracion'?badge('Ejemplo','demo'):badge(row.origen==='facial'?'Facial':'Manual',row.origen==='facial'?'good':'neutral')}</td></tr>`;}

async function summaryPage() {
  const [summary,activity]=await Promise.all([api('/api/resumen'),api('/api/actividad')]);
  const statistics=[['Personal presente',summary.personal_presente,'people',''],['Tasa de puntualidad',summary.tasa_puntualidad+'%','clock','green'],['Alertas activas',summary.alertas_activas,'bell','red']];
  return {html:`<section class="stats-grid">${statistics.map(([label,value,image,color])=>`<article class="stat-card ${color}"><div><small>${label}</small><strong>${value}</strong></div><span class="stat-circle">${icon(image)}</span></article>`).join('')}</section><section class="panel"><div class="panel-heading"><h2>Log de Actividad Reciente</h2><button class="text-button" id="all-history">Ver historial ${icon('history')}</button></div>${activity.length?tableMarkup(['Hora','Nombre','Área','Acción'],activity.slice(0,5).map(row=>`<tr><td>${esc(row.hora)}</td><td>${esc(row.nombre)} ${esc(row.apellido)}</td><td>${esc(row.cargo)}</td><td>${esc(row.accion_texto)} ${row.origen==='demostracion'?badge('Ejemplo','demo'):''}</td></tr>`)):empty('Las nuevas marcaciones aparecerán aquí.','Sin actividad reciente')}</section><div class="page-toolbar"><p class="muted small">${summary.en_refrigerio} en refrigerio · ${summary.personal_activo} personas activas</p><button class="soft-button" id="mark-attendance">${icon('plus')} Marcación manual</button></div>`,bind(){ $('#all-history').onclick=()=>navigate('historial'); $('#mark-attendance').onclick=markDialog; }};
}
async function markDialog() {
  try {
    const people=await api('/api/personal');
    const own=people.find(p=>p.id===app.user.id); const state=own?.estado_asistencia;
    const suggested=state==='Ausente'?'entrada':state==='En refrigerio'?'entrada_refrigerio':'salida_refrigerio';
    showDialog('Registrar asistencia',`<p class="manual-note">Este registro se identificará como manual. La marcación facial se realiza desde la aplicación de escritorio.</p><div class="form-grid"><div class="field full-width"><label for="mark-person">Empleado</label><select id="mark-person" name="empleado_id">${employeeOptions(people,app.user.id)}</select></div><div class="field full-width"><label for="mark-action">Acción</label><select id="mark-action" name="accion">${actionOptions(suggested)}</select></div><div class="field full-width"><label for="mark-note">Observación o motivo</label><textarea id="mark-note" name="observacion" maxlength="500" placeholder="Obligatorio al registrar por otra persona"></textarea></div></div>`,async data=>{await api('/api/marcaciones',{method:'POST',data:Object.fromEntries(data)});toast('Asistencia registrada correctamente.');navigate('resumen');},'Registrar');
  }catch(error){toast(error.message,true);}
}

async function staffPage() {
  const people=await api('/api/personal?inactivos=1'); app.people=people;
  return {html:`<div class="page-toolbar"><div><h2>Personal del hospital</h2><p>Directorio y estado de asistencia</p></div>${isAdmin()?`<button class="primary" id="new-person">${icon('plus')} Registrar personal</button>`:''}</div><div class="list-toolbar"><input id="staff-search" class="search-box" type="search" placeholder="Buscar por nombre, usuario o cargo" aria-label="Buscar personal">${isManager()?'<label class="checkbox-row"><input type="checkbox" id="show-inactive"> Mostrar dados de baja</label>':''}</div><section class="staff-grid" id="staff-cards"></section>`,bind(){
    function paint(){const query=$('#staff-search').value.toLocaleLowerCase('es'); const inactive=$('#show-inactive')?.checked; const visible=people.filter(p=>(p.activo||inactive)&&`${fullName(p)} ${p.username} ${p.cargo}`.toLocaleLowerCase('es').includes(query));
      $('#staff-cards').innerHTML=visible.length?visible.map(person=>`<article class="staff-card ${person.activo?'':'inactive'}"><span class="avatar">${esc(initials(person))}</span><h3>${esc(fullName(person))}</h3>${badge(person.cargo)}<p>${esc(person.ocupacion)}</p><div class="staff-badges">${person.activo?presenceBadge(person.estado_asistencia):badge('De baja','bad')}</div><div class="staff-note">${person.rostro_registrado?'Rostro registrado':'Registro facial pendiente'}</div>${isAdmin()?`<div class="card-actions"><button class="secondary" data-action="edit" data-id="${esc(person.id)}">Editar</button><button class="${person.activo?'danger':'soft-button'}" data-action="active" data-id="${esc(person.id)}">${person.activo?'Dar de baja':'Reactivar'}</button></div>`:''}</article>`).join(''):empty('Pruebe con otro nombre o revise el filtro de empleados.');
    }
    paint(); $('#staff-search').oninput=paint; if($('#show-inactive'))$('#show-inactive').onchange=paint;
    if($('#new-person'))$('#new-person').onclick=()=>employeeDialog();
    $('#staff-cards').onclick=async event=>{const button=event.target.closest('button[data-action]');if(!button)return;const person=people.find(p=>p.id===button.dataset.id);if(!person)return;
      if(button.dataset.action==='edit'){employeeDialog(person);return;}
      if(!confirm(`${person.activo?'¿Dar de baja':'¿Reactivar'} a ${fullName(person)}?${person.activo?' Se conservará su historial y se eliminará su registro facial.':''}`))return;
      try{await api(`/api/personal/${encodeURIComponent(person.id)}/estado`,{method:'POST',data:{activo:!person.activo}});toast('Estado actualizado.');navigate('personal');}catch(error){toast(error.message,true);}
    };
  }};
}
function employeeDialog(person=null) {
  showDialog(person?'Editar personal':'Registrar nuevo personal',`<div class="form-grid">${field('Nombre','nombre',person?.nombre,'text','required maxlength="80"')}${field('Apellido','apellido',person?.apellido,'text','required maxlength="80"')}${field('Edad','edad',person?.edad||'','number','required min="18" max="100"')}${field('Ocupación','ocupacion',person?.ocupacion,'text','required maxlength="120"')}${field('Usuario','username',person?.username,'text','placeholder="nombre_apellido" maxlength="50"')}${field(person?'Nueva contraseña (opcional)':'Contraseña','password','','password',`${person?'':'required'} minlength="8" maxlength="128" autocomplete="new-password"`)}<div class="field full-width"><label for="person-role">Cargo / Rol</label><select name="cargo" id="person-role">${ROLES.map(role=>`<option ${person?.cargo===role?'selected':''}>${role}</option>`).join('')}</select></div><label class="checkbox-row full-width"><input type="checkbox" name="consentimiento" ${person?.consentimiento?'checked':''}>Autoriza el uso del rostro para el control de asistencia.</label><div class="enrollment-note full-width">${icon('camera')}<div><strong>${person?.rostro_registrado?'Rostro registrado':'Registro facial pendiente'}</strong>La captura y el enrolamiento se realizan desde la aplicación de escritorio. Puede guardar los datos del personal antes de registrar su rostro.</div></div>${person?.rostro_registrado?'<button type="button" class="danger full-width" id="remove-face">Eliminar registro facial para volver a enrolar</button>':''}</div>`,async data=>{const values=Object.fromEntries(data);values.edad=Number(values.edad);values.consentimiento=data.has('consentimiento');await api(person?`/api/personal/${encodeURIComponent(person.id)}`:'/api/personal',{method:person?'PATCH':'POST',data:values});toast('Datos del personal guardados.');if(person?.id===app.user?.id){showLogin();toast('Inicie sesión nuevamente para aplicar los cambios.');}else navigate('personal');});
  if($('#remove-face'))$('#remove-face').onclick=async()=>{if(!confirm('¿Eliminar el registro facial? El historial de asistencia se conservará.'))return;try{await api(`/api/personal/${encodeURIComponent(person.id)}/rostro`,{method:'DELETE',data:{}});$('#modal').close();toast('Registro facial eliminado.');navigate('personal');}catch(error){$('#dialog-error').textContent=error.message;}};
}

async function schedulesPage() {
  const [people,shifts]=await Promise.all([api('/api/personal'),api('/api/todos_los_horarios')]);
  return {html:`<section class="panel">${isManager()?`<h2>Añadir Nuevo Turno</h2><form id="shift-form" class="shift-form"><div class="field"><label for="shift-person">Empleado</label><select id="shift-person" name="empleado_id" required>${employeeOptions(people)}</select></div><div class="field"><label for="shift-date">Fecha</label><input id="shift-date" name="fecha" type="date" value="${today()}" required></div><div class="field"><label for="shift-type">Tipo de Turno</label><select id="shift-type" name="turno"><option>Mañana</option><option>Tarde</option><option>Noche</option><option>Personalizado</option></select></div><button class="primary" type="submit">Añadir Turno</button><div class="shift-custom" id="shift-custom" hidden>${field('Inicio','inicio','07:00','time')}${field('Fin','fin','13:00','time')}</div><p class="form-error full-width" id="shift-error" role="alert"></p></form>`:''}<h2>Horarios Programados</h2><div id="shifts-table"></div></section><p class="caption">Los turnos nocturnos pueden terminar al día siguiente. Se impide asignar horarios superpuestos al mismo empleado.</p>`,bind(){
    mountTable($('#shifts-table'),['Fecha','Empleado','Cargo','Turno','Horario',...(isManager()?['Acciones']:[])],shifts,row=>`<tr><td>${humanDate(row.fecha)}</td><td><strong>${esc(row.nombre)}</strong></td><td>${esc(row.cargo)}</td><td>${esc(row.turno)}</td><td class="nowrap">${esc(row.inicio)} – ${esc(row.fin)}</td>${isManager()?`<td><div class="table-actions"><button class="secondary" data-action="edit" data-id="${row.id}">Editar</button><button class="danger" data-action="delete" data-id="${row.id}">Eliminar</button></div></td>`:''}</tr>`);
    if($('#shift-type'))$('#shift-type').onchange=()=>{$('#shift-custom').hidden=$('#shift-type').value!=='Personalizado';};
    if($('#shift-form'))$('#shift-form').onsubmit=async event=>{event.preventDefault();$('#shift-error').textContent='';const button=$('button[type=submit]',event.target);button.disabled=true;try{await api('/api/todos_los_horarios',{method:'POST',data:Object.fromEntries(new FormData(event.target))});toast('Turno programado.');navigate('horarios');}catch(error){$('#shift-error').textContent=error.message;}finally{button.disabled=false;}};
    $('#shifts-table').addEventListener('click',async event=>{const button=event.target.closest('button[data-action]');if(!button)return;const row=shifts.find(s=>s.id===button.dataset.id);if(button.dataset.action==='edit'){shiftDialog(row,people);return;}if(!confirm('¿Eliminar este turno programado?'))return;try{await api(`/api/horarios/${row.id}`,{method:'DELETE',data:{}});toast('Turno eliminado.');navigate('horarios');}catch(error){toast(error.message,true);}});
  }};
}
function shiftDialog(shift,people) {
  showDialog('Editar turno',`<div class="form-grid"><div class="field full-width"><label for="edit-shift-person">Empleado</label><select name="empleado_id" id="edit-shift-person" required>${employeeOptions(people,shift.empleado_id)}</select></div>${field('Fecha','fecha',shift.fecha,'date','required')}<div class="field"><label for="edit-shift-type">Tipo de turno</label><select name="turno" id="edit-shift-type">${['Mañana','Tarde','Noche','Personalizado'].map(type=>`<option ${type===shift.turno?'selected':''}>${type}</option>`).join('')}</select></div>${field('Hora de inicio personalizada','inicio',shift.inicio,'time','required')}${field('Hora de fin personalizada','fin',shift.fin,'time','required')}<p class="caption full-width">Para cambiar las horas, seleccione Personalizado. Los demás tipos utilizan el horario predefinido.</p></div>`,async data=>{await api(`/api/horarios/${shift.id}`,{method:'PATCH',data:Object.fromEntries(data)});toast('Turno actualizado.');navigate('horarios');});
}

function filtersMarkup(people,report=false) {
  return `<form id="history-filters" class="filter-grid">${field('Desde','desde',app.filters.desde,'date','required')}${field('Hasta','hasta',app.filters.hasta,'date','required')}<div class="field"><label for="filter-person">Empleado</label><select id="filter-person" name="empleado_id">${employeeOptions(people,app.filters.empleado_id,true)}</select></div><div class="field"><label for="filter-action">Acción</label><select id="filter-action" name="accion">${actionOptions(app.filters.accion,true)}</select></div><button class="primary" type="submit">${report?'Consultar':'Filtrar'}</button><div class="field search-wide"><label for="filter-search">Buscar</label><input id="filter-search" name="buscar" type="search" value="${esc(app.filters.buscar)}" placeholder="Nombre, cargo o acción"></div></form>`;
}
async function historyPage(report=false) {
  const [people,rows]=await Promise.all([api('/api/personal?inactivos=1'),api('/api/historial?'+new URLSearchParams(app.filters))]);
  return {html:`<section class="panel"><h2>${report?'Reporte de Asistencia':'Historial Completo de Asistencia'}</h2>${report?'<p class="report-intro">Seleccione un rango de fechas y genere un reporte con las mismas marcaciones que aparecen en la consulta. Los datos de ejemplo se identifican por su origen.</p>':''}${filtersMarkup(people,report)}<div id="history-table"></div>${report?`<div class="report-actions"><button class="primary" data-format="xlsx">${icon('download')} Exportar Excel</button><button class="secondary" data-format="pdf">${icon('download')} Exportar PDF</button><button class="secondary" data-format="csv">${icon('download')} Exportar CSV</button></div>`:''}</section>`,bind(){mountTable($('#history-table'),['Fecha','Hora','Nombre','Área','Acción','Origen'],rows,historyRow);$('#history-filters').onsubmit=event=>{event.preventDefault();app.filters=Object.fromEntries(new FormData(event.target));navigate(report?'reportes':'historial');};$$('[data-format]').forEach(button=>button.onclick=()=>{const kind=button.dataset.format;const current=Object.fromEntries(new FormData($('#history-filters')));app.filters=current;download('/api/reportes?'+new URLSearchParams({...current,formato:kind}),`BIOTRACK_asistencia.${kind}`);});}};
}

async function predictionsPage() {
  const data=await api('/api/predicciones');
  const check=message=>`<div class="notice-line"><span class="check-circle">${icon('check')}</span><p>${esc(message)}</p></div>`;
  const maximum=Math.max(1,...data.disponibilidad.map(role=>role.presente+role.refrigerio+role.ausente));
  return {html:`<section class="prediction-grid"><article class="panel prediction-card"><div class="prediction-title"><span class="prediction-icon">${icon('clock')}</span><h2>Cobertura de Turnos (Hoy)</h2></div>${data.cobertura.length?data.cobertura.map(row=>`<div class="coverage-item"><div><strong>${esc(row.nombre)}</strong><small>${esc(row.turno)} · ${esc(row.horario)}</small></div>${presenceBadge(row.estado)}</div>`).join(''):check('No hay turnos programados para hoy.')}</article><article class="panel prediction-card"><div class="prediction-title"><span class="prediction-icon purple">${icon('history')}</span><h2>Sobretiempo y Fatiga</h2></div>${data.sobretiempo.length?data.sobretiempo.map(row=>`<div class="coverage-item"><div><strong>${esc(row.nombre)}</strong><small>${esc(row.detalle)}</small></div>${badge(row.horas+' h','warn')}</div>`).join(''):check('No se detectan jornadas superiores al límite configurado.')}</article><article class="panel prediction-card"><div class="prediction-title"><span class="prediction-icon blue">${icon('people')}</span><h2>Disponibilidad de Personal por Rol</h2></div><div class="chart-title">Disponibilidad de Personal por Rol</div><div class="chart-legend"><span><i class="legend-dot"></i>Presente</span><span><i class="legend-dot yellow"></i>En Refrigerio</span><span><i class="legend-dot gray"></i>Ausente</span></div>${data.disponibilidad.map(row=>{const a=row.presente/maximum*100,b=row.refrigerio/maximum*100,c=row.ausente/maximum*100;return `<div class="role-bar"><div class="role-bar-label"><span>${esc(row.cargo)}</span><span>${row.presente} / ${row.refrigerio} / ${row.ausente}</span></div><svg class="bar-track" width="100%" height="13" viewBox="0 0 100 13" preserveAspectRatio="none" role="img" aria-label="${esc(row.cargo)}: ${row.presente} presentes, ${row.refrigerio} en refrigerio y ${row.ausente} ausentes"><rect width="100" height="13" fill="#f2f5f8"/><rect width="${a}" height="13" fill="#50bb89"/><rect x="${a}" width="${b}" height="13" fill="#dfb83e"/><rect x="${a+b}" width="${c}" height="13" fill="#99a4b3"/></svg></div>`;}).join('')}</article></section><p class="caption">Análisis orientativo basado en turnos programados y duración de jornada.</p>`};
}

async function incidentsPage() {
  const rows=await api('/api/incidencias');
  return {html:`<div class="page-toolbar"><div><h2>Incidencias de asistencia</h2><p>${rows.filter(row=>row.estado==='Pendiente').length} pendientes de revisión</p></div>${isManager()?`<button class="primary" id="new-incident">${icon('plus')} Registrar incidencia</button>`:''}</div><section class="panel"><div class="list-toolbar"><input type="search" id="incident-search" class="search-box" placeholder="Buscar empleado o incidencia" aria-label="Buscar incidencias"><select id="incident-status" class="search-box" aria-label="Filtrar estado"><option value="">Todos los estados</option><option>Pendiente</option><option>Resuelta</option><option>Justificada</option></select></div><div id="incident-table"></div></section>`,bind(){
    function paint(){const query=$('#incident-search').value.toLocaleLowerCase('es'),status=$('#incident-status').value;const filtered=rows.filter(row=>(!status||row.estado===status)&&`${row.nombre} ${row.tipo} ${row.detalle}`.toLocaleLowerCase('es').includes(query));mountTable($('#incident-table'),['Fecha','Personal','Tipo','Detalle','Estado',...(isManager()?['Acciones']:[])],filtered,row=>`<tr><td class="nowrap">${humanDate(row.fecha)}</td><td><strong>${esc(row.nombre)}</strong></td><td>${esc(row.tipo)}</td><td><span>${esc(row.detalle)}</span>${row.resolucion?`<p class="small muted">Resolución: ${esc(row.resolucion)}</p>`:''}</td><td>${badge(row.estado,row.estado==='Pendiente'?'warn':'good')}</td>${isManager()?`<td><button class="secondary" data-incident="${row.id}">Revisar</button></td>`:''}</tr>`);}
    paint();$('#incident-search').oninput=paint;$('#incident-status').onchange=paint;
    if($('#new-incident'))$('#new-incident').onclick=async()=>{try{const people=await api('/api/personal');showDialog('Registrar incidencia',`<div class="form-grid"><div class="field full-width"><label for="incident-person">Empleado</label><select name="empleado_id" id="incident-person" required>${employeeOptions(people)}</select></div>${field('Fecha','fecha',today(),'date','required')}<div class="field"><label for="incident-type">Tipo</label><select name="tipo" id="incident-type">${['Tardanza','Ausencia','Reconocimiento fallido','Fuera de turno','Otra'].map(type=>`<option>${type}</option>`).join('')}</select></div><div class="field full-width"><label for="incident-detail">Detalle</label><textarea name="detalle" id="incident-detail" required minlength="5" maxlength="1000"></textarea></div></div>`,async data=>{await api('/api/incidencias',{method:'POST',data:Object.fromEntries(data)});toast('Incidencia registrada.');navigate('incidencias');});}catch(error){toast(error.message,true);}};
    $('#incident-table').addEventListener('click',event=>{const button=event.target.closest('[data-incident]');if(!button)return;const row=rows.find(item=>item.id===button.dataset.incident);showDialog('Revisar incidencia',`<p><strong>${esc(row.nombre)}</strong> · ${esc(row.tipo)}</p><p class="report-intro">${esc(row.detalle)}</p><div class="field"><label for="resolution-status">Estado</label><select id="resolution-status" name="estado">${['Resuelta','Justificada','Pendiente'].map(status=>`<option ${status===row.estado?'selected':''}>${status}</option>`).join('')}</select></div><div class="field"><label for="resolution-note">Resolución o justificación</label><textarea id="resolution-note" name="resolucion" maxlength="1000">${esc(row.resolucion)}</textarea></div>`,async data=>{await api(`/api/incidencias/${row.id}`,{method:'PATCH',data:Object.fromEntries(data)});toast('Incidencia actualizada.');navigate('incidencias');});});
  }};
}

async function settingsPage() {
  const settings=await api('/api/configuracion');
  return {html:`<div class="settings-grid"><section class="panel"><h2>Parámetros del sistema</h2><form id="settings-form" class="settings-form"><div class="full-width">${field('Nombre del hospital','hospital',settings.hospital,'text','required minlength="3" maxlength="100"')}</div>${field('Tolerancia de tardanza (minutos)','tolerancia_minutos',settings.tolerancia_minutos,'number','required min="0" max="120"')}${field('Jornada máxima (horas)','jornada_max_horas',settings.jornada_max_horas,'number','required min="1" max="24"')}${field('Tiempo de refrigerio (minutos)','minutos_refrigerio',settings.minutos_refrigerio,'number','required min="5" max="180"')}${field('Umbral de comparación facial','umbral_rostro',settings.umbral_rostro,'number','required min="0.30" max="0.65" step="0.01"')}${field('Índice de cámara del escritorio','camara_indice',settings.camara_indice??0,'number','required min="0" max="9" step="1"')}<label class="checkbox-row full-width"><input type="checkbox" name="facial_habilitado" ${settings.facial_habilitado?'checked':''}> Habilitar reconocimiento facial</label><p class="caption full-width">La función facial se entrega desactivada. Habilítela únicamente cuando se vayan a realizar las pruebas de cámara y enrolamiento.</p><p id="settings-error" class="form-error full-width" role="alert"></p><button class="primary" type="submit">Guardar configuración</button></form></section><section class="panel"><h2>Datos y seguridad</h2><div class="info-line"><span>Almacenamiento</span><strong>Local · Archivo JSON</strong></div><div class="info-line"><span>Zona horaria</span><strong>Perú (UTC−05:00)</strong></div><div class="info-line"><span>Contraseñas</span><strong>PBKDF2 con sal individual</strong></div><div class="info-line"><span>Reconocimiento facial</span><strong>${settings.facial_habilitado?'Habilitado':'Pendiente de validación'}</strong></div><p class="caption">La copia de seguridad contiene información de usuarios y asistencia. Guárdela en un lugar de acceso restringido.</p><button class="secondary" id="backup-download">${icon('download')} Descargar respaldo</button><p class="caption">Si registra rostros, conserve además el archivo biometric.key del directorio de datos. Es necesario para recuperar sus plantillas cifradas.</p></section></div>`,bind(){
    $('#settings-form').onsubmit=async event=>{event.preventDefault();const form=new FormData(event.target);const data=Object.fromEntries(form);data.facial_habilitado=form.has('facial_habilitado');if(data.facial_habilitado&&!settings.facial_habilitado&&!confirm('¿Habilitar el reconocimiento facial para iniciar su validación? Esta acción no abre la cámara automáticamente.'))return;$('#settings-error').textContent='';try{await api('/api/configuracion',{method:'PATCH',data});toast('Configuración guardada.');navigate('configuracion');}catch(error){$('#settings-error').textContent=error.message;}};
    $('#backup-download').onclick=()=>download('/api/backup','BIOTRACK_respaldo.json');
  }};
}
async function auditPage() {
  const rows=await api('/api/auditoria');return {html:'<section class="panel"><h2>Registro de auditoría</h2><p class="report-intro">Últimas operaciones de administración y marcación. Las contraseñas y los datos faciales no se incluyen en este registro.</p><div id="audit-table"></div></section>',bind(){mountTable($('#audit-table'),['Fecha','Hora','Usuario','Operación','Detalle'],rows,row=>`<tr><td>${humanDate(row.fecha_hora)}</td><td>${esc(row.fecha_hora.slice(11,19))}</td><td><strong>${esc(row.usuario)}</strong></td><td>${esc(row.accion.replaceAll('_',' '))}</td><td>${esc(row.detalle)}</td></tr>`);}};
}
const pages={resumen:summaryPage,personal:staffPage,horarios:schedulesPage,predicciones:predictionsPage,historial:()=>historyPage(false),reportes:()=>historyPage(true),incidencias:incidentsPage,configuracion:settingsPage,auditoria:auditPage};

$('#toggle-password').innerHTML=icon('eye');
$('#toggle-password').onclick=()=>{const field=$('#password');field.type=field.type==='password'?'text':'password';$('#toggle-password').setAttribute('aria-label',field.type==='password'?'Mostrar contraseña':'Ocultar contraseña');};
$('#dialog-close').innerHTML=icon('close'); $('#dialog-close').onclick=()=>$('#modal').close(); $('#dialog-cancel').onclick=()=>$('#modal').close();
$('#menu-toggle').innerHTML=icon('menu'); $('#menu-toggle').onclick=()=>$('#sidebar').classList.toggle('open');
$('#account-button').onclick=()=>showDialog('Cambiar mi contraseña',`<div class="form-grid full-width">${field('Contraseña actual','actual','','password','required autocomplete="current-password"')}${field('Nueva contraseña','nueva','','password','required minlength="8" maxlength="128" autocomplete="new-password"')}<p class="caption full-width">Al guardar se cerrarán sus sesiones activas.</p></div>`,async data=>{await api('/api/password',{method:'POST',data:Object.fromEntries(data)});showLogin();toast('Contraseña actualizada. Inicie sesión nuevamente.');},'Actualizar contraseña');
$('#login-form').onsubmit=async event=>{event.preventDefault();const button=$('.login-submit');button.disabled=true;button.textContent='Ingresando…';$('#login-error').textContent='';try{const result=await api('/api/login',{method:'POST',data:Object.fromEntries(new FormData(event.target))});$('#password').value='';showApplication(result.usuario,result.csrf);}catch(error){$('#login-error').textContent=error.message;}finally{button.disabled=false;button.textContent='Ingresar';}};
function tick(){ $('#clock').textContent=new Intl.DateTimeFormat('es-PE',{timeZone:'America/Lima',hour:'2-digit',minute:'2-digit',hour12:true}).format(new Date()); }
tick();setInterval(tick,1000);
setInterval(()=>{if(app.user&&!document.hidden&&!$('#modal').open&&['resumen','predicciones'].includes(app.page))navigate(app.page,true);},10000);
window.addEventListener('hashchange',()=>{const page=location.hash.slice(1);if(app.user&&page in pages&&page!==app.page)navigate(page);});
(async()=>{try{const session=await api('/api/session');showApplication(session.usuario,session.csrf);}catch(error){showLogin();}})();
