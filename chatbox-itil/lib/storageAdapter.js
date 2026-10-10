const fs = require('fs');
const path = require('path');
const { APP_ROOT } = require('./paths');

const DATA_DIR = path.join(APP_ROOT, 'data');
const TICKETS_FILE = path.join(DATA_DIR, 'tickets.json');
const KNOWLEDGE_FILE = path.join(DATA_DIR, 'knowledge-items.json');
const ATTACHMENTS_DIR = path.join(DATA_DIR, 'knowledge-attachments');

// Ensure data directory exists
if (!fs.existsSync(DATA_DIR)) {
  fs.mkdirSync(DATA_DIR, { recursive: true });
}

// Interface (Docblock only since JS doesn't have native interfaces)
/**
 * IStorageAdapter
 * @method getTickets() : Array
 * @method saveTicket(ticket) : void
 * @method updateTicket(ticket) : void
 * @method getKnowledge() : Array
 * @method getKnowledgeById(id) : Object|null
 * @method saveKnowledge(item) : void
 * @method updateKnowledge(item) : void
 * @method deleteKnowledge(id) : boolean
 * @method searchKnowledge(query, filters) : Array
 * @method saveKnowledgeAttachment(id, buffer) : void
 * @method getKnowledgeAttachment(id) : Buffer|null
 * @method deleteKnowledgeAttachment(id) : void
 */

