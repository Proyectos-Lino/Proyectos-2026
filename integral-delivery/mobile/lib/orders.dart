import 'package:flutter/material.dart';

import 'state.dart';
import 'widgets.dart';

class OrdersScreen extends StatefulWidget {
  final AppState app;
  final bool driverActive, history;
  const OrdersScreen(
    this.app, {
    super.key,
    this.driverActive = false,
    this.history = false,
  });
  @override
  State<OrdersScreen> createState() => _OrdersScreenState();
}

class _OrdersScreenState extends State<OrdersScreen> {
  String filter = 'all', query = '';
  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.app,
    builder: (context, child) {
      final app = widget.app;
      final all = app.orders.where((o) {
        final done = ['delivered', 'cancelled'].contains(o['status']);
        return widget.driverActive
            ? !done
            : widget.history
            ? done
            : true;
      }).toList();
      final orders = all
          .where(
            (o) =>
                (filter == 'all' || o['status'] == filter) &&
                '${o['code']} ${o['customer']['name']}'.toLowerCase().contains(
                  query.toLowerCase(),
                ),
          )
          .toList();
      return RefreshIndicator(
        onRefresh: app.refresh,
        child: ListView(
          padding: const EdgeInsets.all(22),
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    widget.driverActive
                        ? 'Tu ruta de hoy'
                        : widget.history
                        ? 'Entregas anteriores'
                        : app.role == 'customer'
                        ? 'Mis pedidos'
                        : 'Pedidos',
                    style: const TextStyle(
                      fontSize: 28,
                      fontWeight: FontWeight.w900,
                      letterSpacing: -.7,
                    ),
                  ),
                ),
                Container(
                  width: 8,
                  height: 8,
                  decoration: BoxDecoration(
                    color: app.live ? const Color(0xFF21846B) : Colors.orange,
                    shape: BoxShape.circle,
                  ),
                ),
                const SizedBox(width: 6),
                Text(
                  app.live ? 'En vivo' : 'Actualizando',
                  style: const TextStyle(color: muted, fontSize: 11),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              widget.driverActive
                  ? 'Cada entrega, siempre al día.'
                  : 'Consulta el detalle y sigue cada actualización.',
              style: const TextStyle(color: muted),
            ),
            const SizedBox(height: 22),
            TextField(
              onChanged: (v) => setState(() => query = v),
              decoration: const InputDecoration(
                prefixIcon: Icon(Icons.search),
                hintText: 'Buscar pedido o cliente',
              ),
            ),
            const SizedBox(height: 12),
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: [
                  for (final entry in {'all': 'Todos', ...statuses}.entries)
                    Padding(
                      padding: const EdgeInsets.only(right: 8),
                      child: ChoiceChip(
                        label: Text(entry.value),
                        selected: filter == entry.key,
                        onSelected: (_) => setState(() => filter = entry.key),
                      ),
                    ),
                ],
              ),
            ),
            const SizedBox(height: 18),
            Text(
              '${orders.length} pedidos',
              style: const TextStyle(color: muted, fontSize: 12),
            ),
            const SizedBox(height: 12),
            if (orders.isEmpty)
              EmptyState(
                widget.driverActive
                    ? 'Tu ruta está despejada'
                    : 'No hay pedidos aquí',
                widget.driverActive
                    ? 'Las entregas que te asigne el negocio aparecerán en esta pantalla.'
                    : 'Cuando registres pedidos, podrás encontrarlos aquí.',
                icon: widget.driverActive
                    ? Icons.delivery_dining
                    : Icons.receipt_long_outlined,
              ),
            ...orders.map((o) => OrderCard(app, o)),
          ],
        ),
      );
    },
  );
}

class OrderCard extends StatelessWidget {
  final AppState app;
  final Json order;
  const OrderCard(this.app, this.order, {super.key});
  @override
  Widget build(BuildContext context) => Card(
    elevation: 0,
    color: Colors.white,
    margin: const EdgeInsets.only(bottom: 14),
    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
    child: InkWell(
      key: ValueKey('order-${order['id']}'),
      borderRadius: BorderRadius.circular(20),
      onTap: () => Navigator.push(
        context,
        MaterialPageRoute(builder: (_) => OrderDetail(app, order['id'])),
      ),
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    order['code'],
                    style: const TextStyle(
                      fontWeight: FontWeight.w800,
                      fontSize: 13,
                    ),
                  ),
                ),
                StatusBadge(order['status']),
              ],
            ),
            const SizedBox(height: 16),
            Text(
              app.role == 'customer'
                  ? '${records(order['items']).fold<int>(0, (a, b) => a + (b['quantity'] as int))} productos · ${order['zone']}'
                  : order['customer']['name'],
              style: const TextStyle(fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 7),
            Text(
              order['address'],
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(color: muted, fontSize: 12, height: 1.4),
            ),
            const Divider(),
            Row(
              children: [
                Expanded(
                  child: Text(
                    dateLabel(order['created_at']),
                    style: const TextStyle(color: muted, fontSize: 12),
                  ),
                ),
                Text(
                  app.money(order['total_cents']),
                  style: const TextStyle(fontWeight: FontWeight.w800),
                ),
                const SizedBox(width: 8),
                const Icon(Icons.arrow_forward, size: 17),
              ],
            ),
          ],
        ),
      ),
    ),
  );
}

