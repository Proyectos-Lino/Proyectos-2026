import 'package:flutter/material.dart';

import 'state.dart';
import 'widgets.dart';
import 'orders.dart';
import 'account.dart';

void openPage(BuildContext context, Widget page) =>
    Navigator.push(context, MaterialPageRoute(builder: (_) => page));

class AdminDashboard extends StatelessWidget {
  final AppState app;
  const AdminDashboard(this.app, {super.key});
  @override
  Widget build(BuildContext context) {
    final r = app.reports;
    return RefreshIndicator(
      onRefresh: app.refresh,
      child: ListView(
        padding: const EdgeInsets.all(22),
        children: [
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'PANEL DEL NEGOCIO',
                      style: TextStyle(
                        fontSize: 10,
                        fontWeight: FontWeight.w700,
                        color: muted,
                        letterSpacing: 1.4,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      app.business['name'] ?? '',
                      style: const TextStyle(
                        fontSize: 27,
                        fontWeight: FontWeight.w900,
                        letterSpacing: -.7,
                      ),
                    ),
                  ],
                ),
              ),
              IconButton.filledTonal(
                tooltip: 'Configuración del negocio',
                onPressed: () => openPage(context, BusinessEditor(app)),
                icon: const Icon(Icons.storefront_outlined),
              ),
            ],
          ),
          const SizedBox(height: 24),
          Container(
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: ink,
              borderRadius: BorderRadius.circular(24),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'CADA PEDIDO CUENTA',
                  style: TextStyle(
                    color: Color(0xFFC3D4D4),
                    fontSize: 10,
                    letterSpacing: 1.4,
                  ),
                ),
                const SizedBox(height: 14),
                Text(
                  '${r['active'] ?? 0} pedidos\nen movimiento.',
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 31,
                    fontWeight: FontWeight.w800,
                    height: 1.1,
                  ),
                ),
                const SizedBox(height: 16),
                Row(
                  children: [
                    Icon(
                      app.live ? Icons.sensors : Icons.sync,
                      color: const Color(0xFFAAD8B9),
                      size: 17,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        app.live
                            ? 'Tu negocio está sincronizado en tiempo real.'
                            : 'Conectando las actualizaciones del negocio.',
                        style: const TextStyle(
                          fontSize: 12,
                          color: Color(0xFFC3D4D4),
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 18),
          LayoutBuilder(
            builder: (_, c) => GridView.count(
              crossAxisCount: c.maxWidth > 650 ? 4 : 2,
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              crossAxisSpacing: 12,
              mainAxisSpacing: 12,
              childAspectRatio: 1.25,
              children: [
                Metric(
                  'Valor de pedidos vigentes',
                  app.money(r['sales_cents']),
                  Icons.receipt_long_outlined,
                ),
                Metric(
                  'Pagos recibidos',
                  app.money(r['collected_cents']),
                  Icons.account_balance_wallet_outlined,
                  color: const Color(0xFF21846B),
                ),
                Metric(
                  'Entregas completadas',
                  '${r['delivered'] ?? 0}',
                  Icons.task_alt,
                ),
                Metric(
                  'Pagos pendientes',
                  app.money(r['pending_cents']),
                  Icons.schedule,
                  color: const Color(0xFFAF791F),
                ),
              ],
            ),
          ),
          SectionTitle(
            'Últimos pedidos',
            trailing: TextButton(
              onPressed: () => openPage(
                context,
                Scaffold(
                  appBar: AppBar(title: const Text('Todos los pedidos')),
                  body: OrdersScreen(app),
                ),
              ),
              child: const Text('Ver todos'),
            ),
          ),
          if (app.orders.isEmpty)
            const EmptyState(
              'Listo para recibir pedidos',
              'Las nuevas compras aparecerán aquí al instante.',
              icon: Icons.storefront_outlined,
            ),
          ...app.orders.take(3).map((o) => OrderCard(app, o)),
          if (records(r['low_stock']).isNotEmpty) ...[
            const SectionTitle('Atención al inventario'),
            ...records(r['low_stock']).map(
              (p) => ListTile(
                contentPadding: EdgeInsets.zero,
                leading: const Icon(
                  Icons.inventory_2_outlined,
                  color: Color(0xFFAF791F),
                ),
                title: Text(p['name']),
                trailing: Text(
                  '${p['stock']} disponibles',
                  style: const TextStyle(fontSize: 12, color: muted),
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class CatalogAdmin extends StatefulWidget {
  final AppState app;
  const CatalogAdmin(this.app, {super.key});
  @override
  State<CatalogAdmin> createState() => _CatalogAdminState();
}

class _CatalogAdminState extends State<CatalogAdmin> {
  String query = '';
  @override
  Widget build(BuildContext context) {
    final app = widget.app;
    final list = app.products
        .where(
          (p) =>
              p['name'].toString().toLowerCase().contains(query.toLowerCase()),
        )
        .toList();
    return RefreshIndicator(
      onRefresh: app.refresh,
      child: ListView(
        padding: const EdgeInsets.all(22),
        children: [
          Row(
            children: [
              const Expanded(
                child: Text(
                  'Tu catálogo',
                  style: TextStyle(
                    fontWeight: FontWeight.w900,
                    fontSize: 28,
                    letterSpacing: -.7,
                  ),
                ),
              ),
              IconButton.filled(
                key: const Key('new-product'),
                tooltip: 'Nuevo producto',
                onPressed: () => openPage(context, ProductEditor(app)),
                icon: const Icon(Icons.add),
              ),
            ],
          ),
          const SizedBox(height: 8),
          const Text(
            'Productos, precios y existencias bajo control.',
            style: TextStyle(color: muted),
          ),
          const SizedBox(height: 20),
          TextField(
            onChanged: (v) => setState(() => query = v),
            decoration: const InputDecoration(
              hintText: 'Buscar producto',
              prefixIcon: Icon(Icons.search),
            ),
          ),
          const SizedBox(height: 12),
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton.icon(
              onPressed: () => openPage(context, CategoriesScreen(app)),
              icon: const Icon(Icons.category_outlined),
              label: const Text('Administrar categorías'),
            ),
          ),
          const SizedBox(height: 8),
          if (list.isEmpty)
            const EmptyState(
              'Comienza tu catálogo',
              'Crea una categoría y agrega tus primeros productos.',
              icon: Icons.inventory_2_outlined,
            ),
          ...list.map(
            (p) => Card(
              elevation: 0,
              color: Colors.white,
              margin: const EdgeInsets.only(bottom: 12),
              child: ListTile(
                contentPadding: const EdgeInsets.all(12),
                leading: SizedBox(
                  width: 65,
                  child: ProductArt(app, p, height: 65),
                ),
                title: Text(
                  p['name'],
                  style: const TextStyle(
                    fontWeight: FontWeight.w700,
                    fontSize: 14,
                  ),
                ),
                subtitle: Padding(
                  padding: const EdgeInsets.only(top: 7),
                  child: Text(
                    '${app.money(p['price_cents'])} · Stock: ${p['stock']}\n${p['active'] == 1 ? 'Activo' : 'Oculto'} · ${p['category_name']}',
                    style: const TextStyle(fontSize: 12, color: muted),
                  ),
                ),
                trailing: const Icon(Icons.edit_outlined, size: 20),
                onTap: () => openPage(context, ProductEditor(app, product: p)),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class ProductEditor extends StatefulWidget {
  final AppState app;
  final Json? product;
  const ProductEditor(this.app, {super.key, this.product});
  @override
  State<ProductEditor> createState() => _ProductEditorState();
}

class _ProductEditorState extends State<ProductEditor> {
  late final TextEditingController name, description, price, stock;
  int? category;
  String image = '';
  bool active = true;
  @override
  void initState() {
    super.initState();
    final p = widget.product;
    name = TextEditingController(text: p?['name']);
    description = TextEditingController(text: p?['description']);
    price = TextEditingController(
      text: p == null
          ? ''
          : ((p['price_cents'] as int) / 100).toStringAsFixed(2),
    );
    stock = TextEditingController(text: '${p?['stock'] ?? 0}');
    category = p?['category_id'] ?? widget.app.categories.firstOrNull?['id'];
    image = p?['image_url'] ?? '';
    active = p == null || p['active'] == 1;
  }

  @override
  void dispose() {
    name.dispose();
    description.dispose();
    price.dispose();
    stock.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: Text(
        widget.product == null ? 'Nuevo producto' : 'Editar producto',
      ),
    ),
    body: ListView(
      padding: const EdgeInsets.all(24),
      children: [
        ProductArt(
          widget.app,
          {
            'category_id': category ?? 1,
            'category_icon': 'shop',
            'image_url': image,
          },
          height: 180,
          large: true,
        ),
        const SizedBox(height: 10),
        Row(
          children: [
            Expanded(
              child: TextButton.icon(
                onPressed: () => perform(context, () async {
                  final url = await widget.app.pickImage();
                  if (url != null && mounted) setState(() => image = url);
                }),
                icon: const Icon(Icons.photo_library_outlined),
                label: const Text('Elegir imagen'),
              ),
            ),
            if (image.isNotEmpty)
              IconButton(
                onPressed: () => setState(() => image = ''),
                icon: const Icon(Icons.delete_outline),
              ),
          ],
        ),
        const SizedBox(height: 16),
        Field(name, 'Nombre del producto'),
        Field(description, 'Descripción', lines: 3),
        DropdownButtonFormField<int>(
          initialValue: category,
          isExpanded: true,
          decoration: const InputDecoration(labelText: 'Categoría'),
          items: widget.app.categories
              .map(
                (c) => DropdownMenuItem<int>(
                  value: c['id'],
                  child: Text(c['name']),
                ),
              )
              .toList(),
          onChanged: (v) => setState(() => category = v),
        ),
        const SizedBox(height: 16),
        Field(
          price,
          'Precio (${widget.app.business['symbol']})',
          type: const TextInputType.numberWithOptions(decimal: true),
        ),
        Field(stock, 'Existencias disponibles', type: TextInputType.number),
        SwitchListTile(
          contentPadding: EdgeInsets.zero,
          title: const Text('Producto visible'),
          subtitle: const Text('Puedes ocultarlo conservando su historial.'),
          value: active,
          onChanged: (v) => setState(() => active = v),
        ),
        const SizedBox(height: 20),
        ActionButton(
          'Guardar producto',
          key: const Key('save-product'),
          icon: Icons.check,
          onPressed: () async {
            if (category == null)
              throw Exception('Crea una categoría antes de agregar productos.');
            final qty = int.tryParse(stock.text);
            if (qty == null || qty < 0)
              throw Exception('Ingresa una cantidad entera válida.');
            await widget.app.mutate(
              widget.product == null ? 'POST' : 'PUT',
              '/api/admin/products${widget.product == null ? '' : '/${widget.product!['id']}'}',
              {
                'name': name.text,
                'description': description.text,
                'category_id': category,
                'price_cents': cents(price.text),
                'stock': qty,
                'image_url': image,
                'active': active,
              },
            );
            if (context.mounted) {
              Navigator.pop(context);
              toast(context, 'Producto guardado.');
            }
          },
        ),
      ],
    ),
  );
}

class Management extends StatelessWidget {
  final AppState app;
  const Management(this.app, {super.key});
  @override
  Widget build(BuildContext context) => ListView(
    padding: const EdgeInsets.all(22),
    children: [
      const SectionTitle(
        'Gestión',
        subtitle: 'Adapta la aplicación a tu forma de trabajar.',
      ),
      const SizedBox(height: 14),
      _tile(
        context,
        Icons.storefront_outlined,
        'Mi negocio',
        'Identidad, horarios, pagos y delivery',
        BusinessEditor(app),
      ),
      _tile(
        context,
        Icons.group_outlined,
        'Usuarios y repartidores',
        'Cuentas, roles y acceso al sistema',
        UsersScreen(app),
      ),
      _tile(
        context,
        Icons.category_outlined,
        'Categorías',
        'Organiza los productos del catálogo',
        CategoriesScreen(app),
      ),
      _tile(
        context,
        Icons.bar_chart_outlined,
        'Reportes',
        'Pedidos, cobros y exportación CSV',
        ReportsScreen(app),
      ),
      _tile(
        context,
        Icons.history,
        'Registro de actividad',
        'Consulta los cambios administrativos',
        AuditScreen(app),
      ),
      _tile(
        context,
        Icons.person_outline,
        'Mi cuenta',
        'Perfil, contraseña y conexión',
        Scaffold(
          appBar: AppBar(title: const Text('Mi cuenta')),
          body: AccountScreen(app),
        ),
      ),
      const SizedBox(height: 22),
      Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: const Color(0xFFE8EFE7),
          borderRadius: BorderRadius.circular(18),
        ),
        child: const Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(Icons.tips_and_updates_outlined, size: 22),
            SizedBox(width: 12),
            Expanded(
              child: Text(
                'Un solo negocio, múltiples posibilidades. Personaliza la identidad, las categorías y las zonas de reparto para adaptarlo a tu empresa.',
                style: TextStyle(fontSize: 13, height: 1.5),
              ),
            ),
          ],
        ),
      ),
    ],
  );
  Widget _tile(
    BuildContext context,
    IconData icon,
    String title,
    String sub,
    Widget page,
  ) => Card(
    elevation: 0,
    color: Colors.white,
    margin: const EdgeInsets.only(bottom: 12),
    child: ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 18, vertical: 10),
      leading: Icon(icon, color: app.accent),
      title: Text(title, style: const TextStyle(fontWeight: FontWeight.w700)),
      subtitle: Text(sub, style: const TextStyle(fontSize: 12, color: muted)),
      trailing: const Icon(Icons.chevron_right, size: 19),
      onTap: () => openPage(context, page),
    ),
  );
}

class CategoriesScreen extends StatelessWidget {
  final AppState app;
  const CategoriesScreen(this.app, {super.key});
  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: app,
    builder: (_, child) => Scaffold(
      appBar: AppBar(
        title: const Text('Categorías'),
        actions: [
          IconButton(
            tooltip: 'Nueva categoría',
            onPressed: () => edit(context),
            icon: const Icon(Icons.add),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          const Text(
            'Ocultar una categoría también oculta sus productos del catálogo del cliente.',
            style: TextStyle(color: muted, height: 1.5),
          ),
          const SizedBox(height: 18),
          if (app.categories.isEmpty)
            const EmptyState(
              'Organiza tu catálogo',
              'Pulsa + para crear tu primera categoría.',
            ),
          ...app.categories.map(
            (c) => Card(
              elevation: 0,
              color: Colors.white,
              child: ListTile(
                leading: Icon(categoryIcon(c['icon'])),
                title: Text(c['name']),
                subtitle: Text(c['active'] == 1 ? 'Visible' : 'Oculta'),
                trailing: const Icon(Icons.edit_outlined, size: 19),
                onTap: () => edit(context, c),
              ),
            ),
          ),
        ],
      ),
    ),
  );
  Future<void> edit(BuildContext context, [Json? category]) async {
    final name = TextEditingController(text: category?['name']);
    String icon = category?['icon'] ?? 'shop';
    bool active = category == null || category['active'] == 1;
    await sheet(
      context,
      StatefulBuilder(
        builder: (ctx, set) => Sheet(
          category == null ? 'Nueva categoría' : 'Editar categoría',
          [
            Field(name, 'Nombre'),
            DropdownButtonFormField<String>(
              initialValue: icon,
              decoration: const InputDecoration(labelText: 'Ícono'),
              items: const ['shop', 'basket', 'home', 'devices', 'spa', 'build']
                  .map(
                    (i) => DropdownMenuItem(
                      value: i,
                      child: Row(
                        children: [
                          Icon(categoryIcon(i)),
                          const SizedBox(width: 12),
                          Text(
                            {
                              'shop': 'Negocio',
                              'basket': 'Compras',
                              'home': 'Hogar',
                              'devices': 'Tecnología',
                              'spa': 'Cuidado',
                              'build': 'Herramientas',
                            }[i]!,
                          ),
                        ],
                      ),
                    ),
                  )
                  .toList(),
              onChanged: (v) => set(() => icon = v!),
            ),
            const SizedBox(height: 12),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text('Categoría visible'),
              value: active,
              onChanged: (v) => set(() => active = v),
            ),
            const SizedBox(height: 12),
            ActionButton(
              'Guardar categoría',
              onPressed: () async {
                await app.mutate(
                  category == null ? 'POST' : 'PUT',
                  '/api/admin/categories${category == null ? '' : '/${category['id']}'}',
                  {'name': name.text, 'icon': icon, 'active': active},
                );
                if (ctx.mounted) Navigator.pop(ctx);
              },
            ),
          ],
        ),
      ),
    );
    Future<void>.delayed(const Duration(seconds: 1), name.dispose);
  }
}

class UsersScreen extends StatefulWidget {
  final AppState app;
  const UsersScreen(this.app, {super.key});
  @override
  State<UsersScreen> createState() => _UsersScreenState();
}

class _UsersScreenState extends State<UsersScreen> {
  String query = '';
  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.app,
    builder: (_, child) => Scaffold(
      appBar: AppBar(
        title: const Text('Usuarios y repartidores'),
        actions: [
          IconButton(
            tooltip: 'Nuevo usuario',
            onPressed: () => openPage(context, UserEditor(widget.app)),
            icon: const Icon(Icons.person_add_alt),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(22),
        children: [
          TextField(
            onChanged: (v) => setState(() => query = v),
            decoration: const InputDecoration(
              hintText: 'Buscar por nombre o correo',
              prefixIcon: Icon(Icons.search),
            ),
          ),
          const SizedBox(height: 16),
          ...widget.app.users
              .where(
                (u) => '${u['name']} ${u['email']}'.toLowerCase().contains(
                  query.toLowerCase(),
                ),
              )
              .map(
                (u) => Card(
                  elevation: 0,
                  color: Colors.white,
                  child: ListTile(
                    leading: CircleAvatar(
                      backgroundColor: widget.app.accent.withValues(alpha: .1),
                      child: Icon(
                        u['role'] == 'driver'
                            ? Icons.delivery_dining
                            : u['role'] == 'admin'
                            ? Icons.admin_panel_settings_outlined
                            : Icons.person_outline,
                        color: widget.app.accent,
                      ),
                    ),
                    title: Text(
                      u['name'],
                      style: const TextStyle(
                        fontWeight: FontWeight.w700,
                        fontSize: 14,
                      ),
                    ),
                    subtitle: Text(
                      '${u['email']}\n${roles[u['role']]} · ${u['active'] == 1 ? 'Activo' : 'Desactivado'}',
                      style: const TextStyle(fontSize: 12),
                    ),
                    isThreeLine: true,
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () =>
                        openPage(context, UserEditor(widget.app, user: u)),
                  ),
                ),
              ),
        ],
      ),
    ),
  );
}

class UserEditor extends StatefulWidget {
  final AppState app;
  final Json? user;
  const UserEditor(this.app, {super.key, this.user});
  @override
  State<UserEditor> createState() => _UserEditorState();
}

class _UserEditorState extends State<UserEditor> {
  late final TextEditingController name, email, phone, password;
  String role = 'driver';
  bool active = true;
  @override
  void initState() {
    super.initState();
    final u = widget.user;
    name = TextEditingController(text: u?['name']);
    email = TextEditingController(text: u?['email']);
    phone = TextEditingController(text: u?['phone']);
    password = TextEditingController();
    role = u?['role'] ?? 'driver';
    active = u == null || u['active'] == 1;
  }

  @override
  void dispose() {
    for (final c in [name, email, phone, password]) {
      c.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: Text(widget.user == null ? 'Nuevo usuario' : 'Editar usuario'),
    ),
    body: ListView(
      padding: const EdgeInsets.all(24),
      children: [
        Field(name, 'Nombre completo'),
        if (widget.user == null)
          Field(email, 'Correo electrónico', type: TextInputType.emailAddress)
        else
          Padding(
            padding: const EdgeInsets.only(bottom: 22),
            child: Text(
              widget.user!['email'],
              style: const TextStyle(color: muted),
            ),
          ),
        Field(phone, 'Teléfono', type: TextInputType.phone),
        DropdownButtonFormField<String>(
          initialValue: role,
          decoration: const InputDecoration(labelText: 'Rol'),
          items: roles.entries
              .map((e) => DropdownMenuItem(value: e.key, child: Text(e.value)))
              .toList(),
          onChanged: (v) => setState(() => role = v!),
        ),
        const SizedBox(height: 18),
        Field(
          password,
          widget.user == null
              ? 'Contraseña (mínimo 8 caracteres)'
              : 'Nueva contraseña (opcional)',
          secret: true,
        ),
        SwitchListTile(
          contentPadding: EdgeInsets.zero,
          title: const Text('Cuenta activa'),
          value: active,
          onChanged: (v) => setState(() => active = v),
        ),
        const SizedBox(height: 20),
        ActionButton(
          'Guardar usuario',
          onPressed: () async {
            await widget.app.mutate(
              widget.user == null ? 'POST' : 'PUT',
              '/api/admin/users${widget.user == null ? '' : '/${widget.user!['id']}'}',
              {
                'name': name.text,
                if (widget.user == null) 'email': email.text,
                'phone': phone.text,
                'password': password.text,
                'role': role,
                'active': active,
              },
            );
            if (context.mounted) {
              Navigator.pop(context);
              toast(context, 'Usuario guardado.');
            }
          },
        ),
      ],
    ),
  );
}

class BusinessEditor extends StatefulWidget {
  final AppState app;
  const BusinessEditor(this.app, {super.key});
  @override
  State<BusinessEditor> createState() => _BusinessEditorState();
}

class _BusinessEditorState extends State<BusinessEditor> {
  final fields = <String, TextEditingController>{};
  late List<Json> zones;
  late bool open, cash, transfer;
  String logo = '';
  @override
  void initState() {
    super.initState();
    final b = widget.app.business;
    for (final key in [
      'name',
      'description',
      'phone',
      'email',
      'address',
      'currency',
      'symbol',
      'color',
      'hours',
      'bank_details',
    ]) {
      fields[key] = TextEditingController(text: '${b[key] ?? ''}');
    }
    fields['minimum'] = TextEditingController(
      text: ((b['minimum_cents'] as int? ?? 0) / 100).toStringAsFixed(2),
    );
    zones = records(b['zones']);
    open = b['is_open'] == true;
    cash = (b['payment_methods'] as List).contains('cash');
    transfer = (b['payment_methods'] as List).contains('transfer');
    logo = b['logo_url'] ?? '';
  }

  @override
  void dispose() {
    for (final c in fields.values) {
      c.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Mi negocio')),
    body: ListView(
      padding: const EdgeInsets.all(24),
      children: [
        const SectionTitle(
          'Identidad del negocio',
          subtitle: 'Una aplicación que se adapta a tu empresa.',
        ),
        if (logo.isNotEmpty)
          Center(
            child: SizedBox(
              width: 100,
              height: 100,
              child: Image.network(
                '${widget.app.server}$logo',
                fit: BoxFit.contain,
                errorBuilder: (_, e, s) =>
                    const Icon(Icons.storefront, size: 50),
              ),
            ),
          ),
        TextButton.icon(
          onPressed: () => perform(context, () async {
            final result = await widget.app.pickImage();
            if (result != null && mounted) setState(() => logo = result);
          }),
          icon: const Icon(Icons.photo_library_outlined),
          label: const Text('Cargar logo del negocio'),
        ),
        if (logo.isNotEmpty)
          TextButton(
            onPressed: () => setState(() => logo = ''),
            child: const Text('Quitar logo'),
          ),
        Field(fields['name']!, 'Nombre comercial'),
        Field(fields['description']!, 'Descripción', lines: 2),
        Field(fields['color']!, 'Color de marca', hint: '#EC6838'),
        const SectionTitle('Contacto y atención'),
        Field(fields['phone']!, 'Teléfono', type: TextInputType.phone),
        Field(
          fields['email']!,
          'Correo de contacto',
          type: TextInputType.emailAddress,
        ),
        Field(fields['address']!, 'Dirección del negocio'),
        Field(fields['hours']!, 'Horario de atención'),
        SwitchListTile(
          contentPadding: EdgeInsets.zero,
          title: const Text('Recibir nuevos pedidos'),
          subtitle: const Text(
            'Cierra temporalmente la recepción cuando lo necesites.',
          ),
          value: open,
          onChanged: (v) => setState(() => open = v),
        ),
        const SectionTitle('Moneda y pagos'),
        Field(fields['currency']!, 'Código de moneda', hint: 'PEN, USD, EUR…'),
        Field(fields['symbol']!, 'Símbolo de moneda', hint: 'S/'),
        Field(
          fields['minimum']!,
          'Compra mínima (sin delivery)',
          type: const TextInputType.numberWithOptions(decimal: true),
        ),
        CheckboxListTile(
          contentPadding: EdgeInsets.zero,
          title: const Text('Efectivo al recibir'),
          value: cash,
          onChanged: (v) => setState(() => cash = v!),
        ),
        CheckboxListTile(
          contentPadding: EdgeInsets.zero,
          title: const Text('Transferencia bancaria'),
          value: transfer,
          onChanged: (v) => setState(() => transfer = v!),
        ),
        Field(
          fields['bank_details']!,
          'Instrucciones para transferencia',
          lines: 3,
        ),
        SectionTitle(
          'Zonas de reparto',
          subtitle: 'Tarifa fija por zona, sin cálculo automático de rutas.',
          trailing: IconButton(
            onPressed: () => _zone(context),
            icon: const Icon(Icons.add_circle_outline),
          ),
        ),
        ...zones.asMap().entries.map(
          (e) => Card(
            elevation: 0,
            color: Colors.white,
            child: ListTile(
              title: Text(e.value['name']),
              subtitle: Text(widget.app.money(e.value['fee_cents'])),
              onTap: () => _zone(context, e.key),
              trailing: IconButton(
                tooltip: 'Eliminar zona',
                onPressed: () => setState(() => zones.removeAt(e.key)),
                icon: const Icon(Icons.delete_outline),
              ),
            ),
          ),
        ),
        const SizedBox(height: 20),
        ActionButton(
          'Guardar configuración',
          icon: Icons.check,
          onPressed: () async {
            final data = <String, dynamic>{
              for (final e in fields.entries)
                if (e.key != 'minimum') e.key: e.value.text.trim(),
              'minimum_cents': cents(fields['minimum']!.text),
              'logo_url': logo,
              'is_open': open,
              'zones': zones,
              'payment_methods': [if (cash) 'cash', if (transfer) 'transfer'],
            };
            data['currency'] = data['currency'].toString().toUpperCase();
            await widget.app.mutate('PUT', '/api/admin/business', data);
            if (context.mounted) {
              Navigator.pop(context);
              toast(context, 'Negocio actualizado para todos los usuarios.');
            }
          },
        ),
      ],
    ),
  );
  Future<void> _zone(BuildContext context, [int? index]) async {
    final existing = index == null ? null : zones[index];
    final name = TextEditingController(text: existing?['name']),
        fee = TextEditingController(
          text: existing == null
              ? '0.00'
              : ((existing['fee_cents'] as int) / 100).toStringAsFixed(2),
        );
    await sheet(
      context,
      Sheet('Zona de reparto', [
        Field(name, 'Nombre de zona'),
        Field(
          fee,
          'Tarifa de envío',
          type: const TextInputType.numberWithOptions(decimal: true),
        ),
        ActionButton(
          'Guardar zona',
          onPressed: () async {
            if (name.text.trim().length < 2)
              throw Exception('Indica el nombre de la zona.');
            final value = {
              'id':
                  existing?['id'] ??
                  'zona-${DateTime.now().microsecondsSinceEpoch}',
              'name': name.text.trim(),
              'fee_cents': cents(fee.text),
            };
            setState(() {
              if (index == null) {
                zones.add(value);
              } else {
                zones[index] = value;
              }
            });
            Navigator.pop(context);
          },
        ),
      ]),
    );
    Future<void>.delayed(const Duration(seconds: 1), () {
      name.dispose();
      fee.dispose();
    });
  }
}

class ReportsScreen extends StatefulWidget {
  final AppState app;
  const ReportsScreen(this.app, {super.key});
  @override
  State<ReportsScreen> createState() => _ReportsScreenState();
}

class _ReportsScreenState extends State<ReportsScreen> {
  DateTime? start, end;
  Json? data;
  bool busy = false;
  String get params =>
      '?start=${start?.toIso8601String().substring(0, 10) ?? ''}&end=${end?.toIso8601String().substring(0, 10) ?? ''}';
  Future<void> load() async {
    setState(() => busy = true);
    try {
      final r = await widget.app.request('GET', '/api/admin/reports$params');
      if (mounted) setState(() => data = Map<String, dynamic>.from(r));
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final app = widget.app, r = data ?? app.reports;
    return Scaffold(
      appBar: AppBar(title: const Text('Reportes')),
      body: ListView(
        padding: const EdgeInsets.all(22),
        children: [
          const Text(
            'Filtra por fecha de creación del pedido. Los cortes de fecha del servidor utilizan UTC.',
            style: TextStyle(color: muted, fontSize: 12, height: 1.5),
          ),
          const SizedBox(height: 14),
          Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: () => _date(true),
                  icon: const Icon(Icons.calendar_today_outlined, size: 16),
                  label: Text(
                    start == null
                        ? 'Desde'
                        : start!.toIso8601String().substring(0, 10),
                  ),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: () => _date(false),
                  icon: const Icon(Icons.calendar_today_outlined, size: 16),
                  label: Text(
                    end == null
                        ? 'Hasta'
                        : end!.toIso8601String().substring(0, 10),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          ActionButton(
            'Aplicar filtro',
            icon: Icons.filter_alt_outlined,
            onPressed: load,
          ),
          TextButton(
            onPressed: () => setState(() {
              start = null;
              end = null;
              data = null;
            }),
            child: const Text('Ver todo el historial'),
          ),
          if (busy) const LinearProgressIndicator(),
          const SectionTitle('Resumen del período'),
          GridView.count(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            crossAxisCount: 2,
            childAspectRatio: 1.2,
            mainAxisSpacing: 12,
            crossAxisSpacing: 12,
            children: [
              Metric(
                'Pedidos registrados',
                '${r['count'] ?? 0}',
                Icons.receipt_long_outlined,
              ),
              Metric(
                'Valor de pedidos vigentes',
                app.money(r['sales_cents']),
                Icons.shopping_bag_outlined,
              ),
              Metric(
                'Cobrado',
                app.money(r['collected_cents']),
                Icons.payments_outlined,
              ),
              Metric(
                'Pendiente de cobro',
                app.money(r['pending_cents']),
                Icons.schedule,
              ),
            ],
          ),
          const SectionTitle('Estado de los pedidos'),
          ...statuses.entries.map((e) {
            final count = (r['by_status'] as Map?)?[e.key] ?? 0;
            return Padding(
              padding: const EdgeInsets.only(bottom: 15),
              child: Column(
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          e.value,
                          style: const TextStyle(fontSize: 13),
                        ),
                      ),
                      Text(
                        '$count',
                        style: const TextStyle(fontWeight: FontWeight.w800),
                      ),
                    ],
                  ),
                  const SizedBox(height: 7),
                  LinearProgressIndicator(
                    value: (r['count'] ?? 0) == 0
                        ? 0
                        : count / (r['count'] as num),
                    minHeight: 7,
                    borderRadius: BorderRadius.circular(10),
                    color: statusColor(e.key),
                    backgroundColor: Colors.white,
                  ),
                ],
              ),
            );
          }),
          const SectionTitle('Productos más pedidos'),
          if (records(r['top_products']).isEmpty)
            const Text(
              'Aún no hay ventas en este período.',
              style: TextStyle(color: muted),
            ),
          ...records(r['top_products']).map(
            (p) => ListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(p['name'], style: const TextStyle(fontSize: 14)),
              subtitle: Text('${p['quantity']} unidades'),
              trailing: Text(
                app.money(p['total_cents']),
                style: const TextStyle(fontWeight: FontWeight.w700),
              ),
            ),
          ),
          const SizedBox(height: 22),
          ActionButton(
            'Exportar pedidos a CSV',
            secondary: true,
            icon: Icons.download_outlined,
            onPressed: () async {
              final csv = await app.request(
                'GET',
                '/api/admin/reports.csv$params',
                text: true,
              );
              await platform.invokeMethod('saveFile', {
                'name': 'pedidos-integral.csv',
                'content': csv,
                'mime': 'text/csv',
              });
            },
          ),
        ],
      ),
    );
  }

  Future<void> _date(bool first) async {
    final result = await showDatePicker(
      context: context,
      initialDate: (first ? start : end) ?? DateTime.now(),
      firstDate: DateTime(2020),
      lastDate: DateTime(2100),
    );
    if (result != null && mounted)
      setState(() {
        if (first) {
          start = result;
        } else {
          end = result;
        }
      });
  }
}

class AuditScreen extends StatelessWidget {
  final AppState app;
  const AuditScreen(this.app, {super.key});
  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: app,
    builder: (_, child) => Scaffold(
      appBar: AppBar(title: const Text('Registro de actividad')),
      body: ListView(
        padding: const EdgeInsets.all(22),
        children: [
          const Text(
            'Últimos 100 cambios administrativos.',
            style: TextStyle(color: muted),
          ),
          const SizedBox(height: 18),
          ...app.audit.map(
            (e) => ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.history, size: 21),
              title: Text(e['action'], style: const TextStyle(fontSize: 13)),
              subtitle: Text(
                '${e['actor']} · ${dateLabel(e['created_at'])}',
                style: const TextStyle(fontSize: 11),
              ),
            ),
          ),
        ],
      ),
    ),
  );
}
