import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:my_flutter_app/features/authenticator/data/model/auth_account.dart';
import 'package:my_flutter_app/features/authenticator/data/service/account_store.dart';
import 'package:my_flutter_app/features/authenticator/data/service/enrollment_service.dart';

const _session = 'a9c62262-bdd5-43a9-8a3b-f89bc0c3a870';
const _otherSession = 'a9c62262-bdd5-43a9-8a3b-f89bc0c3a871';
const _secret = 'GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ'; // public test vector

String _token(String session) {
  String enc(Object object) => base64Url.encode(utf8.encode(jsonEncode(object))).replaceAll('=', '');
  // Test fixture only; server authentication is not exercised by this adapter.
  return '${enc({'alg': 'test'})}.${enc({'sub': '1', 'client': 'MOBILE', 'token_type': 'ACCESS', 'session_id': session})}.signature';
}

class _Auth extends EnrollmentAuth {
  String session = _session;
  @override
  Future<String?> accessToken() async => _token(session);
  @override
  Future<AdminProfile?> profile() async => const AdminProfile(adminId: '1', name: 'Test');
}

class _SwitchOnUnauthorized implements HttpClientAdapter {
  _SwitchOnUnauthorized(this.auth);
  final _Auth auth;
  int confirms = 0;
  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<Uint8List>? requestStream, Future<void>? cancelFuture) async {
    if (options.path.endsWith('/pending')) {
      return ResponseBody.fromString(jsonEncode({'success': true, 'data': {
        'credentialId': 42, 'periodSeconds': 30, 'digits': 6, 'algorithm': 'HmacSHA256'}}), 200,
        headers: {Headers.contentTypeHeader: ['application/json']});
    }
    confirms++;
    auth.session = _otherSession;
    return ResponseBody.fromString('{}', 401, headers: {Headers.contentTypeHeader: ['application/json']});
  }
  @override
  void close({bool force = false}) {}
}

