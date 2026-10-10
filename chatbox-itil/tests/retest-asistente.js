const http = require('http');

const PORT = 3000;

function postData(path, data) {
  return new Promise((resolve, reject) => {
    const req = http.request({
      hostname: 'localhost',
      port: PORT,
      path: path,
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      }
    }, (res) => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => resolve(JSON.parse(body)));
    });
    req.on('error', reject);
    req.write(JSON.stringify(data));
    req.end();
  });
}

async function runTest() {
  console.log("========== A: Pregunta de estadisticas (sin ticket en foco) ==========");
  const resA = await postData('/api/asistente-tecnico/mensaje', {
    mensaje: "¿cuantos tickets tenemos hoy?",
    ticketId: null,
    excluirIds: []
  });
  console.log(JSON.stringify(resA, null, 2));

  console.log("\n========== B: Solucion parecida (con ticket en foco) ==========");
  const resB = await postData('/api/asistente-tecnico/mensaje', {
    mensaje: "dame una solucion parecida",
    ticketId: "open-test-1", // Suponemos que existe, de lo contrario buscara en KB general
    excluirIds: []
  });
  console.log(JSON.stringify(resB, null, 2));

  console.log("\n========== C: Pregunta sin datos en el sistema ==========");
  const resC = await postData('/api/asistente-tecnico/mensaje', {
    mensaje: "¿cual sera el clima mañana?",
    ticketId: null,
    excluirIds: []
  });
  console.log(JSON.stringify(resC, null, 2));
}

runTest().catch(console.error);
