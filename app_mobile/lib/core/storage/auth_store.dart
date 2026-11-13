import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Lưu mọi thứ nhạy cảm vào Keystore (Android) / Keychain (iOS).
class AuthStore {
  static const _s = FlutterSecureStorage();

  static const _kAdminId = 'adminId';
  static const _kName = 'adminName';
  static const _kAccess = 'accessToken';
  static const _kRefresh = 'refreshToken';
  static const _kSecret = 'totpSecret';   // khóa Base32 đã xác nhận khớp server
  static const _kPeriod = 'totpPeriod';
  static const _kOffset = 'serverOffsetMs';

  static Future<void> saveSession({
    required String adminId,
    required String name,
    required String accessToken,
    required String refreshToken,
  }) async {
    await _s.write(key: _kAdminId, value: adminId);
    await _s.write(key: _kName, value: name);
    await _s.write(key: _kAccess, value: accessToken);
    await _s.write(key: _kRefresh, value: refreshToken);
  }

  static Future<String?> get adminId => _s.read(key: _kAdminId);
  static Future<String?> get name => _s.read(key: _kName);
  static Future<String?> get accessToken => _s.read(key: _kAccess);
  static Future<String?> get refreshToken => _s.read(key: _kRefresh);

  static Future<void> updateAccessToken(String t) => _s.write(key: _kAccess, value: t);

  /// Chỉ gọi SAU KHI server đã xác nhận mã khớp.
  static Future<void> saveTotp(String secret, int period, int offsetMs) async {
    await _s.write(key: _kSecret, value: secret);
    await _s.write(key: _kPeriod, value: period.toString());
    await _s.write(key: _kOffset, value: offsetMs.toString());
  }

  static Future<String?> get totpSecret => _s.read(key: _kSecret);
  static Future<int> get totpPeriod async =>
      int.tryParse(await _s.read(key: _kPeriod) ?? '') ?? 30;
  static Future<int> get serverOffsetMs async =>
      int.tryParse(await _s.read(key: _kOffset) ?? '') ?? 0;

  static Future<bool> get hasTotp async => (await totpSecret) != null;

  static Future<void> clearTotp() async {
    await _s.delete(key: _kSecret);
    await _s.delete(key: _kPeriod);
    await _s.delete(key: _kOffset);
  }

  static Future<void> clearAll() => _s.deleteAll();
}