class JsonStorageAdapter {
  _attachmentPath(id) {
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id)) throw new Error('Identificador de adjunto inválido.');
    return path.join(ATTACHMENTS_DIR, id + '.bin');
  }

  saveKnowledgeAttachment(id, buffer) {
    const file = this._attachmentPath(id);
    fs.mkdirSync(ATTACHMENTS_DIR, { recursive: true });
    fs.writeFileSync(file, buffer, { flag: 'wx' });
  }

  getKnowledgeAttachment(id) {
    const file = this._attachmentPath(id);
    try { return fs.readFileSync(file); }
    catch (error) { if (error.code === 'ENOENT') return null; throw error; }
  }

  deleteKnowledgeAttachment(id) {
    const file = this._attachmentPath(id);
    if (fs.existsSync(file)) fs.unlinkSync(file);
  }

  constructor() {
    this._initFile(TICKETS_FILE, []);
    this._initFile(KNOWLEDGE_FILE, []);
  }

  _initFile(filePath, defaultData) {
    if (!fs.existsSync(filePath)) {
      fs.writeFileSync(filePath, JSON.stringify(defaultData, null, 2), 'utf-8');
    }
  }

  // ─────────────────────────────────────────
  // TICKETS
  // ─────────────────────────────────────────
  _readTickets() {
    try {
      const data = fs.readFileSync(TICKETS_FILE, 'utf-8');
      return JSON.parse(data);
    } catch (error) {
      console.error('Error reading tickets file:', error);
      return [];
    }
  }

  _writeTickets(tickets) {
    try {
      fs.writeFileSync(TICKETS_FILE, JSON.stringify(tickets, null, 2), 'utf-8');
    } catch (error) {
      console.error('Error writing tickets file:', error);
    }
  }

  getTickets() {
    return this._readTickets();
  }

  getTicketById(id) {
    const tickets = this._readTickets();
    return tickets.find(t => t.id === id);
  }

  saveTicket(ticket) {
    const tickets = this._readTickets();
    tickets.push(ticket);
    this._writeTickets(tickets);
  }

  updateTicket(ticket) {
    const tickets = this._readTickets();
    const index = tickets.findIndex(t => t.id === ticket.id);
    if (index !== -1) {
      tickets[index] = ticket;
      this._writeTickets(tickets);
    } else {
      this.saveTicket(ticket);
    }
  }

  // ─────────────────────────────────────────
  // KNOWLEDGE ITEMS
  // ─────────────────────────────────────────
  _readKnowledge() {
    try {
      const data = fs.readFileSync(KNOWLEDGE_FILE, 'utf-8');
      const parsed = JSON.parse(data);
      return Array.isArray(parsed) ? parsed : [];
    } catch (error) {
      console.error('Error reading knowledge file:', error);
      return [];
    }
  }

  _writeKnowledge(items) {
    try {
      fs.writeFileSync(KNOWLEDGE_FILE, JSON.stringify(items, null, 2), 'utf-8');
    } catch (error) {
      console.error('Error writing knowledge file:', error);
      throw error;
    }
  }

  /**
   * Returns all active knowledge items.
   * @param {boolean} includeInactive - If true, includes inactive items too.
   */
  getKnowledge(includeInactive = false) {
    const items = this._readKnowledge();
    if (includeInactive) return items;
    return items.filter(item => item.estado !== 'inactivo');
  }

  getKnowledgeById(id) {
    const items = this._readKnowledge();
    return items.find(item => item.id === id) || null;
  }

  saveKnowledge(item) {
    const items = this._readKnowledge();
    // Soft duplicate check by title (case-insensitive)
    const existing = items.find(i =>
      i.titulo && item.titulo &&
      i.titulo.toLowerCase() === item.titulo.toLowerCase() &&
      i.estado !== 'inactivo'
    );
    if (existing) {
      throw new Error(`Ya existe un conocimiento activo con el titulo: "${item.titulo}"`);
    }
    items.push(item);
    this._writeKnowledge(items);
    return item;
  }

  updateKnowledge(item) {
    const items = this._readKnowledge();
    const index = items.findIndex(i => i.id === item.id);
    if (index !== -1) {
      item.fechaActualizacion = new Date().toISOString();
      items[index] = item;
      this._writeKnowledge(items);
      return item;
    } else {
      throw new Error(`Conocimiento con id ${item.id} no encontrado`);
    }
  }

  /**
   * Soft delete: sets estado = 'inactivo'
   */
  deleteKnowledge(id) {
    const items = this._readKnowledge();
    const index = items.findIndex(i => i.id === id);
    if (index === -1) return false;
    items[index].estado = 'inactivo';
    items[index].fechaActualizacion = new Date().toISOString();
    this._writeKnowledge(items);
    return true;
  }

  /**
   * Basic keyword + filter search over knowledge items.
   * Returns items sorted by relevance score (descending).
   *
   * @param {string} query - Free text query
   * @param {Object} filters - { tipo, categoriaITIL, servicio, estado }
   */
  searchKnowledge(query = '', filters = {}) {
    let items = this.getKnowledge(); // only active by default

    // Apply structural filters first
    if (filters.tipo) {
      items = items.filter(i => i.tipo === filters.tipo);
    }
    if (filters.categoriaITIL) {
      items = items.filter(i => i.categoriaITIL === filters.categoriaITIL);
    }
    if (filters.servicio) {
      items = items.filter(i =>
        i.servicio && i.servicio.toLowerCase().includes(filters.servicio.toLowerCase())
      );
    }
    if (filters.estado) {
      items = items.filter(i => i.estado === filters.estado);
    }

    if (!query || query.trim() === '') return items;

    // Tokenize query (words > 2 chars)
    const tokens = query.toLowerCase()
      .split(/\s+/)
      .filter(w => w.length > 2);

    if (tokens.length === 0) return items;

    // Score each item
    const scored = items.map(item => {
      let score = 0;
      const searchable = [
        item.titulo || '',
        item.descripcion || '',
        item.contenido || '',
        item.servicio || '',
        (item.etiquetas || []).join(' ')
      ].join(' ').toLowerCase();

      tokens.forEach(token => {
        // Count occurrences for better ranking
        const matches = searchable.split(token).length - 1;
        score += matches;
        // Bonus for title matches
        if ((item.titulo || '').toLowerCase().includes(token)) score += 3;
        // Bonus for tag exact match
        if ((item.etiquetas || []).some(t => t.toLowerCase() === token)) score += 2;
      });

      return { item, score };
    });

    return scored
      .filter(s => s.score > 0)
      .sort((a, b) => b.score - a.score)
      .map(s => s.item);
  }
}

// Export a singleton instance of the adapter
module.exports = new JsonStorageAdapter();
