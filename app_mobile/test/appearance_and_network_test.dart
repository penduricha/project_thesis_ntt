import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:my_flutter_app/core/appearance/app_preferences.dart';
import 'package:my_flutter_app/core/appearance/app_theme.dart';
import 'package:my_flutter_app/core/localization/app_strings.dart';
import 'package:my_flutter_app/core/network/server_connection.dart';
import 'package:my_flutter_app/core/network/server_probe.dart';
import 'package:my_flutter_app/features/settings/presentation/settings_screen.dart';

class _Adapter implements HttpClientAdapter {
  bool offline = false;
  int status = 200;
  RequestOptions? lastRequest;

  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<Uint8List>? requestStream, Future<void>? cancelFuture) async {
    lastRequest = options;
    if (offline) {
      throw DioException(requestOptions: options, type: DioExceptionType.connectionError);
    }
    return ResponseBody.fromString('{}', status, headers: {Headers.contentTypeHeader: ['application/json']});
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('theme and language are restored independently of the login session', () async {
    FlutterSecureStorage.setMockInitialValues({});
    final first = AppPreferences();
    await first.setTheme(ThemeMode.dark);
    await first.setLocale(const Locale('es', 'ES'));
    final restored = AppPreferences();
    await restored.load();
    expect(restored.themeMode, ThemeMode.dark);
    expect(restored.locale, const Locale('es', 'ES'));
    expect(AppTheme.create(Brightness.dark).scaffoldBackgroundColor, const Color(0xff0b1220));
  });

  test('every locale has the same common settings and validation messages', () {
    const keys = ['settings', 'appearance', 'light', 'dark', 'system', 'language', 'network', 'login', 'enterId', 'enterPassword', 'secretTitle', 'secretLength', 'authenticatorTab', 'chatTab'];
    for (final locale in AppStrings.locales) {
      final strings = AppStrings(locale);
      for (final key in keys) {
        expect(strings.text(key), isNot(key), reason: '${locale.languageCode}: $key');
      }
      expect(strings.text('secretLength'), contains('{length}'));
      expect(strings.text('secretLength'), contains('{count}'));
    }
    expect(AppStrings(const Locale('vi')).text('network'), 'Không thể kết nối tới máy chủ.');
  });

  testWidgets('settings lets users select a theme and locale', (tester) async {
    FlutterSecureStorage.setMockInitialValues({});
    await AppPreferences.instance.load();
    await tester.pumpWidget(const MaterialApp(home: SettingsScreen()));
    await tester.tap(find.text('Dark'));
    await tester.pump();
    expect(AppPreferences.instance.themeMode, ThemeMode.dark);
    await tester.ensureVisible(find.text('Español (España)'));
    await tester.tap(find.text('Español (España)'));
    await tester.pump();
    expect(AppPreferences.instance.locale, const Locale('es', 'ES'));
  });

  test('read-only startup probe treats 401 as reachable and does not send credentials', () async {
    final adapter = _Adapter();
    final client = Dio(BaseOptions(baseUrl: 'https://example.invalid'))..httpClientAdapter = adapter;
    adapter.offline = true;
    await ServerProbe.check(client: client);
    expect(ServerConnection.unavailable.value, isTrue);
    expect(adapter.lastRequest?.method, 'GET');
    expect(adapter.lastRequest?.path, '/admin/auth/login/auto/access-token');
    expect(adapter.lastRequest?.headers.containsKey('Authorization'), isFalse);
    expect(adapter.lastRequest?.data, isNull);
    adapter.offline = false;
    adapter.status = 401;
    await ServerProbe.check(client: client);
    expect(ServerConnection.unavailable.value, isFalse);
    client.close();
  });

  test('connection failures show the banner; HTTP errors are not network failures', () async {
    final adapter = _Adapter();
    final client = Dio(BaseOptions(baseUrl: 'https://example.invalid'));
    client.httpClientAdapter = adapter;
    client.interceptors.add(ServerConnection.interceptor());
    ServerConnection.unavailable.value = false;
    adapter.offline = true;
    await expectLater(client.get('/test'), throwsA(isA<DioException>()));
    expect(ServerConnection.unavailable.value, isTrue);
    adapter.offline = false;
    adapter.status = 500;
    await expectLater(client.get('/test'), throwsA(isA<DioException>()));
    expect(ServerConnection.unavailable.value, isFalse);
    adapter.status = 200;
    await client.get('/test');
    expect(ServerConnection.unavailable.value, isFalse);
    client.close();
  });
}
