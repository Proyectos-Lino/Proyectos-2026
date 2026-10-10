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
  console.log('=== RETEST CIERRE MANUAL SOBRE EXACTAMENTE TICKET d1770813-61a2-4358-87f0-b2f2f45f78c6 ===\n');

  const ticketId = 'd1770813-61a2-4358-87f0-b2f2f45f78c6';
  const bodyClose = { autor: "agente_ti_01", texto: "se cambio el cable usb de la camara", minutosInvertidos: 15 };

  console.log(`--- POST /api/tickets/${ticketId}/close ---`);
  console.log('Request Body:', bodyClose);
  const resClose = await request('POST', `/api/tickets/${ticketId}/close`, bodyClose);

  console.log('\n================ OUTPUT CRUDO FINAL (RESPUESTA SOLICITADA) ================');
  console.log('HTTP Status:', resClose.status);
  console.log('Response Body Raw JSON:\n', JSON.stringify(resClose.body, null, 2));
  console.log('===========================================================================\n');

  process.exit(0);
}

setTimeout(run, 1000);
