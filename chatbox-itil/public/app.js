// Global State
const state = {
  currentAgent: 'agente_01',
  currentTeam: 'mesa_de_ayuda',
  activeTicket: null,
  // Agente de soporte: ticket en foco para dar contexto a Gemini
  asistenteTicketFoco: null,
  // Acumulacion de IDs de sugerencias ya mostradas en el asistente (por ticket)
  asistenteExcluidosMap: new Map(), // ticketId -> [id1, id2, ...]
  asistenteConversaciones: {} // Historial efimero por sesion
};

// (Eliminada la persistencia en localStorage para evitar conflictos de caché entre sesiones)

const API_BASE = '/api';

function escapeHtml(text) {
  if (!text) return '';
  return String(text).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#039;');
}

let systemConfig = { teams: [], shifts: [] };

// Stealth UI config logic
async function attachAddNewLogic(selectId, listKey) {
  const select = document.getElementById(selectId);
  if (!select) return;
  select.addEventListener('change', async (e) => {
    if (e.target.value === '_ADD_NEW_') {
      const newValue = prompt(`Añadir nueva opción (quedará guardada para futuros casos):`);
      if (newValue && newValue.trim()) {
        try {
          const res = await fetch(`${API_BASE}/config/add`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ listKey, value: newValue.trim() })
          });
          const data = await res.json();
          if (data.success) {
            await loadConfig();
            document.getElementById(selectId).value = newValue.trim();
          } else {
            DeskUI.notify('Error al añadir: ' + data.error);
            select.value = '';
          }
        } catch(err) {
          DeskUI.notify('Error de red al añadir');
          select.value = '';
        }
      } else {
        select.value = '';
      }
    } else if (e.target.value === '_REMOVE_') {
      const delValue = prompt(`Escribe EXACTAMENTE el nombre de la opción a eliminar de la lista:`);
      if (delValue && delValue.trim()) {
        try {
          const res = await fetch(`${API_BASE}/config/remove`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ listKey, value: delValue.trim() })
          });
          const data = await res.json();
          if (data.success) {
            await loadConfig();
            DeskUI.notify(`Opción "${delValue}" eliminada con éxito.`);
            select.value = '';
          } else {
            DeskUI.notify('Error al eliminar: ' + data.error);
            select.value = '';
          }
        } catch(err) {
          DeskUI.notify('Error de red al eliminar');
          select.value = '';
        }
      } else {
        select.value = '';
      }
    }
  });
}

async function fetchGlobalConfig() {
  try {
    const res = await fetch(`${API_BASE}/config`);
    systemConfig = await res.json();
    const container = document.getElementById('derivate-teams-container');
    if (container && systemConfig.teams) {
      container.innerHTML = systemConfig.teams.map(team =>
        `<label class="team-card"><input type="radio" name="derivate_team" value="${team}" required><span>${team.replace(/_/g, ' ').toUpperCase()}</span></label>`
      ).join('');
    }

    const selectTipo = document.getElementById('new-ticket-tipo');
    if (selectTipo && systemConfig.tiposTicket) {
      selectTipo.innerHTML = '<option value="">Seleccione...</option>' + systemConfig.tiposTicket.map(t => `<option value="${t}">${t}</option>`).join('') + '<option value="_ADD_NEW_">[+] Añadir nuevo...</option><option value="_REMOVE_">[-] Eliminar...</option>';
    }

    const selectPrioridad = document.getElementById('new-ticket-prioridad');
    if (selectPrioridad && systemConfig.prioridades) {
      selectPrioridad.innerHTML = '<option value="">Seleccione...</option>' + systemConfig.prioridades.map(p => `<option value="${p}">${p}</option>`).join('') + '<option value="_ADD_NEW_">[+] Añadir nuevo...</option><option value="_REMOVE_">[-] Eliminar...</option>';
    }

    const selectSubcategoria = document.getElementById('close-ticket-subcategoria');
    if (selectSubcategoria && systemConfig.subcategoriasTecnicas) {
      selectSubcategoria.innerHTML = '<option value="">Seleccione subcategoría técnica...</option>' + systemConfig.subcategoriasTecnicas.map(sc => `<option value="${sc}">${sc}</option>`).join('') + '<option value="_ADD_NEW_">[+] Añadir nuevo...</option><option value="_REMOVE_">[-] Eliminar...</option>';
    }

    // Attach event listeners only once
    if (!window.__stealthLogicAttached) {
      attachAddNewLogic('new-ticket-tipo', 'tiposTicket');
      attachAddNewLogic('new-ticket-prioridad', 'prioridades');
      attachAddNewLogic('close-ticket-subcategoria', 'subcategoriasTecnicas');
      window.__stealthLogicAttached = true;
    }
  } catch (e) {
    console.error('Error fetching config', e);
  }
}

const loadConfig = fetchGlobalConfig;

// UI Elements
const navChat = document.getElementById('nav-chat');
const navTickets = document.getElementById('nav-tickets');
const navKnowledge = document.getElementById('nav-knowledge');
const navDashboard = document.getElementById('nav-dashboard');
const navAsistenteIA = document.getElementById('nav-asistente-ia');
const viewChat = document.getElementById('view-chat');
const viewTickets = document.getElementById('view-tickets');
const viewKnowledge = document.getElementById('view-knowledge');
const viewDashboard = document.getElementById('view-dashboard');
const viewAsistenteIA = document.getElementById('view-asistente-ia');
const chatHistory = document.getElementById('chat-history');
const chatForm = document.getElementById('chat-form');
const chatInput = document.getElementById('chat-input');
const ticketPanel = document.getElementById('active-ticket-panel');
const ticketIdSpan = document.getElementById('active-ticket-id');
const ticketStatus = document.getElementById('active-ticket-status');

const navKnowledgeMgmt = document.getElementById('nav-knowledge-mgmt');
const viewKnowledgeMgmt = document.getElementById('view-knowledge-mgmt');
const viewTicketDetail = document.getElementById('view-ticket-detail');

const ALL_NAVS = [navChat, navTickets, navKnowledgeMgmt, navKnowledge, navDashboard, navAsistenteIA];
const ALL_VIEWS = [viewChat, viewTickets, viewKnowledgeMgmt, viewKnowledge, viewDashboard, viewAsistenteIA, viewTicketDetail];

function switchView(activeNav, activeView) {
  ALL_NAVS.forEach(n => n.classList.remove('active'));
  ALL_VIEWS.forEach(v => { v.style.display = 'none'; v.classList.remove('active-view'); });
  activeNav.classList.add('active');
  activeView.style.display = 'block';
  activeView.classList.add('active-view');
}

navChat.addEventListener('click', e => { e.preventDefault(); switchView(navChat, viewChat); });
navTickets.addEventListener('click', e => { e.preventDefault(); switchView(navTickets, viewTickets); loadAllTickets(); });

navKnowledge.addEventListener('click', e => { e.preventDefault(); switchView(navKnowledge, viewKnowledge); renderKnowledge(); });
navKnowledgeMgmt.addEventListener('click', e => { e.preventDefault(); switchView(navKnowledgeMgmt, viewKnowledgeMgmt); renderKnowledgeMgmt(); });
navDashboard.addEventListener('click', e => { e.preventDefault(); switchView(navDashboard, viewDashboard); renderDashboard(); });
navAsistenteIA.addEventListener('click', e => { e.preventDefault(); switchView(navAsistenteIA, viewAsistenteIA); });


// Formatea el bloque <SUGERENCIA> de Gemini de forma segura
function formatSuggestionHTML(rawText) {
  // 1. Escapar HTML (Sanitizacion obligatoria antes de cualquier otra transformacion)
  let safeText = rawText.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

  // 2. Extraer bloque SUGERENCIA del texto ya escapado
  const suggestionRegex = /&lt;(?:SUGERENCIA|SUGGESTION)&gt;([\s\S]*?)&lt;\/(?:SUGERENCIA|SUGGESTION)&gt;/i;
  const match = safeText.match(suggestionRegex);

  if (!match) {
    // Sin etiquetas: mostrar el texto completo sin componente especial
    return safeText.replace(/\n/g, '<br>');
  }

  let suggestionContent = match[1].trim();

  // 3. Convertir vinetas "- " al inicio de linea en <ul><li> (sobre texto ya escapado)
  let inList = false;
  let newLines = [];
  suggestionContent.split('\n').forEach(line => {
    if (line.trim().startsWith('- ')) {
      if (!inList) { newLines.push('<ul style="padding-left: 20px; margin: 4px 0;">'); inList = true; }
      newLines.push(`<li>${line.trim().substring(2)}</li>`);
    } else {
      if (inList) { newLines.push('</ul>'); inList = false; }
      newLines.push(line);
    }
  });
  if (inList) newLines.push('</ul>');
  const formattedContent = newLines.join('<br>').replace(/<\/ul><br>/g, '</ul>').replace(/<ul[^>]*><br>/g, '<ul>');

  const suggestionBox = `<div class="suggestion-box"><div class="suggestion-box-header">Casos similares encontrados</div><div class="suggestion-box-content">${formattedContent}</div></div>`;

  // Reemplazar el bloque con el componente y limpiar el texto restante
  let resultText = safeText.replace(suggestionRegex, suggestionBox);
  // Limpiar etiquetas huerfanas por si Gemini las mal-forma
  resultText = resultText.replace(/&lt;\/?(?:SUGERENCIA|SUGGESTION)&gt;/gi, '');
  return resultText.replace(/\n/g, '<br>');
}

// Chat Logic
function addMessage(text, type) {
  const div = document.createElement('div');
  div.className = `message ${type}`;
  // Escape HTML y convierte saltos de linea
  const safe = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\n/g, '<br>');
  div.innerHTML = `<div class="message-content">${safe}</div>`;
  chatHistory.appendChild(div);
  chatHistory.scrollTop = chatHistory.scrollHeight;
}

chatForm.addEventListener('submit', async (e) => {
  e.preventDefault();
  const text = chatInput.value.trim();
  if (!text) return;

  addMessage(text, 'user');
  chatInput.value = '';

  if (state.activeTicket && (state.activeTicket.estado === 'cerrado' || state.activeTicket.estado === 'derivado')) {
    state.activeTicket = null;
  }

  if (!state.activeTicket) {
    // Primera interaccion -> Crea ticket
    try {
      const res = await fetch(`${API_BASE}/tickets`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: text, usuario: state.currentAgent })
      });
      const data = await res.json();

      addMessage(data.response, 'system');

      state.activeTicket = data.ticket;
      updateTicketPanel();

    } catch (err) {
      addMessage('Error de comunicacion con el servidor.', 'system');
    }
  } else {
    // Flujo secundario - En este demo basico, todo lo extra que se escriba
    // en el chat si hay un ticket abierto, se agrega a la bitacora automaticamente
    try {
      const res = await fetch(`${API_BASE}/tickets/${state.activeTicket.id}/log`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          autor: state.currentAgent,
          texto: `Consulta adicional: ${text}`,
          minutosInvertidos: 1
        })
      });
      const data = await res.json();
      state.activeTicket = data.ticket;

      if (data.response) {
        addMessage(data.response, 'system');
      } else {
        addMessage('He registrado tu mensaje en la bitacora.', 'system');
      }
      updateTicketPanel();
    } catch (err) {
      addMessage('Error registrando entrada.', 'system');
    }
  }
});

// Ticket Panel Logic
function updateTicketPanel() {
  if (!state.activeTicket) {
    ticketPanel.style.display = 'none';
    return;
  }

  ticketPanel.style.display = 'block';
  ticketIdSpan.textContent = state.activeTicket.id.substring(0, 8) + '...';

  const estado = state.activeTicket.estado;
  ticketStatus.textContent = estado.toUpperCase();

  if (estado === 'cerrado' || estado === 'derivado') {
    ticketStatus.className = 'ticket-status-badge ' + (estado === 'cerrado' ? 'status-closed' : 'status-derived');
    document.getElementById('log-form').style.display = 'none';
    document.querySelector('.action-buttons').style.display = 'none';
  } else {
    ticketStatus.className = 'ticket-status-badge status-open';
    document.getElementById('log-form').style.display = 'flex';
    document.querySelector('.action-buttons').style.display = 'flex';
  }
}

