const http = require('http');

function postReq(path, data) {
  return new Promise((resolve, reject) => {
    const req = http.request({
      hostname: 'localhost', port: 3000, path, method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }, res => {
      let b=''; res.on('data', c=>b+=c); res.on('end', ()=>resolve(JSON.parse(b)));
    });
    req.on('error', reject);
    req.write(JSON.stringify(data));
    req.end();
  });
}

async function run() {
  console.log("Creating tickets...");
  // 1. Reseteo de clave (resuelto)
  const t1 = (await postReq('/api/tickets', { message: 'como reseteo mi clave', userId: 'user1' })).ticket;
  console.log("T1:", t1.id);
  // Simular cierre por agente
  await postReq('/api/agent/tickets', { ticketId: t1.id, action: 'cerrar', autor: 'agente1', texto: 'Se brindó enlace', minutos: 5 });
  // Simular encuesta para T1
  await postReq('/api/surveys', { ticketId: t1.id, answers: { q1: 5, q2: 5, q3: 4, q4: 5, q5: 5, q6: 4 }, comments: 'Buen servicio' });

  // 2. VPN (escalado, no resuelto)
  const t2 = (await postReq('/api/tickets', { message: 'no puedo conectar a la vpn me sale error', userId: 'user2' })).ticket;
  console.log("T2:", t2.id);
  await postReq('/api/agent/tickets', { ticketId: t2.id, action: 'derivar', autor: 'agente1', texto: 'Se requiere validar token en firewall', minutos: 10, nuevoEquipo: 'area_tecnica' });

  // 3. Hardware urgencia alta (falla)
  const t3 = (await postReq('/api/tickets', { message: 'urgente mi pc de escritorio se apago de la nada y no prende', userId: 'user3' })).ticket;
  console.log("T3:", t3.id);
  
  // 4. Consulta general (cerrado)
  const t4 = (await postReq('/api/tickets', { message: 'buenos dias', userId: 'user4' })).ticket;
  console.log("T4:", t4.id);
  await postReq('/api/agent/tickets', { ticketId: t4.id, action: 'cerrar', autor: 'agente2', texto: 'Saludo correspondido', minutos: 1 });

  console.log("Data setup complete.");
}

run();
