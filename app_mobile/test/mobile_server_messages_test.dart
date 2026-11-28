import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:my_flutter_app/core/appearance/app_preferences.dart';
import 'package:my_flutter_app/core/localization/app_strings.dart';
import 'package:my_flutter_app/core/localization/mobile_server_messages.dart';
import 'package:my_flutter_app/features/authenticator/data/service/enrollment_service.dart';
import 'package:my_flutter_app/features/authenticator/presentation/screens/totp_generator.dart';
import 'package:my_flutter_app/features/chat/data/api/chat_api_exception.dart';
import 'package:my_flutter_app/features/login/data/model/admin_account.dart';
import 'package:my_flutter_app/features/login/data/model/login_result.dart';
import 'package:my_flutter_app/features/login/data/service/login_service.dart';

class _Auth extends EnrollmentAuth {
  @override
  Future<String?> accessToken() async => 'test-token';
  @override
  Future<AdminProfile?> profile() async => const AdminProfile(adminId: '1', name: 'Test');
}

class _ResponseAdapter implements HttpClientAdapter {
  _ResponseAdapter(this.status, this.data);
  final int status;
  final Map<String, Object?> data;
  int requests = 0;
  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<Uint8List>? requestStream, Future<void>? cancelFuture) async {
    requests++;
    return ResponseBody.fromString(jsonEncode(data), status,
        headers: {Headers.contentTypeHeader: ['application/json']});
  }
  @override
  void close({bool force = false}) {}
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  test('translations exist in all six locales and interpolate server parameters', () {
    for (final locale in AppStrings.locales) {
      for (final key in ['totpRequired', 'totpInvalid', 'loginLockedSeconds', 'noPending', 'replayed', 'disableLocked', 'NOT_OWNER', 'GROUP_MIN_SIZE']) {
        final text = MobileServerMessages.lookup(key, locale: locale, params: {'seconds': 12, 'minutes': 3});
        expect(text, isNotNull, reason: '${locale.languageCode}: $key');
        expect(text, isNot(contains('{')));
      }
      expect(MobileServerMessages.lookup('unknown', locale: locale), isNull);
    }
  });

  test('chat maps known codes without changing original or retry semantics', () async {
    await AppPreferences.instance.setLocale(const Locale('en'));
    const error = ChatApiException(statusCode: 403, code: 'NOT_OWNER', message: 'Original server text');
    expect(error.userMessage, 'Only the group owner can perform this action.');
    expect(error.message, 'Original server text');
    expect(error.code, 'NOT_OWNER');
    expect(error.isRetryable, isFalse);
    await AppPreferences.instance.setLocale(const Locale('vi'));
    expect(error.userMessage, 'Chỉ chủ nhóm mới có thể thực hiện thao tác này.');
    const unknown = ChatApiException(statusCode: 400, code: 'NEW_ERROR', message: 'New server detail');
    expect(unknown.userMessage, 'New server detail');
    const serverError = ChatApiException(statusCode: 500, code: 'NEW_ERROR', message: 'internal detail');
    expect(serverError.userMessage, MobileServerMessages.lookup('server'));
    expect(serverError.isRetryable, isTrue);
  });

  test('login retains server outcome and lock duration', () async {
    final adapter = _ResponseAdapter(200, {
      'success': false, 'message': 'Original server message',
      'data': {'code': 'LOCKED', 'retryAfterSeconds': 77},
    });
    final dio = Dio(BaseOptions(baseUrl: 'https://example.invalid', validateStatus: (status) => status != null && status < 500))
      ..httpClientAdapter = adapter;
    final result = await LoginService(dio: dio).login(const AdminLogin(
      adminId: 123, password: 'not-a-real-password', deviceName: 'Test', platform: 'TEST',
    ));
    expect(result, isA<LoginTotpRequired>());
    final locked = result as LoginTotpRequired;
    expect(locked.reason, LoginTotpFailureReason.locked);
    expect(locked.retryAfterSeconds, 77);
    expect(locked.message, 'Too many attempts. Try again in 77 seconds.');
    expect(adapter.requests, 1);
    dio.close();
  });

  test('2FA disable accepts only a matching explicit success response', () async {
    for (final sample in [
      {'success': true, 'data': {'totpEnabled': false}},
      {'success': true, 'data': <String, Object?>{}},
      {'success': false, 'data': {'totpEnabled': false}},
    ]) {
      final adapter = _ResponseAdapter(200, sample);
      final dio = Dio(BaseOptions(baseUrl: 'https://example.invalid'))..httpClientAdapter = adapter;
      final result = EnrollmentService(auth: _Auth(), dio: dio).disable(
        secret: 'GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ', periodSeconds: 30, digits: 6, algorithm: TotpAlgorithm.sha1,
      );
      if ((sample['data'] as Map)['totpEnabled'] == false && sample['success'] == true) {
        expect(await result, DisableOutcome.disabled);
      } else {
        await expectLater(result, throwsA(isA<EnrollmentException>()));
      }
      expect(adapter.requests, 1);
      dio.close();
    }
  });

  test('2FA disable keeps the local key on an ambiguous 404', () async {
    final adapter = _ResponseAdapter(404, {'message': 'Not found'});
    final dio = Dio(BaseOptions(baseUrl: 'https://example.invalid'))..httpClientAdapter = adapter;
    final service = EnrollmentService(auth: _Auth(), dio: dio);
    await expectLater(
      service.disable(secret: 'GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ', periodSeconds: 30, digits: 6, algorithm: TotpAlgorithm.sha1),
      throwsA(isA<EnrollmentException>()),
    );
    expect(adapter.requests, 1);
    dio.close();
  });

  test('2FA disable preserves replay and lock outcomes and makes one request', () async {
    await AppPreferences.instance.setLocale(const Locale('en'));
    for (final sample in <Map<String, Object>>[
      {'status': 422, 'data': <String, Object?>{'data': {'code': 'TOTP_REPLAYED'}}, 'key': 'replayed'},
      {'status': 429, 'data': <String, Object?>{'data': {'code': 'LOCKED', 'retryAfterSeconds': 120}}, 'key': 'disableLocked'},
    ]) {
      final key = sample['key'] as String;
      final adapter = _ResponseAdapter(sample['status'] as int, sample['data'] as Map<String, Object?>);
      final dio = Dio(BaseOptions(baseUrl: 'https://example.invalid'))..httpClientAdapter = adapter;
      final service = EnrollmentService(auth: _Auth(), dio: dio);
      try {
        await service.disable(secret: 'GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ', periodSeconds: 30, digits: 6, algorithm: TotpAlgorithm.sha1);
        fail('Expected a rejected disable request');
      } on EnrollmentException catch (error) {
        expect(error.messageKey, key);
        expect(error.userMessage, MobileServerMessages.lookup(key, params: key == 'disableLocked' ? {'minutes': 2} : {}));
        expect(error.message, isNotEmpty);
      }
      expect(adapter.requests, 1);
      dio.close();
    }
  });
}
