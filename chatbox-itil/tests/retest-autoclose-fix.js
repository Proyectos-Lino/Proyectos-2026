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
  console.log('=== RETEST FIX LOGICA DE AUTO-CIERRE EN TICKETS ESCALADOS ===\n');

  // PASO 1: Crear ticket no resuelto (queda abierto y escalado)
  const msg1 = "no funciona mi camara web";
  console.log('--- PASO 1: POST /api/tickets (Crear ticket sin match en KB) ---');
  console.log('Request Body:', { message: msg1, usuario: 'usuario_camara' });
  const res1 = await request('POST', '/api/tickets', { message: msg1, usuario: 'usuario_camara' });
  console.log('Response (HTTP', res1.status, '):\n', JSON.stringify(res1.body, null, 2));

  const ticketId = res1.body.ticket.id;

  // PASO 2: Mandar un segundo mensaje con un texto que SÍ tiene match KB ("horario")
  console.log('\n--- PASO 2: POST /api/tickets/' + ticketId + '/log (Enviar "horario" a ticket ya escalado) ---');
  const body2 = { autor: 'usuario_camara', texto: 'horario', minutosInvertidos: 0 };
  console.log('Request Body:', body2);
  const res2 = await request('POST', `/api/tickets/${ticketId}/log`, body2);

  console.log('\n================ OUTPUT CRUDO FINAL (RESPUESTA PASO 2 REQUERIDO) ================');
  console.log('HTTP Status:', res2.status);
  console.log('Response Body Raw JSON:\n', JSON.stringify(res2.body, null, 2));
  console.log('=================================================================================\n');

  process.exit(0);
}

setTimeout(run, 1000);
