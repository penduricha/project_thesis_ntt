import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../model/auth_account.dart';

// ============================================================================
//  LƯU TÀI KHOẢN AUTHENTICATOR  (MỖI ADMIN MỘT KHÓA LƯU RIÊNG)
//
//  Khóa bí mật phải nằm ở vùng lưu trữ an toàn (Android Keystore / iOS Keychain),
//  không dùng SharedPreferences, không ghi log.
//
//  Nghiệp vụ: chỉ adminId đang đăng nhập mới thấy TOTP của chính mình.
//   - Key lưu:  authenticator_accounts_<adminId>   (admin khác không đọc được key này)
//   - load() / save() còn lọc thêm theo adminId, phòng dữ liệu bị lẫn.
//   - Key cũ dùng chung `authenticator_accounts` (bản demo) bị xóa ở lần load đầu tiên;
//     chỉ mục của CHÍNH admin này (nếu có) được chuyển sang key riêng.
// ============================================================================

class AccountStore {
  AccountStore({required this.adminId, FlutterSecureStorage? storage})
      : _storage = storage ?? const FlutterSecureStorage();

  /// adminId đang đăng nhập, dạng chuỗi (vd: "2500021773"). Khớp AuthAccount.account.
  final String adminId;

  static const String _keyPrefix = 'authenticator_accounts_';
  static const String _legacyKey = 'authenticator_accounts'; // key chung của bản demo

  final FlutterSecureStorage _storage;

  String get _key => '$_keyPrefix$adminId';

  /// Danh sách TOTP của admin này (không bao giờ trả mục của admin khác).
  Future<List<AuthAccount>> load() async {
    await _migrateLegacy();
    final stored = await _read(_key);
    return stored.where((a) => a.account == adminId).toList();
  }

  Future<void> save(Iterable<AuthAccount> accounts) {
    final own = accounts.where((a) => a.account == adminId);
    final json = jsonEncode(own.map((a) => a.toJson()).toList());
    return _storage.write(key: _key, value: json);
  }

  /// Xóa TOTP của admin này khỏi máy (dùng nếu muốn xóa khi đăng xuất).
  Future<void> clear() => _storage.delete(key: _key);

  Future<List<AuthAccount>> _read(String key) async {
    try {
      final raw = await _storage.read(key: key);
      if (raw == null || raw.isEmpty) return [];
      final list = jsonDecode(raw) as List<dynamic>;
      return list
          .map((e) => AuthAccount.fromJson(e as Map<String, dynamic>))
          .toList();
    } catch (_) {
      return []; // dữ liệu hỏng: coi như chưa có, không làm app crash
    }
  }

  /// Một lần: giữ lại mục của chính admin này, xóa toàn bộ phần còn lại của key cũ
  /// (mục demo, mục của admin khác).
  Future<void> _migrateLegacy() async {
    try {
      final raw = await _storage.read(key: _legacyKey);
      if (raw == null) return;

      final mine = (await _read(_legacyKey))
          .where((a) => a.account == adminId)
          .toList();
      final alreadyHasOwn = (await _storage.read(key: _key)) != null;
      if (mine.isNotEmpty && !alreadyHasOwn) {
        await save(mine);
      }
      await _storage.delete(key: _legacyKey);
    } catch (_) {
      // không migrate được: bỏ qua, mục cũ vẫn không hiển thị vì đã lọc theo adminId
    }
  }
}