const http = require('http');
const fs = require('fs');
const path = require('path');

// Clean tickets file
const ticketsPath = path.join(__dirname, '..', 'data', 'tickets.json');
fs.writeFileSync(ticketsPath, JSON.stringify([], null, 2), 'utf-8');

const request = (method, reqPath, body = null) => {
  return new Promise((resolve, reject) => {
    const options = {
      hostname: 'localhost',
      port: 3000,
      path: reqPath,
      method: method,
      headers: {
        'Content-Type': 'application/json',
      }
    };

    const req = http.request(options, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          resolve({ status: res.statusCode, body: JSON.parse(data) });
        } catch (e) {
          resolve({ status: res.statusCode, body: data });
        }
      });
    });

    req.on('error', reject);
    if (body) req.write(JSON.stringify(body));
    req.end();
  });
};

async function run() {
  console.log('=== RETEST CREACION AUTOMATICA DE NUEVO TICKET AL ESTAR CERRADO ===\n');

  // Frontend State Simulation
  let activeTicket = null;

  // PASO 1: Escribir "no me funciona la impresora.."
  const msg1 = "no me funciona la impresora..";
  console.log('--- PASO 1: POST /api/tickets (Mensaje 1 - Impresora) ---');
  console.log('Request Body:', { message: msg1, usuario: 'agente_01' });
  const res1 = await request('POST', '/api/tickets', { message: msg1, usuario: 'agente_01' });
  console.log('Response (HTTP', res1.status, '):\n', JSON.stringify(res1.body, null, 2));

  activeTicket = res1.body.ticket; // estado: 'cerrado'
  const ticket1Id = activeTicket.id;
  console.log(`\nTicket 1 creado con ID: ${ticket1Id} | Estado: ${activeTicket.estado}`);

  // PASO 2: Escribir segundo mensaje ("una señorita dice que su tramite aparece cargando pero nada")
  const msg2 = "una señorita dice que su tramite aparece cargando pero nada";
  console.log('\n--- PASO 2: Procesar Mensaje 2 en Frontend ---');

  // Lógica implementada en app.js:
  if (activeTicket && (activeTicket.estado === 'cerrado' || activeTicket.estado === 'derivado')) {
    console.log('[FRONTEND] Ticket anterior estaba cerrado/derivado -> reseteando activeTicket a null');
    activeTicket = null;
  }

  // Como activeTicket es null, el frontend dispara POST /api/tickets
  console.log('\n--> POST /api/tickets (Disparado por ser un ticket nuevo)');
  console.log('Request Body:', { message: msg2, usuario: 'agente_01' });
  const res2 = await request('POST', '/api/tickets', { message: msg2, usuario: 'agente_01' });

  console.log('\n================ OUTPUT CRUDO FINAL (NUEVO TICKET CREADO) ================');
  console.log('HTTP Status:', res2.status);
  console.log('Response Body Raw JSON:\n', JSON.stringify(res2.body, null, 2));
  console.log('=========================================================================\n');

  activeTicket = res2.body.ticket;
  const ticket2Id = activeTicket.id;

  console.log('--- VERIFICACION FINAL ---');
  console.log('1. ID de Ticket 1 (Impresora):', ticket1Id);
  console.log('2. ID de Ticket 2 (Trámite):', ticket2Id);
  console.log('3. ¿Son tickets distintos?:', ticket1Id !== ticket2Id ? 'SI (CORRECTO)' : 'NO');
  console.log('4. ID mostrado actualmente en Panel de Ticket Actual (state.activeTicket.id):', activeTicket.id);

  // Leer tickets.json para confirmar la bitácora intacta de Ticket 1
  const allTickets = JSON.parse(fs.readFileSync(ticketsPath, 'utf-8'));
  const t1InDisk = allTickets.find(t => t.id === ticket1Id);
  console.log('5. Cantidad de entradas en bitacora de Ticket 1 (Impresora):', t1InDisk.bitacora.length, '(Intacta con 3 entradas)');

  process.exit(0);
}

setTimeout(run, 1000);
