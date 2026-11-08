import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

typedef Json = Map<String, dynamic>;
const platform = MethodChannel('integral/platform');
const appTitle =
    'Aplicación móvil integral con servicio de delivery y gestión en tiempo real';
const statuses = <String, String>{
  'pending': 'Pendiente',
  'confirmed': 'Confirmado',
  'preparing': 'En preparación',
  'ready': 'Listo para recoger',
  'en_route': 'En camino',
  'delivered': 'Entregado',
  'cancelled': 'Cancelado',
};
const roles = {
  'customer': 'Cliente',
  'admin': 'Administrador',
  'driver': 'Repartidor',
};
const paymentNames = {
  'cash': 'Efectivo al recibir',
  'transfer': 'Transferencia bancaria',
};
const nextStates = {
  'pending': 'confirmed',
  'confirmed': 'preparing',
  'preparing': 'ready',
  'ready': 'en_route',
  'en_route': 'delivered',
};

List<Json> records(dynamic value) =>
    (value as List? ?? []).map((e) => Map<String, dynamic>.from(e)).toList();
String dateLabel(dynamic value) {
  final d = DateTime.tryParse('$value')?.toLocal();
  if (d == null) return '';
  return '${d.day.toString().padLeft(2, '0')}/${d.month.toString().padLeft(2, '0')} ${d.hour.toString().padLeft(2, '0')}:${d.minute.toString().padLeft(2, '0')}';
}

int cents(String value) {
  final number = double.tryParse(value.trim().replaceAll(',', '.'));
  if (number == null || !number.isFinite || number < 0)
    throw Exception('Ingresa un importe válido.');
  return (number * 100).round();
}

String errorText(Object e) => e is PlatformException
    ? (e.message ?? 'No se pudo completar la acción.')
    : e.toString().replaceFirst(RegExp(r'^Exception: '), '');

