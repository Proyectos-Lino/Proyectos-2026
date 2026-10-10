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
  console.log('=== RETEST PUNTOS 1, 2 Y 3 ===\n');

  // -------------------------------------------------------------
  // PUNTO 1: Reclasificacion continua en escenario real (Bug 1)
  // -------------------------------------------------------------
  console.log('--- PUNTO 1.1: Crear Ticket sin Match en KB (Escalado Inicial) ---');
  const msgInicial = "no funciona mi camara web";
  console.log('Request Body:', { message: msgInicial, usuario: 'usuario_camara' });
  const resPaso1 = await request('POST', '/api/tickets', { message: msgInicial, usuario: 'usuario_camara' });
  console.log('Response (HTTP', resPaso1.status, '):\n', JSON.stringify(resPaso1.body, null, 2));

  const ticketId = resPaso1.body.ticket.id;

  console.log('\n--- PUNTO 1.2: Segundo Mensaje al Ticket Abierto con Texto con Match KB ("horario") ---');
  const bodyLog = { autor: 'usuario_camara', texto: 'horario', minutosInvertidos: 0 };
  console.log('Request Body (POST /api/tickets/' + ticketId + '/log):', bodyLog);
  const resPaso2 = await request('POST', `/api/tickets/${ticketId}/log`, bodyLog);
  console.log('\n================ OUTPUT CRUDO FINAL (PUNTO 1 REQUERIDO) ================');
  console.log('HTTP Status:', resPaso2.status);
  console.log('Response Body Raw JSON:\n', JSON.stringify(resPaso2.body, null, 2));
  console.log('=========================================================================\n');


  // -------------------------------------------------------------
  // PUNTO 2: Probar GET /api/metrics
  // -------------------------------------------------------------
  console.log('\n--- PUNTO 2: GET /api/metrics ---');
  const resMetrics = await request('GET', '/api/metrics');
  console.log('\n================ OUTPUT CRUDO FINAL (PUNTO 2 REQUERIDO) ================');
  console.log('HTTP Status:', resMetrics.status);
  console.log('Response Body Raw JSON:\n', JSON.stringify(resMetrics.body, null, 2));
  console.log('=========================================================================\n');


  // -------------------------------------------------------------
  // PUNTO 3: Cargar y verificar contenido real en knowledge-base.json
  // -------------------------------------------------------------
  console.log('\n--- PUNTO 3: POST /api/kb/reload (Verificar KB cargada) ---');
  const resKb = await request('POST', '/api/kb/reload');
  console.log('\n================ OUTPUT CRUDO FINAL (PUNTO 3 REQUERIDO) ================');
  console.log('HTTP Status:', resKb.status);
  console.log('Response Body Raw JSON:\n', JSON.stringify(resKb.body, null, 2));
  console.log('=========================================================================\n');

  process.exit(0);
}

setTimeout(run, 1000);
