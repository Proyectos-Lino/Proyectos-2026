const http = require('http');

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
  console.log('=== RETEST DE ESCENARIO FRONTEND EN PUBLIC/APP.JS ===\n');

  // PASO 1: Escribir "no me funciona la impresora.." (crea ticket, auto-cierra por FCR)
  const msg1 = "no me funciona la impresora..";
  console.log('--- PASO 1: POST /api/tickets ---');
  console.log('Request Body:', { message: msg1, usuario: 'agente_01' });
  const res1 = await request('POST', '/api/tickets', { message: msg1, usuario: 'agente_01' });
  console.log('Response (HTTP', res1.status, '):\n', JSON.stringify(res1.body, null, 2));

  const ticketId = res1.body.ticket.id;
  console.log('\nTicket ID obtenido correctamente:', ticketId);

  // PASO 2: Escribir segundo mensaje ("una señorita dice que su tramite aparece cargando pero nada.. como lo soluciono?")
  const msg2 = "una señorita dice que su tramite aparece cargando pero nada.. como lo soluciono?";
  console.log(`\n--- PASO 2: POST /api/tickets/${ticketId}/log ---`);
  const reqBody2 = { autor: 'agente_01', texto: `Consulta adicional: ${msg2}`, minutosInvertidos: 1 };
  console.log('Request Body:', reqBody2);
  const res2 = await request('POST', `/api/tickets/${ticketId}/log`, reqBody2);

  console.log('\n================ OUTPUT CRUDO FINAL (RESPUESTA PASO 2 REQUERIDO) ================');
  console.log('HTTP Status:', res2.status);
  console.log('Response Body Raw JSON:\n', JSON.stringify(res2.body, null, 2));
  console.log('=================================================================================\n');

  // Desempaquetado como hace el frontend corregido:
  const data = res2.body;
  const activeTicket = data.ticket;
  console.log('Verificación de desempaquetado en frontend (state.activeTicket):');
  console.log('state.activeTicket.id:', activeTicket.id);
  console.log('state.activeTicket.estado:', activeTicket.estado);

  process.exit(0);
}

setTimeout(run, 1000);