class AppState extends ChangeNotifier {
  String server = const String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8000',
  );
  String token = '';
  Json? user;
  Json business = {
    'name': 'Integral Delivery',
    'symbol': 'S/',
    'color': '#EC6838',
  };
  List<Json> products = [],
      categories = [],
      orders = [],
      addresses = [],
      users = [],
      audit = [];
  Json reports = {};
  Map<int, int> cart = {};
  bool booting = true, loading = false, online = false, live = false;
  String? connectionError;
  WebSocket? _socket;
  Timer? _heartbeat, _retry, _poll, _debounce;
  bool _syncing = false, _again = false, _disposed = false;
  int _generation = 0, _retrySeconds = 2;
  String get role => user?['role'] ?? '';
  Color get accent => Color(
    int.tryParse('${business['color']}'.replaceFirst('#', 'FF'), radix: 16) ??
        0xFFEC6838,
  );
  String money(dynamic value) =>
      '${business['symbol'] ?? 'S/'} ${((value as num? ?? 0) / 100).toStringAsFixed(2)}';
  int get cartCount => cart.values.fold(0, (a, b) => a + b);
  int get subtotal => cart.entries.fold(
    0,
    (sum, e) => sum + (product(e.key)?['price_cents'] as int? ?? 0) * e.value,
  );
  Json? product(int id) {
    for (final p in products) {
      if (p['id'] == id) return p;
    }
    return null;
  }

  Json? order(int id) {
    for (final p in orders) {
      if (p['id'] == id) return p;
    }
    return null;
  }

  void changed() {
    if (!_disposed) notifyListeners();
  }

  Future<void> initialize() async {
    try {
      server =
          await platform.invokeMethod<String>('read', {'key': 'server'}) ??
          server;
      token =
          await platform.invokeMethod<String>('read', {'key': 'token'}) ?? '';
    } on MissingPluginException {
      /* pruebas de widgets */
    } catch (_) {
      token = '';
    }
    try {
      business = Map<String, dynamic>.from(
        (await request('GET', '/api/public', auth: false))['business'],
      );
    } catch (_) {}
    if (token.isNotEmpty) {
      try {
        await refresh();
        connect();
      } catch (e) {
        connectionError = errorText(e);
      }
    }
    booting = false;
    _poll = Timer.periodic(const Duration(seconds: 30), (_) {
      if (user != null) unawaited(refreshQuietly());
    });
    changed();
  }

  Future<dynamic> request(
    String method,
    String path, {
    Json? body,
    bool auth = true,
    bool text = false,
  }) async {
    final client = HttpClient()..connectionTimeout = const Duration(seconds: 8);
    try {
      final req = await client
          .openUrl(method, Uri.parse('$server$path'))
          .timeout(const Duration(seconds: 10));
      if (auth && token.isNotEmpty)
        req.headers.set('Authorization', 'Bearer $token');
      if (body != null) {
        req.headers.contentType = ContentType.json;
        req.write(jsonEncode(body));
      }
      final response = await req.close().timeout(const Duration(seconds: 15));
      final result = await utf8.decoder
          .bind(response)
          .join()
          .timeout(const Duration(seconds: 15));
      dynamic data;
      try {
        data = jsonDecode(result);
      } catch (_) {
        data = {};
      }
      if (response.statusCode >= 400) {
        if (response.statusCode == 401 && auth) {
          await clearSession();
        }
        final detail = data is Map ? data['detail'] : null;
        throw Exception(
          detail is String
              ? detail
              : detail is List
              ? 'Revisa los campos del formulario.'
              : 'No se pudo completar la operación (${response.statusCode}).',
        );
      }
      online = true;
      connectionError = null;
      return text ? result : data;
    } on SocketException {
      online = false;
      throw Exception(
        'No se pudo conectar al servidor. Comprueba la dirección y tu conexión.',
      );
    } on TimeoutException {
      online = false;
      throw Exception(
        'El servidor tardó demasiado en responder. Vuelve a intentarlo.',
      );
    } on HandshakeException {
      online = false;
      throw Exception(
        'No se pudo verificar el certificado HTTPS del servidor.',
      );
    } finally {
      client.close(force: true);
    }
  }

  Future<void> refresh() async {
    if (token.isEmpty) return;
    if (_syncing) {
      _again = true;
      return;
    }
    _syncing = true;
    final generation = _generation;
    try {
      final data = await request('GET', '/api/sync');
      if (generation != _generation) return;
      user = Map<String, dynamic>.from(data['user']);
      business = Map<String, dynamic>.from(data['business']);
      products = records(data['products']);
      categories = records(data['categories']);
      orders = records(data['orders']);
      addresses = records(data['addresses']);
      users = records(data['users']);
      audit = records(data['audit']);
      reports = Map<String, dynamic>.from(data['reports'] ?? {});
      connectionError = null;
    } catch (e) {
      connectionError = errorText(e);
      rethrow;
    } finally {
      _syncing = false;
      changed();
      if (_again) {
        _again = false;
        unawaited(refreshQuietly());
      }
    }
  }

  Future<void> refreshQuietly() async {
    try {
      await refresh();
    } catch (_) {}
  }

  Future<dynamic> mutate(String method, String path, [Json? body]) async {
    final result = await request(method, path, body: body);
    await refreshQuietly();
    return result;
  }

  Future<void> login(
    String email,
    String password, {
    String? name,
    String phone = '',
  }) async {
    final data = await request(
      'POST',
      name == null ? '/api/auth/login' : '/api/auth/register',
      auth: false,
      body: {
        'email': email.trim(),
        'password': password,
        if (name != null) 'name': name,
        if (name != null) 'phone': phone,
      },
    );
    _generation++;
    token = data['token'];
    user = Map<String, dynamic>.from(data['user']);
    await platform.invokeMethod('write', {'key': 'token', 'value': token});
    await refresh();
    connect();
    changed();
  }

  Future<void> clearSession() async {
    _generation++;
    token = '';
    user = null;
    orders = [];
    users = [];
    addresses = [];
    audit = [];
    reports = {};
    cart.clear();
    _retry?.cancel();
    _heartbeat?.cancel();
    _socket?.close();
    _socket = null;
    live = false;
    try {
      await platform.invokeMethod('write', {'key': 'token', 'value': ''});
    } catch (_) {}
    changed();
  }

  Future<void> logout() async {
    try {
      await request('POST', '/api/auth/logout');
    } catch (_) {}
    await clearSession();
  }

  Future<void> setServer(String url) async {
    var value = url.trim().replaceAll(RegExp(r'/+$'), '');
    final uri = Uri.tryParse(value);
    if (uri == null ||
        !['http', 'https'].contains(uri.scheme) ||
        uri.host.isEmpty ||
        uri.hasQuery ||
        uri.hasFragment ||
        uri.userInfo.isNotEmpty ||
        (uri.path.isNotEmpty && uri.path != '/')) {
      throw Exception(
        'Usa la URL base del servidor, por ejemplo https://delivery.tudominio.com, sin /api.',
      );
    }
    if (uri.scheme == 'http' &&
        !['10.0.2.2', '127.0.0.1', 'localhost'].contains(uri.host)) {
      throw Exception('Para un servidor remoto utiliza HTTPS.');
    }
    final previous = server;
    server = value;
    try {
      final data = await request('GET', '/api/public', auth: false);
      await clearSession();
      business = Map<String, dynamic>.from(data['business']);
      await platform.invokeMethod('write', {'key': 'server', 'value': value});
      changed();
    } catch (e) {
      server = previous;
      rethrow;
    }
  }

  Future<void> connect() async {
    if (token.isEmpty || _disposed) return;
    final generation = _generation;
    _retry?.cancel();
    _heartbeat?.cancel();
    await _socket?.close();
    _socket = null;
    try {
      final uri = Uri.parse('$server/api/ws')
          .replace(scheme: server.startsWith('https:') ? 'wss' : 'ws');
      final socket = await WebSocket.connect(
        uri.toString(),
        headers: {'Authorization': 'Bearer $token'},
      ).timeout(const Duration(seconds: 10));
      if (generation != _generation || _disposed) {
        await socket.close();
        return;
      }
      _socket = socket;
      live = true;
      _retrySeconds = 2;
      changed();
      _heartbeat = Timer.periodic(const Duration(seconds: 20), (_) {
        socket.add('ping');
      });
      socket.listen(
        (message) {
          final event = jsonDecode(message);
          if (event['type'] != 'pong') {
            _debounce?.cancel();
            _debounce = Timer(
              const Duration(milliseconds: 150),
              () => unawaited(refreshQuietly()),
            );
          }
        },
        onDone: () => _disconnected(generation),
        onError: (Object e) => _disconnected(generation),
        cancelOnError: true,
      );
    } catch (_) {
      _disconnected(generation);
    }
  }

  void _disconnected(int generation) {
    if (generation != _generation || _disposed) return;
    live = false;
    _heartbeat?.cancel();
    changed();
    _retry?.cancel();
    if (token.isNotEmpty) {
      _retry = Timer(
        Duration(seconds: _retrySeconds),
        () => unawaited(connect()),
      );
      _retrySeconds = (_retrySeconds * 2).clamp(2, 30);
    }
  }

  void add(Json product, [int delta = 1]) {
    final id = product['id'] as int;
    final value = (cart[id] ?? 0) + delta;
    if (value <= 0) {
      cart.remove(id);
    } else {
      cart[id] = value.clamp(0, (product['stock'] as int).clamp(0, 999));
    }
    changed();
  }

  Future<String?> pickImage() async {
    final bytes = await platform.invokeMethod<Uint8List>('pickImage');
    if (bytes == null) return null;
    final client = HttpClient()
      ..connectionTimeout = const Duration(seconds: 10);
    try {
      final boundary = 'integral${DateTime.now().microsecondsSinceEpoch}';
      final req = await client.postUrl(Uri.parse('$server/api/admin/uploads'));
      req.headers.set('Authorization', 'Bearer $token');
      req.headers.set(
        'Content-Type',
        'multipart/form-data; boundary=$boundary',
      );
      req.add(
        utf8.encode(
          '--$boundary\r\nContent-Disposition: form-data; name="file"; filename="image.jpg"\r\nContent-Type: application/octet-stream\r\n\r\n',
        ),
      );
      req.add(bytes);
      req.add(utf8.encode('\r\n--$boundary--\r\n'));
      final response = await req.close().timeout(const Duration(seconds: 30));
      final data = jsonDecode(await utf8.decoder.bind(response).join());
      if (response.statusCode >= 400)
        throw Exception(data['detail'] ?? 'No se pudo cargar la imagen.');
      return data['url'];
    } finally {
      client.close(force: true);
    }
  }

  @override
  void dispose() {
    _disposed = true;
    _generation++;
    _poll?.cancel();
    _retry?.cancel();
    _heartbeat?.cancel();
    _debounce?.cancel();
    _socket?.close();
    super.dispose();
  }
}
