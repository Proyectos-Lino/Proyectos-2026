/**
 * knowledgeService.js
 * ─────────────────────────────────────────────────────────────────────────────
 * Capa de servicio para Gestión del Conocimiento Institucional.
 *
 * Abstracción KnowledgeProvider:
 *   Actualmente usa LocalKnowledgeProvider (storageAdapter + JSON).
 *   Para migrar a una BD o Gemini Notebook Enterprise, implementar la misma
 *   interfaz en un nuevo provider y cambiar la línea de instanciación al final.
 *
 * Interfaz KnowledgeProvider (contrato):
 *   search(query, filters, topK) -> Promise<{ items, fuentes }>
 *   create(data)                 -> Promise<KnowledgeItem>
 *   getById(id)                  -> Promise<KnowledgeItem|null>
 *   getAll(filters)              -> Promise<KnowledgeItem[]>
 *   update(id, data)             -> Promise<KnowledgeItem>
 *   remove(id)                   -> Promise<boolean>
 *   getStats()                   -> Promise<Object>
 *   recordUsage(id, ticketId)    -> Promise<void>
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * CATEGORIAS ITIL SOPORTADAS
 * ─────────────────────────────────────────────────────────────────────────────
 */
const crypto = require('crypto');
const storageAdapter = require('./storageAdapter');
const { prepareAttachments } = require('./knowledgeAttachments');

const ITIL_CATEGORIES = [
  'Gestion de Incidentes',
  'Gestion de Problemas',
  'Gestion de Solicitudes de Servicio',
  'Gestion de Cambios',
  'Gestion de Activos y Configuracion',
  'Gestion del Conocimiento',
  'Gestion de Niveles de Servicio',
  'Gestion de Disponibilidad',
  'Gestion de Continuidad',
  'Procedimientos de Soporte',
  'General'
];

const KNOWLEDGE_TYPES = ['documento', 'imagen', 'texto', 'procedimiento', 'solucion', 'video', 'enlace'];

const ESTADOS_VALIDOS = ['pendiente', 'procesando', 'pendiente_revision', 'publicado', 'error', 'inactivo'];

// ─────────────────────────────────────────────────────────────────────────────
// LOCAL KNOWLEDGE PROVIDER
// Implementa la interfaz usando storageAdapter (JSON local).
// Para reemplazar: crear DatabaseKnowledgeProvider con la misma interfaz.
// ─────────────────────────────────────────────────────────────────────────────
class LocalKnowledgeProvider {
  /**
   * Busca conocimiento relevante para una consulta.
   * Diseñado para ser sustituido por búsqueda semántica/embeddings sin
   * modificar el Asistente IA (solo cambia el provider).
   *
   * @param {string} query      - Texto de la consulta
   * @param {Object} filters    - Filtros adicionales
   * @param {number} topK       - Máximo de resultados (default 3)
   * @returns {{ items: Array, fuentes: Array }}
   */
  async search(query, filters = {}, topK = 3) {
    const results = storageAdapter.searchKnowledge(query, {
      ...filters,
      estado: 'publicado'
    });
    const items = results.slice(0, topK);
    const fuentes = items.map(item => ({
      id: item.id,
      titulo: item.titulo,
      tipo: item.tipo,
      categoriaITIL: item.categoriaITIL,
      servicio: item.servicio,
      fuente: item.fuente || item.titulo
    }));
    return { items, fuentes };
  }

  /**
   * Crea un nuevo item de conocimiento.
   * @param {Object} data - Datos del item (validados antes de llamar)
   * @returns {KnowledgeItem}
   */
  async create(data) {
    const now = new Date().toISOString();
    const item = {
      id: data.id || `ki-${crypto.randomUUID()}`,
      titulo: (data.titulo || '').trim(),
      tipo: data.tipo || 'texto',
      categoriaITIL: data.categoriaITIL || 'General',
      servicio: (data.servicio || '').trim(),
      descripcion: (data.descripcion || '').trim(),
      contenido: (data.contenido || '').trim(),
      fuente: (data.fuente || '').trim(),
      url: (data.url || '').trim(),
      etiquetas: Array.isArray(data.etiquetas) ? data.etiquetas : [],
      fechaCreacion: data.fechaCreacion || now,
      fechaActualizacion: now,
      estado: data.estado || 'publicado',
      metadatos: {
        procesadoPorIA: data.metadatos?.procesadoPorIA || false,
        tamanoArchivo: data.metadatos?.tamanoArchivo || 0,
        formatoOriginal: data.metadatos?.formatoOriginal || '',
        usoContador: 0,
        ultimoUso: null,
        trazabilidad: []   // { ticketId, consulta, fecha } — para auditoria futura
      }
    };

    this._validate(item);
    return this._saveWithAttachments(item, data.adjuntos, [], false);
  }

  async getById(id) {
    return storageAdapter.getKnowledgeById(id);
  }

  async getAttachment(itemId, attachmentId) {
    const item = storageAdapter.getKnowledgeById(itemId);
    if (!item || item.estado === 'inactivo') return null;
    const metadata = (item.adjuntos || []).find(file => file.id === attachmentId);
    if (!metadata) return null;
    const buffer = storageAdapter.getKnowledgeAttachment(metadata.id);
    return buffer ? { ...metadata, buffer } : null;
  }

  _saveWithAttachments(item, input, existing, update) {
    const prepared = prepareAttachments(input, existing);
    const written = [];
    try {
      for (const file of prepared) {
        if (file.buffer) {
          storageAdapter.saveKnowledgeAttachment(file.metadata.id, file.buffer);
          written.push(file.metadata.id);
        }
      }
      item.adjuntos = prepared.map(file => file.metadata);
      return update ? storageAdapter.updateKnowledge(item) : storageAdapter.saveKnowledge(item);
    } catch (error) {
      for (const id of written) storageAdapter.deleteKnowledgeAttachment(id);
      throw error;
    }
  }

