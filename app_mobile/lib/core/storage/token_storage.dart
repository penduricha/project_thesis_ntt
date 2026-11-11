import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Cặp token do server cấp khi đăng nhập.
class AuthTokens {
  const AuthTokens({required this.accessToken, required this.refreshToken});

  final String accessToken;
  final String refreshToken;
}

/// Lưu token cục bộ trên máy (tương đương localStorage ở Web).
///
/// Dùng flutter_secure_storage: dữ liệu được mã hóa bằng Android Keystore /
/// iOS Keychain nên an toàn hơn SharedPreferences (lưu chữ thuần).
/// Muốn đổi cách lưu thì chỉ cần sửa class này.
class TokenStorage {
  TokenStorage({FlutterSecureStorage? storage})
      : _storage = storage ?? const FlutterSecureStorage();

  static const String _accessTokenKey = 'access_token';
  static const String _refreshTokenKey = 'refresh_token';

  final FlutterSecureStorage _storage;

  Future<void> saveTokens(AuthTokens tokens) async {
    await Future.wait([
      _storage.write(key: _accessTokenKey, value: tokens.accessToken),
      _storage.write(key: _refreshTokenKey, value: tokens.refreshToken),
    ]);
  }

  /// Dùng cho chức năng auto login sau này. Trả về null nếu chưa có token.
  Future<AuthTokens?> readTokens() async {
    final accessToken = await _storage.read(key: _accessTokenKey);
    final refreshToken = await _storage.read(key: _refreshTokenKey);
    if (accessToken == null || refreshToken == null) return null;
    return AuthTokens(accessToken: accessToken, refreshToken: refreshToken);
  }

  /// Gọi khi đăng xuất.
  Future<void> clearTokens() async {
    await Future.wait([
      _storage.delete(key: _accessTokenKey),
      _storage.delete(key: _refreshTokenKey),
    ]);
  }
}
