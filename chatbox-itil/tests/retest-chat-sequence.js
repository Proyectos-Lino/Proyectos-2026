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
  console.log("Checking API KEY:", process.env.GEMINI_API_KEY ? "ACTIVE" : "MISSING");
  
  // Message 1 (Ticket creation)
  const res1 = await postReq('/api/tickets', { message: 'Tengo un problema con una pc sobre la bd ayudam', usuario: 'userTest' });
  console.log("--- MESSAGE 1 JSON ---");
  console.log(JSON.stringify(res1, null, 2));

  const ticketId = res1.ticket.id;
  
  // Message 2 (Rejection 1)
  const res2 = await postReq(`/api/tickets/${ticketId}/log`, { texto: 'no me funciono, tienes otra?', autor: 'userTest' });
  console.log("--- MESSAGE 2 JSON (Rejection 1) ---");
  console.log(JSON.stringify(res2, null, 2));

  // Message 3 (Rejection 2)
  const res3 = await postReq(`/api/tickets/${ticketId}/log`, { texto: 'no me funciono, tienes otra?', autor: 'userTest' });
  console.log("--- MESSAGE 3 JSON (Rejection 2) ---");
  console.log(JSON.stringify(res3, null, 2));
}

run();