class OrderDetail extends StatelessWidget {
  final AppState app;
  final int id;
  const OrderDetail(this.app, this.id, {super.key});
  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: app,
    builder: (_, child) {
      final o = app.order(id);
      if (o == null)
        return Scaffold(
          appBar: AppBar(title: const Text('Pedido')),
          body: const EmptyState(
            'Pedido no disponible',
            'Actualiza la lista para comprobar tu acceso al pedido.',
          ),
        );
      final admin = app.role == 'admin',
          driver = app.role == 'driver',
          done = ['delivered', 'cancelled'].contains(o['status']);
      final next = nextStates[o['status']];
      return Scaffold(
        appBar: AppBar(
          title: Text(
            o['code'],
            style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800),
          ),
          actions: [
            IconButton(
              tooltip: 'Compartir resumen',
              onPressed: () => perform(context, () async {
                await platform.invokeMethod('share', {'text': receipt(app, o)});
              }),
              icon: const Icon(Icons.ios_share),
            ),
          ],
        ),
        body: RefreshIndicator(
          onRefresh: app.refresh,
          child: ListView(
            padding: const EdgeInsets.all(22),
            children: [
              Container(
                padding: const EdgeInsets.all(22),
                decoration: BoxDecoration(
                  color: ink,
                  borderRadius: BorderRadius.circular(22),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    StatusBadge(o['status']),
                    const SizedBox(height: 16),
                    Text(
                      statuses[o['status']] ?? '',
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 26,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      _description(o['status']),
                      style: const TextStyle(
                        color: Color(0xFFBED0D1),
                        height: 1.5,
                      ),
                    ),
                    const SizedBox(height: 18),
                    Row(
                      children: [
                        const Icon(
                          Icons.schedule,
                          size: 15,
                          color: Color(0xFFBED0D1),
                        ),
                        const SizedBox(width: 6),
                        Text(
                          'Actualizado ${dateLabel(o['updated_at'])}',
                          style: const TextStyle(
                            color: Color(0xFFBED0D1),
                            fontSize: 12,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              if (!done &&
                  (admin ||
                      (driver &&
                          ['ready', 'en_route'].contains(o['status'])))) ...[
                const SizedBox(height: 18),
                if (next != null)
                  ActionButton(
                    _nextLabel(next),
                    key: const Key('advance-order'),
                    icon: next == 'delivered'
                        ? Icons.task_alt
                        : Icons.arrow_forward,
                    onPressed: () async {
                      await app.mutate('POST', '/api/orders/$id/status', {
                        'status': next,
                      });
                      if (context.mounted)
                        toast(context, 'Estado actualizado.');
                    },
                  ),
              ],
              const SectionTitle('Detalle de la compra'),
              ...records(o['items']).map(
                (item) => Padding(
                  padding: const EdgeInsets.only(bottom: 14),
                  child: Row(
                    children: [
                      Container(
                        width: 32,
                        height: 32,
                        alignment: Alignment.center,
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Text(
                          '${item['quantity']}×',
                          style: const TextStyle(
                            fontWeight: FontWeight.w700,
                            fontSize: 12,
                          ),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Text(
                          item['name'],
                          style: const TextStyle(fontSize: 14),
                        ),
                      ),
                      const SizedBox(width: 10),
                      Text(
                        app.money(
                          (item['price_cents'] as int) *
                              (item['quantity'] as int),
                        ),
                        style: const TextStyle(fontWeight: FontWeight.w700),
                      ),
                    ],
                  ),
                ),
              ),
              const Divider(),
              _row('Productos', app.money(o['subtotal_cents'])),
              _row('Delivery · ${o['zone']}', app.money(o['delivery_cents'])),
              _row(
                'Total del pedido',
                app.money(o['total_cents']),
                strong: true,
              ),
              const SectionTitle('Entrega y contacto'),
              _info(Icons.location_on_outlined, o['address']),
              _info(Icons.person_outline, o['customer']['name']),
              if (o['driver'] != null)
                _info(
                  Icons.delivery_dining,
                  'Repartidor: ${o['driver']['name']}',
                ),
              if ('${o['notes']}'.isNotEmpty) _info(Icons.notes, o['notes']),
              if (driver)
                Wrap(
                  spacing: 8,
                  children: [
                    TextButton.icon(
                      onPressed: () => _launch(
                        context,
                        'geo:0,0?q=${Uri.encodeComponent(o['address'])}',
                      ),
                      icon: const Icon(Icons.map_outlined),
                      label: const Text('Abrir mapa'),
                    ),
                    if ('${o['customer']['phone']}'.isNotEmpty)
                      TextButton.icon(
                        onPressed: () =>
                            _launch(context, 'tel:${o['customer']['phone']}'),
                        icon: const Icon(Icons.call_outlined),
                        label: const Text('Llamar al cliente'),
                      ),
                  ],
                ),
              if (!driver &&
                  o['driver'] != null &&
                  '${o['driver']['phone']}'.isNotEmpty)
                TextButton.icon(
                  onPressed: () =>
                      _launch(context, 'tel:${o['driver']['phone']}'),
                  icon: const Icon(Icons.call_outlined),
                  label: const Text('Contactar al repartidor'),
                ),
              if (app.role == 'customer' &&
                  '${app.business['phone'] ?? ''}'.isNotEmpty)
                TextButton.icon(
                  onPressed: () =>
                      _launch(context, 'tel:${app.business['phone']}'),
                  icon: const Icon(Icons.support_agent),
                  label: const Text('Contactar al negocio'),
                ),
              if (admin && !done && o['status'] != 'en_route') ...[
                const SizedBox(height: 8),
                ActionButton(
                  o['driver'] == null
                      ? 'Asignar repartidor'
                      : 'Cambiar repartidor',
                  key: const Key('assign-driver'),
                  icon: Icons.person_add_alt,
                  secondary: true,
                  onPressed: () async {
                    await _assign(context);
                  },
                ),
              ],
              const SectionTitle('Pago'),
              _row(
                paymentNames[o['payment_method']] ?? '',
                o['payment_status'] == 'paid' ? 'Pagado' : 'Pendiente',
              ),
              if (o['payment_method'] == 'transfer')
                Padding(
                  padding: const EdgeInsets.only(bottom: 16),
                  child: Text(
                    app.business['bank_details'] ?? '',
                    style: const TextStyle(color: muted, height: 1.5),
                  ),
                ),
              if (o['status'] != 'cancelled' &&
                  (admin ||
                      (driver &&
                          o['payment_method'] == 'cash' &&
                          ['en_route', 'delivered'].contains(o['status']) &&
                          o['payment_status'] != 'paid'))) ...[
                ActionButton(
                  o['payment_status'] == 'paid'
                      ? 'Registrar devolución / pago pendiente'
                      : 'Confirmar pago recibido',
                  key: const Key('confirm-payment'),
                  icon: Icons.payments_outlined,
                  secondary: true,
                  onPressed: () async {
                    await app.mutate('POST', '/api/orders/$id/payment', {
                      'status': o['payment_status'] == 'paid'
                          ? 'pending'
                          : 'paid',
                    });
                    if (context.mounted) toast(context, 'Pago actualizado.');
                  },
                ),
              ],
              const SectionTitle(
                'Actividad del pedido',
                subtitle:
                    'Un historial compartido por todos los participantes.',
              ),
              ...records(o['events']).reversed.map(
                (e) => Padding(
                  padding: const EdgeInsets.only(bottom: 18),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Container(
                        margin: const EdgeInsets.only(top: 3),
                        padding: const EdgeInsets.all(7),
                        decoration: BoxDecoration(
                          color: e['kind'] == 'issue'
                              ? const Color(0xFFFFE7DA)
                              : Colors.white,
                          shape: BoxShape.circle,
                        ),
                        child: Icon(
                          e['kind'] == 'issue'
                              ? Icons.priority_high
                              : Icons.check,
                          size: 15,
                          color: e['kind'] == 'issue'
                              ? Colors.deepOrange
                              : app.accent,
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              e['message'],
                              style: const TextStyle(
                                fontSize: 13,
                                height: 1.45,
                                fontWeight: FontWeight.w500,
                              ),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              '${e['actor']} · ${dateLabel(e['created_at'])}',
                              style: const TextStyle(
                                fontSize: 11,
                                color: muted,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              if (!done) ...[
                ActionButton(
                  'Reportar una incidencia',
                  secondary: true,
                  icon: Icons.flag_outlined,
                  onPressed: () async {
                    final message = await askText(
                      context,
                      'Reportar incidencia',
                      label: '¿Qué sucedió?',
                    );
                    if (message != null && message.isNotEmpty) {
                      await app.mutate('POST', '/api/orders/$id/issue', {
                        'message': message,
                      });
                      if (context.mounted)
                        toast(context, 'Incidencia registrada.');
                    }
                  },
                ),
                const SizedBox(height: 10),
              ],
              if (!done && (admin || (!driver && o['status'] == 'pending')))
                TextButton(
                  onPressed: () => perform(context, () async {
                    final reason = await askText(
                      context,
                      'Cancelar pedido',
                      label: 'Motivo de cancelación',
                    );
                    if (reason != null && reason.isNotEmpty)
                      await app.mutate('POST', '/api/orders/$id/status', {
                        'status': 'cancelled',
                        'message': reason,
                      });
                  }),
                  child: const Text(
                    'Cancelar pedido',
                    style: TextStyle(color: Color(0xFFB84E45)),
                  ),
                ),
              const SizedBox(height: 12),
              ActionButton(
                'Guardar resumen del pedido',
                secondary: true,
                icon: Icons.download_outlined,
                onPressed: () async {
                  await platform.invokeMethod('saveFile', {
                    'name': '${o['code']}.txt',
                    'content': receipt(app, o),
                    'mime': 'text/plain',
                  });
                },
              ),
            ],
          ),
        ),
      );
    },
  );
  Future<void> _launch(BuildContext context, String url) =>
      perform(context, () async {
        await platform.invokeMethod('launch', {'url': url});
      });
  Future<void> _assign(BuildContext context) async {
    final drivers = app.users
        .where((u) => u['role'] == 'driver' && u['active'] == 1)
        .toList();
    await sheet(
      context,
      Sheet(
        'Elige un repartidor',
        drivers.isEmpty
            ? [
                const Text(
                  'Crea o activa una cuenta de repartidor desde Gestión → Usuarios.',
                ),
              ]
            : drivers
                  .map(
                    (d) => ListTile(
                      key: ValueKey('driver-${d['id']}'),
                      leading: const CircleAvatar(
                        child: Icon(Icons.delivery_dining),
                      ),
                      title: Text(d['name']),
                      subtitle: Text(d['phone']),
                      onTap: () async {
                        await perform(context, () async {
                          await app.mutate('POST', '/api/orders/$id/assign', {
                            'driver_id': d['id'],
                          });
                          if (context.mounted) Navigator.pop(context);
                        });
                      },
                    ),
                  )
                  .toList(),
      ),
    );
  }

  Widget _row(String a, String b, {bool strong = false}) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 7),
    child: Row(
      children: [
        Expanded(
          child: Text(
            a,
            style: TextStyle(
              color: strong ? ink : muted,
              fontSize: strong ? 16 : 13,
            ),
          ),
        ),
        const SizedBox(width: 12),
        Text(
          b,
          style: TextStyle(
            fontWeight: FontWeight.w800,
            fontSize: strong ? 19 : 13,
          ),
        ),
      ],
    ),
  );
  Widget _info(IconData icon, String text) => Padding(
    padding: const EdgeInsets.only(bottom: 14),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, size: 20, color: muted),
        const SizedBox(width: 12),
        Expanded(
          child: Text(text, style: const TextStyle(fontSize: 14, height: 1.5)),
        ),
      ],
    ),
  );
  String _description(String s) => switch (s) {
    'pending' => 'El negocio recibió tu pedido y lo confirmará pronto.',
    'confirmed' => 'Tu pedido fue confirmado. Pronto comenzará la preparación.',
    'preparing' => 'Estamos preparando los productos de tu compra.',
    'ready' => 'Todo está listo para que el repartidor recoja tu pedido.',
    'en_route' => 'Tu pedido está en camino a la dirección indicada.',
    'delivered' => 'La entrega finalizó. Gracias por tu compra.',
    'cancelled' =>
      'El pedido fue cancelado. Los productos regresaron al stock.',
    _ => '',
  };
  String _nextLabel(String s) => switch (s) {
    'confirmed' => 'Confirmar pedido',
    'preparing' => 'Comenzar preparación',
    'ready' => 'Marcar listo para recoger',
    'en_route' => 'Iniciar reparto',
    'delivered' => 'Confirmar entrega',
    _ => 'Actualizar estado',
  };
}

String receipt(AppState app, Json o) {
  final items = records(o['items'])
      .map(
        (i) =>
            "${i['quantity']} x ${i['name']} — ${app.money((i['price_cents'] as int) * (i['quantity'] as int))}",
      )
      .join('\n');
  return '${app.business['name']}\nRESUMEN DE PEDIDO ${o['code']}\n${dateLabel(o['created_at'])}\nCliente: ${o['customer']['name']}\nEntrega: ${o['address']}\n\n$items\n\nProductos: ${app.money(o['subtotal_cents'])}\nDelivery: ${app.money(o['delivery_cents'])}\nTOTAL: ${app.money(o['total_cents'])}\nEstado: ${statuses[o['status']]}\nPago: ${paymentNames[o['payment_method']]} · ${o['payment_status'] == 'paid' ? 'Pagado' : 'Pendiente'}\n\nEste resumen no sustituye un comprobante tributario.';
}