// Log manual form
document.getElementById('log-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const text = document.getElementById('log-text').value;
  const mins = parseInt(document.getElementById('log-mins').value) || 0;

  try {
    const res = await fetch(`${API_BASE}/tickets/${state.activeTicket.id}/log`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        autor: state.currentAgent,
        texto: text,
        minutosInvertidos: mins
      })
    });

    if (res.ok) {
      const data = await res.json();
      state.activeTicket = data.ticket;
      document.getElementById('log-text').value = '';
      document.getElementById('log-mins').value = '';
      DeskUI.notify('Bitacora actualizada.');
    }
  } catch(err) {
    DeskUI.notify('Error al guardar en bitacora.');
  }
});

// Close Ticket Trigger
document.getElementById('btn-close-ticket').addEventListener('click', () => {
  if (!state.activeTicket) return;
  document.getElementById('close-ticket-id').value = state.activeTicket.id;
  document.getElementById('close-ticket-solucion').value = '';
  document.getElementById('close-ticket-mins').value = '0';
  document.getElementById('close-ticket-subcategoria').value = '';
  document.getElementById('modal-close-ticket').style.display = 'flex';
});

// Close Ticket Submit
document.getElementById('form-close-ticket').addEventListener('submit', async (e) => {
  e.preventDefault();
  const ticketId = document.getElementById('close-ticket-id').value;
  const subcategoria = document.getElementById('close-ticket-subcategoria').value;
  const solucion = document.getElementById('close-ticket-solucion').value.trim();
  const mins = parseInt(document.getElementById('close-ticket-mins').value) || 0;

  if (!ticketId || !subcategoria || !solucion) return;

  try {
    const res = await fetch(`${API_BASE}/tickets/${ticketId}/close`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        autor: state.currentAgent,
        texto: `Solución Aplicada [${subcategoria}]: ${solucion}`,
        minutosInvertidos: mins,
        subcategoriaTecnica: subcategoria
      })
    });

    if (res.ok) {
      const data = await res.json();
      state.activeTicket = data.ticket;
      updateTicketPanel();
      document.getElementById('modal-close-ticket').style.display = 'none';
      loadAllTickets();
      addMessage('El ticket ha sido cerrado.', 'system');

      setTimeout(() => {
        state.activeTicket = null;
        updateTicketPanel();
        chatHistory.innerHTML = '<div class="message system"><div class="message-content">Ticket finalizado. Listo para nueva consulta.</div></div>';
      }, 3000);
    } else {
      const err = await res.json();
      DeskUI.notify("Error al cerrar ticket: " + (err.error || "Desconocido"));
    }
  } catch(err) {
    DeskUI.notify("Error de conexión");
  }
});
// Derivate Modal
const modalDerivate = document.getElementById('modal-derivate');
document.getElementById('btn-derivate-ticket').addEventListener('click', () => {
  document.getElementById('derivate-ticket-id').value = state.activeTicket.id;
  modalDerivate.style.display = 'flex';
});

