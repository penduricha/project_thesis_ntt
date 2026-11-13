import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../model/auth_account.dart';

// ============================================================================
//  LƯU TÀI KHOẢN AUTHENTICATOR
//  Khóa bí mật phải nằm ở vùng lưu trữ an toàn (Android Keystore / iOS Keychain),
//  không dùng SharedPreferences, không ghi log.
//  Chỉ lưu tài khoản kích hoạt từ server (enrolled). Tài khoản demo không lưu.
// ============================================================================

class AccountStore {
  AccountStore({FlutterSecureStorage? storage})
      : _storage = storage ?? const FlutterSecureStorage();

  static const String _key = 'authenticator_accounts';

  final FlutterSecureStorage _storage;

  Future<List<AuthAccount>> load() async {
    try {
      final raw = await _storage.read(key: _key);
      if (raw == null || raw.isEmpty) return [];
      final list = jsonDecode(raw) as List<dynamic>;
      return list
          .map((e) => AuthAccount.fromJson(e as Map<String, dynamic>))
          .toList();
    } catch (_) {
      return []; // dữ liệu hỏng: coi như chưa có, không làm app crash
    }
  }

  Future<void> save(Iterable<AuthAccount> accounts) {
    final json = jsonEncode(accounts.map((a) => a.toJson()).toList());
    return _storage.write(key: _key, value: json);
  }
}