  /**
   * @param {Object} filters - { tipo, categoriaITIL, servicio, estado, includeInactive }
   */
  async getAll(filters = {}) {
    const includeInactive = filters.includeInactive === true;
    let items = storageAdapter.getKnowledge(includeInactive);

    if (filters.tipo) items = items.filter(i => i.tipo === filters.tipo);
    if (filters.categoriaITIL) items = items.filter(i => i.categoriaITIL === filters.categoriaITIL);
    if (filters.servicio) items = items.filter(i =>
      i.servicio && i.servicio.toLowerCase().includes(filters.servicio.toLowerCase())
    );
    if (filters.estado) items = items.filter(i => i.estado === filters.estado);

    // Sort by fechaCreacion desc
    return items.sort((a, b) => new Date(b.fechaCreacion) - new Date(a.fechaCreacion));
  }

  async update(id, data) {
    const existing = storageAdapter.getKnowledgeById(id);
    if (!existing) throw new Error(`Conocimiento con id ${id} no encontrado`);

    const updated = {
      ...existing,
      ...data,
      id, // never overwrite id
      fechaActualizacion: new Date().toISOString()
    };
    this._validate(updated);
    return this._saveWithAttachments(updated, data.adjuntos, existing.adjuntos || [], true);
  }

  async remove(id) {
    return storageAdapter.deleteKnowledge(id);
  }

  /**
   * Estadísticas por tipo y estado para el dashboard de Knowledge.
   */
  async getStats() {
    const items = storageAdapter.getKnowledge(true); // include inactive
    const activos = items.filter(i => i.estado !== 'inactivo');

    const byTipo = {};
    KNOWLEDGE_TYPES.forEach(t => { byTipo[t] = 0; });
    activos.forEach(i => {
      if (byTipo[i.tipo] !== undefined) byTipo[i.tipo]++;
      else byTipo['texto'] = (byTipo['texto'] || 0) + 1;
    });

    const byEstado = {};
    ESTADOS_VALIDOS.forEach(e => { byEstado[e] = 0; });
    items.forEach(i => {
      if (byEstado[i.estado] !== undefined) byEstado[i.estado]++;
    });

    return {
      total: activos.length,
      byTipo,
      byEstado,
      categorias: ITIL_CATEGORIES
    };
  }

  /**
   * Registra que un item fue usado por el Asistente IA.
   * Incrementa usoContador y guarda trazabilidad.
   * @param {string} id       - ID del knowledge item
   * @param {string} ticketId - ID del ticket o null si es consulta general
   * @param {string} consulta - Texto de la consulta original
   */
  async recordUsage(id, ticketId = null, consulta = '') {
    try {
      const item = storageAdapter.getKnowledgeById(id);
      if (!item) return;

      item.metadatos = item.metadatos || {};
      item.metadatos.usoContador = (item.metadatos.usoContador || 0) + 1;
      item.metadatos.ultimoUso = new Date().toISOString();
      item.metadatos.trazabilidad = item.metadatos.trazabilidad || [];

      // Keep only last 50 usage records
      if (item.metadatos.trazabilidad.length >= 50) {
        item.metadatos.trazabilidad.shift();
      }
      item.metadatos.trazabilidad.push({
        ticketId: ticketId || 'general',
        consulta: consulta.substring(0, 200),
        fecha: new Date().toISOString()
      });

      storageAdapter.updateKnowledge(item);
    } catch (err) {
      // Non-fatal: log and continue
      console.error('[KnowledgeService] Error registering usage:', err.message);
    }
  }

  _validate(item) {
    if (!item.titulo || item.titulo.length < 3) {
      throw new Error('El titulo debe tener al menos 3 caracteres');
    }
    if (!KNOWLEDGE_TYPES.includes(item.tipo)) {
      throw new Error(`Tipo invalido. Debe ser uno de: ${KNOWLEDGE_TYPES.join(', ')}`);
    }
    if (!ITIL_CATEGORIES.includes(item.categoriaITIL)) {
      throw new Error(`Categoria ITIL invalida. Debe ser una de: ${ITIL_CATEGORIES.join(', ')}`);
    }
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// FUTURE PROVIDERS (stub - not yet implemented)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * DatabaseKnowledgeProvider
 * Para activar: implementar los mismos métodos que LocalKnowledgeProvider
 * conectando a PostgreSQL / SQL Server / MongoDB.
 *
 * Requiere configurar en .env:
 *   DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASS
 *
 * class DatabaseKnowledgeProvider { ... }
 */

/**
 * NotebookKnowledgeProvider
 * Para activar cuando se disponga de Gemini Notebook Enterprise:
 *   - Google Cloud Project ID
 *   - Notebook ID
 *   - Service Account con permisos
 *   - API endpoint de Vertex AI / Gemini Notebook
 *
 * class NotebookKnowledgeProvider { ... }
 */

// ─────────────────────────────────────────────────────────────────────────────
// ACTIVE PROVIDER SELECTION
// Para cambiar de proveedor: reemplazar esta línea sin modificar el Asistente IA.
// ─────────────────────────────────────────────────────────────────────────────
const activeProvider = new LocalKnowledgeProvider();

module.exports = {
  knowledgeService: activeProvider,
  ITIL_CATEGORIES,
  KNOWLEDGE_TYPES,
  ESTADOS_VALIDOS
};
