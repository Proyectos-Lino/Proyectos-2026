const fs = require('fs');
const path = require('path');

const ticketsFile = path.join(__dirname, '../data/tickets.json');
let tickets = JSON.parse(fs.readFileSync(ticketsFile, 'utf8'));

// Filter out old closed tickets for clean test
tickets = tickets.filter(t => t.estado !== 'cerrado');

const newClosed = [];
for (let i = 1; i <= 5; i++) {
  newClosed.push({
    "id": `closed-test-${i}`,
    "categoria": "incidencia_tecnica",
    "equipoAsignado": "area_tecnica",
    "reportadoPor": "user_test",
    "atendidoPor": "sistema",
    "turno": "mañana",
    "estado": "cerrado",
    "bitacora": [
      {
        "autor": "user_test",
        "texto": `Consulta: tengo un problema con una pc sobre la bd ayudam error ${i}`,
        "fecha": `2026-09-0${i}T10:00:00.000Z`,
        "minutosInvertidos": 0
      },
      {
        "autor": "sistema",
        "texto": `Respuesta: Solución ${i}: ${['Reiniciar equipo','Revisar cable de red','Desactivar VPN','Borrar cache de DNS','Actualizar credenciales'][i-1]}`,
        "fecha": `2026-09-0${i}T10:05:00.000Z`,
        "minutosInvertidos": 5
      }
    ],
    "fechaCreacion": `2026-09-0${i}T10:00:00.000Z`,
    "tfrMinutos": 5,
    "ttrMinutos": 5,
    "resueltoEnPrimerContacto": true,
    "requiereEscalamiento": false,
    "flowState": null,
    "sugerenciasMostradasIds": []
  });
}

tickets = [...newClosed, ...tickets];
fs.writeFileSync(ticketsFile, JSON.stringify(tickets, null, 2));
console.log('5 closed tickets injected.');
