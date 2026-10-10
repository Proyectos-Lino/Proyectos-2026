const fs = require('fs');
const path = require('path');
const { Ticket, getAllTickets } = require('../models/ticket');

console.log('--- INICIANDO QA FINAL (Fase 3) ---');

// 1. Zero Emojis check in public/ and models/ and lib/
function checkEmojisDir(dirPath) {
  const files = fs.readdirSync(dirPath);
  for (const file of files) {
    const fullPath = path.join(dirPath, file);
    if (fs.statSync(fullPath).isDirectory()) {
      checkEmojisDir(fullPath);
    } else if (file.endsWith('.js') || file.endsWith('.html') || file.endsWith('.css') || file.endsWith('.json')) {
      const content = fs.readFileSync(fullPath, 'utf-8');
      const emojiRegex = /[\u{1F600}-\u{1F64F}\u{1F300}-\u{1F5FF}\u{1F680}-\u{1F6FF}\u{1F700}-\u{1F77F}\u{1F780}-\u{1F7FF}\u{1F800}-\u{1F8FF}\u{1F900}-\u{1F9FF}\u{1FA00}-\u{1FA6F}\u{1FA70}-\u{1FAFF}\u{2600}-\u{26FF}\u{2700}-\u{27BF}]/gu;
      const match = content.match(emojiRegex);
      if (match) {
        throw new Error(`¡ALERTA! Emojis encontrados en ${fullPath}`);
      }
    }
  }
}

try {
  checkEmojisDir(path.join(__dirname, '..', 'public'));
  checkEmojisDir(path.join(__dirname, '..', 'models'));
  checkEmojisDir(path.join(__dirname, '..', 'lib'));
  checkEmojisDir(path.join(__dirname, '..', 'config'));
  console.log('[OK] 1. Cero emojis verificados globalmente.');
} catch(e) {
  console.error(e.message);
  process.exit(1);
}

// 2. Simular Derivacion y conservacion de bitacora
try {
  const t = new Ticket({ categoria: 'incidencia_tecnica', equipoAsignado: 'mesa_de_ayuda', reportadoPor: 'usuario' });
  t.addLog('agente1', 'Intentando resolver problema de red', 10);
  
  // Derivamos
  t.derivate('area_tecnica', 'agente1', 'Requiere soporte de nivel 2');
  
  // Area tecnica recibe y agrega
  t.addLog('agente2', 'Revisando logs de router', 20);
  
  t.close('agente2', 'Puerto reseteado. Funciona.', 5);
  
  if (t.bitacora.length !== 4) { // log1 + derivacion + log2 + cierre
    throw new Error(`La bitacora perdio entradas: tiene ${t.bitacora.length}, se esperaban 4`);
  }
  
  if (t.resueltoEnPrimerContacto === true) {
    throw new Error('FCR deberia ser false tras derivacion.');
  }

  console.log('[OK] 2. Traspaso de turno/derivacion comprobado (bitacora intacta).');
} catch(e) {
  console.error(e.message);
  process.exit(1);
}

// 3. Simular un flow guiado
try {
  // First step of flow
  const t = new Ticket({ categoria: 'costos', equipoAsignado: 'mesa_de_ayuda', reportadoPor: 'ciudadano' });
  t.addLog('sistema', 'Por favor, indique el valor del bien en la moneda local.', 0);
  t.requiereEscalamiento = true;

  // Second step simulated via chat by user
  t.addLog('ciudadano', '150000', 0);
  
  // System responds (mocking controller logic)
  t.addLog('sistema', 'Indique el porcentaje de tasa aplicable.', 0);
  
  t.addLog('ciudadano', '5', 0);
  t.addLog('sistema', 'El costo estimado de la compraventa es de: 7500', 0);
  
  t.close('sistema', 'Resuelto automaticamente por KB en flow', 0);

  if (t.bitacora.length < 5) {
    throw new Error('Flow guiado incompleto en bitacora.');
  }
  
  console.log('[OK] 3. Flow guiado (multiples pasos) simulado y conservado.');
} catch(e) {
  console.error(e.message);
  process.exit(1);
}

console.log('--- QA FINAL SUPERADO EXITOSAMENTE ---');
