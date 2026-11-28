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
