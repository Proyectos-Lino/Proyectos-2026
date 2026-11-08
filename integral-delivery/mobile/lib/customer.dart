import 'dart:math';

import 'package:flutter/material.dart';

import 'state.dart';
import 'widgets.dart';
import 'orders.dart';
import 'account.dart' show editAddress;

class CatalogScreen extends StatefulWidget {
  final AppState app;
  const CatalogScreen(this.app, {super.key});
  @override
  State<CatalogScreen> createState() => _CatalogScreenState();
}

class _CatalogScreenState extends State<CatalogScreen> {
  String query = '';
  int? category;
  @override
  Widget build(BuildContext context) {
    final app = widget.app;
    final products = app.products
        .where(
          (p) =>
              (p['active'] == 1 || p['active'] == true) &&
              (category == null || p['category_id'] == category) &&
              '${p['name']} ${p['description']}'.toLowerCase().contains(
                query.toLowerCase(),
              ),
        )
        .toList();
    return RefreshIndicator(
      onRefresh: app.refresh,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(22, 16, 22, 24),
        children: [
          Row(
            children: [
              if ('${app.business['logo_url'] ?? ''}'.isNotEmpty) ...[
                BusinessMark(app),
                const SizedBox(width: 10),
              ],
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Hola, ${app.user?['name'].toString().split(' ').first ?? ''}',
                      style: const TextStyle(color: muted, fontSize: 13),
                    ),
                    const SizedBox(height: 5),
                    Text(
                      app.business['name'] ?? '',
                      style: const TextStyle(
                        fontSize: 22,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ],
                ),
              ),
              Badge(
                isLabelVisible: app.cartCount > 0,
                label: Text('${app.cartCount}'),
                child: IconButton.filledTonal(
                  key: const Key('open-cart'),
                  tooltip: 'Ver carrito',
                  onPressed: () => Navigator.push(
                    context,
                    MaterialPageRoute(builder: (_) => CartScreen(app)),
                  ),
                  icon: const Icon(Icons.shopping_bag_outlined),
                ),
              ),
            ],
          ),
          const SizedBox(height: 20),
          Container(
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: ink,
              borderRadius: BorderRadius.circular(24),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Container(
                      width: 7,
                      height: 7,
                      decoration: BoxDecoration(
                        color: app.business['is_open'] == true
                            ? const Color(0xFF9ED5AC)
                            : Colors.orange,
                        shape: BoxShape.circle,
                      ),
                    ),
                    const SizedBox(width: 7),
                    Text(
                      app.business['is_open'] == true
                          ? 'RECIBIMOS TUS PEDIDOS'
                          : 'CERRADO POR AHORA',
                      style: const TextStyle(
                        color: Color(0xFFCEE1D4),
                        fontSize: 10,
                        fontWeight: FontWeight.w700,
                        letterSpacing: 1.1,
                      ),
                    ),
                    const Spacer(),
                    const Icon(
                      Icons.delivery_dining,
                      color: Color(0xFFF7BE83),
                      size: 30,
                    ),
                  ],
                ),
                const SizedBox(height: 18),
                const Text(
                  'Tu próximo pedido,\na un toque.',
                  style: TextStyle(
                    fontSize: 29,
                    fontWeight: FontWeight.w800,
                    height: 1.15,
                    color: Colors.white,
                    letterSpacing: -.7,
                  ),
                ),
                const SizedBox(height: 12),
                Text(
                  app.business['description'] ?? '',
                  style: const TextStyle(
                    color: Color(0xFFBFCFD1),
                    fontSize: 13,
                    height: 1.45,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 20),
          TextField(
            key: const Key('product-search'),
            onChanged: (v) => setState(() => query = v),
            decoration: const InputDecoration(
              hintText: '¿Qué estás buscando?',
              prefixIcon: Icon(Icons.search),
              filled: true,
              fillColor: Colors.white,
            ),
          ),
          const SizedBox(height: 12),
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: [
                Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: ChoiceChip(
                    label: const Text('Todo'),
                    selected: category == null,
                    onSelected: (_) => setState(() => category = null),
                  ),
                ),
                ...app.categories.map(
                  (c) => Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: ChoiceChip(
                      avatar: Icon(categoryIcon(c['icon']), size: 17),
                      label: Text(c['name']),
                      selected: category == c['id'],
                      onSelected: (_) => setState(() => category = c['id']),
                    ),
                  ),
                ),
              ],
            ),
          ),
          SectionTitle(
            'Encuentra tu favorito',
            subtitle: '${products.length} productos para elegir',
            trailing: Icon(
              Icons.grid_view_rounded,
              color: app.accent,
              size: 20,
            ),
          ),
          if (products.isEmpty)
            const EmptyState(
              'Sin productos',
              'Prueba otra categoría o una búsqueda diferente.',
              icon: Icons.search_off,
            ),
          LayoutBuilder(
            builder: (context, constraints) {
              final count = constraints.maxWidth > 650 ? 3 : 2;
              return GridView.builder(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                itemCount: products.length,
                gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: count,
                  crossAxisSpacing: 14,
                  mainAxisSpacing: 14,
                  mainAxisExtent: 270,
                ),
                itemBuilder: (_, i) => ProductCard(app, products[i]),
              );
            },
          ),
          const SizedBox(height: 24),
          Row(
            children: [
              const Icon(Icons.schedule, size: 16, color: muted),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  app.business['hours'] ?? '',
                  style: const TextStyle(fontSize: 12, color: muted),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class ProductCard extends StatelessWidget {
  final AppState app;
  final Json product;
  const ProductCard(this.app, this.product, {super.key});
  @override
  Widget build(BuildContext context) {
    final available = (product['stock'] as int) > 0;
    return Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(20),
      child: InkWell(
        borderRadius: BorderRadius.circular(20),
        onTap: () => sheet(
          context,
          Sheet(product['name'], [
            ProductArt(app, product, height: 220, large: true),
            const SizedBox(height: 16),
            Text(
              product['description'],
              style: const TextStyle(color: muted, height: 1.5),
            ),
            const SizedBox(height: 12),
            Text(
              '${app.money(product['price_cents'])} · ${product['stock']} disponibles',
              style: const TextStyle(fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 20),
            ActionButton(
              'Agregar al carrito',
              icon: Icons.add,
              onPressed: available
                  ? () async {
                      app.add(product);
                      Navigator.pop(context);
                      toast(context, 'Producto agregado al carrito.');
                    }
                  : null,
            ),
          ]),
        ),
        child: Padding(
          padding: const EdgeInsets.all(10),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              ProductArt(app, product, height: 120),
              const SizedBox(height: 10),
              Text(
                product['category_name'],
                style: const TextStyle(color: muted, fontSize: 10),
              ),
              const SizedBox(height: 4),
              Expanded(
                child: Text(
                  product['name'],
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontWeight: FontWeight.w700,
                    fontSize: 14,
                  ),
                ),
              ),
              Row(
                children: [
                  Expanded(
                    child: Text(
                      app.money(product['price_cents']),
                      style: const TextStyle(
                        fontWeight: FontWeight.w800,
                        fontSize: 15,
                      ),
                    ),
                  ),
                  SizedBox(
                    width: 34,
                    height: 34,
                    child: IconButton.filled(
                      key: ValueKey('add-product-${product['id']}'),
                      padding: EdgeInsets.zero,
                      onPressed: available
                          ? () {
                              app.add(product);
                              toast(context, 'Agregado al carrito.');
                            }
                          : null,
                      icon: Icon(
                        available ? Icons.add : Icons.remove,
                        size: 19,
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class CartScreen extends StatefulWidget {
  final AppState app;
  const CartScreen(this.app, {super.key});
  @override
  State<CartScreen> createState() => _CartScreenState();
}

class _CartScreenState extends State<CartScreen> {
  int? address;
  String? zone, method;
  final notes = TextEditingController();
  late final String idempotency =
      'app-${DateTime.now().microsecondsSinceEpoch}-${Random.secure().nextInt(1 << 30)}';
  @override
  void dispose() {
    notes.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.app,
    builder: (_, child) {
      final app = widget.app;
      final zones = records(app.business['zones']);
      final methods = (app.business['payment_methods'] as List? ?? [])
          .cast<String>();
      if (!app.addresses.any((a) => a['id'] == address))
        address = app.addresses.firstOrNull?['id'];
      if (!zones.any((z) => z['id'] == zone)) zone = zones.firstOrNull?['id'];
      if (!methods.contains(method)) method = methods.firstOrNull;
      final shipping =
          zones.where((z) => z['id'] == zone).firstOrNull?['fee_cents'] ?? 0;
      return Scaffold(
        appBar: AppBar(title: const Text('Tu carrito')),
        body: app.cart.isEmpty
            ? const SingleChildScrollView(
                child: EmptyState(
                  'Aún no hay productos',
                  'Explora el catálogo y agrega tus favoritos.',
                  icon: Icons.shopping_bag_outlined,
                ),
              )
            : ListView(
                padding: const EdgeInsets.all(22),
                children: [
                  Text(
                    '${app.cartCount} productos · un solo envío',
                    style: const TextStyle(color: muted),
                  ),
                  const SizedBox(height: 18),
                  ...app.cart.entries.toList().map((e) {
                    final p = app.product(e.key);
                    return Card(
                      elevation: 0,
                      margin: const EdgeInsets.only(bottom: 12),
                      color: Colors.white,
                      child: Padding(
                        padding: const EdgeInsets.all(14),
                        child: Row(
                          children: [
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    p?['name'] ?? 'Producto no disponible',
                                    style: const TextStyle(
                                      fontWeight: FontWeight.w700,
                                    ),
                                  ),
                                  const SizedBox(height: 6),
                                  Text(
                                    app.money(
                                      (p?['price_cents'] as int? ?? 0) *
                                          e.value,
                                    ),
                                    style: TextStyle(
                                      color: app.accent,
                                      fontWeight: FontWeight.w700,
                                    ),
                                  ),
                                  if (p == null ||
                                      e.value > (p['stock'] as int))
                                    const Text(
                                      'Revisa la disponibilidad.',
                                      style: TextStyle(
                                        color: Colors.red,
                                        fontSize: 11,
                                      ),
                                    ),
                                ],
                              ),
                            ),
                            IconButton(
                              onPressed: () {
                                if (p != null) {
                                  app.add(p, -1);
                                } else {
                                  app.cart.remove(e.key);
                                  app.changed();
                                }
                              },
                              icon: const Icon(Icons.remove_circle_outline),
                            ),
                            Text(
                              '${e.value}',
                              style: const TextStyle(
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                            IconButton(
                              onPressed:
                                  p != null && e.value < (p['stock'] as int)
                                  ? () => app.add(p)
                                  : null,
                              icon: const Icon(Icons.add_circle_outline),
                            ),
                          ],
                        ),
                      ),
                    );
                  }),
                  const SectionTitle('¿A dónde lo llevamos?'),
                  if (app.addresses.isNotEmpty)
                    DropdownButtonFormField<int>(
                      initialValue: address,
                      key: ValueKey('address-$address'),
                      isExpanded: true,
                      decoration: const InputDecoration(
                        labelText: 'Dirección de entrega',
                      ),
                      items: app.addresses
                          .map(
                            (a) => DropdownMenuItem<int>(
                              value: a['id'],
                              child: Text(
                                '${a['label']} · ${a['street']}',
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                          )
                          .toList(),
                      onChanged: (v) => setState(() => address = v),
                    ),
                  TextButton.icon(
                    onPressed: () => editAddress(context, app),
                    icon: const Icon(Icons.add_location_alt_outlined),
                    label: const Text('Agregar dirección'),
                  ),
                  const SizedBox(height: 8),
                  DropdownButtonFormField<String>(
                    initialValue: zone,
                    key: ValueKey('zone-$zone'),
                    isExpanded: true,
                    decoration: const InputDecoration(
                      labelText: 'Zona de reparto',
                    ),
                    items: zones
                        .map(
                          (z) => DropdownMenuItem<String>(
                            value: z['id'],
                            child: Text(
                              '${z['name']} · ${app.money(z['fee_cents'])}',
                            ),
                          ),
                        )
                        .toList(),
                    onChanged: (v) => setState(() => zone = v),
                  ),
                  const SectionTitle('Forma de pago'),
                  DropdownButtonFormField<String>(
                    initialValue: method,
                    key: ValueKey('method-$method'),
                    isExpanded: true,
                    items: methods
                        .map(
                          (m) => DropdownMenuItem(
                            value: m,
                            child: Text(paymentNames[m] ?? m),
                          ),
                        )
                        .toList(),
                    onChanged: (v) => setState(() => method = v),
                  ),
                  if (method == 'transfer')
                    Padding(
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      child: Text(
                        app.business['bank_details'] ?? '',
                        style: const TextStyle(color: muted, height: 1.5),
                      ),
                    ),
                  const SizedBox(height: 18),
                  Field(notes, 'Indicaciones del pedido (opcional)', lines: 2),
                  Container(
                    padding: const EdgeInsets.all(20),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(18),
                    ),
                    child: Column(
                      children: [
                        _total('Productos', app.money(app.subtotal)),
                        const SizedBox(height: 10),
                        _total('Delivery', app.money(shipping)),
                        const Divider(),
                        _total(
                          'Total',
                          app.money(app.subtotal + shipping),
                          bold: true,
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 18),
                  ActionButton(
                    'Confirmar pedido',
                    key: const Key('place-order'),
                    icon: Icons.check_circle_outline,
                    onPressed: address == null || zone == null || method == null
                        ? null
                        : () async {
                            final result = await app.mutate(
                              'POST',
                              '/api/orders',
                              {
                                'items': app.cart.entries
                                    .map(
                                      (e) => {
                                        'product_id': e.key,
                                        'quantity': e.value,
                                      },
                                    )
                                    .toList(),
                                'address_id': address,
                                'zone_id': zone,
                                'payment_method': method,
                                'notes': notes.text,
                                'idempotency_key': idempotency,
                              },
                            );
                            app.cart.clear();
                            app.changed();
                            if (!context.mounted) return;
                            Navigator.pushReplacement(
                              context,
                              MaterialPageRoute(
                                builder: (_) => OrderDetail(app, result['id']),
                              ),
                            );
                            toast(context, '¡Pedido recibido por el negocio!');
                          },
                  ),
                  const SizedBox(height: 10),
                  const Text(
                    'El negocio confirmará tu pedido. Podrás seguir cada cambio desde Mis pedidos.',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: muted, fontSize: 12, height: 1.5),
                  ),
                ],
              ),
      );
    },
  );
  Widget _total(String label, String value, {bool bold = false}) => Row(
    children: [
      Expanded(
        child: Text(
          label,
          style: TextStyle(
            fontWeight: bold ? FontWeight.w800 : FontWeight.w400,
            color: bold ? ink : muted,
          ),
        ),
      ),
      Text(
        value,
        style: TextStyle(fontWeight: FontWeight.w800, fontSize: bold ? 21 : 15),
      ),
    ],
  );
}
