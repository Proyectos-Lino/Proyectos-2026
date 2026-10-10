const fs = require('fs');
const path = require('path');
const { APP_ROOT } = require('./paths');
const { Ticket, getAllTickets, saveTicket } = require('../models/ticket');
const { knowledgeService, ITIL_CATEGORIES } = require('./knowledgeService');
const { analyzeVideo, MAX_VIDEO_BYTES } = require('./videoService');
const { callGemini, uploadToGemini } = require('./geminiService');
const { getKnowledgeBase, processMessageWithKb } = require('./chatFlowService');
const { parseBody, sendJson } = require('./utils');
const { extractTextFromFile } = require('./documentService');

let activeVideoUploads = 0;
async function readVideoBody(req) {
  const chunks = [];
  let size = 0;
  req.setTimeout(120000, () => req.destroy(new Error('Tiempo de carga agotado.')));
  for await (const chunk of req) {
    size += chunk.length;
    if (size > MAX_VIDEO_BYTES) throw new Error('El límite de análisis de la aplicación es 256 MB por video.');
    chunks.push(chunk);
  }
  req.setTimeout(0);
  return Buffer.concat(chunks);
}

async function handleApiRequest(req, res, parsedUrl, pathName) {
  // ═══════════════════════════════════════════════════════════
  // TICKETS
  // ═══════════════════════════════════════════════════════════

  // POST /api/tickets — crear ticket (interaccion inicial del chat)
  if (req.method === 'POST' && pathName === '/api/tickets') {
    const body = await parseBody(req);
    const userMessage = typeof body.message === 'string' ? body.message.trim() : '';
    if (!userMessage) return sendJson(res, 400, { error: 'Escribe una consulta para registrar el ticket.' });
    const kbResult = await processMessageWithKb(userMessage, null);

    let equipoAsignado = 'mesa_de_ayuda';
    if (kbResult.category === 'incidencia_tecnica') equipoAsignado = 'area_tecnica';
    if (kbResult.category === 'reclamo') equipoAsignado = 'supervisor';

    const ticket = new Ticket({
      categoria: kbResult.category,
      equipoAsignado,
      reportadoPor: body.usuario || 'usuario_anonimo',
      requiereEscalamiento: kbResult.requiresEscalation !== undefined ? kbResult.requiresEscalation : !kbResult.resolved,
      flowState: kbResult.flowState || null,
      urgencia: kbResult.isHighUrgency ? 'alta' : 'media',
      titulo: body.titulo || '[General] + [Sistema] + [Incidencia no clasificada]',
      tipo: body.tipo || 'Incidente',
      prioridad: body.prioridad || 'P3 (Media)',
      codigoPatrimonial: body.codigoPatrimonial || 'N/D',
      mensajeError: body.mensajeError || 'N/D',
      alcance: body.alcance || 'Un Puesto',
      subcategoriaTecnica: null
    });

    ticket.addLog(ticket.reportadoPor, `Consulta: ${userMessage}`, 0);
    ticket.addLog('sistema', `Respuesta: ${kbResult.response}`, 0);
    if (kbResult.resolved) ticket.estado = 'en_progreso';

    saveTicket(ticket);
    return sendJson(res, 201, { ticket, response: kbResult.response });
  }

  // GET /api/tickets — listar tickets
  if (req.method === 'GET' && pathName === '/api/tickets') {
    const { equipoAsignado, estado, turno } = parsedUrl.query;
    let tickets = getAllTickets();
    if (equipoAsignado) tickets = tickets.filter(t => t.equipoAsignado === equipoAsignado);
    if (estado) tickets = tickets.filter(t => t.estado === estado);
    if (turno) tickets = tickets.filter(t => t.turno === turno);
    return sendJson(res, 200, tickets);
  }

  const idMatch = pathName.match(/^\/api\/tickets\/([^\/]+)\/(log|derivate|close|escalate)$/);
  if (req.method === 'POST' && idMatch) {
    const ticketId = idMatch[1];
    const action = idMatch[2];
    const body = await parseBody(req);

    const tickets = getAllTickets();
    const ticketData = tickets.find(t => t.id === ticketId);
    if (!ticketData) return sendJson(res, 404, { error: 'Ticket no encontrado' });

    const ticket = new Ticket(ticketData);
    if (ticket.estado === 'cerrado') return sendJson(res, 409, { error: 'El ticket ya está cerrado.' });
    if (['log', 'close'].includes(action) && (typeof body.texto !== 'string' || !body.texto.trim())) {
      return sendJson(res, 400, { error: 'Describe la acción o la solución aplicada.' });
    }
    if (body.minutosInvertidos !== undefined && (!Number.isFinite(body.minutosInvertidos) || body.minutosInvertidos < 0)) {
      return sendJson(res, 400, { error: 'El tiempo invertido debe ser un número mayor o igual a cero.' });
    }
    if (action === 'derivate' && !getKnowledgeBase().teams.includes(body.nuevoEquipo)) {
      return sendJson(res, 400, { error: 'Equipo de derivación inválido.' });
    }
    if (action === 'close' && !body.subcategoriaTecnica && !ticket.subcategoriaTecnica) {
      return sendJson(res, 400, { error: 'Selecciona una subcategoría técnica para cerrar el ticket.' });
    }

    if (action === 'log') {
      if (body.autor === ticket.reportadoPor && (ticket.estado === 'abierto' || ticket.estado === 'en_progreso' || ticket.estado === 'resuelto_pendiente')) {
        ticket.addLog(body.autor, `Consulta: ${body.texto}`, body.minutosInvertidos || 0);
        const kbResult = await processMessageWithKb(body.texto, ticket);
        if (kbResult.isHighUrgency) ticket.urgencia = 'alta';

        if (ticket.requiereEscalamiento) {
          const fallbacks = [
            'Tu caso ya se encuentra en cola para ser atendido por un agente. Hemos añadido tu ultimo mensaje al expediente.',
            'Gracias, he agregado ese detalle a tu expediente para el agente.',
            'Entendido. Esa informacion extra ha sido guardada en tu ticket.',
            'Tu caso sigue en cola para ser atendido por un agente. Hemos añadido tu ultimo mensaje al expediente.'
          ];
          const fallbackMsg = fallbacks[ticket.bitacora.length % fallbacks.length];
          ticket.addLog('sistema', fallbackMsg, 0);
          ticket.flowState = kbResult.flowState || null;
          ticket.requiereEscalamiento = true;
          saveTicket(ticket);
          return sendJson(res, 200, { ticket, response: fallbackMsg });
        } else {
          ticket.addLog('sistema', `Respuesta: ${kbResult.response}`, 0);
          ticket.flowState = kbResult.flowState || null;
          if (kbResult.resolved) { ticket.requiereEscalamiento = false; ticket.estado = 'en_progreso'; }
          else { ticket.requiereEscalamiento = kbResult.requiresEscalation !== undefined ? kbResult.requiresEscalation : true; ticket.estado = 'abierto'; }
          saveTicket(ticket);
          return sendJson(res, 200, { ticket, response: kbResult.response });
        }
      } else {
        ticket.addLog(body.autor, body.texto, body.minutosInvertidos || 0);
      }
    } else if (action === 'derivate') {
      ticket.derivate(body.nuevoEquipo, body.autor, body.motivo);
    } else if (action === 'close') {
      if (body.subcategoriaTecnica) ticket.subcategoriaTecnica = body.subcategoriaTecnica;
      ticket.close(body.autor, body.texto, body.minutosInvertidos || 0);
    } else if (action === 'escalate') {
      ticket.requiereEscalamiento = true;
      ticket.estado = 'abierto';
      ticket.addLog('sistema', 'El usuario solicito hablar con un asesor explicitamente', 0);

    }

    saveTicket(ticket);
    return sendJson(res, 200, { ticket });
  }

  // GET /api/tickets/similar
  if (req.method === 'GET' && pathName === '/api/tickets/similar') {
    const { query, equipoAsignado } = parsedUrl.query;
    if (!query) return sendJson(res, 400, { error: 'Se requiere parametro query' });
    let tickets = getAllTickets().filter(t => t.estado === 'cerrado');
    if (equipoAsignado) tickets = tickets.filter(t => t.equipoAsignado === equipoAsignado);
    const similares = tickets.filter(t => t.bitacora.some(b => b.texto.toLowerCase().includes(query.toLowerCase())));
    return sendJson(res, 200, similares);
  }

  // ═══════════════════════════════════════════════════════════
  // ASISTENTE IA (panel de TI) — usa conocimiento institucional
  // ═══════════════════════════════════════════════════════════
  if (req.method === 'POST' && pathName === '/api/asistente-tecnico/mensaje') {
    const body = await parseBody(req);
    const { mensaje, ticketId, excluirIds: excluirIdsRaw, historialConversacion, archivosBase64 } = body;
    if (!mensaje) return sendJson(res, 400, { error: 'Falta el campo mensaje' });
    const excluirIds = Array.isArray(excluirIdsRaw) ? excluirIdsRaw : [];

    const allTickets = getAllTickets();
    const kb = getKnowledgeBase();
    const lowerMsg = mensaje.toLowerCase();

    // ---- Contexto 1: CONOCIMIENTO INSTITUCIONAL (prioridad maxima) ----
    const kiSearch = await knowledgeService.search(mensaje, {}, 3);
    const kiItems = kiSearch.items;
    const kiFuentes = kiSearch.fuentes;
    let contextInstitucional = '';
    if (kiItems.length > 0) {
      contextInstitucional = `\n[CONOCIMIENTO INSTITUCIONAL - PRIORIDAD MAXIMA para responder]:\n` +
        kiItems.map((item, i) => {
          const contenido = (item.contenido || item.descripcion || '').substring(0, 800);
          return `--- Fuente ${i+1} [ID: ${item.id}]: "${item.titulo}" [${item.tipo}] [${item.categoriaITIL}] Servicio: ${item.servicio || 'N/D'} ---\n${contenido}`;
        }).join('\n\n');
    }

    // Registrar trazabilidad de uso
    for (const item of kiItems) {
      await knowledgeService.recordUsage(item.id, ticketId || null, mensaje);
    }

    // ---- Contexto 2: Intents del chatbot (respaldo) ----
    const kbResumen = `Intents (${kb.intents.length}): ${kb.intents.map(i => `[${i.category}] trigger: ${Array.isArray(i.trigger) ? i.trigger.join(',') : i.trigger} => resp: ${i.response.substring(0,80)}`).join(' | ')}`;

    // ---- Contexto 3: Estadisticas del sistema ----
    const statsKeywords = ['cuantos','cuantas','total','promedio','hoy','pendientes','abiertos','cerrados','escalados','tickets','metricas'];
    let contextStats = '';
    if (statsKeywords.some(kw => lowerMsg.includes(kw))) {
      const hoy = new Date().toISOString().substring(0, 10);
      const todosTickets = allTickets;
      const ticketsHoy = todosTickets.filter(t => t.fechaCreacion && t.fechaCreacion.startsWith(hoy));
      const abiertos = todosTickets.filter(t => t.estado === 'abierto' || t.estado === 'en_progreso');
      const cerrados = todosTickets.filter(t => t.estado === 'cerrado');
      const escalados = todosTickets.filter(t => t.requiereEscalamiento && t.estado !== 'cerrado');
      const ttrCerrados = cerrados.filter(t => t.ttrMinutos !== null);
      const ttrProm = ttrCerrados.length > 0 ? (ttrCerrados.reduce((a,t) => a + t.ttrMinutos, 0) / ttrCerrados.length).toFixed(1) : 'N/D';
      const fcrCount = cerrados.filter(t => t.resueltoEnPrimerContacto === true).length;
      const fcrPct = cerrados.length > 0 ? ((fcrCount / cerrados.length) * 100).toFixed(1) : 'N/D';

      contextStats = `\n[DATOS REALES DEL SISTEMA - ${hoy}]:\n- Tickets creados hoy: ${ticketsHoy.length}\n- Total: ${todosTickets.length}\n- Abiertos/En progreso: ${abiertos.length}\n- Cerrados: ${cerrados.length}\n- Escalados pendientes: ${escalados.length}\n- TTR promedio: ${ttrProm} min\n- FCR: ${fcrPct}%`;
    }

    // ---- Contexto 4: Ticket especifico ----
    let contextTicket = '';
    let ticketData = null;
    if (ticketId) {
      ticketData = allTickets.find(t => t.id === ticketId);
      if (ticketData) {
        const labels = { 'mesa_de_ayuda': 'Mesa de ayuda', 'area_tecnica': 'Area tecnica', 'supervisor': 'Supervisor', 'otro': 'Otro' };
        contextTicket = `\n[TICKET ACTIVO - ID: ${ticketData.id.substring(0,8)}]:\n- Categoria: ${ticketData.categoria}\n- Estado: ${ticketData.estado}\n- Equipo asignado: ${labels[ticketData.equipoAsignado] || ticketData.equipoAsignado}\n- Reportado por: ${ticketData.reportadoPor}\n- Mensaje original: ${ticketData.bitacora.find(b => b.autor === ticketData.reportadoPor)?.texto || 'N/D'}\n- Entradas en bitacora: ${ticketData.bitacora.length}`;
      }
    }

    // ---- Contexto 5: Casos similares de tickets cerrados ----
    let contextSimilares = '';
    let usadosIds = [];
    const busquedaKeywords = ['solucion','similar','parecido','caso','resolvieron','resolvio','antes','antecedente','historial','busca','encuentra'];
    const hacerBusqueda = (ticketData && ticketData.estado !== 'cerrado') || busquedaKeywords.some(kw => lowerMsg.includes(kw));

    if (hacerBusqueda) {
      const textoBase = ticketData ? (ticketData.bitacora.find(b => b.autor === ticketData.reportadoPor)?.texto || mensaje) : mensaje;
      const words = textoBase.toLowerCase().split(' ').filter(w => w.length > 3);
      const cerradosList = allTickets.filter(t => t.estado === 'cerrado' && (!ticketId || t.id !== ticketId));
      const candidatos = cerradosList
        .filter(t => !excluirIds.includes(t.id))
        .filter(t => words.length === 0 || words.some(w => JSON.stringify(t.bitacora).toLowerCase().includes(w)))
        .slice(0, 3);

      if (candidatos.length > 0) {
        usadosIds = candidatos.map(t => t.id);
        contextSimilares = `\n[CASOS SIMILARES RESUELTOS (anonimizados)]:\n` +
          candidatos.map((t, i) => {
            const sistEntradas = t.bitacora.filter(b => b.autor === 'sistema');
            const ultima = sistEntradas.length > 0 ? sistEntradas[sistEntradas.length - 1].texto : 'Sin solucion registrada';
            return `Caso ${i+1} [${t.categoria}]: ${ultima}`;
          }).join('\n');
      } else if (excluirIds.length > 0) {
        contextSimilares = '\n[No se encontraron mas casos similares distintos a los ya mostrados.]';
      }
    }

    // ---- Construir prompt y llamar a Gemini ----
    const hayContextoSimilares = usadosIds.length > 0;
    const agotado = hacerBusqueda && usadosIds.length === 0 && excluirIds.length > 0;
    const hayKnowledge = kiItems.length > 0;

    if (!process.env.GEMINI_API_KEY) {
      return sendJson(res, 200, { respuesta: 'GEMINI_API_KEY no configurada.', usadosIds, agotado, kiFuentes, hayKnowledge });
    }

    const prompt = `Eres un asistente conversacional de soporte tecnico para los agentes de la mesa de ayuda.

REGLAS DE COMPORTAMIENTO:
1. Tono natural, fluido y de companerismo con el agente.
2. PRIORIDAD MAXIMA: Si existe [CONOCIMIENTO INSTITUCIONAL], basate PRINCIPALMENTE en ese contenido para responder.
3. Si NO existe conocimiento institucional, explícaselo claramente, pero LUEGO usa tu conocimiento general de IA para ofrecerle sugerencias u opciones. Aclara SIEMPRE de forma explícita que se trata de una sugerencia de la IA y no de un procedimiento oficial, y que el técnico no está obligado a utilizarla. Envuelve TODO este texto de descargo de responsabilidad (Disclaimer) usando obligatoriamente esta etiqueta HTML: <span style="color: red; font-weight: bold;">texto aqui</span>.
4. Usa formato HTML (como &lt;p&gt;, &lt;ul&gt;, &lt;li&gt;, &lt;strong&gt;) para separar tu respuesta en párrafos cortos y evitar la fatiga visual. NO mandes un solo bloque de texto.
5. Puedes ejecutar acciones sobre los tickets SOLO si el agente lo pide explicitamente en su mensaje (ej. "deriva el ticket", "cierra este caso"). 
   - Si piden "derivar" pero NO indican el área destino, usa la acción "solicitar_derivacion".
   - Si piden "cerrar" el ticket, usa la acción "solicitar_cierre" y extrae un resumen de la solución en el campo 'motivo'. El sistema le mostrará las opciones de guardado al usuario.
   - Solo si el agente confirma explícitamente haber elegido "Solo guardar solucion" o "Anexar toda la conversacion", usa la acción "cerrar". Si el agente envio un texto como "[Subcat: Hardware]", extrae esa subcategoria y agregala en un campo "subcategoriaTecnica" dentro de la accion.
   - MUY IMPORTANTE: Si el agente solo te pide "analizar", "sugerir", "buscar" o no pide explícitamente una acción de sistema, el campo "accion" DEBE SER null. NUNCA asumas una acción.
6. Si el agente te pide MÁS opciones, "otras soluciones" o alternativas, REVISA OBLIGATORIAMENTE el [HISTORIAL DE LA CONVERSACION ACTUAL]. NO vuelvas a proponer la misma solución que ya sugeriste anteriormente. Busca enfoques totalmente distintos, escalamientos de segundo nivel, o comprobaciones creativas diferentes a las iniciales.

FORMATO DE RESPUESTA OBLIGATORIO:
Devuelve unicamente un objeto JSON valido con la siguiente estructura (NO envuelvas en \`\`\`json, devuelve texto limpio para JSON.parse):
{
  "respuesta": "Tu respuesta conversacional aqui, en HTML formateado.",
  "fuentes_utilizadas": ["id_fuente_1", "id_fuente_2"],
  "accion": null // OJO: reemplaza por el objeto abajo SOLO si el usuario solicito explicitamente una accion:
  /* { 
    "tipo": "derivar" | "anexar" | "solicitar_derivacion" | "solicitar_cierre" | "cerrar",
    "ticketId": "id_del_ticket", 
    "equipoDestino": "mesa_de_ayuda" | "area_tecnica" | "supervisor" | "otro", 
    "motivo": "resumen corto de la solucion a registrar",
    "anexar_chat": true | false
  } */
}

${contextInstitucional}

[BASE DE CONOCIMIENTO CHATBOT - orientacion general]:
${kbResumen}
${contextStats}
${contextTicket}
${contextSimilares}
${historialConversacion ? `\n[HISTORIAL DE LA CONVERSACION ACTUAL]:\n${historialConversacion}` : ''}

PREGUNTA DEL AGENTE: "${mensaje}"

${hayContextoSimilares ? 'Si mencionas los casos similares de tickets cerrados, incluyelos dentro de "respuesta".' : ''}`;

    try {
      const parts = [{ text: prompt }];
      if (archivosBase64 && Array.isArray(archivosBase64)) {
        for (const base64Str of archivosBase64) {
          const matches = base64Str.match(/^data:([a-zA-Z0-9]+\/[a-zA-Z0-9-+.]+);base64,(.+)$/);
          if (matches && matches.length === 3) {
            const mimeType = matches[1];
            const data = matches[2];
            
            if (mimeType.startsWith('video/') || mimeType.startsWith('audio/')) {
              try {
                const buffer = Buffer.from(data, 'base64');
                const fileUri = await uploadToGemini(buffer, mimeType);
                parts.push({ fileData: { fileUri, mimeType } });
              } catch (err) {
                console.error('Error uploading media to Gemini in chat:', err);
              }
            } else {
              parts.push({
                inline_data: {
                  mime_type: mimeType,
                  data: data
                }
              });
            }
          }
        }
      }
      let aiText = await callGemini(parts);
      aiText = aiText.replace(/^```json/i, '').replace(/^```/, '').replace(/```$/i, '').trim();
      
      let parsed = null;
      try {
        parsed = JSON.parse(aiText);
      } catch(e) {
        parsed = { respuesta: aiText, fuentes_utilizadas: [], accion: null };
      }

      let accionMensaje = '';
      let tObj = null;
      if (parsed.accion && parsed.accion.tipo && parsed.accion.ticketId) {
        const tId = parsed.accion.ticketId.trim();
        // Find the ticket by checking if ID starts with the specified string, as users might just give a prefix
        tObj = allTickets.find(t => t.id.startsWith(tId));
        
        if (tObj) {
          if (tObj.estado === 'cerrado' && parsed.accion.tipo !== 'anexar') {
            accionMensaje = `<div style="margin-top:10px; padding:8px; background:#ffebee; border-radius:4px; font-size:0.85rem; color:#b71c1c;"><strong>&#10008; Accion rechazada:</strong> El ticket ya se encuentra cerrado.</div>`;
          } else if (parsed.accion.tipo === 'solicitar_derivacion') {
            accionMensaje = `
              <div style="margin-top:10px; padding:10px; background:#fff3e0; border-left:4px solid #ff9800; border-radius:4px; font-size:0.85rem;">
                <strong>Opciones de Derivacion:</strong> Elige a que equipo quieres derivar el ticket ${tObj.id.substring(0,8)}:<br>
                <div style="margin-top:8px; display:flex; gap:8px;">
                  <button class="btn btn-sm btn-outline" style="border: 1px solid var(--primary); padding: 5px 10px; border-radius: 4px; cursor: pointer; background: transparent; color: var(--primary);" onclick="window.triggerAsistenteCommand('Deriva el ticket ${tObj.id} a mesa_de_ayuda')">Mesa de Ayuda</button>
                  <button class="btn btn-sm btn-outline" style="border: 1px solid var(--primary); padding: 5px 10px; border-radius: 4px; cursor: pointer; background: transparent; color: var(--primary);" onclick="window.triggerAsistenteCommand('Deriva el ticket ${tObj.id} a area_tecnica')">Area Tecnica</button>
                  <button class="btn btn-sm btn-outline" style="border: 1px solid var(--primary); padding: 5px 10px; border-radius: 4px; cursor: pointer; background: transparent; color: var(--primary);" onclick="window.triggerAsistenteCommand('Deriva el ticket ${tObj.id} a supervisor')">Supervisor</button>
                </div>
              </div>`;
          } else if (parsed.accion.tipo === 'solicitar_cierre') {
            const sol = parsed.accion.motivo || 'Solucion exitosa';
            accionMensaje = `
              <div style="margin-top:10px; padding:10px; background:#e8f5e9; border-left:4px solid #4caf50; border-radius:4px; font-size:0.85rem;">
                <strong>Opciones de Cierre:</strong> Elige como documentar este cierre en la bitacora oficial:<br>
                <div style="margin-top:8px; margin-bottom:8px;">
                  <label>Subcategoria Tecnica:</label>
                  <select id="close-subcategory-${tObj.id}" style="padding:4px; font-size:12px; margin-left:8px; border: 1px solid #ccc; border-radius: 4px;">
                    <option value="">-- Seleccionar --</option>
                    <option value="Hardware">Hardware</option>
                    <option value="Software">Software</option>
                    <option value="Redes">Redes</option>
                    <option value="Cuentas/Accesos">Cuentas/Accesos</option>
                    <option value="Otro">Otro</option>
                  </select>
                </div>
                <div style="margin-top:8px; display:flex; flex-direction:column; gap:8px;">
                  <button class="btn btn-sm btn-outline" style="border: 1px solid #4caf50; padding: 5px 10px; border-radius: 4px; cursor: pointer; background: transparent; color: #2e7d32;" onclick="
                    const subcat = document.getElementById('close-subcategory-${tObj.id}').value;
                    if(!subcat) { alert('Seleccione una subcategoria tecnica primero.'); return; }
                    window.triggerAsistenteCommand('Solo guardar solucion: ${sol} [Subcat: '+subcat+']');
                  ">Solo registrar la solucion corta en la bitacora</button>
                  <button class="btn btn-sm btn-outline" style="border: 1px solid #4caf50; padding: 5px 10px; border-radius: 4px; cursor: pointer; background: transparent; color: #2e7d32;" onclick="
                    const subcat = document.getElementById('close-subcategory-${tObj.id}').value;
                    if(!subcat) { alert('Seleccione una subcategoria tecnica primero.'); return; }
                    window.anexarChatYcerrar('${sol} [Subcat: '+subcat+']');
                  ">Anexar la conversacion completa y cerrar</button>
                </div>
              </div>`;
          } else if (parsed.accion.tipo === 'cerrar') {
            const sol = parsed.accion.motivo || 'Ticket cerrado';
            if (parsed.accion.subcategoriaTecnica) {
              tObj.subcategoriaTecnica = parsed.accion.subcategoriaTecnica;
            }
            if (!tObj.subcategoriaTecnica) {
              accionMensaje = `<div style="margin-top:10px; padding:8px; background:#ffebee; border-radius:4px; font-size:0.85rem; color:#b71c1c;"><strong>&#10008; Error de cierre:</strong> Falta seleccionar la subcategoría técnica. Pídesela nuevamente al agente.</div>`;
            } else {
              if (parsed.accion.anexar_chat) {
                tObj.close('sistema', `Cierre y Anexo por Asistente IA.\nDetalle de conversacion guardado en historial.\nSolucion final: ${sol}`, 0);
              } else {
                tObj.close('sistema', `Ticket resuelto mediante Asistente IA. Solucion: ${sol}`, 0);
              }
              saveTicket(tObj);
              accionMensaje = `<div style="margin-top:10px; padding:8px; background:#e3f2fd; border-radius:4px; font-size:0.85rem; color:#0d47a1;"><strong>&#10004; Ticket Cerrado:</strong> Se registro el cierre de ${tObj.id.substring(0,8)} exitosamente.</div>`;
            }
          } else if (parsed.accion.tipo === 'derivar' && parsed.accion.equipoDestino) {
            tObj.derivate(parsed.accion.equipoDestino, 'sistema', parsed.accion.motivo || 'Derivación solicitada por el agente');
            saveTicket(tObj);
            accionMensaje = `<div style="margin-top:10px; padding:8px; background:#e3f2fd; border-radius:4px; font-size:0.85rem; color:#0d47a1;"><strong>&#10004; Accion ejecutada:</strong> Ticket ${tObj.id.substring(0,8)} derivado a ${parsed.accion.equipoDestino.replace(/_/g, ' ')}</div>`;
          } else if (parsed.accion.tipo === 'anexar') {
            tObj.addLog('sistema', `Conversacion anexada por Asistente IA. Motivo: ${parsed.accion.motivo}`, 0);
            saveTicket(tObj);
            accionMensaje = `<div style="margin-top:10px; padding:8px; background:#e8f5e9; border-radius:4px; font-size:0.85rem; color:#1b5e20;"><strong>&#10004; Accion ejecutada:</strong> Conversacion anexada a la bitacora del ticket ${tObj.id.substring(0,8)}</div>`;
          }
        } else {
          accionMensaje = `<div style="margin-top:10px; padding:8px; background:#ffebee; border-radius:4px; font-size:0.85rem; color:#b71c1c;"><strong>&#10008; Accion fallida:</strong> No se encontro ningun ticket que coincida con el ID '${tId}'</div>`;
        }
      }

      const finalFuentes = kiFuentes.filter(f => (parsed.fuentes_utilizadas || []).includes(f.id));

      return sendJson(res, 200, { respuesta: parsed.respuesta, accionHtml: accionMensaje, usadosIds, agotado, kiFuentes: finalFuentes, hayKnowledge: finalFuentes.length > 0, ticketEstado: tObj ? tObj.estado : null });
    } catch (e) {
      console.error('Error en asistente-tecnico:', e.message);
      const fallback = 'Error al contactar el servicio de IA. Intente de nuevo.';
      return sendJson(res, 200, { respuesta: fallback, usadosIds, agotado, kiFuentes, hayKnowledge });
    }
  }

  // ═══════════════════════════════════════════════════════════
  // METRICAS
  // ═══════════════════════════════════════════════════════════
  if (req.method === 'GET' && pathName === '/api/metrics') {
    const { equipoAsignado, turno } = parsedUrl.query;
    let tickets = getAllTickets();
    if (equipoAsignado) tickets = tickets.filter(t => t.equipoAsignado === equipoAsignado);
    if (turno) tickets = tickets.filter(t => t.turno === turno);

    const ticketsWithTfr = tickets.filter(t => t.tfrMinutos !== null && t.tfrMinutos !== undefined);
    const totalTfr = ticketsWithTfr.reduce((acc, t) => acc + t.tfrMinutos, 0);
    const tfrPromedioMinutos = ticketsWithTfr.length > 0 ? Number((totalTfr / ticketsWithTfr.length).toFixed(2)) : 0;

    const cerrados = tickets.filter(t => t.estado === 'cerrado');
    const totalTtr = cerrados.reduce((acc, t) => acc + (t.ttrMinutos || 0), 0);
    const ttrPromedioMinutos = cerrados.length > 0 ? Number((totalTtr / cerrados.length).toFixed(2)) : 0;

    const fcrCount = cerrados.filter(t => t.resueltoEnPrimerContacto === true).length;
    const fcrPorcentaje = cerrados.length > 0 ? Number(((fcrCount / cerrados.length) * 100).toFixed(2)) : 0;

    const kb = getKnowledgeBase();
    const distribucionPorCategoria = {};
    (kb.categories || []).forEach(cat => { distribucionPorCategoria[cat] = 0; });
    tickets.forEach(t => {
      if (distribucionPorCategoria[t.categoria] !== undefined) distribucionPorCategoria[t.categoria]++;
      else distribucionPorCategoria[t.categoria] = 1;
    });

    const pendientesEscalados = tickets.filter(t => t.requiereEscalamiento === true && t.estado !== 'cerrado').length;

    return sendJson(res, 200, { totalTickets: tickets.length, tfrPromedioMinutos, ttrPromedioMinutos, fcrPorcentaje, distribucionPorCategoria, pendientesEscalados });
  }

  // ═══════════════════════════════════════════════════════════
  // ═══════════════════════════════════════════════════════════
  // ═══════════════════════════════════════════════════════════
  // EXTRACCION DE CONTEXTO IA (Para Nuevo Ticket)
  // ═══════════════════════════════════════════════════════════
  if (req.method === 'POST' && pathName === '/api/extraer-contexto') {
    const body = await parseBody(req);
    const { media } = body;
    if (!media) return sendJson(res, 400, { error: 'No se envió evidencia multimedia.' });

    const prompt = `Eres un asistente de Service Desk especializado en ITIL. Tu objetivo es ayudar a los agentes a registrar nuevos tickets analizando evidencia (una foto de un error, un pantallazo, un audio transcrito o video).
Analiza el archivo adjunto y extrae los siguientes datos obligatorios para crear un ticket formal en un sistema ITSM. Si no puedes determinar un dato, infiérepelo lógicamente o déjalo vacío según corresponda.
1. "reporter": El nombre de la persona que reporta el problema (si se menciona o infiere en el archivo). Si no logras determinarlo, pon "".
2. "desc": Una descripción técnica, formal y clara del incidente o solicitud.
3. "titulo": Título obligatorio con la estructura "[Área u Oficina inferida] + [Componente afectado] + [Breve Descripción]". Ej: [Contabilidad] + [Impresora HP] + [Atasco de papel]. No uses palabras como "Error" o "Ayuda".
4. "tipo": Debe ser estrictamente "Incidente" (interrupción de un servicio) o "Requerimiento" (solicitud de un nuevo acceso, equipo, etc).
5. "prioridad": Clasifica según impacto/urgencia: "P1 (Crítica)", "P2 (Alta)", "P3 (Media)", "P4 (Baja)".
6. "codigoPatrimonial": Si ves una etiqueta de inventario o código (ej: CP-12345, INV-987), anótalo. Si no, pon "N/D".
7. "mensajeError": Si hay un mensaje de error explícito en la pantalla (Ej: "502 Bad Gateway" o "Falta archivo DLL"), transcríbelo. Si no, pon "N/D".
8. "alcance": Infiere a quién afecta: "Un Puesto", "Toda la Oficina", "Toda la Sede".

Devuelve UNICAMENTE un JSON válido con este formato, sin usar \`\`\`json:
{
  "reporter": "Nombre o vacio",
  "desc": "Descripcion clara del incidente...",
  "titulo": "[Area] + [Componente] + [Descripcion]",
  "tipo": "Incidente|Requerimiento",
  "prioridad": "P1|P2|P3|P4",
  "codigoPatrimonial": "Codigo o N/D",
  "mensajeError": "Mensaje exacto o N/D",
  "alcance": "Alcance inferido"
}`;

    try {
      const parts = [{ text: prompt }];
      const matches = media.match(/^data:([a-zA-Z0-9]+\/[a-zA-Z0-9-+.]+);base64,(.+)$/);
      if (matches && matches.length === 3) {
        const mimeType = matches[1];
        const data = matches[2];
        if (mimeType.startsWith('video/') || mimeType.startsWith('audio/')) {
          try {
            const buffer = Buffer.from(data, 'base64');
            const fileUri = await uploadToGemini(buffer, mimeType);
            parts.push({ fileData: { fileUri, mimeType } });
          } catch (err) {
            console.error('Error uploading media to Gemini:', err);
          }
        } else {
          parts.push({
            inline_data: {
              mime_type: mimeType,
              data: data
            }
          });
        }
      }

      let aiText = await callGemini(parts);
      aiText = aiText.replace(/^```json/i, '').replace(/^```/, '').replace(/```$/i, '').trim();
      
      let parsed = null;
      try {
        parsed = JSON.parse(aiText);
      } catch(e) {
        parsed = { reporter: "", desc: aiText };
      }
      
      return sendJson(res, 200, parsed);
    } catch (e) {
      console.error('Error en extraer-contexto:', e.message);
      return sendJson(res, 500, { error: 'Error al procesar la evidencia con IA.' });
    }
  }


  // ═══════════════════════════════════════════════════════════
  // KNOWLEDGE BASE (chatbot intents — solo lectura)
  // ═══════════════════════════════════════════════════════════
  if (req.method === 'GET' && pathName === '/api/kb') {
    return sendJson(res, 200, getKnowledgeBase());
  }

  if (req.method === 'POST' && pathName === '/api/kb/reload') {
    return sendJson(res, 200, { message: 'Base de conocimiento recargada con exito', kb: getKnowledgeBase() });
  }

  // ═══════════════════════════════════════════════════════════
  // GESTION DEL CONOCIMIENTO INSTITUCIONAL
  // ═══════════════════════════════════════════════════════════

  // GET /api/knowledge/stats
  if (req.method === 'GET' && pathName === '/api/knowledge/stats') {
    const stats = await knowledgeService.getStats();
    return sendJson(res, 200, stats);
  }

  // GET /api/knowledge — listar con filtros opcionales y busqueda
  if (req.method === 'GET' && pathName === '/api/knowledge') {
    const { tipo, categoriaITIL, servicio, estado, q, includeInactive } = parsedUrl.query;
    const filters = {};
    if (tipo) filters.tipo = tipo;
    if (categoriaITIL) filters.categoriaITIL = categoriaITIL;
    if (servicio) filters.servicio = servicio;
    if (estado) filters.estado = estado;
    if (includeInactive === 'true') filters.includeInactive = true;

    let items = await knowledgeService.getAll(filters);
    if (q && q.trim()) {
      const normalize = value => String(value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
      const words = normalize(q).trim().split(/\s+/);
      items = items.filter(item => {
        const text = normalize([item.titulo, item.descripcion, item.contenido, item.servicio, item.fuente, ...(item.etiquetas || [])].join(' '));
        return words.every(word => text.includes(word));
      });
    }
    return sendJson(res, 200, items);
  }

  // POST /api/knowledge/analyze — Gemini analiza contenido y sugiere metadata
  if (req.method === 'POST' && (pathName === '/api/knowledge/analyze' || pathName === '/api/knowledge/analyze-video')) {
    let body, videoBuffer;
    if (pathName.endsWith('/analyze-video')) {
      if (activeVideoUploads >= 1) return sendJson(res, 429, { error: 'Ya hay un video en procesamiento. Reintenta al terminar.' });
      activeVideoUploads++;
      let released = false;
      const release = () => { if (!released) { released = true; activeVideoUploads--; } };
      res.once('finish', release);
      res.once('close', release);
      try { videoBuffer = await readVideoBody(req); }
      catch (error) { return sendJson(res, 413, { error: error.message }); }
      body = { videoMime: req.headers['content-type']?.split(';')[0], contenido: '' };
    } else body = await parseBody(req);
    const { contenido, imageBase64, imageMime, videoBase64, videoMime, videoUrl } = body;

    if (!process.env.GEMINI_API_KEY) {
      return sendJson(res, 400, { error: 'GEMINI_API_KEY no configurada' });
    }

    let parts = [];
    const categoriasStr = ITIL_CATEGORIES.join(' | ');

    if (videoBuffer || videoUrl || videoBase64) {
      const streaming = String(req.headers.accept || '').includes('application/x-ndjson');
      const started = Date.now();
      let stage = 'Preparando el análisis del video...';
      const emit = event => { if (streaming && !res.destroyed) res.write(JSON.stringify(event) + '\n'); };
      const progress = message => { stage = message; emit({ type: 'progress', message, segundos: Math.round((Date.now() - started) / 1000) }); };
      const finish = (status, data) => {
        if (!streaming) return sendJson(res, status, data);
        emit({ type: data.error ? 'error' : 'result', ...data });
        res.end();
      };
      if (streaming) {
        res.writeHead(200, { 'Content-Type': 'application/x-ndjson; charset=utf-8', 'Cache-Control': 'no-cache', 'X-Accel-Buffering': 'no' });
        progress(stage);
      }
      const heartbeat = streaming ? setInterval(() => progress(stage), 5000) : null;
      try {
        const result = await analyzeVideo({ buffer: videoBuffer || (videoBase64 ? Buffer.from(videoBase64, 'base64') : undefined), mime: videoMime, url: videoUrl, prompt: `Analiza este archivo multimedia relacionado con soporte tecnico de TI.\nDevuelve EXACTAMENTE este JSON sin texto adicional:\n{\n  "titulo": "titulo descriptivo",\n  "descripcion": "resumen breve",\n  "contenido": "descripcion detallada para busquedas futuras basado en lo que ocurre en el archivo",\n  "categoriaITIL": "una de: ${categoriasStr}",\n  "servicio": "servicio de TI relacionado",\n  "etiquetas": ["etiqueta1","etiqueta2","etiqueta3"]\n}` + (contenido ? '\nContexto del agente:\n' + contenido : '') }, { onProgress: progress });
        if (!ITIL_CATEGORIES.includes(result.suggestion.categoriaITIL)) result.suggestion.categoriaITIL = 'General';
        return finish(200, { ...result, procesadoPorIA: true });
      } catch (error) {
        return finish(422, { error: error.message, validacion: error.validacion || null, reintentar: true });
      } finally {
        clearInterval(heartbeat);
      }
    } else if (imageBase64 && imageMime) {
      parts = [
        { inline_data: { mime_type: imageMime, data: imageBase64 } },
        { text: `Analiza esta imagen relacionada con soporte tecnico de TI.\nDevuelve EXACTAMENTE este JSON sin texto adicional:\n{\n  "titulo": "titulo descriptivo",\n  "descripcion": "resumen breve",\n  "contenido": "descripcion detallada para busquedas futuras",\n  "categoriaITIL": "una de: ${categoriasStr}",\n  "servicio": "servicio de TI relacionado",\n  "etiquetas": ["etiqueta1","etiqueta2","etiqueta3"]\n}` }
      ];
    } else if (contenido) {
      parts = [{ text: `Analiza el siguiente contenido de soporte tecnico de TI y extrae informacion clave.\nDevuelve EXACTAMENTE este JSON sin texto adicional:\n{\n  "titulo": "titulo descriptivo",\n  "descripcion": "resumen de 1-2 oraciones",\n  "contenido": "contenido estructurado (maximo 1000 palabras)",\n  "categoriaITIL": "una de: ${categoriasStr}",\n  "servicio": "servicio de TI al que corresponde",\n  "etiquetas": ["etiqueta1","etiqueta2","etiqueta3","etiqueta4"]\n}\n\nContenido:\n---\n${String(contenido)}\n---` }];
    } else {
      return sendJson(res, 400, { error: 'Proporcione contenido o imagen para analizar' });
    }

    try {
      let aiText = await callGemini(parts, 0.1, 60000);
      aiText = aiText.replace(/^```json\s*/i, '').replace(/^```\s*/i, '').replace(/\s*```$/i, '');

      let suggestion;
      try {
        suggestion = JSON.parse(aiText);
        if (!ITIL_CATEGORIES.includes(suggestion.categoriaITIL)) suggestion.categoriaITIL = 'General';
      } catch (e) {
        return sendJson(res, 422, { error: 'Gemini no devolvió un análisis estructurado válido. El texto extraído sigue en memoria; reintenta el análisis.' });
      }
      return sendJson(res, 200, { suggestion, procesadoPorIA: true });
    } catch (e) {
      console.error('[Knowledge/analyze] Error:', e.message);
      return sendJson(res, 500, { error: 'Error al analizar con Gemini: ' + e.message });
    }
  }

  // POST /api/knowledge/extract-file — extrae texto de PDF, DOCX o TXT
  if (req.method === 'POST' && pathName === '/api/knowledge/extract-file') {
    try {
      const body = await parseBody(req);
      const result = await extractTextFromFile(body.fileBase64, body.fileName);
      return sendJson(res, 200, result);
    } catch (e) {
      console.error('[Knowledge/extract-file] Error:', e.message);
      const status = e.message.includes('Proporcione') || e.message.includes('limite') || e.message.includes('Formato') || e.message.includes('extraer texto') ? 400 : 500;
      return sendJson(res, status, { error: e.message });
    }
  }

  // Originals are served only through their owning knowledge record.
  const attachmentMatch = pathName.match(/^\/api\/knowledge\/([^/]+)\/attachments\/([^/]+)$/);
  if (req.method === 'GET' && attachmentMatch) {
    const file = await knowledgeService.getAttachment(attachmentMatch[1], attachmentMatch[2]);
    if (!file) return sendJson(res, 404, { error: 'Archivo original no disponible' });
    const downloadable = parsedUrl.query.download === '1' || !/^(image\/|video\/|application\/pdf$)/.test(file.mime);
    const encodedName = encodeURIComponent(file.nombre).replace(/['()*]/g, c => '%' + c.charCodeAt(0).toString(16));
    const headers = {
      'Content-Type': file.mime,
      'Content-Disposition': `${downloadable ? 'attachment' : 'inline'}; filename*=UTF-8''${encodedName}`,
      'X-Content-Type-Options': 'nosniff',
      'Accept-Ranges': 'bytes',
      'Cache-Control': 'private, no-cache'
    };
    let start = 0, end = file.buffer.length - 1, status = 200;
    if (req.headers.range) {
      const range = /^bytes=(\d*)-(\d*)$/.exec(req.headers.range);
      if (range && (range[1] || range[2])) {
        start = range[1] ? Number(range[1]) : Math.max(0, file.buffer.length - Number(range[2]));
        end = range[1] && range[2] ? Math.min(Number(range[2]), end) : end;
      }
      if (!range || (!range[1] && !range[2]) || !Number.isSafeInteger(start) || !Number.isSafeInteger(end) || start > end || start >= file.buffer.length) {
        res.writeHead(416, { 'Content-Range': `bytes */${file.buffer.length}` });
        return res.end();
      }
      status = 206;
      headers['Content-Range'] = `bytes ${start}-${end}/${file.buffer.length}`;
    }
    headers['Content-Length'] = end - start + 1;
    res.writeHead(status, headers);
    return res.end(file.buffer.subarray(start, end + 1));
  }

  // Rutas con ID: GET/PUT/DELETE /api/knowledge/:id
  const kiIdMatch = pathName.match(/^\/api\/knowledge\/([^\/]+)$/);

  if (kiIdMatch) {
    const kiId = kiIdMatch[1];

    if (req.method === 'GET') {
      const item = await knowledgeService.getById(kiId);
      if (!item) return sendJson(res, 404, { error: 'Conocimiento no encontrado' });
      return sendJson(res, 200, item);
    }

    if (req.method === 'PUT') {
      const body = await parseBody(req);
      try {
        const updated = await knowledgeService.update(kiId, body);
        return sendJson(res, 200, { message: 'Conocimiento actualizado', item: updated });
      } catch (e) {
        return sendJson(res, e.message.includes('no encontrado') ? 404 : 400, { error: e.message });
      }
    }

    if (req.method === 'DELETE') {
      const deleted = await knowledgeService.remove(kiId);
      if (!deleted) return sendJson(res, 404, { error: 'Conocimiento no encontrado' });
      return sendJson(res, 200, { message: 'Conocimiento eliminado' });
    }
  }

  // POST /api/knowledge — crear nuevo item
  if (req.method === 'POST' && pathName === '/api/knowledge') {
    const body = await parseBody(req);
    const { titulo, tipo, categoriaITIL, servicio, descripcion, contenido, fuente, url: itemUrl, etiquetas, estado, metadatos } = body;

    if (!titulo || !contenido) return sendJson(res, 400, { error: 'Los campos titulo y contenido son requeridos' });

    try {
      const item = await knowledgeService.create({
        titulo, tipo: tipo || 'texto',
        categoriaITIL: categoriaITIL || 'General',
        servicio: servicio || '', descripcion: descripcion || '',
        contenido, fuente: fuente || '', url: itemUrl || '',
        etiquetas: Array.isArray(etiquetas) ? etiquetas : [],
        estado: estado || 'publicado',
        metadatos: metadatos || { procesadoPorIA: false },
        adjuntos: body.adjuntos
      });
      return sendJson(res, 201, { message: 'Conocimiento creado', item });
    } catch (e) {
      const status = e.message.includes('Ya existe') ? 409 : 400;
      return sendJson(res, status, { error: e.message });
    }
  }

  // GET /api/config — obtener configuracion dinamica (equipos y turnos)
  if (req.method === 'GET' && pathName === '/api/config') {
    const kb = getKnowledgeBase();
    return sendJson(res, 200, {
      teams: kb.teams || [],
      shifts: kb.shifts || [],
      sedes: kb.sedes || [],
      prioridades: kb.prioridades || [],
      tiposTicket: kb.tiposTicket || [],
      subcategoriasTecnicas: kb.subcategoriasTecnicas || []
    });
  }

  // POST /api/config/add - agregar opcion dinámica (stealth libre albedrío)
  if (req.method === 'POST' && pathName === '/api/config/add') {
    const body = await parseBody(req);
    const listKey = body.listKey;
    const value = body.value;
    if (!listKey || !value) return sendJson(res, 400, { error: 'listKey y value requeridos' });

    const kb = getKnowledgeBase();
    if (kb[listKey] && Array.isArray(kb[listKey])) {
      if (!kb[listKey].includes(value)) {
        kb[listKey].push(value);
        const fs = require('fs');
        const path = require('path');
        const kbPath = path.join(APP_ROOT, 'data', 'knowledge-base.json');
        fs.writeFileSync(kbPath, JSON.stringify(kb, null, 2), 'utf8');
      }
      return sendJson(res, 200, { success: true, updatedList: kb[listKey] });
    }
    return sendJson(res, 400, { error: 'Lista no encontrada' });
  }

  // POST /api/config/remove - eliminar opcion dinámica (stealth libre albedrío)
  if (req.method === 'POST' && pathName === '/api/config/remove') {
    const body = await parseBody(req);
    const listKey = body.listKey;
    const value = body.value;
    if (!listKey || !value) return sendJson(res, 400, { error: 'listKey y value requeridos' });

    const kb = getKnowledgeBase();
    if (kb[listKey] && Array.isArray(kb[listKey])) {
      const index = kb[listKey].indexOf(value);
      if (index > -1) {
        kb[listKey].splice(index, 1);
        const fs = require('fs');
        const path = require('path');
        const kbPath = path.join(APP_ROOT, 'data', 'knowledge-base.json');
        fs.writeFileSync(kbPath, JSON.stringify(kb, null, 2), 'utf8');
        return sendJson(res, 200, { success: true, updatedList: kb[listKey] });
      } else {
        return sendJson(res, 400, { error: 'Opción no encontrada en la lista' });
      }
    }
    return sendJson(res, 400, { error: 'Lista no encontrada' });
  }

  // POST /api/format-title - Formatea títulos usando IA
  if (req.method === 'POST' && pathName === '/api/format-title') {
    const body = await parseBody(req);
    const rawTitle = body.title || '';
    if (!rawTitle) return sendJson(res, 400, { error: 'Se requiere texto' });

    try {
      const { callGemini } = require('./geminiService');
      const prompt = `Actúa como un experto en soporte de TI ITIL.
Tu única tarea es transformar el siguiente texto en un título formal que cumpla estrictamente con este formato: [Area] + [Componente] + [Breve Descripción].
No agregues saludos, explicaciones, ni comillas. Solo devuelve el título formateado.

Texto del usuario: "${rawTitle}"`;
      const formatted = await callGemini([{ text: prompt }]);
      return sendJson(res, 200, { title: formatted.trim() });
    } catch(err) {
      return sendJson(res, 500, { error: err.message });
    }
  }

  return null; // Devuelve null si la ruta no fue manejada
}

module.exports = {
  handleApiRequest
};
