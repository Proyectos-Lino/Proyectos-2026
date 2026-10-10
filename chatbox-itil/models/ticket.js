const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const storageAdapter = require('../lib/storageAdapter');
const { getKnowledgeBase } = require('../lib/chatFlowService');

class Ticket {
  constructor(data) {
    const kb = getKnowledgeBase();
    
    // Dynamic validation
    if (data.categoria && !kb.categories.includes(data.categoria)) {
      throw new Error(`Categoria invalida. Debe ser una de: ${kb.categories.join(', ')}`);
    }
    if (data.equipoAsignado && !kb.teams.includes(data.equipoAsignado)) {
      throw new Error(`Equipo asignado invalido. Debe ser uno de: ${kb.teams.join(', ')}`);
    }

    this.id = data.id || crypto.randomUUID();
    this.titulo = data.titulo || '[General] - Ticket de soporte';
    this.tipo = data.tipo || 'Incidente';
    this.prioridad = data.prioridad || 'P3';
    this.codigoPatrimonial = data.codigoPatrimonial || '';
    this.mensajeError = data.mensajeError || '';
    this.alcance = data.alcance || 'Puesto';
    this.subcategoriaTecnica = data.subcategoriaTecnica || '';
    this.categoria = data.categoria || 'otro';
    this.equipoAsignado = data.equipoAsignado || 'mesa_de_ayuda';
    this.reportadoPor = data.reportadoPor || 'usuario';
    this.atendidoPor = data.atendidoPor || 'sistema';
    let calculatedTurno = 'mañana';
    const hour = new Date().getHours();
    if (hour >= 6 && hour < 14) calculatedTurno = 'mañana';
    else if (hour >= 14 && hour < 22) calculatedTurno = 'tarde';
    else calculatedTurno = 'noche';
    
    this.turno = data.turno || calculatedTurno;
    
    // Compatibilidad con tickets anteriores a la unificación de estados.
    this.estado = data.estado === 'resuelto_pendiente' ? 'en_progreso' : (data.estado || 'abierto');
    
    // Append-only log
    this.bitacora = data.bitacora || [];
    
    // Metrics
    this.fechaCreacion = data.fechaCreacion || new Date().toISOString();
    this.tfrMinutos = data.tfrMinutos !== undefined ? data.tfrMinutos : null;
    this.ttrMinutos = data.ttrMinutos !== undefined ? data.ttrMinutos : null;
    this.resueltoEnPrimerContacto = data.resueltoEnPrimerContacto !== undefined ? data.resueltoEnPrimerContacto : null;
    this.requiereEscalamiento = data.requiereEscalamiento !== undefined ? data.requiereEscalamiento : false;
    this.flowState = data.flowState || null;
    this.sugerenciasMostradasIds = data.sugerenciasMostradasIds || [];
    this.respondidoPorIA = data.respondidoPorIA || false;
  }

  // Adds an entry to the bitacora and recalculates metrics
  addLog(autor, texto, minutosInvertidos = 0) {
    const now = new Date();
    
    this.bitacora.push({
      autor,
      texto,
      fecha: now.toISOString(),
      minutosInvertidos
    });

    // Update atendidoPor if an agent interacts
    if (autor && autor !== 'sistema' && autor !== this.reportadoPor) {
      this.atendidoPor = autor;
    }

    // Calculate TFR (Time to First Response)
    if (this.tfrMinutos === null && autor !== this.reportadoPor) {
      const creationDate = new Date(this.fechaCreacion);
      this.tfrMinutos = Math.round((now - creationDate) / 60000);
    }
  }

  close(autor, texto, minutosInvertidos = 0) {
    if (this.estado === 'cerrado') return;
    if (typeof texto !== 'string' || !texto.trim()) {
      throw new Error('Registra la solución aplicada antes de cerrar el ticket.');
    }
    if (!this.subcategoriaTecnica) {
      throw new Error('No se puede cerrar un ticket sin definir una subcategoria tecnica.');
    }
    
    if (texto) {
      this.addLog(autor, texto, minutosInvertidos);
    }

    if (this.bitacora.length === 0) {
      throw new Error('No se puede cerrar un ticket sin entradas en la bitacora.');
    }
    
    if (!this.subcategoriaTecnica) {
      throw new Error('No se puede cerrar un ticket sin definir una subcategoria tecnica.');
    }

    this.estado = 'cerrado';
    
    const now = new Date();
    const creationDate = new Date(this.fechaCreacion);
    this.ttrMinutos = Math.round((now - creationDate) / 60000);

    // Calculate FCR
    // Solo es true si no tuvo derivaciones y no requirio escalamiento/interacciones adicionales
    const derivaciones = this.bitacora.filter(b => b.texto.includes('Derivado a'));
    if (derivaciones.length === 0 && !this.requiereEscalamiento) {
      this.resueltoEnPrimerContacto = true;
    } else {
      this.resueltoEnPrimerContacto = false;
    }

    // Un ticket cerrado ya no requiere escalamiento
    this.requiereEscalamiento = false;
  }

  derivate(nuevoEquipo, autor, motivo) {
    const kb = getKnowledgeBase();
    if (!kb.teams.includes(nuevoEquipo)) {
      throw new Error(`Equipo de derivacion invalido. Debe ser uno de: ${kb.teams.join(', ')}`);
    }

    this.estado = 'derivado';
    this.addLog(autor, `Derivado a ${nuevoEquipo}. Motivo: ${motivo}`, 0);
    this.equipoAsignado = nuevoEquipo;
  }
}

function getAllTickets() {
  const data = storageAdapter.getTickets();
  return data.map(t => new Ticket(t));
}

function saveTicket(ticketInstance) {
  storageAdapter.updateTicket(ticketInstance);
}

module.exports = {
  Ticket,
  getAllTickets,
  saveTicket
};
