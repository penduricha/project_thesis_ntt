import 'package:flutter/foundation.dart'
    show kIsWeb, kReleaseMode, defaultTargetPlatform, TargetPlatform;
import 'package:flutter_dotenv/flutter_dotenv.dart';

/// Đọc toàn bộ cấu hình mạng từ file .env.
/// Key nào thiếu hoặc để trống thì dùng giá trị mặc định (fallback).
///
/// Quy ước đường dẫn (khớp với Spring Boot):
///   Spring: context-path "/api" + @RequestMapping("/mobile/admin") -> /api/mobile/admin/...
///   Flutter: SPRING_API_PREFIX = "/api/mobile", mỗi service chỉ ghi phần đuôi "/admin/...".
/// Mọi service gọi Spring (kể cả 2FA) đều phải dùng [requestSpringBoot], không tự ghép host / prefix riêng.
class NetworkConfiguration {
  NetworkConfiguration._();

  // ---------- Hàm đọc .env ----------
  static String _str(String key, String fallback) {
    final value = dotenv.maybeGet(key)?.trim();
    return (value == null || value.isEmpty) ? fallback : value;
  }

  /// Chuẩn hóa tiền tố path: luôn bắt đầu bằng "/" và không kết thúc bằng "/".
  /// Gõ "api/mobile" hay "/api/mobile/" trong .env vẫn ra "/api/mobile",
  /// tránh path bị ghép sai (thiếu "/" hoặc thừa "//") dẫn tới 404.
  static String _prefix(String key, String fallback) {
    var value = _str(key, fallback);
    if (!value.startsWith('/')) value = '/$value';
    while (value.endsWith('/')) {
      value = value.substring(0, value.length - 1);
    }
    return value;
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
  static String get apiPrefix => _prefix('SPRING_API_PREFIX', '/api/mobile');

  // Basic Auth gửi lên Spring Boot (để trống nếu server không yêu cầu).
  static String get springAuthUsername => _str('SPRING_AUTH_USERNAME', '');
  static String get springAuthPassword => _str('SPRING_AUTH_PASSWORD', '');
  static bool get hasSpringBasicAuth =>
      springAuthUsername.isNotEmpty || springAuthPassword.isNotEmpty;

  // ---------- Node.js ----------
  static String get nodeJsProtocol => _str('NODEJS_PROTOCOL', 'http');
  static String get nodeJsHost => _str('NODEJS_HOST', _devHost);
  static int get nodeJsPort => _int('NODEJS_PORT', 3000);
  static String get nodeJsApiPrefix => _prefix('NODEJS_API_PREFIX', '/api/mobile');

  // ---------- Thời gian chờ ----------
  static Duration get connectTimeout => _seconds('CONNECT_TIMEOUT_SECONDS', 10);
  static Duration get sendTimeout => _seconds('SEND_TIMEOUT_SECONDS', 15);
  static Duration get receiveTimeout => _seconds('RECEIVE_TIMEOUT_SECONDS', 15);

  // ---------- Base URL ----------
  static String requestSpringBoot() {
    final url = '$springProtocol://$springHost:$springPort$apiPrefix';
    if (kReleaseMode) {
      // The bundled .env is public configuration, not a place for credentials.
      // Never silently fall back to an emulator address or plaintext in release.
      if (_str('SPRING_PROTOCOL', '').isEmpty || _str('SPRING_HOST', '').isEmpty ||
          !isSafeReleaseSpringUrl(url)) {
        throw StateError('Release requires an explicit HTTPS Spring host');
      }
    }
    return url;
  }

  /// Structural validation only; the platform TLS stack must still validate certificates.
  static bool isSafeReleaseSpringUrl(String url) {
    final uri = Uri.tryParse(url);
    if (uri == null || uri.scheme != 'https' || !uri.hasAuthority ||
        uri.host.isEmpty || uri.userInfo.isNotEmpty || uri.hasQuery || uri.hasFragment ||
        uri.port < 1 || uri.port > 65535) return false;
    final host = uri.host.toLowerCase();
    return host != 'localhost' && !host.endsWith('.localhost') &&
        host != '127.0.0.1' && host != '::1' && host != '10.0.2.2';
  }

  static String requestNodeJS() =>
      '$nodeJsProtocol://$nodeJsHost:$nodeJsPort$nodeJsApiPrefix';
}