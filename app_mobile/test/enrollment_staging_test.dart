import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:my_flutter_app/features/authenticator/data/model/auth_account.dart';
import 'package:my_flutter_app/features/authenticator/data/service/account_store.dart';
import 'package:my_flutter_app/features/authenticator/data/service/enrollment_service.dart';
import 'package:my_flutter_app/features/authenticator/presentation/screens/totp_generator.dart';

class _Auth extends EnrollmentAuth {
  @override
  Future<String?> accessToken() async => 'test-token';
  @override
  Future<AdminProfile?> profile() async => const AdminProfile(adminId: '1', name: 'Test');
}

class _TokenLostAuth extends EnrollmentAuth {
  int calls = 0;
  @override
  Future<String?> accessToken() async => ++calls == 1 ? 'test-token' : null;
  @override
  Future<AdminProfile?> profile() async => const AdminProfile(adminId: '1', name: 'Test');
}

class _SwitchingAuth extends EnrollmentAuth {
  String adminId = '1';
  int refreshes = 0;
  @override
  Future<String?> accessToken() async => 'token-for-$adminId';
  @override
  Future<AdminProfile?> profile() async => AdminProfile(adminId: adminId, name: 'Test');
  @override
  Future<String?> refreshAccessToken() async { refreshes++; return 'new-token-for-$adminId'; }
}

class _SwitchOnUnauthorized implements HttpClientAdapter {
  _SwitchOnUnauthorized(this.auth);
  final _SwitchingAuth auth;
  int requests = 0;
  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<Uint8List>? requestStream, Future<void>? cancelFuture) async {
    requests++;
    auth.adminId = '2';
    return ResponseBody.fromString('{}', 401, headers: {Headers.contentTypeHeader: ['application/json']});
  }
  @override
  void close({bool force = false}) {}
}

class _Adapter implements HttpClientAdapter {
  _Adapter({this.failConfirm = false});
  final bool failConfirm;
  final paths = <String>[];
  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<Uint8List>? requestStream, Future<void>? cancelFuture) async {
    paths.add(options.path);
    if (failConfirm && options.path.endsWith('/confirm')) {
      throw DioException(requestOptions: options, type: DioExceptionType.connectionError);
    }
    return ResponseBody.fromString(jsonEncode({
      'success': true,
      'data': {'credentialId': 42, 'periodSeconds': 30, 'digits': 6, 'algorithm': 'HmacSHA256'}
    }), 200, headers: {Headers.contentTypeHeader: ['application/json']});
  }
  @override
  void close({bool force = false}) {}
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('cannot confirm if another unresolved secret is already staged', () async {
    FlutterSecureStorage.setMockInitialValues({});
    const previous = AuthAccount(id: 'admin_1', issuer: 'Test', account: '1',
        secret: 'JBSWY3DPEHPK3PXP', credentialId: 42, algorithm: TotpAlgorithm.sha256);
    await AccountStore(adminId: '1').stage(42, previous);
    final adapter = _Adapter();
    final dio = Dio(BaseOptions(baseUrl: 'https://example.invalid'))..httpClientAdapter = adapter;
    final service = EnrollmentService(auth: _Auth(), dio: dio);
    await expectLater(service.activate('GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ'),
        throwsA(isA<EnrollmentException>()));
    expect(adapter.paths, ['/admin/2fa/enrollment/pending']);
    expect((await AccountStore(adminId: '1').loadPending()).single.account.secret, previous.secret);
    dio.close();
  });

  test('a different already saved key prevents confirmation', () async {
    FlutterSecureStorage.setMockInitialValues({});
    const old = AuthAccount(id: 'admin_1', issuer: 'Test', account: '1',
        secret: 'JBSWY3DPEHPK3PXP', credentialId: 41, algorithm: TotpAlgorithm.sha256);
    await AccountStore(adminId: '1').save([old]);
    final adapter = _Adapter();
    final dio = Dio(BaseOptions(baseUrl: 'https://example.invalid'))..httpClientAdapter = adapter;
    await expectLater(EnrollmentService(auth: _Auth(), dio: dio).activate('GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ'),
        throwsA(isA<EnrollmentException>()));
    expect(adapter.paths, ['/admin/2fa/enrollment/pending']);
    expect((await AccountStore(adminId: '1').load()).single.secret, old.secret);
    dio.close();
  });

  test('lost session before confirm never falls back to the old token', () async {
    FlutterSecureStorage.setMockInitialValues({});
    final adapter = _Adapter();
    final dio = Dio(BaseOptions(baseUrl: 'https://example.invalid'))..httpClientAdapter = adapter;
    await expectLater(EnrollmentService(auth: _TokenLostAuth(), dio: dio).activate('GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ'),
        throwsA(isA<EnrollmentException>()));
    expect(adapter.paths, ['/admin/2fa/enrollment/pending']);
    expect((await AccountStore(adminId: '1').loadPending()).single.credentialId, 42);
    dio.close();
  });

  test('401 never retries a disable with another administrator session', () async {
    final auth = _SwitchingAuth();
    final adapter = _SwitchOnUnauthorized(auth);
    final dio = Dio(BaseOptions(baseUrl: 'https://example.invalid'))..httpClientAdapter = adapter;
    await expectLater(EnrollmentService(auth: auth, dio: dio).disable(
      secret: 'GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ', periodSeconds: 30,
      digits: 6, algorithm: TotpAlgorithm.sha256, credentialId: 42, expectedAdminId: '1'),
      throwsA(isA<EnrollmentException>()));
    expect(adapter.requests, 1);
    expect(auth.refreshes, 0);
    dio.close();
  });

  test('a lost confirm response retains the staged secret', () async {
    FlutterSecureStorage.setMockInitialValues({});
    const secret = 'GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ';
    final adapter = _Adapter(failConfirm: true);
    final dio = Dio(BaseOptions(baseUrl: 'https://example.invalid'))..httpClientAdapter = adapter;
    await expectLater(EnrollmentService(auth: _Auth(), dio: dio).activate(secret),
        throwsA(isA<EnrollmentException>()));
    expect(adapter.paths, ['/admin/2fa/enrollment/pending', '/admin/2fa/enrollment/confirm']);
    final pending = await AccountStore(adminId: '1').loadPending();
    expect(pending.single.credentialId, 42);
    expect(pending.single.account.secret, secret);
    expect(await AccountStore(adminId: '1').load(), isEmpty);
    dio.close();
  });
}
