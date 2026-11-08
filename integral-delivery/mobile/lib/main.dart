import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

import 'state.dart';
import 'widgets.dart';
import 'login.dart';
import 'customer.dart';
import 'orders.dart';
import 'admin.dart';
import 'account.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const IntegralApp());
}

class IntegralApp extends StatefulWidget {
  final AppState? state;
  const IntegralApp({super.key, this.state});
  @override
  State<IntegralApp> createState() => _IntegralAppState();
}

class _IntegralAppState extends State<IntegralApp> with WidgetsBindingObserver {
  late final AppState app;
  final navigator = GlobalKey<NavigatorState>();
  int? lastUser;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    app = widget.state ?? AppState();
    lastUser = app.user?['id'];
    app.addListener(authChanged);
    if (widget.state == null) unawaited(app.initialize());
  }

  void authChanged() {
    final id = app.user?['id'] as int?;
    if (id != lastUser) {
      lastUser = id;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) navigator.currentState?.popUntil((r) => r.isFirst);
      });
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && app.user != null) {
      unawaited(app.refreshQuietly());
      if (!app.live) unawaited(app.connect());
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    app.removeListener(authChanged);
    if (widget.state == null) app.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: app,
    builder: (_, child) => MaterialApp(
      title: 'Integral Delivery',
      debugShowCheckedModeBanner: false,
      navigatorKey: navigator,
      locale: const Locale('es'),
      supportedLocales: const [Locale('es')],
      localizationsDelegates: GlobalMaterialLocalizations.delegates,
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(
          seedColor: app.accent,
          primary: app.accent,
          surface: Colors.white,
        ),
        scaffoldBackgroundColor: canvas,
        fontFamily: 'sans-serif',
        textTheme: ThemeData.light().textTheme.apply(
          bodyColor: ink,
          displayColor: ink,
        ),
        appBarTheme: const AppBarTheme(
          backgroundColor: canvas,
          foregroundColor: ink,
          elevation: 0,
          centerTitle: false,
        ),
        filledButtonTheme: FilledButtonThemeData(
          style: FilledButton.styleFrom(
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(14),
            ),
            textStyle: const TextStyle(
              fontWeight: FontWeight.w700,
              fontSize: 15,
            ),
          ),
        ),
        outlinedButtonTheme: OutlinedButtonThemeData(
          style: OutlinedButton.styleFrom(
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(14),
            ),
          ),
        ),
        inputDecorationTheme: InputDecorationTheme(
          filled: true,
          fillColor: const Color(0xFFF8F9F6),
          contentPadding: const EdgeInsets.symmetric(
            horizontal: 16,
            vertical: 17,
          ),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(14),
            borderSide: const BorderSide(color: Color(0xFFDDE2DC)),
          ),
          enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(14),
            borderSide: const BorderSide(color: Color(0xFFDDE2DC)),
          ),
        ),
        dividerTheme: const DividerThemeData(
          color: Color(0xFFE6EAE4),
          space: 24,
        ),
        navigationBarTheme: NavigationBarThemeData(
          backgroundColor: Colors.white,
          indicatorColor: app.accent.withValues(alpha: .12),
          labelTextStyle: WidgetStateProperty.all(
            const TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
          ),
        ),
      ),
      home: app.booting
          ? const Scaffold(body: Center(child: CircularProgressIndicator()))
          : app.user == null
          ? LoginScreen(app)
          : Home(app, key: ValueKey(app.user!['id'])),
    ),
  );
}

class Home extends StatefulWidget {
  final AppState app;
  const Home(this.app, {super.key});
  @override
  State<Home> createState() => _HomeState();
}

class _HomeState extends State<Home> {
  int index = 0;
  @override
  Widget build(BuildContext context) {
    final app = widget.app;
    final isAdmin = app.role == 'admin', isDriver = app.role == 'driver';
    final labels = isAdmin
        ? ['Resumen', 'Pedidos', 'Catálogo', 'Gestión']
        : isDriver
        ? ['Mi ruta', 'Historial', 'Cuenta']
        : ['Explorar', 'Mis pedidos', 'Cuenta'];
    final icons = isAdmin
        ? [
            Icons.dashboard_outlined,
            Icons.receipt_long_outlined,
            Icons.inventory_2_outlined,
            Icons.tune,
          ]
        : isDriver
        ? [Icons.delivery_dining, Icons.history, Icons.person_outline]
        : [
            Icons.storefront_outlined,
            Icons.receipt_long_outlined,
            Icons.person_outline,
          ];
    final pages = isAdmin
        ? <Widget>[
            AdminDashboard(app),
            OrdersScreen(app),
            CatalogAdmin(app),
            Management(app),
          ]
        : isDriver
        ? <Widget>[
            OrdersScreen(app, driverActive: true),
            OrdersScreen(app, history: true),
            AccountScreen(app),
          ]
        : <Widget>[CatalogScreen(app), OrdersScreen(app), AccountScreen(app)];
    return Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            if (app.connectionError != null)
              Material(
                color: const Color(0xFFFFE9DF),
                child: InkWell(
                  onTap: () => perform(context, app.refresh),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 10,
                    ),
                    child: Row(
                      children: [
                        const Icon(Icons.wifi_off, size: 18),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Text(
                            'Sin conexión. Los datos pueden estar desactualizados.',
                            style: const TextStyle(fontSize: 12),
                          ),
                        ),
                        const Icon(Icons.refresh, size: 20),
                      ],
                    ),
                  ),
                ),
              ),
            Expanded(child: pages[index]),
          ],
        ),
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: index,
        onDestinationSelected: (v) => setState(() => index = v),
        destinations: List.generate(
          labels.length,
          (i) => NavigationDestination(
            key: ValueKey('nav-$i'),
            icon: Icon(icons[i]),
            label: labels[i],
          ),
        ),
      ),
    );
  }
}