class _LostResponseAdapter implements HttpClientAdapter {
  final requests = <Map<String, dynamic>>[];
  bool committed = false;
  int v2Requests = 0;
  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<Uint8List>? requestStream, Future<void>? cancelFuture) async {
    ResponseBody ok(Object data) => ResponseBody.fromString(jsonEncode(data), 200,
        headers: {Headers.contentTypeHeader: ['application/json']});
    if (options.path.endsWith('/pending')) {
      return ok({'success': true, 'data': {'credentialId': 42, 'periodSeconds': 30,
        'digits': 6, 'algorithm': 'HmacSHA256'}});
    }
    if (options.path.endsWith('/confirm-v2')) {
      requests.add(Map<String, dynamic>.from(options.data as Map));
      v2Requests++;
      if (!committed) {
        committed = true; // simulate DB commit before the client loses the response
        throw DioException(requestOptions: options, type: DioExceptionType.connectionError);
      }
      if (jsonEncode(requests.first) != jsonEncode(requests.last)) {
        return ResponseBody.fromString('{}', 409, headers: {Headers.contentTypeHeader: ['application/json']});
      }
      return ok({'success': true, 'data': {'credentialId': 42, 'totpEnabled': true}});
    }
    throw StateError('Unexpected endpoint ${options.path}');
  }
  @override
  void close({bool force = false}) {}
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('concurrent prepare keeps one attempt and refuses a different session', () async {
    FlutterSecureStorage.setMockInitialValues({});
    final store = AccountStore(adminId: '1');
    const account = AuthAccount(id: 'admin_1', issuer: 'Test', account: '1',
        secret: _secret, credentialId: 42);
    await store.stage(42, account);
    final first = ConfirmAttempt(requestId: 'aa' * 32, sessionId: _session, createdAtMs: 1000);
    final second = ConfirmAttempt(requestId: 'bb' * 32, sessionId: _session, createdAtMs: 2000);
    final results = await Future.wait([
      store.prepareConfirmAttempt(42, account, first),
      store.prepareConfirmAttempt(42, account, second),
    ]);
    expect(results[0].requestId, results[1].requestId);
    expect(results[0].createdAtMs, results[1].createdAtMs);
    await expectLater(store.prepareConfirmAttempt(42, account,
      ConfirmAttempt(requestId: 'cc' * 32, sessionId: _otherSession, createdAtMs: 3000)),
      throwsA(isA<StateError>()));
    expect((await store.loadConfirmAttempt(42))!.requestId, results[0].requestId);
  });

  test('expired or malformed retry metadata never triggers a confirmation or fallback', () async {
    FlutterSecureStorage.setMockInitialValues({});
    final store = AccountStore(adminId: '1');
    const account = AuthAccount(id: 'admin_1', issuer: 'Test', account: '1',
        secret: _secret, credentialId: 42);
    await store.stage(42, account);
    await store.prepareConfirmAttempt(42, account, ConfirmAttempt(
      requestId: 'aa' * 32, sessionId: _session,
      createdAtMs: DateTime.now().subtract(const Duration(hours: 24)).millisecondsSinceEpoch));
    final adapter = _LostResponseAdapter();
    final dio = Dio(BaseOptions(baseUrl: 'https://example.invalid'))..httpClientAdapter = adapter;
    final service = EnrollmentService(auth: _Auth(), dio: dio, useIdempotentConfirm: true);
    expect(await service.isConfirmedCredential(42, account), isFalse);
    expect(adapter.v2Requests, 0);
    await const FlutterSecureStorage().write(key: 'authenticator_confirm_v2_1_42', value: 'invalid');
    await expectLater(service.isConfirmedCredential(42, account), throwsA(isA<FormatException>()));
    expect(adapter.v2Requests, 0);
    expect((await store.loadPending()).single.account.secret, _secret);
    dio.close();
  });

  test('lost response reuses persisted request and regenerated OTP; promotion clears metadata', () async {
    FlutterSecureStorage.setMockInitialValues({});
    final auth = _Auth();
    final adapter = _LostResponseAdapter();
    final dio = Dio(BaseOptions(baseUrl: 'https://example.invalid'))..httpClientAdapter = adapter;
    final service = EnrollmentService(auth: auth, dio: dio, useIdempotentConfirm: true);
    await expectLater(service.activate(_secret), throwsA(isA<EnrollmentException>()));
    final store = AccountStore(adminId: '1');
    final pending = (await store.loadPending()).single;
    final metadata = await const FlutterSecureStorage().read(key: 'authenticator_confirm_v2_1_42');
    expect(metadata, isNotNull);
    expect((jsonDecode(metadata!) as Map).keys.toSet(), {'version', 'requestId', 'sessionId', 'createdAtMs'});
    expect((await store.loadConfirmAttempt(42))!.requestId, matches(RegExp(r'^[0-9a-f]{64}$')));
    expect(await service.isConfirmedCredential(42, pending.account), isTrue);
    expect(adapter.requests, hasLength(2));
    expect(adapter.requests.last, adapter.requests.first);
    await store.promotePending(42, pending.account);
    expect(await store.loadConfirmAttempt(42), isNull);
    expect(await store.loadPending(), isEmpty);
    expect((await store.load()).single.secret, _secret);
    dio.close();
  });

  test('401 cannot resend confirmation under a replacement session for the same admin', () async {
    FlutterSecureStorage.setMockInitialValues({});
    final auth = _Auth();
    final adapter = _SwitchOnUnauthorized(auth);
    final dio = Dio(BaseOptions(baseUrl: 'https://example.invalid'))..httpClientAdapter = adapter;
    final service = EnrollmentService(auth: auth, dio: dio, useIdempotentConfirm: true);
    await expectLater(service.activate(_secret), throwsA(isA<EnrollmentException>()));
    expect(adapter.confirms, 1);
    expect((await AccountStore(adminId: '1').loadConfirmAttempt(42))!.sessionId, _session);
    expect((await AccountStore(adminId: '1').loadPending()).single.account.secret, _secret);
    dio.close();
  });

  test('changed session or disabled flag cannot retry or fall back to old receipt', () async {
    FlutterSecureStorage.setMockInitialValues({});
    final auth = _Auth();
    final adapter = _LostResponseAdapter();
    final dio = Dio(BaseOptions(baseUrl: 'https://example.invalid'))..httpClientAdapter = adapter;
    final service = EnrollmentService(auth: auth, dio: dio, useIdempotentConfirm: true);
    await expectLater(service.activate(_secret), throwsA(isA<EnrollmentException>()));
    final store = AccountStore(adminId: '1');
    final item = (await store.loadPending()).single;
    auth.session = _otherSession;
    expect(await service.isConfirmedCredential(42, item.account), isFalse);
    expect(adapter.v2Requests, 1);
    auth.session = _session;
    final disabled = EnrollmentService(auth: auth, dio: dio, useIdempotentConfirm: false);
    expect(await disabled.isConfirmedCredential(42, item.account), isFalse);
    expect(adapter.v2Requests, 1);
    expect((await store.loadPending()).single.account.secret, _secret);
    dio.close();
  });
}
