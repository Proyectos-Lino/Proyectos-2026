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
  console.log('=== RETEST CIERRE MANUAL SOBRE TICKET ESCALADO ===\n');

  // PASO 1: Crear ticket no resuelto (queda abierto y escalado)
  const msg1 = "no funciona mi camara web";
  console.log('--- PASO 1: POST /api/tickets (Crear ticket sin match en KB) ---');
  const res1 = await request('POST', '/api/tickets', { message: msg1, usuario: 'usuario_camara' });
  const ticketId = res1.body.ticket.id;
  console.log('Ticket ID Creado:', ticketId);

  // PASO 2: Mandar segundo mensaje ("horario")
  console.log('\n--- PASO 2: POST /api/tickets/' + ticketId + '/log (Enviar "horario") ---');
  await request('POST', `/api/tickets/${ticketId}/log`, { autor: 'usuario_camara', texto: 'horario', minutosInvertidos: 0 });

  // PASO 3: Cierre manual por agente humano
  console.log('\n--- PASO 3: POST /api/tickets/' + ticketId + '/close (Cierre manual por agente) ---');
  const bodyClose = { autor: "agente_ti_01", texto: "se cambio el cable usb de la camara", minutosInvertidos: 15 };
  console.log('Request Body:', bodyClose);
  const resClose = await request('POST', `/api/tickets/${ticketId}/close`, bodyClose);

  console.log('\n================ OUTPUT CRUDO FINAL (PASO 3 REQUERIDO) ================');
  console.log('HTTP Status:', resClose.status);
  console.log('Response Body Raw JSON:\n', JSON.stringify(resClose.body, null, 2));
  console.log('=========================================================================\n');

  process.exit(0);
}

setTimeout(run, 1000);
