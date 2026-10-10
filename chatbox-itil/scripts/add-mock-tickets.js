const { Ticket, saveTicket } = require('./models/ticket');

const tickets = [
  {
    categoria: 'incidencia_tecnica',
    reportadoPor: 'jgarcia',
    equipoAsignado: 'area_tecnica',
    turno: 'mañana',
    mensajes: ['No puedo acceder a mi correo institucional.', 'Revisé la contraseña y está correcta, me sale "Cuenta bloqueada".']
  },
  {
    categoria: 'otro',
    reportadoPor: 'mlopez',
    equipoAsignado: 'mesa_de_ayuda',
    turno: 'tarde',
    mensajes: ['Necesito que instalen AutoCAD en el equipo de la oficina 3.']
  },
  {
    categoria: 'incidencia_tecnica',
    reportadoPor: 'atorres',
    equipoAsignado: 'area_tecnica',
    turno: 'noche',
    mensajes: ['El sistema de contabilidad está muy lento, tarda minutos en guardar un registro.']
  },
  {
    categoria: 'reclamo',
    reportadoPor: 'sruiz',
    equipoAsignado: 'supervisor',
    turno: 'mañana',
    mensajes: ['Llevo 3 días esperando el reemplazo de mi monitor y nadie se comunica.']
  },
  {
    categoria: 'otro',
    reportadoPor: 'pcastillo',
    equipoAsignado: 'mesa_de_ayuda',
    turno: 'tarde',
    mensajes: ['Por favor habilitar los permisos de red para la carpeta compartida de RRHH.']
  },
  {
    categoria: 'incidencia_tecnica',
    reportadoPor: 'lfernandez',
    equipoAsignado: 'area_tecnica',
    turno: 'mañana',
    mensajes: ['La impresora del piso 2 marca error de atasco de papel, pero ya la revisamos y no tiene nada.']
  },
  {
    categoria: 'incidencia_tecnica',
    reportadoPor: 'dquiroz',
    equipoAsignado: 'mesa_de_ayuda',
    turno: 'noche',
    mensajes: ['No me puedo conectar a la VPN corporativa desde mi casa.']
  },
  {
    categoria: 'otro',
    reportadoPor: 'fcastro',
    equipoAsignado: 'mesa_de_ayuda',
    turno: 'mañana',
    mensajes: ['Solicito un teclado nuevo porque varias teclas dejaron de funcionar.']
  },
  {
    categoria: 'reclamo',
    reportadoPor: 'ehurtado',
    equipoAsignado: 'supervisor',
    turno: 'tarde',
    mensajes: ['Mi ticket anterior fue cerrado sin solución. Sigo sin acceso al portal de ventas.']
  },
  {
    categoria: 'incidencia_tecnica',
    reportadoPor: 'vramirez',
    equipoAsignado: 'area_tecnica',
    turno: 'mañana',
    mensajes: ['El servidor de base de datos de pruebas está rechazando las conexiones entrantes.']
  }
];

tickets.forEach((t, i) => {
  const ticket = new Ticket({
    categoria: t.categoria,
    equipoAsignado: t.equipoAsignado,
    reportadoPor: t.reportadoPor,
    turno: t.turno,
    estado: 'abierto',
    requiereEscalamiento: true
  });
  t.mensajes.forEach(m => ticket.addLog(t.reportadoPor, 'Consulta: ' + m, 0));
  saveTicket(ticket);
  console.log(`Ticket ${i+1} creado: ${ticket.id}`);
});

console.log('10 tickets creados exitosamente.');
