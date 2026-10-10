// Retest de reestructuracion: verifica los 5 casos del plan
const http = require('http');

function req(method, path, body) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null;
    const opts = {
      hostname: 'localhost', port: 3000, path, method,
      headers: { 'Content-Type': 'application/json', ...(data ? { 'Content-Length': Buffer.byteLength(data) } : {}) }
    };
    const r = http.request(opts, res => {
      let raw = '';
      res.on('data', c => raw += c);
      res.on('end', () => { try { resolve(JSON.parse(raw)); } catch(e) { resolve(raw); } });
    });
    r.on('error', reject);
    if (data) r.write(data);
    r.end();
  });
}

async function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

async function run() {
  // ===== CASO A: Empleado reporta sin match KB - NO debe invocar Gemini =====
  console.log('\n========== CASO A: Empleado reporta sin match KB ==========');
  const resA = await req('POST', '/api/tickets', { message: 'tengo un problema raro con una pc sobre la bd ayudam', usuario: 'empleado_test' });
  const ticketId = resA.ticket.id;
  console.log('Status:', resA.ticket.estado);
  console.log('respondidoPorIA:', resA.ticket.respondidoPorIA); // Debe ser undefined/false
  console.log('requiereEscalamiento:', resA.ticket.requiereEscalamiento); // Debe ser true
  console.log('response:', resA.response);
  console.log('PASS A:', !resA.ticket.respondidoPorIA && resA.ticket.requiereEscalamiento ? 'SIN GEMINI ✓' : 'FALLO - Gemini invocado!');

  await sleep(1000);

  // ===== CASO B: Panel TI - Primera busqueda con IA (3 candidatos) =====
  console.log('\n========== CASO B: POST /buscar-solucion - Primera llamada ==========');
  const resB = await req('POST', `/api/tickets/${ticketId}/buscar-solucion`, { excluirIds: [] });
  console.log(JSON.stringify(resB, null, 2));
  console.log('PASS B - agotado:', resB.agotado);
  console.log('PASS B - usadosIds count:', resB.usadosIds ? resB.usadosIds.length : 0);

  await sleep(1000);

  // ===== CASO C: Segunda busqueda con excluirIds acumulados (candidatos distintos) =====
  console.log('\n========== CASO C: POST /buscar-solucion - Con excluirIds del paso B ==========');
  const excluirC = resB.usadosIds || [];
  const resC = await req('POST', `/api/tickets/${ticketId}/buscar-solucion`, { excluirIds: excluirC });
  console.log(JSON.stringify(resC, null, 2));
  // Verificar que ninguno de los IDs de C coincide con los de B
  const overlap = (resC.usadosIds || []).some(id => excluirC.includes(id));
  console.log('PASS C - sin solapamiento con B:', !overlap ? '✓' : 'FALLO - hay solapamiento!');

  await sleep(1000);

  // ===== CASO D: Agotar candidatos =====
  console.log('\n========== CASO D: POST /buscar-solucion - Agotado ==========');
  const excluirD = [...new Set([...(resB.usadosIds || []), ...(resC.usadosIds || [])])];
  const resD = await req('POST', `/api/tickets/${ticketId}/buscar-solucion`, { excluirIds: excluirD });
  console.log(JSON.stringify(resD, null, 2));
  console.log('PASS D - agotado:', resD.agotado === true ? '✓' : 'FALLO - deberia ser agotado!');

  await sleep(500);

  // ===== CASO E: Cerrar el ticket con solucion y verificar metricas =====
  console.log('\n========== CASO E: Cerrar ticket y verificar metricas ==========');
  await req('POST', `/api/tickets/${ticketId}/log`, {
    autor: 'tecnico_01',
    texto: 'Agente aplico solucion: flush de DNS y reinicio de equipo. Problema resuelto.',
    minutosInvertidos: 15
  });
  const resClose = await req('POST', `/api/tickets/${ticketId}/close`, {
    autor: 'tecnico_01',
    texto: 'Ticket cerrado tras aplicar solucion de caso similar.',
    minutosInvertidos: 5
  });
  console.log('Estado tras cierre:', resClose.ticket.estado);
  console.log('TTR calculado (minutos):', resClose.ticket.ttrMinutos);
  console.log('FCR:', resClose.ticket.resueltoEnPrimerContacto);
  console.log('PASS E - estado cerrado:', resClose.ticket.estado === 'cerrado' ? '✓' : 'FALLO');

  const metrics = await req('GET', '/api/metrics', null);
  console.log('\nMetricas globales:');
  console.log('  totalTickets:', metrics.totalTickets);
  console.log('  ttrPromedioMinutos:', metrics.ttrPromedioMinutos);
  console.log('  fcrPorcentaje:', metrics.fcrPorcentaje);
}

run().catch(console.error);