document.getElementById('form-derivate').addEventListener('submit', async (e) => {
  e.preventDefault();
  const ticketIdInput = document.getElementById('derivate-ticket-id').value;
  const ticketId = ticketIdInput || (state.activeTicket ? state.activeTicket.id : null);

  if (!ticketId) return;

  const newTeam = document.querySelector('input[name="derivate_team"]:checked')?.value || 'otro';
  const reason = document.getElementById('derivate-reason').value;

  try {
    const res = await fetch(`${API_BASE}/tickets/${ticketId}/derivate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        nuevoEquipo: newTeam,
        autor: state.currentAgent,
        motivo: reason
      })
    });

    if (res.ok) {
      const data = await res.json();
      modalDerivate.style.display = 'none';

      if (state.activeTicket && state.activeTicket.id === ticketId) {
        state.activeTicket = data.ticket;
        updateTicketPanel();
        addMessage(`El ticket ha sido derivado a ${newTeam}.`, 'system');

        setTimeout(() => {
          state.activeTicket = null;
          updateTicketPanel();
          chatHistory.innerHTML = '<div class="message system"><div class="message-content">Ticket derivado. Listo para nueva consulta.</div></div>';
        }, 3000);
      } else {
        DeskUI.notify('Ticket derivado exitosamente.');
      }

      // Actualizar vistas si están activas
      if (document.getElementById('view-tickets').classList.contains('active-view')) loadAllTickets();
    }
  } catch(err) {
    DeskUI.notify('Error al derivar.');
  }
});

// Comment Modal Logic
document.getElementById('form-comment').addEventListener('submit', async (e) => {
  e.preventDefault();
  const ticketId = document.getElementById('comment-ticket-id').value;
  const text = document.getElementById('comment-text').value;
  const mins = parseInt(document.getElementById('comment-mins').value) || 0;

  if (!ticketId) return;

  try {
    const res = await fetch(`${API_BASE}/tickets/${ticketId}/log`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        autor: state.currentAgent,
        texto: text,
        minutosInvertidos: mins
      })
    });

    if (res.ok) {
      document.getElementById('modal-comment').style.display = 'none';
      DeskUI.notify('Comentario agregado a la bitácora.');

      // Actualizar vistas si están activas
      if (document.getElementById('view-tickets').classList.contains('active-view')) loadAllTickets();
    }
  } catch(err) {
    DeskUI.notify('Error al guardar comentario.');
  }
});


// UI helpers for tickets
window.toggleMenu = function(btn) {
  const dropdown = btn.nextElementSibling;
  const isShowing = dropdown.classList.contains('show');
  document.querySelectorAll('.ticket-dropdown.show').forEach(el => el.classList.remove('show'));
  if (!isShowing) {
    dropdown.classList.add('show');
  }
};

document.addEventListener('click', function(e) {
  if (!e.target.closest('.ticket-menu')) {
    document.querySelectorAll('.ticket-dropdown.show').forEach(el => el.classList.remove('show'));
  }
});

window.toggleAccordion = function(btn) {
  const content = btn.nextElementSibling;
  const arrow = btn.querySelector('.accordion-arrow');
  if (content.style.display === 'none') {
    content.style.display = 'block';
    arrow.classList.add('open');
  } else {
    content.style.display = 'none';
    arrow.classList.remove('open');
  }
};

window.openDerivateModal = function(ticketId) {
  document.getElementById('derivate-ticket-id').value = ticketId;
  document.getElementById('derivate-reason').value = '';
  // Clear radios
  document.querySelectorAll('input[name="derivate_team"]').forEach(r => r.checked = false);
  document.getElementById('modal-derivate').style.display = 'flex';
  document.querySelectorAll('.ticket-dropdown.show').forEach(el => el.classList.remove('show'));
};

window.openCommentModal = function(ticketId) {
  document.getElementById('comment-ticket-id').value = ticketId;
  document.getElementById('comment-text').value = '';
  document.getElementById('comment-mins').value = '0';
  document.getElementById('modal-comment').style.display = 'flex';
  document.querySelectorAll('.ticket-dropdown.show').forEach(el => el.classList.remove('show'));
};

const agentInput = document.getElementById('current-agent-input');
if (agentInput) {
  agentInput.addEventListener('change', (e) => {
    state.currentAgent = e.target.value.trim() || 'agente_01';
    loadAllTickets();
  });
}






// All Tickets View Logic (General Management)
const ticketListState = { items: [], page: 1, request: 0 };
const ticketIconPaths = {
  'file-text':'<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z"/><path d="M14 2v6h6M8 13h8M8 17h8"/>',
  clock:'<circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/>',
  'corner-up-right':'<path d="M15 14l5-5-5-5M20 9H9a5 5 0 0 0-5 5v6"/>',
  'circle-check':'<circle cx="12" cy="12" r="10"/><path d="m8 12 3 3 5-6"/>',
  'triangle-alert':'<path d="m10.3 3.9-8.5 14.2A2 2 0 0 0 3.5 21h17a2 2 0 0 0 1.7-2.9L13.7 3.9a2 2 0 0 0-3.4 0Z"/><path d="M12 9v4M12 17h.01"/>',
  'user-round':'<circle cx="12" cy="8" r="5"/><path d="M20 21a8 8 0 0 0-16 0"/>',
  sparkles:'<path d="m12 3 1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9L12 3ZM19 17l.7 1.3L21 19l-1.3.7L19 21l-.7-1.3L17 19l1.3-.7L19 17Z"/>',
  'arrow-right':'<path d="M5 12h14m-6-6 6 6-6 6"/>',
  ellipsis:'<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>'
};
const ticketIcon = name => `<svg class="ticket-icon" viewBox="0 0 24 24" aria-hidden="true">${ticketIconPaths[name] || ''}</svg>`;
const normalizeSearch = value => String(value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
async function loadAllTickets() {
  const container = document.getElementById('all-tickets-list');
  const request = ++ticketListState.request;
  container.innerHTML = '<div class="loading">Cargando tickets...</div>';
  try {
    const res = await fetch(`${API_BASE}/tickets`);
    if (!res.ok) throw new Error('No se pudieron cargar los tickets');
    const tickets = await res.json();
    if (request !== ticketListState.request) return;
    ticketListState.items = tickets.map(t => ({ ...t, totalMinutos: (t.bitacora || []).reduce((sum,b) => sum + (b.minutosInvertidos || 0),0) })).sort((a,b) => new Date(b.fechaCreacion)-new Date(a.fechaCreacion));
    const prioritySelect = document.getElementById('filter-tickets-priority');
    const selectedPriority = prioritySelect.value;
    const priorities = [...new Set(tickets.map(t => t.prioridad).filter(Boolean))].sort();
    prioritySelect.innerHTML = '<option value="">Todas las prioridades</option>' + priorities.map(p => `<option value="${escapeHtml(p)}">${escapeHtml(p)}</option>`).join('');
    prioritySelect.value = priorities.includes(selectedPriority) ? selectedPriority : '';
    renderTicketList();
  } catch (err) {
    if (request !== ticketListState.request) return;
    ticketListState.items = [];
    renderTicketList();
    container.innerHTML = '<p class="empty-state">No se pudieron cargar los tickets. Recarga la página para reintentar.</p>';
  }
}
function renderTicketList() {
  const status = document.getElementById('filter-tickets-status').value;
  const priority = document.getElementById('filter-tickets-priority').value;
  const words = normalizeSearch(document.getElementById('ticket-search').value).trim().split(/\s+/).filter(Boolean);
  const openStates = ['abierto','en_progreso','resuelto_pendiente'];
  const items = ticketListState.items.filter(t => {
    if (status && !(status === 'abierto' ? openStates.includes(t.estado) : t.estado === status)) return false;
    if (priority && t.prioridad !== priority) return false;
    const text = normalizeSearch([t.id,t.titulo,t.categoria,t.reportadoPor,t.atendidoPor,t.equipoAsignado,t.prioridad,t.codigoPatrimonial,t.mensajeError,...(t.bitacora || []).map(b => `${b.autor} ${b.texto}`)].join(' '));
    return words.every(word => text.includes(word));
  });
  const size = Number(document.getElementById('ticket-page-size').value);
  const pages = Math.max(1,Math.ceil(items.length/size));
  ticketListState.page = Math.max(1,Math.min(ticketListState.page,pages));
  const start = (ticketListState.page-1)*size;
  document.getElementById('all-tickets-list').innerHTML = items.slice(start,start+size).map(renderTicketWorkspaceCard).join('') || '<div class="empty-state"><strong>No hay coincidencias</strong><p>Prueba otro texto o cambia los filtros.</p></div>';
  document.getElementById('ticket-results').textContent = items.length ? `${start+1}–${Math.min(start+size,items.length)} de ${items.length} tickets` : '0 tickets';
  document.getElementById('ticket-page-label').textContent = `${ticketListState.page} / ${pages}`;
  document.getElementById('ticket-prev').disabled = ticketListState.page <= 1;
  document.getElementById('ticket-next').disabled = ticketListState.page >= pages;
  const all = ticketListState.items;
  document.getElementById('ticket-overview').innerHTML = [['Total',all.length,'file-text'],['En atención',all.filter(t=>openStates.includes(t.estado)).length,'clock'],['Derivados',all.filter(t=>t.estado==='derivado').length,'corner-up-right'],['Cerrados',all.filter(t=>t.estado==='cerrado').length,'circle-check'],['Críticos',all.filter(t=>/^(P1|cr[ií]tic)/i.test(String(t.prioridad || ''))).length,'triangle-alert']].map(([label,count,icon])=>`<div class="overview-card"><span class="overview-icon">${ticketIcon(icon)}</span><div><strong>${count}</strong><span>${label}</span></div></div>`).join('');
}
for (const id of ['ticket-search','filter-tickets-status','filter-tickets-priority','ticket-page-size']) {
  document.getElementById(id).addEventListener(id==='ticket-search'?'input':'change', () => { ticketListState.page=1; renderTicketList(); });
}
for (const [id,direction] of [['ticket-prev',-1],['ticket-next',1]]) {
  document.getElementById(id).addEventListener('click',()=>{ ticketListState.page+=direction; renderTicketList(); document.getElementById('ticket-search').scrollIntoView({block:'start'}); });
}

function formatTicketMinutes(value) {
  const minutes = Math.max(0, Math.round(Number(value) || 0));
  return minutes < 60 ? minutes + ' min' : Math.floor(minutes / 60) + ' h ' + (minutes % 60) + ' min';
}
function renderTicketWorkspaceCard(ticket) {
  const id = escapeHtml(ticket.id);
  const priority = escapeHtml(String(ticket.prioridad || 'Sin prioridad'));
  const statusClass = ticket.estado === 'cerrado' ? 'status-closed' : ticket.estado === 'derivado' ? 'status-derived' : 'status-open';
  const statusLabel = ({cerrado:'Cerrado',derivado:'Derivado',en_progreso:'En progreso',resuelto_pendiente:'Pendiente de cierre',abierto:'Abierto'})[ticket.estado] || ticket.estado;
  const last = (ticket.bitacora || []).at(-1);
  const ageMinutes = Math.max(0,Math.floor((Date.now()-new Date(ticket.fechaCreacion || Date.now()))/60000));
  const age = ageMinutes < 60 ? `${ageMinutes} min` : ageMinutes < 1440 ? `${Math.floor(ageMinutes/60)} h` : `${Math.floor(ageMinutes/1440)} d`;
  const description = String(ticket.descripcion || last?.texto || '').slice(0,150);
  const showFoco = ticket.estado !== 'cerrado';
  return `<article class="ticket-card ticket-general priority-${String(ticket.prioridad || '').toLowerCase()}">
    <div class="ticket-card-top"><span class="priority-badge ${(/^p[1-4]/i.exec(ticket.prioridad || '')?.[0] || '').toLowerCase()}">${priority}</span><span class="ticket-id">#${escapeHtml(ticket.id.slice(0,8))}</span><span class="ticket-status-badge ${statusClass}">${escapeHtml(statusLabel)}</span><span class="ticket-age">${ticketIcon('clock')}${age}</span><div class="ticket-menu"><button class="btn-ticket-menu" aria-label="Acciones del ticket ${id}" aria-expanded="false" onclick="toggleMenu(this)">${ticketIcon('ellipsis')}</button><div class="ticket-dropdown"><button class="ticket-dropdown-item" onclick="openDerivateModal('${id}')">Derivar situación</button><button class="ticket-dropdown-item" onclick="openCommentModal('${id}')">Dejar comentario</button></div></div></div>
    <h3 class="ticket-title">${escapeHtml(ticket.titulo || String(ticket.categoria || 'Ticket').replace(/_/g,' '))}</h3>
    <div class="ticket-meta"><span>${escapeHtml(String(ticket.categoria || '').replace(/_/g,' '))}</span><span>·</span><span>${escapeHtml(String(ticket.equipoAsignado || '').replace(/_/g,' '))}</span></div>
    <p class="ticket-description">${escapeHtml(description)}</p>
    <div class="ticket-card-bottom"><div class="ticket-requester"><span class="requester-icon">${ticketIcon('user-round')}</span><span><strong>${escapeHtml(ticket.reportadoPor || 'Sin solicitante')}</strong><small>Solicitante</small></span></div><div class="ticket-time">${ticket.estado === 'cerrado' && ticket.ttrMinutos !== null && ticket.ttrMinutos !== undefined && Number.isFinite(Number(ticket.ttrMinutos)) ? `Resuelto en ${formatTicketMinutes(ticket.ttrMinutos)}` : `${formatTicketMinutes((ticket.bitacora || []).reduce((total, entry) => total + (Number(entry.minutosInvertidos) || 0), 0))} invertidos`}</div></div>
    <div class="ticket-row-actions">${showFoco ? `<button class="btn-enfocar-asistente" data-ticket-id="${id}" data-ticket-cat="${escapeHtml(ticket.categoria || '')}" data-ticket-estado="${escapeHtml(ticket.estado)}" data-ticket-reportado="${escapeHtml(ticket.reportadoPor || '')}">${ticketIcon('sparkles')}<span>Consultar IA<small>Obtener ayuda y sugerencias</small></span></button>` : '<span></span>'}<button class="ticket-view-link" onclick="window.openTicketDetail('${id}')">Ver ticket ${ticketIcon('arrow-right')}</button></div>
  </article>`;
}

function renderTicketCard(ticket, viewType = 'handover') {
  const statusClass = ticket.estado === 'cerrado' ? 'status-closed' : ticket.estado === 'derivado' ? 'status-derived' : 'status-open';
  const label = ticket.estado === 'derivado' ? `Derivado a ${ticket.equipoAsignado.replace(/_/g, ' ')}` : ticket.estado.replace(/_/g, ' ');
  const last = ticket.bitacora[ticket.bitacora.length - 1];
  const showFoco = ticket.estado !== 'cerrado';
  const id = escapeHtml(ticket.id);
  return `<article class="ticket-card ticket-general">
    <div class="ticket-header"><div class="ticket-heading"><div class="ticket-id">${escapeHtml(ticket.id.substring(0,8))}<span class="ticket-priority">${escapeHtml(ticket.prioridad || "Sin prioridad")}</span></div><h3 class="ticket-title">${escapeHtml(ticket.titulo || ticket.categoria.replace(/_/g,' '))}</h3></div>
      <div class="ticket-header-status"><span class="ticket-status-badge ${statusClass}">${escapeHtml(label)}</span><div class="ticket-menu"><button class="btn-ticket-menu" aria-label="Acciones del ticket ${id}" aria-expanded="false" onclick="toggleMenu(this)">&#8942;</button><div class="ticket-dropdown"><button class="ticket-dropdown-item" onclick="openDerivateModal('${id}')">Derivar situación</button><button class="ticket-dropdown-item" onclick="openCommentModal('${id}')">Dejar comentario</button></div></div></div></div>
    <div class="ticket-meta"><span class="ticket-chip">${escapeHtml(ticket.categoria.replace(/_/g,' '))}</span><span class="ticket-chip">${escapeHtml(ticket.reportadoPor)}</span><span class="ticket-chip">${escapeHtml((ticket.equipoAsignado || "").replace(/_/g," "))}</span><span class="ticket-chip">${ticket.totalMinutos} min</span></div>
    ${last ? `<div class="ticket-summary"><strong>Última entrada:</strong> ${escapeHtml(last.texto.length > 100 ? last.texto.substring(0,100) + '\u2026' : last.texto)}</div>` : ''}
    <div class="ticket-row-actions"><button class="btn-secondary" onclick="window.openTicketDetail('${id}')">Ver detalle <span aria-hidden="true">&nearr;</span></button>${showFoco ? `<button class="btn-enfocar-asistente" data-ticket-id="${id}" data-ticket-cat="${escapeHtml(ticket.categoria)}" data-ticket-estado="${escapeHtml(ticket.estado)}" data-ticket-reportado="${escapeHtml(ticket.reportadoPor)}">Consultar Agente de soporte</button>` : ''}</div>
    ${ticket.estado === 'cerrado' ? `<div class="ticket-metrics">Tiempo de primera respuesta (TFR): ${ticket.tfrMinutos || 0} min • Tiempo de resolución (TTR): ${ticket.ttrMinutos || 0} min • Primer contacto: ${ticket.resueltoEnPrimerContacto ? 'Sí' : 'No'}</div>` : ''}
  </article>`;
}

// Nueva logica para la vista de Detalles de Ticket
window.openTicketDetail = async function(ticketId) {
  const container = document.getElementById('ticket-detail-content');
  document.getElementById('detail-ticket-id').innerText = ticketId;
  container.innerHTML = '<div class="loading">Cargando detalles...</div>';

  // Ocultar todas las vistas principales y mostrar el detalle
  document.querySelectorAll('.view').forEach(v => { v.style.display = 'none'; v.classList.remove('active-view'); });
  document.getElementById('view-ticket-detail').style.display = 'block';
  document.getElementById('view-ticket-detail').classList.add('active-view');

  try {
    const res = await fetch(`${API_BASE}/tickets`);
    const tickets = await res.json();
    const t = tickets.find(x => x.id === ticketId);

    if (!t) {
      container.innerHTML = '<p>No se encontro el ticket.</p>';
      return;
    }

    const statusClass = t.estado === 'cerrado' ? 'status-closed' : t.estado === 'derivado' ? 'status-derived' : 'status-open';
    const bitacoraHtml = t.bitacora.map(b => `<article class="bitacora-item"><div class="bitacora-head"><span class="bitacora-author">${escapeHtml(b.autor)}</span><span class="bitacora-time">${escapeHtml(String(b.minutosInvertidos ?? 0))} min · ${new Date(b.fecha).toLocaleString('es-PE')}</span></div><div class="bitacora-text">${escapeHtml(b.texto).replace(/\n/g, '<br>')}</div></article>`).join('');
    container.innerHTML = `<div class="detail-overview"><span class="ticket-status-badge ${statusClass}">${escapeHtml(t.estado.replace(/_/g,' '))}</span>${t.titulo ? `<h3 class="ticket-title">${escapeHtml(t.titulo.replace(/\\]\\s*\\+\\s*\\[/g, ' - ').replace(/\\[|\\]/g, ''))}</h3>` : ''}<div class="detail-meta"><div><strong>Categoría</strong>${escapeHtml(t.categoria)}</div><div><strong>Reportado por</strong>${escapeHtml(t.reportadoPor)}</div><div><strong>Equipo actual</strong>${escapeHtml(t.equipoAsignado)}</div></div></div><h3 class="timeline-title">Bitácora del caso <small>Historial completo de acciones</small></h3><div class="bitacora-list">${bitacoraHtml || '<p class="empty-state">Sin entradas registradas.</p>'}</div>`;

  } catch(e) {
    container.innerHTML = '<p>Error al cargar la linea de tiempo del ticket.</p>';
  }
};

document.getElementById('btn-back-to-list').addEventListener('click', () => {
  document.getElementById('view-ticket-detail').style.display = 'none';
  const activeNav = document.querySelector('.nav-links a.active');
  if (activeNav) {
    activeNav.click();
  } else {
    // Fallback if none active
    document.getElementById('nav-handover').click();
  }
});

// Listener delegado: boton "Consultar en Agente de soporte" en las tarjetas de ticket
document.body.addEventListener('click', (e) => {
  const btnFoco = e.target.closest('.btn-enfocar-asistente');
  if (btnFoco) {
    const t = btnFoco.dataset;
    const prevId = state.asistenteTicketFoco || 'general';
    state.asistenteConversaciones[prevId] = document.getElementById('asistente-history').innerHTML;

    state.asistenteTicketFoco = t.ticketId;
    // Resetear excluidos para este ticket al cambiar foco
    state.asistenteExcluidosMap.delete(t.ticketId);
    // Actualizar panel de contexto
    const infoEl = document.getElementById('asistente-ticket-info');
    const safe = (s) => s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
    infoEl.innerHTML = `<div class="context-ticket-row">
      <span class="ctx-id">ID: ${safe(t.ticketId.substring(0,8))}</span>
      <span class="ctx-detail">Categoria: ${safe(t.ticketCat || '')}</span>
      <span class="ctx-detail">Estado: ${safe(t.ticketEstado || '')}</span>
      <span class="ctx-detail">Reportado por: ${safe(t.ticketReportado || '')}</span>
    </div>`;
    document.getElementById('btn-clear-asistente-ticket').style.display = 'block';

    // Restaurar conversacion
    document.getElementById('asistente-history').innerHTML = state.asistenteConversaciones[t.ticketId] || '';
    if (!state.asistenteConversaciones[t.ticketId]) {
      // Auto-resumen la primera vez
      enviarMensajeAsistente('Resume este caso brevemente', true);
    }

    switchView(navAsistenteIA, viewAsistenteIA);
    const inputEl = document.getElementById('asistente-input');
    const btnEl = document.querySelector('#asistente-form button[type=submit]');
    if (inputEl) {
      inputEl.disabled = false;
      inputEl.placeholder = "Escribe tu consulta o pidele a el agente que derive/cierre el ticket...";
      inputEl.focus();
    }
    if (btnEl) btnEl.disabled = false;
    return;
  }
});

// Boton para quitar el ticket del foco
document.getElementById('btn-clear-asistente-ticket').addEventListener('click', () => {
  const prevId = state.asistenteTicketFoco || 'general';
  state.asistenteConversaciones[prevId] = document.getElementById('asistente-history').innerHTML;

  state.asistenteTicketFoco = null;
  document.getElementById('asistente-ticket-info').innerHTML = '<span class="no-ticket-msg">Ninguno seleccionado.<br>Ve a Gestión de Tickets y haz clic en un ticket para enfocar.</span>';
  document.getElementById('btn-clear-asistente-ticket').style.display = 'none';

  document.getElementById('asistente-history').innerHTML = state.asistenteConversaciones['general'] || '';

  const inputEl = document.getElementById('asistente-input');
  const btnEl = document.querySelector('#asistente-form button[type=submit]');
  if (inputEl) {
    inputEl.disabled = false;
    inputEl.placeholder = "Haz una consulta general (sin ticket asociado)...";
  }
  if (btnEl) btnEl.disabled = false;
});



// Knowledge View Logic
async function renderKnowledge() {
  const container = document.getElementById('knowledge-container');
  container.innerHTML = '<div class="loading">Cargando base de conocimiento...</div>';
  try {
    const res = await fetch(`${API_BASE}/kb`);
    const kb = await res.json();

    let html = '';

    // Group by category
    kb.categories.forEach(cat => {
      const intents = kb.intents.filter(i => i.category === cat);
      if (intents.length > 0) {
        html += `<h3 style="margin-top:20px; border-bottom: 2px solid var(--primary); padding-bottom: 5px; text-transform: uppercase;">${cat.replace('_', ' ')}</h3>`;
        html += '<div style="display: flex; flex-direction: column; gap: 10px; margin-top: 10px;">';
        intents.forEach(intent => {
          html += `
            <div class="ticket-card" style="border-left: 4px solid var(--primary);">
              <div style="font-weight: bold; margin-bottom: 5px;">Palabras clave: <span style="color: var(--text-muted); font-weight: normal;">${intent.trigger.join(', ')}</span></div>
              <div style="background: #f1f3f5; padding: 10px; border-radius: 4px;">${intent.response}</div>
            </div>
          `;
        });
        html += '</div>';
      }
    });

    // Flows
    if (kb.flows && kb.flows.length > 0) {
      html += `<h3 style="margin-top:20px; border-bottom: 2px solid var(--warning); padding-bottom: 5px; text-transform: uppercase;">Procesos Guiados (Flujos)</h3>`;
      html += '<div style="display: flex; flex-direction: column; gap: 10px; margin-top: 10px;">';
      kb.flows.forEach(flow => {
        html += `
          <div class="ticket-card" style="border-left: 4px solid var(--warning);">
            <div style="font-weight: bold;">Palabras clave: <span style="color: var(--text-muted); font-weight: normal;">${flow.trigger.join(', ')}</span></div>
            <div style="margin-top: 5px;">Cantidad de pasos: ${flow.steps.length}</div>
          </div>
        `;
      });
      html += '</div>';
    }

    container.innerHTML = html || '<p>No hay conocimientos registrados.</p>';
  } catch (err) {
    container.innerHTML = '<p>Error al cargar conocimiento.</p>';
  }
}

// Dashboard View Logic
async function renderDashboard() {
  const container = document.getElementById('dashboard-container');
  container.innerHTML = '<div class="loading">Cargando métricas...</div>';
  try {
    const res = await fetch(`${API_BASE}/tickets`);
    if (!res.ok) throw new Error('No se pudieron cargar los tickets');
    const tickets = await res.json();
    const closed = tickets.filter(t => t.estado === 'cerrado');
    const pending = tickets.filter(t => ['abierto', 'en_progreso'].includes(t.estado));
    const derived = tickets.filter(t => t.estado === 'derivado');
    const resolvedWithTime = closed.filter(t => Number.isFinite(Number(t.ttrMinutos)) && t.ttrMinutos !== null && Number(t.ttrMinutos) >= 0);
    const times = resolvedWithTime.map(t => Number(t.ttrMinutos));
    const average = times.length ? Math.round(times.reduce((sum,n) => sum+n,0)/times.length) : null;
    const minimum = times.length ? Math.min(...times) : null;
    const formatMinutes = n => n === null ? 'Sin datos' : n < 60 ? `${n} min` : `${(n/60).toFixed(1)} h`;
    const metric = (label,value,note) => `<article class="report-metric"><span>${label}</span><strong>${value}</strong><small>${note}</small></article>`;

    const now = new Date();
    const days = new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate();
    const daily = Array(days).fill(0);
    for (const ticket of tickets) {
      const date = new Date(ticket.fechaCreacion);
      if (date.getFullYear() === now.getFullYear() && date.getMonth() === now.getMonth()) daily[date.getDate() - 1]++;
    }
    const monthLabel = now.toLocaleDateString('es-PE', { month: 'long', year: 'numeric' });
    const monthTotal = daily.reduce((total, count) => total + count, 0);
    const dailyChart = () => {
      if (!monthTotal) return '<p class="chart-empty">No hay casos registrados este mes.</p>';
      const width = 600, height = 250, left = 42, top = 30, baseline = 200;
      const step = (width - left - 18) / days;
      const max = Math.max(...daily);
      const ticks = [...new Set([0, Math.ceil(max / 2), max])];
      const grid = ticks.map(count => {
        const y = baseline - count / max * (baseline - top);
        return `<line x1="${left}" y1="${y}" x2="582" y2="${y}" class="chart-grid-line"/><text x="${left - 8}" y="${y + 4}" text-anchor="end">${count}</text>`;
      }).join('');
      const bars = daily.map((count, i) => {
        if (!count) return '';
        const x = left + (i + 0.5) * step;
        const barHeight = count / max * (baseline - top);
        return `<rect class="chart-bar" x="${x - step * 0.32}" y="${baseline - barHeight}" width="${step * 0.64}" height="${barHeight}" rx="2"><title>Día ${i + 1}: ${count} casos</title></rect><text x="${x}" y="${baseline - barHeight - 7}" text-anchor="middle">${count}</text>`;
      }).join('');
      const labels = daily.map((_, i) => `<text x="${left + (i + 0.5) * step}" y="219" text-anchor="middle">${i + 1}</text>`).join('');
      const description = daily.map((count, i) => count ? `Día ${i + 1}: ${count} casos` : '').filter(Boolean).join('; ');
      return `<svg class="report-chart" viewBox="0 0 ${width} ${height}" role="img" aria-label="Casos por día de ${escapeHtml(monthLabel)}. Total: ${monthTotal}. ${escapeHtml(description)}"><text x="${left}" y="14">Casos</text>${grid}${bars}${labels}<text x="312" y="244" text-anchor="middle">Día del mes</text></svg>`;
    };
    const chart = (values,labels,{dotsOnly=false,unit=''}={}) => {
      if (!values.length) return '<p class="chart-empty">Sin datos para graficar.</p>';
      const width=600,height=220,left=42,right=18,top=18,bottom=38;
      const max=Math.max(1,...values),plotW=width-left-right,plotH=height-top-bottom;
      const points=values.map((v,i)=>({x:left+(values.length===1 ? plotW/2 : i*plotW/(values.length-1)),y:top+plotH-(v/max)*plotH,value:v,label:labels[i]}));
      const grid=[0,.5,1].map(frac=>{const y=top+plotH-frac*plotH;return `<line x1="${left}" y1="${y}" x2="${width-right}" y2="${y}" class="chart-grid-line"/><text x="${left-8}" y="${y+4}" text-anchor="end">${Math.round(max*frac)}</text>`}).join('');
      const line=dotsOnly ? '' : `<polyline class="chart-line" points="${points.map(p=>`${p.x},${p.y}`).join(' ')}"/>`;
      const circles=points.map(p=>`<circle class="chart-point" cx="${p.x}" cy="${p.y}" r="6"><title>${escapeHtml(p.label)}: ${p.value}${unit}</title></circle>`).join('');
      const xLabels=points.filter((_,i)=>values.length<=10 || i%5===0 || i===values.length-1).map(p=>`<text x="${p.x}" y="${height-12}" text-anchor="middle">${escapeHtml(p.label)}</text>`).join('');
      return `<svg class="report-chart" viewBox="0 0 ${width} ${height}" role="img" aria-label="${dotsOnly ? 'Gráfico de puntos' : 'Gráfico de líneas'}: ${escapeHtml(labels.map((l,i)=>`${l}, ${values[i]}${unit}`).join('; '))}">${grid}${line}${circles}${xLabels}</svg>`;
    };
    const recentClosed=resolvedWithTime.slice().sort((a,b)=>new Date(a.fechaCreacion)-new Date(b.fechaCreacion)).slice(-8);
    const hours=recentClosed.map(t=>Number((Number(t.ttrMinutos)/60).toFixed(1)));
    const caseLabels=recentClosed.map((t,i)=>`Caso ${i+1}`);
    const rate=tickets.length ? Math.round(closed.length/tickets.length*100) : 0;
    container.innerHTML=`<div class="report-intro"><div><h3>Estado general</h3><p>Datos acumulados de todos los tickets registrados.</p></div><span>${tickets.length ? `Cierre: ${rate}%` : 'Sin tickets'}</span></div>
      <div class="report-metrics">${metric('Casos registrados',tickets.length,'Total histórico')}${metric('Casos resueltos',closed.length,'Tickets cerrados')}${metric('Casos pendientes',pending.length,'Abiertos o en progreso')}${metric('Casos derivados',derived.length,'Tickets derivados')}${metric('Tiempo promedio',formatMinutes(average),`${times.length} casos cerrados con tiempo registrado`)}${metric('Resolución más rápida',formatMinutes(minimum),'Menor tiempo de resolución registrado')}</div>
      <div class="report-charts"><section class="report-panel"><header><h3>Casos registrados por día</h3><p>${escapeHtml(monthLabel)}: ${monthTotal} casos de ${tickets.length} registrados en total.</p><p>Cada barra indica cuántos casos se registraron ese día. Sin barra = 0 casos.</p></header>${dailyChart()}</section><section class="report-panel"><header><h3>Tiempo de resolución por caso</h3><p>Últimos ${recentClosed.length} casos cerrados con tiempo registrado, en horas.</p></header>${chart(hours,caseLabels,{dotsOnly:true,unit:' h'})}</section></div>
      <p class="report-footnote">El tiempo promedio y el mínimo se calculan solo con tickets cerrados que tienen un tiempo de resolución válido. “Pendientes” incluye abiertos y en progreso. Los derivados se muestran por separado.</p>`;
  } catch (err) {
    container.innerHTML = '<p class="empty-state">No se pudo cargar el reporte. Vuelve a abrir el Dashboard para reintentar.</p>';
  }
}

// ==========================================
// LOGICA DEL ASISTENTE IA (Panel de TI)
// ==========================================

const asistenteForm = document.getElementById('asistente-form');
const asistenteInput = document.getElementById('asistente-input');
const asistenteHistory = document.getElementById('asistente-history');

// Soporte para pegar imagenes directamente
if (asistenteInput) {
  asistenteInput.addEventListener('paste', e => {
    const items = (e.clipboardData || e.originalEvent.clipboardData).items;
    for (let index in items) {
      const item = items[index];
      if (item.kind === 'file' && item.type.startsWith('image/')) {
        const blob = item.getAsFile();
        const dataTransfer = new DataTransfer();
        dataTransfer.items.add(new File([blob], "captura_pegada.png", { type: item.type }));
        const fileInput = document.getElementById('asistente-image-input');
        if (fileInput) {
          fileInput.files = dataTransfer.files;
          fileInput.dispatchEvent(new Event('change'));
          DeskUI.notify('Imagen pegada lista para enviar al asistente.');
          e.preventDefault();
          break;
        }
      }
    }
  });
}

// Hydrate initial chat state (default is 'general')
if (asistenteHistory && state.asistenteConversaciones['general']) {
  asistenteHistory.innerHTML = state.asistenteConversaciones['general'];
}

function addAsistenteMessage(text, type, isHtml = false) {
  document.querySelector('.assistant-welcome')?.remove();
  const div = document.createElement('div');
  div.className = `asistente-msg ${type}`;

  const contentDiv = document.createElement('div');
  contentDiv.className = 'asistente-msg-content';

  if (isHtml) {
    contentDiv.innerHTML = text;
  } else {
    // Escape simple para texto plano
    contentDiv.innerHTML = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\n/g, '<br>');
  }

  div.appendChild(contentDiv);
  asistenteHistory.appendChild(div);
  if (type === 'system') {
    asistenteHistory.scrollTop += div.getBoundingClientRect().top - asistenteHistory.getBoundingClientRect().top - 20;
  } else { asistenteHistory.scrollTop = asistenteHistory.scrollHeight; }
  return div;
}

async function enviarMensajeAsistente(mensaje, esAuto = false, archivos = []) {
  if (!mensaje && archivos.length === 0) return;

  // 1. Mostrar mensaje del usuario o sistema (si es auto-resumen)
  if (!esAuto) {
    let htmlMsg = (mensaje || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\n/g, '<br>');
    if (archivos && archivos.length > 0) {
      let filesHtml = '<div style="display:flex; gap:5px; flex-wrap:wrap; margin-bottom:5px;">';
      let hasVideo = false;
      archivos.forEach(f => {
        if (f.startsWith('data:video')) {
          hasVideo = true;
          filesHtml += `<div class="attachment-tile"> Video Adjunto</div>`;
        } else if (f.startsWith('data:application/pdf')) {
          filesHtml += `<div class="attachment-tile"> PDF Adjunto</div>`;
        } else {
          filesHtml += `<img src="${f}" onclick="const w = window.open(); w.document.write('<img src=\\'${f}\\' style=\\'max-width:100%;\\'>'); w.document.close();" style="max-width: 100px; max-height: 100px; border-radius: 4px; cursor: zoom-in;" title="Clic para ampliar">`;
        }
      });
      filesHtml += '</div>';
      if (hasVideo) {
        filesHtml += `<div style="font-size:0.8rem; color:var(--warning); margin-bottom:5px;">Procesando video... esto puede tomar un poco más de tiempo.</div>`;
      }
      htmlMsg = filesHtml + htmlMsg;
    }
    addAsistenteMessage(htmlMsg, 'user', true);
  } else {
    // Si es auto, no mostramos el mensaje "Resume este caso..." escrito,
    // solo el indicador de carga y la respuesta.
  }

  // 2. Mostrar indicador de carga
  const loadingDiv = addAsistenteMessage(esAuto ? 'Generando resumen automatico...' : 'Analizando contexto y buscando informacion...', 'loading');

  // 3. Preparar payload
  const ticketId = state.asistenteTicketFoco;
  const excluirIds = ticketId ? (state.asistenteExcluidosMap.get(ticketId) || []) : [];

  let historialConversacion = "";
  document.querySelectorAll('#asistente-history .asistente-msg-content').forEach(node => {
    historialConversacion += node.innerText.trim() + "\\n\\n";
  });

  try {
    const res = await fetch(`${API_BASE}/asistente-tecnico/mensaje`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ mensaje, ticketId, excluirIds, historialConversacion, archivosBase64: archivos })
    });

    const data = await res.json();
    loadingDiv.remove();

    if (data.error) {
      addAsistenteMessage(`Error: ${data.error}`, 'system');
      return;
    }

    // 4. Actualizar excluidos si se hizo una busqueda
    if (ticketId && data.usadosIds && data.usadosIds.length > 0) {
      const prev = state.asistenteExcluidosMap.get(ticketId) || [];
      state.asistenteExcluidosMap.set(ticketId, [...new Set([...prev, ...data.usadosIds])]);
    }

    // 5. Renderizar respuesta
    let respHtml = data.respuesta || '';
    if (data.accionHtml) {
      respHtml += `<br>${data.accionHtml}`;
    }
    if (data.agotado) {
      respHtml += `<br><em style="color:var(--text-muted); font-size:0.85rem;">(No se encontraron mas casos similares distintos a los ya mostrados)</em>`;
    }

    // 6. Mostrar fuente de conocimiento institucional si se uso
    if (data.kiFuentes && data.kiFuentes.length > 0) {
      const fuentesHtml = data.kiFuentes.map(f =>
        `<div class="ki-source-item"><a href="#" onclick="openKIDetail('${f.id}'); return false;" style="text-decoration: underline; font-weight: bold; color: inherit;">${escapeHtml(f.titulo)}</a> &mdash; ${escapeHtml(f.categoriaITIL)}${f.servicio ? ' | ' + escapeHtml(f.servicio) : ''}</div>`
      ).join('');
      respHtml += `<div class="ki-source-box"><strong>Fuente de conocimiento institucional consultada:</strong><br>${fuentesHtml}</div>`;
    }

    // 7. Bloquear chat si el ticket se cerro o derivo
    if (data.ticketEstado === 'cerrado' || data.ticketEstado === 'derivado') {
      const inputEl = document.getElementById('asistente-input');
      const btnEl = document.querySelector('#asistente-form button[type=submit]');
      if (inputEl) {
        inputEl.disabled = true;
        inputEl.placeholder = `El ticket actual esta ${data.ticketEstado}. Chat desactivado.`;
      }
      if (btnEl) btnEl.disabled = true;
    }

    addAsistenteMessage(respHtml, 'system', true);

  } catch (err) {
    loadingDiv.remove();
    addAsistenteMessage('Error de conexion con el asistente.', 'system');
    console.error(err);
  }
}

// Manual Ticket Creation Logic
const modalNewTicket = document.getElementById('modal-new-ticket');
document.getElementById('btn-new-ticket').addEventListener('click', () => {
  document.getElementById('form-new-ticket').reset();
  const loadingDiv = document.getElementById('autofill-loading');
  if (loadingDiv) loadingDiv.style.display = 'none';
  if (btnAutofillAI) btnAutofillAI.disabled = false;
  document.querySelectorAll('#form-new-ticket .error-highlight').forEach(el => el.classList.remove('error-highlight'));
  modalNewTicket.style.display = 'flex';
});

document.getElementById('form-new-ticket').addEventListener('submit', async (e) => {
  e.preventDefault();

  const titulo = document.getElementById('new-ticket-titulo').value.trim();
  const titleRegex = /^\[.*?\]\s*\+\s*\[.*?\]\s*\+\s*.+$/;
  const errorLabel = document.getElementById('titulo-error');
  if (!titleRegex.test(titulo) || titulo.toLowerCase() === 'error' || titulo.toLowerCase() === 'ayuda') {
    errorLabel.style.display = 'block';
    return;
  } else {
    errorLabel.style.display = 'none';
  }

  const reporter = document.getElementById('new-ticket-reporter').value.trim();
  const desc = document.getElementById('new-ticket-desc').value.trim();
  const alcance = document.getElementById('new-ticket-alcance').value.trim();
  const tipo = document.getElementById('new-ticket-tipo').value;
  const prioridad = document.getElementById('new-ticket-prioridad').value;
  const codigo = document.getElementById('new-ticket-codigo').value.trim();
  const mensajeError = document.getElementById('new-ticket-error').value.trim();

  if (!reporter || !desc || !titulo || !tipo || !prioridad || !alcance) return;

  try {
    const res = await fetch(`${API_BASE}/tickets`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        message: desc,
        usuario: reporter,
        titulo: titulo,
        tipo: tipo,
        prioridad: prioridad,
        codigoPatrimonial: codigo,
        mensajeError: mensajeError,
        alcance: alcance
      })
    });

    if (res.ok) {
      const data = await res.json();
      modalNewTicket.style.display = 'none';
      e.target.reset(); // clear all fields

      const categoryLabel = data.ticket.categoria.replace('_', ' ');
      const teamLabel = data.ticket.equipoAsignado.replace(/_/g, ' ');
      DeskUI.notify(`Ticket creado con exito.\nID: ${data.ticket.id.substring(0,8)}\nCategoría: ${categoryLabel}\nAsignado a: ${teamLabel}`);

      loadAllTickets();
    } else {
      DeskUI.notify('Error al crear el ticket.');
    }
  } catch(err) {
    DeskUI.notify('Error de conexion.');
  }
});

const asistenteImageInput = document.getElementById('asistente-image-input');
const asistenteImagePreview = document.getElementById('asistente-image-preview');
let currentFilesBase64 = [];

if (asistenteImageInput) {
  asistenteImageInput.addEventListener('change', async (e) => {
    const files = Array.from(e.target.files);
    if (files.length > 0) {
      for (const file of files) {
        const base64 = await fileToBase64(file);
        currentFilesBase64.push(base64);
      }
      renderAsistentePreview();
    }
  });
}

function fileToBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = (evt) => resolve(evt.target.result);
    reader.onerror = (e) => reject(e);
    reader.readAsDataURL(file);
  });
}

function renderAsistentePreview() {
  if (currentFilesBase64.length === 0) {
    asistenteImagePreview.style.display = 'none';
    asistenteImagePreview.innerHTML = '';
    return;
  }
  let html = '<div style="display:flex; gap:10px; flex-wrap:wrap;">';
  currentFilesBase64.forEach((f, idx) => {
    if (f.startsWith('data:video')) {
      html += `<div class="attachment-tile"><br>Video</div>`;
    } else if (f.startsWith('data:application/pdf')) {
      html += `<div class="attachment-tile"><br>PDF</div>`;
    } else {
      html += `<img src="${f}" onclick="const w = window.open(); w.document.write('<img src=\\'${f}\\' style=\\'max-width:100%;\\'>'); w.document.close();" style="max-height: 50px; border-radius: 4px; cursor: zoom-in;" title="Clic para ampliar">`;
    }
  });
  html += `</div><button type="button" class="btn-cancel" style="padding: 2px 8px; font-size:0.8rem; margin-top:5px;" onclick="clearAsistenteImage()">Limpiar adjuntos</button>`;
  asistenteImagePreview.innerHTML = html;
  asistenteImagePreview.style.display = 'block';
}

window.clearAsistenteImage = function() {
  currentFilesBase64 = [];
  if (asistenteImageInput) asistenteImageInput.value = '';
  renderAsistentePreview();
};

asistenteForm.addEventListener('submit', async (e) => {
  e.preventDefault();
  const mensaje = asistenteInput.value.trim();
  if (!mensaje && currentFilesBase64.length === 0) return;

  asistenteInput.value = '';
  const archivosToSend = [...currentFilesBase64];
  clearAsistenteImage();

  await enviarMensajeAsistente(mensaje, false, archivosToSend);
});

// Autocompletar con IA logic
const btnAutofillAI = document.getElementById('btn-autofill-ai');
if (btnAutofillAI) {
  btnAutofillAI.addEventListener('click', async () => {
    const fileInput = document.getElementById('new-ticket-evidence');
    const loadingDiv = document.getElementById('autofill-loading');

    if (!fileInput.files || fileInput.files.length === 0) {
      DeskUI.notify("Por favor suba al menos un archivo (imagen, pdf, audio o video) como evidencia.");
      return;
    }

    loadingDiv.style.display = 'block';
    btnAutofillAI.disabled = true;

    try {
      const base64Str = await fileToBase64(fileInput.files[0]);
      const res = await fetch(`${API_BASE}/extraer-contexto`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ media: base64Str })
      });

      const data = await res.json();
      loadingDiv.style.display = 'none';
      btnAutofillAI.disabled = false;

      if (res.ok) {
        let hasMissing = false;
        ['reporter', 'desc', 'titulo', 'tipo', 'prioridad', 'codigoPatrimonial', 'mensajeError', 'alcance'].forEach(field => {
          const inputId = 'new-ticket-' + (field === 'codigoPatrimonial' ? 'codigo' : field === 'mensajeError' ? 'error' : field);
          const input = document.getElementById(inputId);
          const val = data[field];
          if (input) {
            input.classList.remove('error-highlight');
            if (val && val !== 'N/D' && String(val).trim() !== '') {
              input.value = val;
            } else {
              hasMissing = true;
              input.classList.add('error-highlight');
              if (val === 'N/D') input.value = '';
            }
          }
        });
        if (hasMissing) DeskUI.notify('Falta información para autocompletar algunos campos (marcados en rojo).');
        else DeskUI.notify('Campos autocompletados correctamente.');
      } else {
        DeskUI.notify(data.error || "No se pudo extraer el contexto.");
      }
    } catch(err) {
      loadingDiv.style.display = 'none';
      btnAutofillAI.disabled = false;
      DeskUI.notify("Error de conexión al autocompletar.");
    }
  });
}

window.triggerAsistenteCommand = async (msg) => {
  await enviarMensajeAsistente(msg);
};

window.anexarChatYcerrar = async (solucion) => {
  const activeId = state.asistenteTicketFoco;
  if (!activeId) return;

  // Extraer texto completo del historial de chat actual
  let chatText = "";
  document.querySelectorAll('#asistente-history .asistente-msg-content').forEach(node => {
    chatText += node.innerText.trim() + "\n\n";
  });

  // Enviar el chat completo como comentario/log del sistema
  try {
    await fetch(`${API_BASE}/tickets/${activeId}/log`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        autor: 'sistema',
        texto: 'Historial completo de conversacion con Agente de soporte:\n' + chatText,
        minutosInvertidos: 0
      })
    });
    // Ahora pedir a el agente que cierre el ticket (ya esta anexado en BD)
    await enviarMensajeAsistente(`Anexar toda la conversacion: ${solucion}`);
  } catch(e) {
    DeskUI.notify('Error al anexar la conversacion.');
  }
};

// ═══════════════════════════════════════════════════════════════════
// GESTION DEL CONOCIMIENTO INSTITUCIONAL
// ═══════════════════════════════════════════════════════════════════

const ITIL_CATEGORIES = [
  'Gestion de Incidentes', 'Gestion de Problemas', 'Gestion de Solicitudes de Servicio',
  'Gestion de Cambios', 'Gestion de Activos y Configuracion', 'Gestion del Conocimiento',
  'Gestion de Niveles de Servicio', 'Gestion de Disponibilidad', 'Gestion de Continuidad',
  'Procedimientos de Soporte', 'General'
];

function escapeHtml(str) {
  return String(str || '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

function getBadgeEstado(estado) {
  const map = {
    publicado: 'ki-badge-estado-publicado',
    pendiente: 'ki-badge-estado-pendiente',
    pendiente_revision: 'ki-badge-estado-pendiente_revision',
    procesando: 'ki-badge-estado-procesando',
    error: 'ki-badge-estado-error',
    inactivo: 'ki-badge-estado-pendiente'
  };
  return `<span class="${map[estado] || 'ki-badge-estado-pendiente'}">${escapeHtml(estado)}</span>`;
}

// Populate ITIL categories in select elements
function populateITILSelects() {
  const selects = ['ki-filter-categoria', 'ki-categoria'];
  selects.forEach(id => {
    const sel = document.getElementById(id);
    if (!sel) return;
    // Keep first option, add categories
    while (sel.options.length > 1) sel.remove(1);
    ITIL_CATEGORIES.forEach(cat => {
      const opt = document.createElement('option');
      opt.value = cat;
      opt.textContent = cat;
      sel.appendChild(opt);
    });
  });
}
populateITILSelects();

// ─────────────────────────────────────────────────────────────
// RENDER MAIN VIEW
// ─────────────────────────────────────────────────────────────
async function renderKnowledgeMgmt() {
  await Promise.all([loadKIStats(), loadKIList()]);
}

async function loadKIStats() {
  const grid = document.getElementById('ki-stats-grid');
  try {
    const res = await fetch(`${API_BASE}/knowledge/stats`);
    const stats = await res.json();
    const tipoLabels = {
      documento: 'Docs', imagen: 'Imagenes', texto: 'Textos',
      procedimiento: 'Procedimientos', solucion: 'Soluciones', video: 'Videos', enlace: 'Enlaces'
    };
    let html = `<div class="ki-stat-card"><div class="ki-stat-num">${stats.total}</div><div class="ki-stat-label">Total</div></div>`;
    Object.entries(stats.byTipo || {}).forEach(([tipo, count]) => {
      if (count > 0 || ['procedimiento','solucion','documento','video'].includes(tipo)) {
        html += `<div class="ki-stat-card"><div class="ki-stat-num">${count}</div><div class="ki-stat-label">${tipoLabels[tipo] || tipo}</div></div>`;
      }
    });
    grid.innerHTML = html;
  } catch (e) {
    grid.innerHTML = '<div class="ki-stat-card"><div class="ki-stat-num">-</div><div class="ki-stat-label">Error</div></div>';
  }
}

let kiListRequest = 0;
async function loadKIList(filters = { q: document.getElementById('ki-search-input').value.trim(), tipo: document.getElementById('ki-filter-tipo').value, categoriaITIL: document.getElementById('ki-filter-categoria').value }) {
  const request = ++kiListRequest;
  const list = document.getElementById('ki-list');
  list.innerHTML = '<div class="loading">Cargando...</div>';
  try {
    const params = new URLSearchParams();
    if (filters.q) params.set('q', filters.q);
    if (filters.tipo) params.set('tipo', filters.tipo);
    if (filters.categoriaITIL) params.set('categoriaITIL', filters.categoriaITIL);
    const res = await fetch(`${API_BASE}/knowledge?${params}`);
    const items = await res.json();
    if (request !== kiListRequest) return;

    if (!items.length) {
      list.innerHTML = `<div class="ki-empty"><strong>No hay conocimiento registrado</strong><p>Haz clic en "+ Agregar Conocimiento" para incorporar el primer elemento.</p></div>`;
      return;
    }

    list.innerHTML = items.map(item => `
      <div class="ki-item-card" data-ki-id="${item.id}">
        <div class="ki-item-header">
          <div class="ki-item-title">${escapeHtml(item.titulo)}</div>
          <div class="ki-item-actions">
            <button class="btn-view" onclick="openKIDetail('${item.id}')">Ver</button>
            <button class="btn-edit" onclick="openKIEdit('${item.id}')">Editar</button>
            <button class="btn-del" onclick="deleteKI('${item.id}', '${escapeHtml(item.titulo)}')">Eliminar</button>
          </div>
        </div>
        <div class="ki-item-meta">
          <span class="ki-badge-tipo">${escapeHtml(item.tipo)}</span>
          <span class="ki-badge-cat">${escapeHtml(item.categoriaITIL)}</span>
          ${getBadgeEstado(item.estado)}
          ${item.servicio ? `<span style="color:var(--text-muted)">| ${escapeHtml(item.servicio)}</span>` : ''}
          <span style="float:right; color:var(--text-muted)">${new Date(item.fechaCreacion).toLocaleDateString('es-PE')}</span>
        </div>
        ${item.descripcion ? `<div style="font-size:0.82rem; color:var(--text-muted); margin-bottom:6px;">${escapeHtml(item.descripcion)}</div>` : ''}
        ${item.etiquetas && item.etiquetas.length ? `<div class="ki-item-tags">${item.etiquetas.map(t => `<span class="ki-tag">${escapeHtml(t)}</span>`).join('')}</div>` : ''}
        ${item.metadatos && item.metadatos.usoContador ? `<div style="font-size:0.72rem; color:var(--text-muted); margin-top:6px;">Usado ${item.metadatos.usoContador} vez/veces por el Agente de soporte</div>` : ''}
      </div>
    `).join('');
  } catch (e) {
    list.innerHTML = '<p style="color:var(--danger);">Error al cargar conocimiento.</p>';
  }
}

for (const id of ['ki-filter-tipo', 'ki-filter-categoria']) document.getElementById(id).addEventListener('change', () => loadKIList());
let kiSearchTimer;
document.getElementById('ki-search-input').addEventListener('input', () => { clearTimeout(kiSearchTimer); kiSearchTimer = setTimeout(() => loadKIList(), 250); });
// Search / filter
document.getElementById('btn-ki-search').addEventListener('click', () => {
  const q = document.getElementById('ki-search-input').value.trim();
  const tipo = document.getElementById('ki-filter-tipo').value;
  const cat = document.getElementById('ki-filter-categoria').value;
  loadKIList({ q, tipo, categoriaITIL: cat });
});
document.getElementById('ki-search-input').addEventListener('keydown', e => {
  if (e.key === 'Enter') document.getElementById('btn-ki-search').click();
});
document.getElementById('btn-ki-refresh').addEventListener('click', renderKnowledgeMgmt);

// ─────────────────────────────────────────────────────────────
// MODAL: SELECTOR DE TIPO
// ─────────────────────────────────────────────────────────────
document.getElementById('btn-add-knowledge').addEventListener('click', () => {
  document.getElementById('modal-ki-type').style.display = 'flex';
});

document.querySelectorAll('.ki-type-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    const tipo = btn.dataset.tipo;
    document.getElementById('modal-ki-type').style.display = 'none';
    openKIForm(tipo);
  });
});

// ─────────────────────────────────────────────────────────────
// MODAL: FORMULARIO AGREGAR / EDITAR
// ─────────────────────────────────────────────────────────────
let kiDocumentText = new Map();
let kiAnalysisController = null;
function cancelKIAnalysis() {
  kiAnalysisController?.abort();
  kiAnalysisController = null;
  kiDocumentText = new Map();
  document.getElementById('btn-ki-analyze').disabled = false;
  setKIProcessing(null);
}
function beginKIAnalysis() {
  kiAnalysisController?.abort();
  kiAnalysisController = new AbortController();
  return kiAnalysisController.signal;
}
const kiFormModal = document.getElementById('modal-ki-form');
new MutationObserver(() => {
  if (kiFormModal.style.display === 'none') cancelKIAnalysis();
}).observe(kiFormModal, { attributes: true, attributeFilter: ['style'] });
function openKIForm(tipo, existingItem = null) {
  cancelKIAnalysis();
  kiDocumentText = new Map();
  // Reset form
  document.getElementById('form-ki').reset();
  document.getElementById('ki-form-id').value = existingItem ? existingItem.id : '';
  document.getElementById('ki-form-tipo').value = tipo;
  document.getElementById('ki-form-title').textContent = existingItem ? 'Editar Conocimiento' : `Agregar Conocimiento - ${tipo.charAt(0).toUpperCase() + tipo.slice(1)}`;
  document.getElementById('ki-processing-bar').style.display = 'none';
  document.getElementById('ki-submit-btn').textContent = existingItem ? 'Guardar Cambios' : 'Guardar Conocimiento';

  // Show/hide field groups based on type
  document.getElementById('ki-file-group').style.display  = tipo === 'documento' ? 'block' : 'none';
  document.getElementById('ki-image-group').style.display = tipo === 'imagen' ? 'block' : 'none';
  document.getElementById('ki-url-group').style.display   = tipo === 'video' ? 'block' : 'none';

  // Reset previews
  document.getElementById('ki-file-preview').style.display = 'none';
  document.getElementById('ki-image-preview').style.display = 'none';
  document.getElementById('ki-url-preview').style.display = 'none';

  // Pre-fill for edit
  if (existingItem) {
    document.getElementById('ki-titulo').value       = existingItem.titulo || '';
    document.getElementById('ki-descripcion').value  = existingItem.descripcion || '';
    document.getElementById('ki-contenido').value    = existingItem.contenido || '';
    document.getElementById('ki-categoria').value    = existingItem.categoriaITIL || 'General';
    document.getElementById('ki-servicio').value     = existingItem.servicio || '';
    document.getElementById('ki-etiquetas').value    = (existingItem.etiquetas || []).join(', ');
    document.getElementById('ki-fuente').value       = existingItem.fuente || '';
    document.getElementById('ki-estado').value       = existingItem.estado || 'publicado';
    if (existingItem.url) document.getElementById('ki-url-input').value = existingItem.url;
  }

  document.getElementById('modal-ki-form').style.display = 'flex';
}

function setKIProcessing(msg) {
  const bar = document.getElementById('ki-processing-bar');
  bar.style.display = msg ? 'block' : 'none';
  if (msg) document.getElementById('ki-processing-text').textContent = msg;
}

async function requestKIVideo(url, init) {
  const button = document.getElementById('btn-ki-analyze');
  button.disabled = true;
  try {
    const response = await fetch(url, { ...init, headers: { ...init.headers, Accept: 'application/x-ndjson' }, signal: AbortSignal.any([init.signal, AbortSignal.timeout(540000)]) });
    if (!response.headers.get('content-type')?.includes('application/x-ndjson')) {
      const data = await response.json();
      if (!response.ok || data.error) throw new Error(data.error || 'No se pudo iniciar el análisis.');
      return data;
    }
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let pending = '';
    try {
      while (true) {
        const { done, value } = await reader.read();
        init.signal.throwIfAborted();
        pending += decoder.decode(value, { stream: !done });
        const lines = pending.split('\n');
        pending = lines.pop();
        for (const line of lines) {
          if (!line.trim()) continue;
          const event = JSON.parse(line);
          if (event.type === 'progress') setKIProcessing(event.message + ' (' + event.segundos + ' s)');
          if (event.type === 'error') throw new Error(event.error);
          if (event.type === 'result') return event;
        }
        if (done) throw new Error('La conexión terminó antes de recibir el análisis. Reintenta la carga.');
      }
    } finally { await reader.cancel().catch(() => {}); }
  } finally { if (!init.signal.aborted) button.disabled = false; }
}

// Analizar texto con tu agente
document.getElementById('btn-ki-analyze').addEventListener('click', async () => {
  const contenido = document.getElementById('ki-contenido').value.trim();
  const urlVideo = document.getElementById('ki-url-input').value.trim();
  const hasImg = document.getElementById('ki-image-input').files.length > 0;
  const hasDoc = document.getElementById('ki-file-input').files.length > 0;
  const hasVid = document.getElementById('ki-video-input').files.length > 0;

  if (hasImg) return document.getElementById('btn-analyze-image').click();
  if (hasDoc && [...document.getElementById('ki-file-input').files].some(file => !kiDocumentText.has(file))) {
    DeskUI.notify('Primero extrae el texto de todos los documentos.'); return;
  }

  if (hasVid) {
    const file = document.getElementById('ki-video-input').files[0];
    if (file.size > 256 * 1024 * 1024) { DeskUI.notify('El límite de análisis es 256 MB.'); return; }
      const signal = beginKIAnalysis();
      setKIProcessing('Subiendo y analizando video con tu agente (puede demorar)...');
      try {
        const data = await requestKIVideo(`${API_BASE}/knowledge/analyze-video`, {
          method: 'POST',
          headers: { 'Content-Type': file.type || ({ mp4: 'video/mp4', webm: 'video/webm', ogg: 'video/ogg' })[file.name.split('.').pop().toLowerCase()] || 'application/octet-stream' },
          body: file, signal
        });
        if (signal.aborted) return;
        if (data.error) { setKIProcessing(null); DeskUI.notify('Error: ' + data.error); return; }
        const s = data.suggestion;
        if (s.titulo) document.getElementById('ki-titulo').value = s.titulo;
        if (s.descripcion) document.getElementById('ki-descripcion').value = s.descripcion;
        if (s.contenido) document.getElementById('ki-contenido').value = s.contenido;
        if (s.categoriaITIL) document.getElementById('ki-categoria').value = s.categoriaITIL;
        if (s.servicio) document.getElementById('ki-servicio').value = s.servicio;
        if (s.etiquetas) document.getElementById('ki-etiquetas').value = (s.etiquetas||[]).join(', ');
        setKIProcessing('Análisis completado. Revisa el contenido antes de guardar.');
        DeskUI.notify('Campos autocompletados basados en el video adjunto.');
      } catch (err) {
        if (signal.aborted) return;
        const message = 'No se pudo analizar el video: ' + err.message;
        setKIProcessing(message);
        DeskUI.notify(message);
      }

    return;
  }

  let textToAnalyze = hasDoc ? [...kiDocumentText.entries()].map(([file, text]) => '--- ' + file.name + ' ---\n' + text).join('\n\n') + '\n' + contenido : contenido;
  if (urlVideo && !hasDoc) {
    textToAnalyze = "Analiza este recurso de video: " + urlVideo + "\n" + contenido;
  } else if (textToAnalyze.length < 20) {
    DeskUI.notify('Por favor ingresa al menos 20 caracteres de contenido, o adjunta un enlace para analizar.');
    return;
  }

  const signal = beginKIAnalysis();
  setKIProcessing('Analizando contenido con tu agente...');
  try {
    const init = {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      signal,
      body: JSON.stringify({ contenido: textToAnalyze, videoUrl: urlVideo || undefined })
    };
    const data = urlVideo ? await requestKIVideo(`${API_BASE}/knowledge/analyze`, init) : await (await fetch(`${API_BASE}/knowledge/analyze`, init)).json();
    if (signal.aborted) return;
    if (data.error) throw new Error(data.error);
    const s = data.suggestion;
    if (s.titulo) document.getElementById('ki-titulo').value = s.titulo;
    if (s.descripcion) document.getElementById('ki-descripcion').value = s.descripcion;
    if (s.contenido) document.getElementById('ki-contenido').value = s.contenido;
    if (s.categoriaITIL) document.getElementById('ki-categoria').value = s.categoriaITIL;
    if (s.servicio) document.getElementById('ki-servicio').value = s.servicio;
    if (s.etiquetas && s.etiquetas.length) document.getElementById('ki-etiquetas').value = s.etiquetas.join(', ');
    setKIProcessing(null);
    DeskUI.notify('Tu agente analizó el contenido y sugiri­o la metadata. Revisa y ajusta antes de guardar.');
  } catch (e) {
    if (signal.aborted) return;
    setKIProcessing('No se pudo completar el análisis: ' + e.message);
    DeskUI.notify('Error al contactar al agente: ' + e.message);
  }
});

// Extraer texto de archivo PDF/DOCX/TXT
document.getElementById('btn-extract-file').addEventListener('click', async () => {
  const fileInput = document.getElementById('ki-file-input');
  if (fileInput.files.length === 0) { DeskUI.notify('Selecciona al menos un archivo.'); return; }
  const extraction = kiDocumentText;
  const selectedFiles = [...fileInput.files];

  const status = document.getElementById('ki-file-status');
  status.textContent = `Procesando ${fileInput.files.length} archivo(s)...`;
  setKIProcessing('Extrayendo texto de los archivos...');

  let names = [];

  for (let i = 0; i < selectedFiles.length; i++) {
    if (kiDocumentText !== extraction) return;
    const file = selectedFiles[i];
    names.push(file.name);
    status.textContent = `Extrayendo "${file.name}" (${i+1}/${fileInput.files.length})...`;

    const base64 = await new Promise((resolve) => {
      const reader = new FileReader();
      reader.onload = e => resolve(e.target.result.split(',')[1]);
      reader.readAsDataURL(file);
    });

    try {
      const res = await fetch(`${API_BASE}/knowledge/extract-file`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ fileBase64: base64, fileName: file.name })
      });
      const data = await res.json();
      if (data.error) { DeskUI.notify(`Error en ${file.name}: ${data.error}`); continue; }

      if (kiDocumentText !== extraction) return;
      extraction.set(file, data.textoExtraido);
    } catch(err) {
      DeskUI.notify(`Fallo en red para ${file.name}`);
    }
  }

  setKIProcessing(null);
  // El texto completo permanece en memoria hasta solicitar el análisis.
  document.getElementById('ki-fuente').value = names.join(', ');
  status.textContent = `Extraídos ${kiDocumentText.size} de ${fileInput.files.length} archivos. ${[...kiDocumentText.values()].reduce((n, text) => n + text.length, 0)} caracteres en memoria. Usa Autocompletar con IA para completar Contenido.`;
  DeskUI.notify(kiDocumentText.size === selectedFiles.length ? 'Texto completo en memoria. Usa Autocompletar con IA para completar Contenido.' : 'Algunos archivos no se pudieron extraer. Reintenta antes de autocompletar.');
});

// Analizar imagen con tu agente
document.getElementById('btn-analyze-image').addEventListener('click', async () => {
  const fileInput = document.getElementById('ki-image-input');
  if (fileInput.files.length === 0) { DeskUI.notify('Selecciona al menos una imagen.'); return; }

  for(let file of fileInput.files) {
     if (file.size > 4 * 1024 * 1024) { DeskUI.notify(`La imagen ${file.name} supera el limite de 4 MB.`); return; }
  }

  const signal = beginKIAnalysis();
  const files = [...fileInput.files];
  setKIProcessing(`Analizando ${fileInput.files.length} imagen(es) con tu agente...`);

  let mergedSuggestion = { titulo: [], descripcion: [], contenido: [], categoriaITIL: 'General', servicio: '' };
  let names = [];

  for (let i = 0; i < files.length; i++) {
    const file = files[i];
    names.push(file.name);
    const base64 = await new Promise((resolve) => {
      const reader = new FileReader();
      reader.onload = e => resolve(e.target.result.split(',')[1]);
      reader.readAsDataURL(file);
    });

    if (signal.aborted) return;
    try {
      const res = await fetch(`${API_BASE}/knowledge/analyze`, {
        method: 'POST', signal,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ imageBase64: base64, imageMime: file.type })
      });
      const data = await res.json();
      if (signal.aborted) return;
      if (data.error) { DeskUI.notify(`Error en ${file.name}: ${data.error}`); continue; }

      const s = data.suggestion;
      if (s.titulo) mergedSuggestion.titulo.push(s.titulo);
      if (s.descripcion) mergedSuggestion.descripcion.push(s.descripcion);
      if (s.contenido) mergedSuggestion.contenido.push(`--- Imagen ${i+1}: ${file.name} ---\n${s.contenido}`);
      if (i === 0) {
        mergedSuggestion.categoriaITIL = s.categoriaITIL;
        mergedSuggestion.servicio = s.servicio;
      }
    } catch(err) {
      if (signal.aborted) return;
      DeskUI.notify(`Fallo en red para ${file.name}`);
    }
  }

  if (signal.aborted) return;
  setKIProcessing(null);
  document.getElementById('ki-titulo').value = mergedSuggestion.titulo.join(' + ').substring(0,100);
  document.getElementById('ki-descripcion').value = mergedSuggestion.descripcion.join('; ').substring(0,250);
  document.getElementById('ki-contenido').value = mergedSuggestion.contenido.join('\n\n');
  document.getElementById('ki-categoria').value = mergedSuggestion.categoriaITIL;
  document.getElementById('ki-servicio').value = mergedSuggestion.servicio;
  document.getElementById('ki-fuente').value = names.join(', ');
});

// Submit formulario (crear o actualizar)
document.getElementById('form-ki').addEventListener('submit', async (e) => {
  e.preventDefault();
  const id = document.getElementById('ki-form-id').value;
  const tipo = document.getElementById('ki-form-tipo').value;
  const titulo = document.getElementById('ki-titulo').value.trim();
  const contenido = document.getElementById('ki-contenido').value.trim();

  if (!titulo) { DeskUI.notify('El titulo es obligatorio.'); return; }
  if (!contenido) { DeskUI.notify('El contenido es obligatorio.'); return; }

  const etiquetasRaw = document.getElementById('ki-etiquetas').value;
  const etiquetas = etiquetasRaw.split(',').map(t => t.trim()).filter(t => t.length > 0);

  setKIProcessing('Preparando archivos adjuntos...');
  let adjuntos = [];
  let fileInput = null;
  if (tipo === 'documento') fileInput = document.getElementById('ki-file-input');
  if (tipo === 'imagen') fileInput = document.getElementById('ki-image-input');
  // Los videos de análisis son temporales y no se guardan como adjuntos.

  if (fileInput && fileInput.files.length > 0) {
    for (let i = 0; i < fileInput.files.length; i++) {
      const file = fileInput.files[i];
      const base64 = await new Promise((resolve) => {
        const reader = new FileReader();
        reader.onload = e => resolve(e.target.result.split(',')[1]);
        reader.readAsDataURL(file);
      });
      adjuntos.push({
        nombre: file.name,
        mime: file.type || 'application/octet-stream',
        base64: base64
      });
    }
  }

  const payload = {
    titulo,
    tipo,
    categoriaITIL: document.getElementById('ki-categoria').value || 'General',
    servicio: document.getElementById('ki-servicio').value.trim(),
    descripcion: document.getElementById('ki-descripcion').value.trim(),
    contenido,
    fuente: document.getElementById('ki-fuente').value.trim(),
    url: document.getElementById('ki-url-input').value.trim(),
    etiquetas,
    estado: document.getElementById('ki-estado').value,
    adjuntos: adjuntos,
    metadatos: { procesadoPorIA: document.getElementById('ki-processing-bar').dataset.procesado === 'true' }
  };

  setKIProcessing('Guardando conocimiento...');
  try {
    const method = id ? 'PUT' : 'POST';
    const url = id ? `${API_BASE}/knowledge/${id}` : `${API_BASE}/knowledge`;
    const res = await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    const data = await res.json();
    setKIProcessing(null);
    if (data.error) { DeskUI.notify('Error: ' + data.error); return; }
    document.getElementById('modal-ki-form').style.display = 'none';
    await renderKnowledgeMgmt();
    DeskUI.notify(id ? 'Conocimiento actualizado.' : 'Conocimiento guardado y disponible para el Agente de soporte.');
  } catch (err) {
    setKIProcessing(null);
    DeskUI.notify('Error de conexion: ' + err.message);
  }
});

// ─────────────────────────────────────────────────────────────
// VER DETALLE
// ─────────────────────────────────────────────────────────────
async function openKIDetail(id) {
  const modal = document.getElementById('modal-ki-detail');
  const content = document.getElementById('ki-detail-content');
  content.innerHTML = '<div class="loading">Cargando...</div>';
  modal.style.display = 'flex';
  try {
    const res = await fetch(`${API_BASE}/knowledge/${id}`);
    const item = await res.json();
    document.getElementById('ki-detail-title').textContent = item.titulo;
    content.innerHTML = `
      <div style="margin-bottom:12px;">
        <span class="ki-badge-tipo">${escapeHtml(item.tipo)}</span>
        <span class="ki-badge-cat">${escapeHtml(item.categoriaITIL)}</span>
        ${getBadgeEstado(item.estado)}
      </div>
      ${item.servicio ? `<p style="margin-bottom:8px;"><strong>Servicio:</strong> ${escapeHtml(item.servicio)}</p>` : ''}
      ${item.fuente ? `<p style="margin-bottom:8px;"><strong>Fuente:</strong> ${escapeHtml(item.fuente)}</p>` : ''}
      ${item.url ? `<p style="margin-bottom:8px;"><strong>URL:</strong> <a href="${escapeHtml(item.url)}" target="_blank" rel="noopener">${escapeHtml(item.url)}</a></p>` : ''}
      ${item.descripcion ? `<p style="margin-bottom:12px; color:var(--text-muted); font-style:italic;">${escapeHtml(item.descripcion)}</p>` : ''}
      <div class="knowledge-article">${escapeHtml(item.contenido)}</div>

      ${item.adjuntos && item.adjuntos.length ? `
        <div style="margin-top: 16px; padding-top: 16px; border-top: 1px solid var(--border);">
          <strong style="display:block; margin-bottom:8px; font-size:0.9rem;">Archivos Adjuntos:</strong>
          <div style="display: flex; gap: 10px; flex-wrap: wrap;">
            ${item.adjuntos.map(a => {
              const attachUrl = `${API_BASE}/knowledge/${item.id}/attachments/${a.id}`;
              if (a.mime.startsWith('image/')) {
                return `<a href="${attachUrl}" target="_blank" style="display:block; max-width: 200px;"><img src="${attachUrl}" alt="${escapeHtml(a.nombre)}" style="max-width:100%; border-radius:4px; border:1px solid #ccc;"></a>`;
              } else if (a.mime.startsWith('video/')) {
                return `<video controls style="max-width: 300px; border-radius:4px; border:1px solid #ccc;"><source src="${attachUrl}" type="${a.mime}"></video>`;
              } else {
                return `<a href="${attachUrl}" target="_blank" class="btn-secondary" style="font-size:0.8rem; text-decoration:none;"> ${escapeHtml(a.nombre)}</a>`;
              }
            }).join('')}
          </div>
        </div>
      ` : ''}

      ${item.etiquetas && item.etiquetas.length ? `<div class="ki-item-tags" style="margin-top:12px;">${item.etiquetas.map(t=>`<span class="ki-tag">${escapeHtml(t)}</span>`).join('')}</div>` : ''}
      <p style="margin-top:12px; font-size:0.75rem; color:var(--text-muted);">Creado: ${new Date(item.fechaCreacion).toLocaleString('es-PE')} | Usos: ${item.metadatos?.usoContador || 0}</p>
    `;
  } catch (e) {
    content.innerHTML = '<p style="color:var(--danger)">Error al cargar el detalle.</p>';
  }
}

// ─────────────────────────────────────────────────────────────
// EDITAR
// ─────────────────────────────────────────────────────────────
async function openKIEdit(id) {
  try {
    const res = await fetch(`${API_BASE}/knowledge/${id}`);
    const item = await res.json();
    openKIForm(item.tipo, item);
  } catch (e) {
    DeskUI.notify('Error al cargar el conocimiento para editar.');
  }
}

// ─────────────────────────────────────────────────────────────
// ELIMINAR
// ─────────────────────────────────────────────────────────────
async function deleteKI(id, titulo) {
  if (!await DeskUI.confirm(`Eliminar el conocimiento:\n"${titulo}"\n\nEsta accion es reversible (se marca como inactivo).`)) return;
  try {
    const res = await fetch(`${API_BASE}/knowledge/${id}`, { method: 'DELETE' });
    const data = await res.json();
    if (data.error) { DeskUI.notify('Error: ' + data.error); return; }
    await renderKnowledgeMgmt();
  } catch (e) {
    DeskUI.notify('Error de conexion: ' + e.message);
  }
}

// =====================================================================
// PREVISUALIZACIONES DE CONOCIMIENTO (Ahorro de tokens)
// =====================================================================

// Imagen
document.getElementById('ki-image-input').addEventListener('change', (e) => {
  const files = e.target.files;
  const previewContainer = document.getElementById('ki-image-preview');
  previewContainer.innerHTML = '';

  if (files.length > 0) {
    previewContainer.style.display = 'flex';
    for (let file of files) {
      if (file.type.startsWith('image/')) {
        const img = document.createElement('img');
        img.style.cssText = 'max-width: 200px; max-height: 150px; border-radius: 4px; border: 1px solid #ccc;';
        const reader = new FileReader();
        reader.onload = ev => img.src = ev.target.result;
        reader.readAsDataURL(file);
        previewContainer.appendChild(img);
      }
    }
  } else {
    previewContainer.style.display = 'none';
  }
});

// Documento (PDF)
document.getElementById('ki-file-input').addEventListener('change', async (e) => {
  kiDocumentText = new Map();
  document.getElementById('ki-file-status').textContent = '';
  const files = e.target.files;
  const previewContainer = document.getElementById('ki-file-preview');
  previewContainer.innerHTML = '';

  if (files.length > 0) {
    previewContainer.style.display = 'flex';
    for (let file of files) {
      if (file.type === 'application/pdf') {
        const objectUrl = URL.createObjectURL(file);
        const frame = document.createElement('iframe');
        frame.src = `${objectUrl}#toolbar=0&navpanes=0`;
        frame.style.cssText = 'width: 100%; height: 350px; border: 1px solid #ddd; flex-shrink: 0;';
        previewContainer.appendChild(frame);
      } else {
        const div = document.createElement('div');
        div.style.cssText = 'padding: 10px; background: #eee; border: 1px solid #ccc; width: 100%; flex-shrink: 0;';
        div.textContent = 'Cargando vista previa de ' + file.name + '...';
        previewContainer.appendChild(div);
        try {
          const base64 = await new Promise((resolve, reject) => { const reader = new FileReader(); reader.onload = () => resolve(reader.result.split(',')[1]); reader.onerror = reject; reader.readAsDataURL(file); });
          const response = await fetch(API_BASE + '/knowledge/extract-file', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ fileBase64: base64, fileName: file.name }) });
          const data = await response.json();
          if (!response.ok) throw new Error(data.error);
          div.style.whiteSpace = 'pre-wrap';
          div.textContent = file.name + '\nVista previa del texto completo (sin formato de Word)\n\n' + data.textoExtraido;
        } catch (error) { div.textContent = 'No se pudo generar la vista previa: ' + error.message; }
      }
    }
  } else {
    previewContainer.style.display = 'none';
  }
});

