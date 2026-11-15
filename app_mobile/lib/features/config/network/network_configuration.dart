import 'package:flutter/foundation.dart'
    show kIsWeb, defaultTargetPlatform, TargetPlatform;
import 'package:flutter_dotenv/flutter_dotenv.dart';

/// Đọc toàn bộ cấu hình mạng từ file .env.
/// Key nào thiếu hoặc để trống thì dùng giá trị mặc định (fallback).
class NetworkConfiguration {
  NetworkConfiguration._();

  // ---------- Hàm đọc .env ----------
  static String _str(String key, String fallback) {
    final value = dotenv.maybeGet(key)?.trim();
    return (value == null || value.isEmpty) ? fallback : value;
  }

  static int _int(String key, int fallback) =>
      int.tryParse(dotenv.maybeGet(key)?.trim() ?? '') ?? fallback;

  static Duration _seconds(String key, int fallback) =>
      Duration(seconds: _int(key, fallback));

  // ---------- Host mặc định theo nền tảng ----------
  static bool get _isAndroid =>
      !kIsWeb && defaultTargetPlatform == TargetPlatform.android;

  static String get _devHost => _isAndroid
      ? _str('HOST_ANDROID_EMULATOR', '10.0.2.2')
      : _str('HOST_DEFAULT', 'localhost');

  // ---------- Spring Boot ----------
  static String get springProtocol => _str('SPRING_PROTOCOL', 'http');
  static String get springHost => _str('SPRING_HOST', _devHost);
  static int get springPort => _int('SPRING_PORT', 8080);
  static String get apiPrefix => _str('SPRING_API_PREFIX', '/api/mobile');

  // Basic Auth gửi lên Spring Boot (để trống nếu server không yêu cầu).
  static String get springAuthUsername => _str('SPRING_AUTH_USERNAME', '');
  static String get springAuthPassword => _str('SPRING_AUTH_PASSWORD', '');
  static bool get hasSpringBasicAuth =>
      springAuthUsername.isNotEmpty || springAuthPassword.isNotEmpty;

  // ---------- Node.js ----------
  static String get nodeJsProtocol => _str('NODEJS_PROTOCOL', 'http');
  static String get nodeJsHost => _str('NODEJS_HOST', _devHost);
  static int get nodeJsPort => _int('NODEJS_PORT', 3000);
  static String get nodeJsApiPrefix => _str('NODEJS_API_PREFIX', '/api/mobile');

  // ---------- Thời gian chờ ----------
  static Duration get connectTimeout => _seconds('CONNECT_TIMEOUT_SECONDS', 10);
  static Duration get sendTimeout => _seconds('SEND_TIMEOUT_SECONDS', 15);
  static Duration get receiveTimeout => _seconds('RECEIVE_TIMEOUT_SECONDS', 15);

  // ---------- Base URL ----------
  static String requestSpringBoot() =>
      '$springProtocol://$springHost:$springPort$apiPrefix';

  static String requestNodeJS() =>
      '$nodeJsProtocol://$nodeJsHost:$nodeJsPort$nodeJsApiPrefix';
}