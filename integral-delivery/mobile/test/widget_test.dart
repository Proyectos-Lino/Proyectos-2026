import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integral_delivery/main.dart';
import 'package:integral_delivery/state.dart';

void main() {
  test('Importes y fechas se representan sin perder centavos', () {
    expect(cents('19,95'), 1995);
    expect(cents('0'), 0);
    expect(() => cents('-1'), throwsException);
    expect(() => cents('abc'), throwsException);
  });
  test('Carrito respeta stock y suma precios en centavos', () {
    final a = AppState();
    final p = {'id': 1, 'stock': 3, 'price_cents': 1995};
    a.products = [p];
    a.add(p);
    a.add(p, 9);
    expect(a.cartCount, 3);
    expect(a.subtotal, 5985);
    a.add(p, -3);
    expect(a.cartCount, 0);
    a.dispose();
  });
  testWidgets('Pantalla de acceso muestra el negocio y configuración', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(430, 1200);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final a = AppState()..booting = false;
    await tester.pumpWidget(IntegralApp(state: a));
    expect(find.text('Todo tu negocio.\nEn un solo lugar.'), findsOneWidget);
    expect(find.byKey(const Key('login-submit')), findsOneWidget);
    await tester.tap(find.byKey(const Key('server-config')));
    await tester.pumpAndSettle();
    expect(find.text('Conectar con el negocio'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
    await tester.pump(const Duration(seconds: 2));
    a.dispose();
  });
  testWidgets('Cliente ve catálogo y no ve gestión administrativa', (
    tester,
  ) async {
    final a = AppState()
      ..booting = false
      ..user = {'id': 2, 'role': 'customer', 'name': 'Cliente'};
    a.business = {
      'name': 'Mi Negocio',
      'symbol': 'S/',
      'color': '#EC6838',
      'is_open': true,
    };
    await tester.pumpWidget(IntegralApp(state: a));
    expect(find.text('Explorar'), findsOneWidget);
    expect(find.text('Gestión'), findsNothing);
    expect(find.byKey(const Key('open-cart')), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
    a.dispose();
  });
  testWidgets('Administrador puede abrir sus cuatro secciones en un teléfono', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(430, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final a = AppState()
      ..booting = false
      ..user = {'id': 1, 'role': 'admin', 'name': 'Administrador'};
    await tester.pumpWidget(IntegralApp(state: a));
    expect(find.text('Resumen'), findsOneWidget);
    for (final index in [1, 2, 3]) {
      await tester.tap(find.byKey(ValueKey('nav-$index')));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
    }
    expect(find.text('Mi negocio'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
    a.dispose();
  });
  testWidgets('Repartidor solo tiene ruta, historial y cuenta', (tester) async {
    final a = AppState()
      ..booting = false
      ..user = {'id': 3, 'role': 'driver', 'name': 'Diego'};
    await tester.pumpWidget(IntegralApp(state: a));
    expect(find.text('Mi ruta'), findsOneWidget);
    expect(find.text('Historial'), findsOneWidget);
    expect(find.text('Gestión'), findsNothing);
    await tester.pumpWidget(const SizedBox());
    a.dispose();
  });
}
