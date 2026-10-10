const http = require('http');
const fs = require('fs');
const path = require('path');

// Clean up tickets file before running test sequence
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
  console.log('=== PRUEBA DE BUSQUEDA DE INCIDENCIAS SIMILAR EN ORDEN EXACTO ===\n');

  // PASO 1: Crear un ticket
  const msg1 = "la impresora de mesa de partes no imprime, dice papel atascado";
  console.log('--- PASO 1: POST /api/tickets ---');
  console.log('Request Body:', { message: msg1, usuario: 'agente_01' });
  const res1 = await request('POST', '/api/tickets', { message: msg1, usuario: 'agente_01' });
  console.log('Response (HTTP', res1.status, '):', JSON.stringify(res1.body, null, 2));

  const ticket1Id = res1.body.ticket.id;

  // PASO 2: Cerrarlo con /close
  console.log('\n--- PASO 2: POST /api/tickets/' + ticket1Id + '/close ---');
  const closeBody = { autor: "agente_01", texto: "se destrabó el rodillo de arrastre", minutosInvertidos: 10 };
  console.log('Request Body:', closeBody);
  const res2 = await request('POST', `/api/tickets/${ticket1Id}/close`, closeBody);
  console.log('Response (HTTP', res2.status, '):', JSON.stringify(res2.body, null, 2));

  // PASO 3: Crear un SEGUNDO ticket nuevo con un mensaje distinto pero relacionado
  const msg2 = "la impresora del área técnica no imprime nada";
  console.log('\n--- PASO 3: POST /api/tickets ---');
  console.log('Request Body:', { message: msg2, usuario: 'agente_02' });
  const res3 = await request('POST', '/api/tickets', { message: msg2, usuario: 'agente_02' });
  console.log('\n================ OUTPUT CRUDO FINAL (PASO 3 / PASO 4 REQUERIDO) ================');
  console.log('HTTP Status:', res3.status);
  console.log('Response Body Raw JSON:\n', JSON.stringify(res3.body, null, 2));
  console.log('=================================================================================\n');

  process.exit(0);
}

setTimeout(run, 1000);
