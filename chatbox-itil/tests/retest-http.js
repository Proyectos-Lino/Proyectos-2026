const http = require('http');

const request = (method, path, body = null) => {
  return new Promise((resolve, reject) => {
    const options = {
      hostname: 'localhost',
      port: 3000,
      path: path,
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
  console.log('--- INICIANDO RETEST DE API ---');
  
  // 1. Similar closed tickets search (Bug 3)
  console.log('\n[PRUEBA] Busqueda de similares cuando no hay match en KB');
  const res1 = await request('POST', '/api/tickets', { message: 'tengo un problema con mi impresora, no imprime nada', usuario: 'usuario1' });
  console.log('--> POST /api/tickets');
  console.log('Request Body:', { message: 'tengo un problema con mi impresora, no imprime nada', usuario: 'usuario1' });
  console.log('Response:', JSON.stringify(res1.body, null, 2));

  // 2. Flow de varios pasos y conversacion continua (Bug 1 & 2)
  console.log('\n[PRUEBA] Flow de multiples pasos (costo de compraventa)');
  
  // Paso 1: Iniciar flow
  const res2 = await request('POST', '/api/tickets', { message: 'quiero saber el costo de compraventa', usuario: 'usuario2' });
  console.log('--> POST /api/tickets (Inicio Flow)');
  console.log('Request Body:', { message: 'quiero saber el costo de compraventa', usuario: 'usuario2' });
  console.log('Response (Paso 1):', JSON.stringify(res2.body, null, 2));

  const ticketId = res2.body.ticket.id;

  // Paso 2: Usuario responde (Ruta de conversacion continua)
  console.log(`\n--> POST /api/tickets/${ticketId}/log (Continuar Flow - Respuesta 1)`);
  const reqBody3 = { autor: 'usuario2', texto: '150000' };
  console.log('Request Body:', reqBody3);
  const res3 = await request('POST', `/api/tickets/${ticketId}/log`, reqBody3);
  console.log('Response (Paso 2):', JSON.stringify(res3.body, null, 2));

  // Paso 3: Usuario responde (Final flow)
  console.log(`\n--> POST /api/tickets/${ticketId}/log (Continuar Flow - Respuesta 2)`);
  const reqBody4 = { autor: 'usuario2', texto: '5' };
  console.log('Request Body:', reqBody4);
  const res4 = await request('POST', `/api/tickets/${ticketId}/log`, reqBody4);
  console.log('Response (Paso 3):', JSON.stringify(res4.body, null, 2));

  // 3. TFR calculation check (Bug 4)
  console.log('\n[PRUEBA] Verificacion de calculo TFR');
  const tfr = res4.body.ticket.tfrMinutos;
  console.log(`TFR calculado para el ticket resuelto por bot: ${tfr} minutos (esperado: no nulo).`);

  console.log('\n--- RETEST COMPLETADO ---');
}

// Ensure the server is ready before running
setTimeout(run, 1000);
