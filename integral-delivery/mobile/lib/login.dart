import 'package:flutter/material.dart';

import 'state.dart';
import 'widgets.dart';

class LoginScreen extends StatefulWidget {
  final AppState app;
  const LoginScreen(this.app, {super.key});
  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final email = TextEditingController(),
      password = TextEditingController(),
      name = TextEditingController(),
      phone = TextEditingController();
  bool register = false;
  @override
  void dispose() {
    email.dispose();
    password.dispose();
    name.dispose();
    phone.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final app = widget.app;
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 520),
            child: ListView(
              keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
              padding: const EdgeInsets.all(28),
              children: [
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(
                        color: app.accent,
                        borderRadius: BorderRadius.circular(14),
                      ),
                      child: const Icon(
                        Icons.local_shipping_outlined,
                        color: Colors.white,
                      ),
                    ),
                    const SizedBox(width: 12),
                    const Expanded(
                      child: Text(
                        'INTEGRAL\nDELIVERY',
                        style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w900,
                          letterSpacing: 2,
                        ),
                      ),
                    ),
                    IconButton(
                      tooltip: 'Configurar servidor',
                      key: const Key('server-config'),
                      onPressed: () => serverDialog(context, app),
                      icon: const Icon(Icons.dns_outlined),
                    ),
                  ],
                ),
                const SizedBox(height: 40),
                Text(
                  register
                      ? 'Tu próxima compra\ncomienza aquí.'
                      : 'Todo tu negocio.\nEn un solo lugar.',
                  style: const TextStyle(
                    fontSize: 36,
                    height: 1.12,
                    fontWeight: FontWeight.w900,
                    letterSpacing: -1.3,
                  ),
                ),
                const SizedBox(height: 16),
                Text(
                  register
                      ? 'Crea tu cuenta para explorar, pedir y seguir cada entrega.'
                      : 'Compra, gestiona y entrega. Ingresa con tu cuenta para continuar.',
                  style: const TextStyle(
                    color: muted,
                    fontSize: 16,
                    height: 1.5,
                  ),
                ),
                const SizedBox(height: 28),
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(18),
                  ),
                  child: Row(
                    children: [
                      BusinessMark(app),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Text(
                          app.business['name'] ?? 'Mi Negocio',
                          style: const TextStyle(fontWeight: FontWeight.w700),
                        ),
                      ),
                      const Icon(Icons.arrow_outward, size: 18, color: muted),
                    ],
                  ),
                ),
                const SizedBox(height: 24),
                if (register) ...[
                  Field(
                    name,
                    'Nombre completo',
                    key: const Key('register-name'),
                  ),
                  Field(phone, 'Teléfono', type: TextInputType.phone),
                ],
                Field(
                  email,
                  'Correo electrónico',
                  key: const Key('login-email'),
                  type: TextInputType.emailAddress,
                ),
                Field(
                  password,
                  'Contraseña',
                  key: const Key('login-password'),
                  secret: true,
                ),
                if (register)
                  const Padding(
                    padding: EdgeInsets.only(bottom: 16),
                    child: Text(
                      'Utiliza al menos 8 caracteres.',
                      style: TextStyle(color: muted, fontSize: 12),
                    ),
                  ),
                ActionButton(
                  register ? 'Crear mi cuenta' : 'Ingresar',
                  key: const Key('login-submit'),
                  icon: Icons.arrow_forward,
                  onPressed: () async {
                    if (email.text.trim().isEmpty ||
                        password.text.isEmpty ||
                        (register && name.text.trim().length < 2))
                      throw Exception('Completa los campos requeridos.');
                    await app.login(
                      email.text,
                      password.text,
                      name: register ? name.text.trim() : null,
                      phone: phone.text.trim(),
                    );
                  },
                ),
                const SizedBox(height: 14),
                TextButton(
                  onPressed: () => setState(() => register = !register),
                  child: Text(
                    register
                        ? 'Ya tengo una cuenta'
                        : '¿Primera vez? Crear cuenta de cliente',
                  ),
                ),
                const SizedBox(height: 28),
                const Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(Icons.lock_outline, size: 15, color: muted),
                    SizedBox(width: 6),
                    Flexible(
                      child: Text(
                        'Una cuenta · una experiencia conectada',
                        textAlign: TextAlign.center,
                        style: TextStyle(color: muted, fontSize: 12),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

Future<void> serverDialog(BuildContext context, AppState app) async {
  final c = TextEditingController(text: app.server);
  await sheet(
    context,
    Sheet('Conectar con el negocio', [
      const Text(
        'Ingresa la dirección HTTPS del servidor del negocio. Cambiar de servidor cierra la sesión actual.',
        style: TextStyle(color: muted, height: 1.5),
      ),
      const SizedBox(height: 20),
      Field(
        c,
        'Dirección del servidor',
        hint: 'https://delivery.tudominio.com',
        type: TextInputType.url,
      ),
      const Padding(
        padding: EdgeInsets.only(bottom: 20),
        child: Text(
          'Para pruebas en el emulador de esta computadora: http://10.0.2.2:8000',
          style: TextStyle(fontSize: 12, color: muted),
        ),
      ),
      ActionButton(
        'Comprobar y guardar',
        icon: Icons.cloud_done_outlined,
        onPressed: () async {
          await app.setServer(c.text);
          if (context.mounted) {
            Navigator.pop(context);
            toast(context, 'Servidor conectado.');
          }
        },
      ),
    ]),
  );
  Future<void>.delayed(const Duration(seconds: 1), c.dispose);
}
