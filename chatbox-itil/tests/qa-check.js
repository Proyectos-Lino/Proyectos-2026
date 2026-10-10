const fs = require('fs');
const path = require('path');
const { Ticket } = require('../models/ticket');

console.log('--- INICIANDO QA BACKEND (Fase 1.5) ---');

// 1. Zero emojis check
const checkEmojis = (filePath) => {
  const content = fs.readFileSync(filePath, 'utf-8');
  // Simple emoji regex check
  const emojiRegex = /[\u{1F600}-\u{1F64F}\u{1F300}-\u{1F5FF}\u{1F680}-\u{1F6FF}\u{1F700}-\u{1F77F}\u{1F780}-\u{1F7FF}\u{1F800}-\u{1F8FF}\u{1F900}-\u{1F9FF}\u{1FA00}-\u{1FA6F}\u{1FA70}-\u{1FAFF}\u{2600}-\u{26FF}\u{2700}-\u{27BF}]/gu;
  const match = content.match(emojiRegex);
  if (match) {
    throw new Error(`¡ALERTA! Emojis encontrados en ${filePath}: ${match.join(', ')}`);
  }
};

try {
  checkEmojis(path.join(__dirname, '../server.js'));
  checkEmojis(path.join(__dirname, '../models/ticket.js'));
  checkEmojis(path.join(__dirname, '../lib/storageAdapter.js'));
  console.log('[OK] 1. Cero emojis verificados en backend.');
} catch (e) {
  console.error(e.message);
  process.exit(1);
}

// 2. Portability / Dynamic validation check
try {
  // Create an alt KB temporarily
  const altKbPath = path.join(__dirname, '../config/knowledge-base.json');
  const originalKb = fs.readFileSync(altKbPath, 'utf-8');
  
  const altKb = {
    categories: ['consulta_indecopi', 'reclamo_indecopi'],
    teams: ['mesa_ayuda_indecopi', 'especialista'],
    intents: [],
    flows: []
  };
  fs.writeFileSync(altKbPath, JSON.stringify(altKb));

  try {
    const t = new Ticket({ categoria: 'consulta_indecopi', equipoAsignado: 'especialista' });
    console.log('[OK] 2. Portabilidad: Modelo funciona con KB alternativo de otra institucion.');
  } catch (err) {
    console.error('Fallo portabilidad:', err);
    process.exit(1);
  } finally {
    // Restore original
    fs.writeFileSync(altKbPath, originalKb);
  }
} catch (e) {
  console.error(e.message);
  process.exit(1);
}

// 3. Calculation of metrics (TFR, TTR, FCR)
try {
  // We need to simulate time passing for TFR and TTR. Since they use Date.now internally, 
  // we will hack the fechaCreacion to be in the past.
  const t = new Ticket({ categoria: 'tramites', equipoAsignado: 'mesa_de_ayuda', reportadoPor: 'usuario123' });
  
  // Set creation 10 minutes ago
  const past = new Date(Date.now() - 10 * 60000);
  t.fechaCreacion = past.toISOString();
  
  // Agente responde (TFR)
  t.addLog('agente1', 'Estamos revisando el caso', 5);
  
  if (t.tfrMinutos < 9 || t.tfrMinutos > 11) {
    throw new Error(`TFR incorrecto: esperado ~10, obtenido ${t.tfrMinutos}`);
  }
  
  // Cerramos (TTR and FCR)
  t.close('agente1', 'Resuelto', 10);
  
  if (t.ttrMinutos < 9 || t.ttrMinutos > 11) {
    throw new Error(`TTR incorrecto: esperado ~10, obtenido ${t.ttrMinutos}`);
  }
  
  if (t.resueltoEnPrimerContacto !== true) {
    throw new Error(`FCR incorrecto: esperado true, obtenido ${t.resueltoEnPrimerContacto}`);
  }
  
  console.log('[OK] 3. Metricas (TFR, TTR, FCR) calculadas correctamente sin intervencion manual.');
} catch (e) {
  console.error(e.message);
  process.exit(1);
}

console.log('--- QA BACKEND SUPERADO EXITOSAMENTE ---');
