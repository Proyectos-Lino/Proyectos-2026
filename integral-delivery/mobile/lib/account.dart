import 'package:flutter/material.dart';

import 'state.dart';
import 'widgets.dart';
import 'login.dart' show serverDialog;

class AccountScreen extends StatelessWidget {
  final AppState app;
  const AccountScreen(this.app, {super.key});
  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: app,
    builder: (_, child) => ListView(
      padding: const EdgeInsets.all(24),
      children: [
        const SectionTitle('Mi cuenta'),
        const SizedBox(height: 10),
        Container(
          padding: const EdgeInsets.all(22),
          decoration: BoxDecoration(
            color: ink,
            borderRadius: BorderRadius.circular(22),
          ),
          child: Row(
            children: [
              CircleAvatar(
                radius: 28,
                backgroundColor: app.accent,
                child: Text(
                  app.user?['name'].toString().substring(0, 1).toUpperCase() ??
                      '',
                  style: const TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.w800,
                    fontSize: 24,
                  ),
                ),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      app.user?['name'] ?? '',
                      style: const TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.w800,
                        fontSize: 19,
                      ),
                    ),
                    const SizedBox(height: 5),
                    Text(
                      roles[app.role] ?? '',
                      style: const TextStyle(color: Color(0xFFBFD0D2)),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 20),
        ListTile(
          contentPadding: EdgeInsets.zero,
          leading: const Icon(Icons.mail_outline),
          title: Text(app.user?['email'] ?? ''),
          subtitle: Text(app.user?['phone'] ?? ''),
        ),
        _option(
          Icons.edit_outlined,
          'Editar mis datos',
          () => _profile(context),
        ),
        _option(
          Icons.lock_outline,
          'Cambiar contraseña',
          () => _password(context),
        ),
        if (app.role == 'customer') ...[
          const SectionTitle('Mis direcciones'),
          ...app.addresses.map(
            (a) => Card(
              elevation: 0,
              color: Colors.white,
              child: ListTile(
                leading: const Icon(Icons.location_on_outlined),
                title: Text(a['label']),
                subtitle: Text('${a['street']}\n${a['district']}'),
                isThreeLine: true,
                trailing: IconButton(
                  tooltip: 'Eliminar dirección',
                  icon: const Icon(Icons.delete_outline, size: 20),
                  onPressed: () => perform(context, () async {
                    await app.mutate('DELETE', '/api/addresses/${a['id']}');
                  }),
                ),
              ),
            ),
          ),
          TextButton.icon(
            onPressed: () => editAddress(context, app),
            icon: const Icon(Icons.add),
            label: const Text('Agregar dirección'),
          ),
        ],
        const Divider(),
        const SectionTitle('Acerca del negocio'),
        Text(
          app.business['name'] ?? '',
          style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 17),
        ),
        const SizedBox(height: 8),
        Text(
          app.business['description'] ?? '',
          style: const TextStyle(color: muted, height: 1.5),
        ),
        const SizedBox(height: 10),
        Text(
          app.business['hours'] ?? '',
          style: const TextStyle(color: muted, fontSize: 13),
        ),
        if ('${app.business['address'] ?? ''}'.isNotEmpty)
          Padding(
            padding: const EdgeInsets.only(top: 10),
            child: Text(
              app.business['address'],
              style: const TextStyle(color: muted, fontSize: 13),
            ),
          ),
        const SizedBox(height: 22),
        _option(
          Icons.dns_outlined,
          'Servidor del negocio',
          () => serverDialog(context, app),
        ),
        _option(
          Icons.logout,
          'Cerrar sesión',
          () => app.logout(),
          key: const Key('logout'),
        ),
        const Divider(),
        const Text(
          appTitle,
          style: TextStyle(fontSize: 12, color: muted, height: 1.5),
        ),
        const SizedBox(height: 8),
        const Text(
          'Versión 1.0.0 · Android',
          style: TextStyle(fontSize: 11, color: muted),
        ),
      ],
    ),
  );
  Widget _option(
    IconData icon,
    String label,
    Future<void> Function() run, {
    Key? key,
  }) => ListTile(
    key: key,
    contentPadding: EdgeInsets.zero,
    leading: Icon(icon),
    title: Text(label, style: const TextStyle(fontSize: 14)),
    trailing: const Icon(Icons.chevron_right),
    onTap: run,
  );
  Future<void> _profile(BuildContext context) async {
    final name = TextEditingController(text: app.user?['name']),
        phone = TextEditingController(text: app.user?['phone']);
    await sheet(
      context,
      Sheet('Mis datos', [
        Field(name, 'Nombre completo'),
        Field(phone, 'Teléfono', type: TextInputType.phone),
        ActionButton(
          'Guardar cambios',
          onPressed: () async {
            await app.mutate('PATCH', '/api/me', {
              'name': name.text,
              'phone': phone.text,
            });
            if (context.mounted) Navigator.pop(context);
          },
        ),
      ]),
    );
    Future<void>.delayed(const Duration(seconds: 1), () {
      name.dispose();
      phone.dispose();
    });
  }

  Future<void> _password(BuildContext context) async {
    final current = TextEditingController(), next = TextEditingController();
    await sheet(
      context,
      Sheet('Cambiar contraseña', [
        const Text(
          'Por seguridad, tendrás que volver a ingresar con la nueva contraseña.',
          style: TextStyle(color: muted),
        ),
        const SizedBox(height: 18),
        Field(current, 'Contraseña actual', secret: true),
        Field(next, 'Nueva contraseña (mínimo 8 caracteres)', secret: true),
        ActionButton(
          'Actualizar contraseña',
          onPressed: () async {
            await app.request(
              'POST',
              '/api/me/password',
              body: {'current': current.text, 'new': next.text},
            );
            if (context.mounted) Navigator.pop(context);
            await app.clearSession();
          },
        ),
      ]),
    );
    Future<void>.delayed(const Duration(seconds: 1), () {
      current.dispose();
      next.dispose();
    });
  }
}

Future<void> editAddress(BuildContext context, AppState app) async {
  final label = TextEditingController(text: 'Casa'),
      street = TextEditingController(),
      district = TextEditingController(),
      reference = TextEditingController();
  await sheet(
    context,
    Sheet('Nueva dirección', [
      Field(label, 'Nombre de la dirección'),
      Field(street, 'Calle, avenida y número'),
      Field(district, 'Distrito / ciudad'),
      Field(reference, 'Referencia (opcional)'),
      ActionButton(
        'Guardar dirección',
        onPressed: () async {
          await app.mutate('POST', '/api/addresses', {
            'label': label.text,
            'street': street.text,
            'district': district.text,
            'reference': reference.text,
          });
          if (context.mounted) Navigator.pop(context);
        },
      ),
    ]),
  );
  Future<void>.delayed(const Duration(seconds: 1), () {
    label.dispose();
    street.dispose();
    district.dispose();
    reference.dispose();
  });
}
