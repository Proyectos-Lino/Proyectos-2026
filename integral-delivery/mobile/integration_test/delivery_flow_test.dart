import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:integral_delivery/main.dart';
import 'package:integral_delivery/state.dart';
import 'package:integral_delivery/orders.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  testWidgets(
    'Compra, actualización remota, gestión y entrega con tres roles',
    (tester) async {
      final app = AppState()..booting = false;
      app.business = Map<String, dynamic>.from(
        (await app.request('GET', '/api/public', auth: false))['business'],
      );
      await tester.pumpWidget(IntegralApp(state: app));
      Future<void> wait(bool Function() ready) async {
        for (var i = 0; i < 200; i++) {
          await tester.pump(const Duration(milliseconds: 100));
          await Future<void>.delayed(const Duration(milliseconds: 50));
          if (ready()) {
            await tester.pump(const Duration(milliseconds: 400));
            return;
          }
        }
        fail('La condición esperada no se alcanzó. ${app.connectionError}');
      }

      Future<void> tap(Key key, {bool settle = true}) async {
        FocusManager.instance.primaryFocus?.unfocus();
        await SystemChannels.textInput.invokeMethod<void>('TextInput.hide');
        await tester.pump(const Duration(milliseconds: 400));
        final finder = find.byKey(key);
        if (finder.evaluate().isEmpty) {
          final scrollable = find.byType(Scrollable).first;
          tester.state<ScrollableState>(scrollable).position.jumpTo(0);
          await tester.pump(const Duration(milliseconds: 400));
          if (finder.evaluate().isEmpty) {
            await tester.scrollUntilVisible(
              finder,
              250,
              scrollable: scrollable,
            );
          }
        }
        await tester.ensureVisible(finder);
        await tester.pump(const Duration(milliseconds: 200));
        await tester.tap(finder);
        if (settle) {
          await tester.pumpAndSettle(
            const Duration(milliseconds: 100),
            EnginePhase.sendSemanticsUpdate,
            const Duration(seconds: 20),
          );
        } else {
          await tester.pump(const Duration(milliseconds: 400));
        }
      }

      Future<void> login(String email, String password) async {
        await wait(
          () => find.byKey(const Key('login-email')).evaluate().isNotEmpty,
        );
        await tester.enterText(
          find.descendant(
            of: find.byKey(const Key('login-email')),
            matching: find.byType(TextField),
          ),
          email,
        );
        await tester.enterText(
          find.descendant(
            of: find.byKey(const Key('login-password')),
            matching: find.byType(TextField),
          ),
          password,
        );
        await tap(const Key('login-submit'));
        await wait(() => app.user != null && app.products.isNotEmpty);
      }

      Future<void> logout(int nav) async {
        await tap(ValueKey('nav-$nav'));
        if (nav == 3) {
          await tester.tap(find.text('Mi cuenta'));
          await tester.pump(const Duration(milliseconds: 500));
        }
        await tap(const Key('logout'));
        await wait(() => app.user == null);
        // Las rutas de gestión se cierran antes de mostrar la siguiente cuenta.
        while (tester
            .state<NavigatorState>(find.byType(Navigator).first)
            .canPop()) {
          tester.state<NavigatorState>(find.byType(Navigator).first).pop();
          await tester.pump(const Duration(milliseconds: 400));
        }
      }

      Future<void> back() async {
        final nav = tester.state<NavigatorState>(find.byType(Navigator).first);
        expect(
          nav.canPop(),
          isTrue,
          reason: 'Debe existir una ruta anterior al detalle (${app.role}).',
        );
        nav.pop();
        await tester.pumpAndSettle();
      }

      await login('cliente@demo.local', 'Cliente2026!');
      await wait(() => app.live);
      await tester.enterText(find.byKey(const Key('product-search')), 'Café');
      FocusManager.instance.primaryFocus?.unfocus();
      await tester.pumpAndSettle();
      await tap(const ValueKey('add-product-1'));
      await tap(const Key('open-cart'));
      await tap(const Key('place-order'));
      await wait(
        () =>
            app.cartCount == 0 &&
            app.orders.isNotEmpty &&
            app.orders.first['status'] == 'pending',
      );
      final id = app.orders.first['id'] as int;
      await wait(() => find.byType(OrderDetail).evaluate().isNotEmpty);
      // Otro usuario cambia el pedido desde otra conexión al backend real.
      final peer = AppState();
      final auth = await peer.request(
        'POST',
        '/api/auth/login',
        auth: false,
        body: {'email': 'admin@integral.local', 'password': 'Admin2026!'},
      );
      peer.token = auth['token'];
      await peer.request(
        'POST',
        '/api/orders/$id/status',
        body: {'status': 'confirmed'},
      );
      await wait(() => app.order(id)?['status'] == 'confirmed');
      expect(app.order(id)?['status'], 'confirmed');
      await back();
      await tester.pump(const Duration(milliseconds: 500));
      await logout(2);
      await login('admin@integral.local', 'Admin2026!');
      await tap(const ValueKey('nav-1'));
      await tap(ValueKey('order-$id'));
      await tap(const Key('advance-order'));
      await wait(() => app.order(id)?['status'] == 'preparing');
      await tap(const Key('advance-order'));
      await wait(() => app.order(id)?['status'] == 'ready');
      await tap(const Key('assign-driver'), settle: false);
      await tap(const ValueKey('driver-3'));
      await wait(() => app.order(id)?['driver_id'] == 3);
      await back();
      await tester.pump(const Duration(milliseconds: 500));
      await logout(3);
      await login('repartidor@demo.local', 'Reparto2026!');
      expect(app.orders.any((o) => o['id'] == id), isTrue);
      await tap(ValueKey('order-$id'));
      await tap(const Key('advance-order'));
      await wait(() => app.order(id)?['status'] == 'en_route');
      await tap(const Key('confirm-payment'));
      await wait(() => app.order(id)?['payment_status'] == 'paid');
      await tap(const Key('advance-order'));
      await wait(() => app.order(id)?['status'] == 'delivered');
      await back();
      await tester.pump(const Duration(milliseconds: 500));
      await logout(2);
      await login('cliente@demo.local', 'Cliente2026!');
      await tap(const ValueKey('nav-1'));
      expect(app.order(id)?['status'], 'delivered');
      expect(app.order(id)?['payment_status'], 'paid');
      final restored = AppState();
      await restored.initialize();
      expect(restored.user?['id'], 2);
      expect(restored.order(id)?['status'], 'delivered');
      restored.dispose();
      peer.dispose();
      await tester.pumpWidget(const SizedBox());
      app.dispose();
    },
  );
}