// Video (URL)
document.getElementById('ki-url-input').addEventListener('input', (e) => {
  const url = e.target.value.trim();
  const previewContainer = document.getElementById('ki-url-preview');

  if (!url) {
    previewContainer.style.display = 'none';
    previewContainer.innerHTML = '';
    return;
  }

  previewContainer.style.display = 'block';

  // Extraer ID de YouTube
  const ytMatch = url.match(/(?:youtube\.com\/(?:[^\/]+\/.+\/|(?:v|e(?:mbed)?)\/|.*[?&]v=)|youtu\.be\/)([^"&?\/\s]{11})/i);
  if (ytMatch && ytMatch[1]) {
    const videoId = ytMatch[1];
    previewContainer.innerHTML = `
      <img src="https://img.youtube.com/vi/${videoId}/hqdefault.jpg" style="max-width:100%; max-height:250px; border-radius:4px; margin-bottom:5px;">
      <br><small style="color:#666;">Vista previa de YouTube cargada correctamente</small>
    `;
    return;
  }

  // Archivo de video directo
  if (url.match(/\.(mp4|webm|ogg)$/i)) {
    previewContainer.innerHTML = `
      <video controls style="max-width:100%; max-height:250px; border-radius:4px;">
        <source src="${url}">
      </video>
      <br><small style="color:#666;">Vista previa de video directo disponible</small>
    `;
    return;
  }

  // Fallback
  previewContainer.innerHTML = `<span style="color:#888; font-size:0.85rem;">No hay vista previa generable para esta URL. (Se soporta YouTube o enlaces directos MP4).</span>`;
});

// Inicializar la vista por defecto
window.addEventListener('DOMContentLoaded', async () => {
  await fetchGlobalConfig();
  loadAllTickets();
});

// Conversation layout controls affect presentation only.
document.getElementById('expand-agent').addEventListener('click', e => {
  const expanded = document.body.classList.toggle('agent-expanded');
  e.currentTarget.setAttribute('aria-pressed', String(expanded));
  e.currentTarget.textContent = expanded ? 'Volver al espacio de trabajo' : 'Ampliar conversación';
  document.querySelector('.sidebar').inert = expanded || matchMedia('(max-width:1023px)').matches;
  document.querySelector('.workspace-bar').inert = expanded;
});
asistenteInput.addEventListener('keydown', e => {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) { e.preventDefault(); asistenteForm.requestSubmit(); }
});

// IA Title formatting
document.getElementById('btn-format-title').addEventListener('click', async () => {
  const input = document.getElementById('new-ticket-titulo');
  const desc = document.getElementById('new-ticket-desc');
  const rawTitle = input.value.trim() || (desc ? desc.value.trim() : '');
  if (!rawTitle) {
    DeskUI.notify('Por favor escribe algo en el título o descripción primero.');
    return;
  }

  const btn = document.getElementById('btn-format-title');
  const originalHtml = btn.innerHTML;
  btn.innerHTML = '⏳';
  btn.disabled = true;

  try {
    const res = await fetch(`${API_BASE}/format-title`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title: rawTitle })
    });
    const data = await res.json();
    if (data.title) {
      input.value = data.title.replace(/^"|"$/g, '');
      const errorLabel = document.getElementById('titulo-error');
      if (errorLabel) errorLabel.style.display = 'none';
      input.dispatchEvent(new Event('input'));
    } else {
      DeskUI.notify('Error al formatear: ' + (data.error || 'Desconocido'));
    }
  } catch(e) {
    DeskUI.notify('Error de red al formatear el título.');
  } finally {
    btn.innerHTML = originalHtml;
    btn.disabled = false;
  }
});
