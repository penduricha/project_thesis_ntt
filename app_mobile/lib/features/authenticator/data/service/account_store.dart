import 'dart:async';
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
//   - Key cũ được giữ nguyên khi migrate: có thể chứa khóa của admin khác.
//     Chỉ mục của CHÍNH admin được sao chép sang key riêng sau khi kiểm tra ghi.
// ============================================================================

class PendingAccount {
  const PendingAccount(this.credentialId, this.account);
  final int credentialId;
  final AuthAccount account;
}

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
  Future<List<AuthAccount>> load() => _serializeStage(_load);

  Future<List<AuthAccount>> _load() async {
    await _migrateLegacy();
    final stored = await _read(_key);
    if (stored.any((a) => a.account != adminId)) {
      throw StateError('Authenticator store belongs to another account');
    }
    return stored;
  }

  Future<void> save(Iterable<AuthAccount> accounts) {
    final copy = accounts.toList();
    return _serializeStage(() => _save(copy));
  }

  Future<void> _save(Iterable<AuthAccount> accounts) {
    final own = accounts.where((a) => a.account == adminId);
    final json = jsonEncode(own.map((a) => a.toJson()).toList());
    return _storage.write(key: _key, value: json);
  }

  String _pendingKey(int credentialId) => 'authenticator_pending_${adminId}_$credentialId';
  // Serialize staging operations in this isolate; cross-isolate/process storage is not transactional.
  static Future<void> _stageTail = Future<void>.value();
  static Future<T> _serializeStage<T>(Future<T> Function() work) {
    final result = Completer<T>();
    _stageTail = _stageTail.then((_) async {
      try { result.complete(await work()); }
      catch (error, stack) { result.completeError(error, stack); }
    });
    return result.future;
  }

  /// Persist separately from confirmed keys before any request that could enable 2FA.
  /// Never overwrite an ambiguous previous attempt for the same credential.
  Future<void> stage(int credentialId, AuthAccount account) => _serializeStage(() async {
    if (credentialId <= 0 || account.account != adminId || account.credentialId != credentialId) {
      throw StateError('Invalid pending account');
    }
    final current = await _load();
    if (current.any((saved) => !_sameCredential(saved, account))) {
      throw StateError('An existing authenticator key must not be overwritten');
    }
    final key = _pendingKey(credentialId);
    final encoded = jsonEncode(account.toJson());
    final previous = await _storage.read(key: key);
    if (previous != null && previous != encoded) {
      throw StateError('An unresolved enrollment is already stored');
    }
    if (previous == null) await _storage.write(key: key, value: encoded);
    if (await _storage.read(key: key) != encoded) throw StateError('Pending key could not be verified');
  });

  Future<List<PendingAccount>> loadPending() async {
    final prefix = 'authenticator_pending_${adminId}_';
    final all = await _storage.readAll();
    final result = <PendingAccount>[];
    for (final entry in all.entries) {
      if (!entry.key.startsWith(prefix)) continue;
      final id = int.tryParse(entry.key.substring(prefix.length));
      if (id == null || id <= 0) throw StateError('Invalid pending credential id');
      final account = AuthAccount.fromJson(jsonDecode(entry.value) as Map<String, dynamic>);
      if (account.account != adminId || account.credentialId != id) {
        throw StateError('Pending account mismatch');
      }
      result.add(PendingAccount(id, account));
    }
    return result;
  }

  // Compare all security-relevant parameters, not the display name. Legacy keys
  // without an ID never authorize replacing or clearing another generation.
  static bool _sameCredential(AuthAccount a, AuthAccount b) =>
      a.account == b.account && a.id == b.id && a.credentialId != null &&
      a.credentialId == b.credentialId && a.secret == b.secret &&
      a.periodSeconds == b.periodSeconds && a.digits == b.digits &&
      a.algorithm == b.algorithm;

  /// Promote only a previously staged key, without replacing a different saved key.
  /// The receipt/confirm must have succeeded before the caller invokes this method.
  Future<List<AuthAccount>> promotePending(int credentialId, AuthAccount expected) =>
      _serializeStage(() async {
        final raw = await _storage.read(key: _pendingKey(credentialId));
        if (raw == null) throw StateError('Pending key is missing');
        final pending = AuthAccount.fromJson(jsonDecode(raw) as Map<String, dynamic>);
        if (expected.account != adminId || credentialId <= 0 ||
            expected.credentialId != credentialId || !_sameCredential(pending, expected)) {
          throw StateError('Pending key changed');
        }
        final current = await _load();
        if (current.any((saved) => !_sameCredential(saved, pending))) {
          throw StateError('An existing authenticator key must not be overwritten');
        }
        if (current.isEmpty) await _save([pending]);
        final verified = await _load();
        if (verified.isEmpty || verified.any((saved) => !_sameCredential(saved, pending))) {
          throw StateError('Confirmed key could not be verified');
        }
        // A failed delete leaves a recoverable duplicate; never delete before read-back.
        await _storage.delete(key: _pendingKey(credentialId));
        return verified;
      });

  /// Remove only the exact local key whose server disable was confirmed.
  Future<List<AuthAccount>> removeConfirmed(AuthAccount expected) => _serializeStage(() async {
    if (expected.account != adminId) throw StateError('Wrong account');
    final current = await _load();
    // Legacy keys lack a credentialId: compare all their stored data exactly.
    bool matches(AuthAccount a) => expected.credentialId == null
        ? jsonEncode(a.toJson()) == jsonEncode(expected.toJson())
        : _sameCredential(a, expected);
    final remaining = current.where((a) => !matches(a)).toList();
    if (remaining.length == current.length) return current;
    await _save(remaining);
    final verified = await _load();
    if (jsonEncode(verified.map((a) => a.toJson()).toList()) !=
        jsonEncode(remaining.map((a) => a.toJson()).toList())) {
      throw StateError('Authenticator deletion could not be verified');
    }
    return verified;
  });

  /// Clear only after the confirmed key has been saved and read back successfully.
  Future<void> clearPending(int credentialId) =>
      _serializeStage(() => _storage.delete(key: _pendingKey(credentialId)));

  /// Clear this user while retaining an empty marker so legacy data is not re-imported.
  /// The shared legacy source is retained separately for other users.
  Future<void> clear() => save(const <AuthAccount>[]);

  Future<List<AuthAccount>> _read(String key) async {
    final raw = await _storage.read(key: key);
    if (raw == null) return [];
    // A corrupt/empty value is not evidence that the user has no key. Let the
    // caller block changes and report a storage error instead of overwriting it.
    final list = jsonDecode(raw) as List<dynamic>;
    return list
        .map((e) => AuthAccount.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Copy only this admin's entries and retain the shared legacy source. It may
  /// contain other users' keys, and deleting it would make their 2FA unavailable.
  Future<void> _migrateLegacy() async {
    if (await _storage.read(key: _key) != null) return;
    final raw = await _storage.read(key: _legacyKey);
    if (raw == null) return;
    final list = jsonDecode(raw) as List<dynamic>;
    final mine = list
        .map((e) => AuthAccount.fromJson(e as Map<String, dynamic>))
        .where((a) => a.account == adminId)
        .toList();
    if (mine.isEmpty) return;
    // _migrateLegacy is called while the in-isolate storage queue is held.
    await _save(mine);
    final copied = await _read(_key);
    if (jsonEncode(copied.map((a) => a.toJson()).toList()) !=
        jsonEncode(mine.map((a) => a.toJson()).toList())) {
      throw StateError('Authenticator migration could not be verified');
    }
  }
}
