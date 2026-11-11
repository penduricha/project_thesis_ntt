import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Phiên đăng nhập lưu trên máy: adminId + tên + cặp token do server cấp.
class AuthSession {
  const AuthSession({
    required this.adminId,
    required this.accessToken,
    required this.refreshToken,
    this.adminName,
  });

  final int adminId;
  final String accessToken;
  final String refreshToken;

  /// Tên hiển thị của admin (server trả khi đăng nhập). Có thể null với phiên cũ.
  final String? adminName;

  /// Bản sao với cặp token mới, giữ nguyên adminId và adminName (dùng khi refresh token).
  AuthSession withTokens({
    required String accessToken,
    required String refreshToken,
  }) {
    return AuthSession(
      adminId: adminId,
      accessToken: accessToken,
      refreshToken: refreshToken,
      adminName: adminName,
    );
  }

  AuthSession withAdminName(String name) {
    return AuthSession(
      adminId: adminId,
      accessToken: accessToken,
      refreshToken: refreshToken,
      adminName: name,
    );
  }
}

/// Lưu phiên đăng nhập cục bộ (tương đương localStorage ở Web).
///
/// Dùng flutter_secure_storage: dữ liệu được mã hóa bằng Android Keystore /
/// iOS Keychain nên an toàn hơn SharedPreferences (lưu chữ thuần).
/// Muốn đổi cách lưu thì chỉ cần sửa class này.
class AuthStorage {
  AuthStorage({FlutterSecureStorage? storage})
      : _storage = storage ?? const FlutterSecureStorage();

  static const String _adminIdKey = 'admin_id';
  static const String _adminNameKey = 'admin_name';
  static const String _accessTokenKey = 'access_token';
  static const String _refreshTokenKey = 'refresh_token';

  // Bản sao trong bộ nhớ, dùng chung toàn app để lấy adminId ngay (đồng bộ),
  // không cần await. Được nạp khi lưu phiên mới hoặc khi gọi readSession().
  static AuthSession? _cachedSession;

  /// Phiên hiện tại trong bộ nhớ (null nếu chưa đăng nhập / chưa nạp).
  static AuthSession? get cachedSession => _cachedSession;

  /// adminId hiện tại trong bộ nhớ, dùng ở bất kỳ màn hình nào: AuthStorage.cachedAdminId
  static int? get cachedAdminId => _cachedSession?.adminId;

  /// Tên admin hiện tại trong bộ nhớ (null nếu chưa có).
  static String? get cachedAdminName => _cachedSession?.adminName;

  final FlutterSecureStorage _storage;

  /// Gọi sau khi đăng nhập thành công: lưu adminId, tên cùng 2 token.
  Future<void> saveSession(AuthSession session) async {
    final adminName = session.adminName;
    await Future.wait([
      _storage.write(key: _adminIdKey, value: session.adminId.toString()),
      _storage.write(key: _accessTokenKey, value: session.accessToken),
      _storage.write(key: _refreshTokenKey, value: session.refreshToken),
      adminName == null
          ? _storage.delete(key: _adminNameKey)
          : _storage.write(key: _adminNameKey, value: adminName),
    ]);
    _cachedSession = session;
  }

  /// Chỉ cập nhật tên admin (dùng khi phiên cũ chưa có tên và vừa lấy được từ server).
  Future<void> saveAdminName(String adminName) async {
    await _storage.write(key: _adminNameKey, value: adminName);
    _cachedSession = _cachedSession?.withAdminName(adminName);
  }

  /// Dùng cho auto login khi mở app. Trả về null nếu thiếu bất kỳ giá trị nào.
  /// Đồng thời nạp phiên vào bộ nhớ để [cachedAdminId] dùng được.
  Future<AuthSession?> readSession() async {
    final adminId = int.tryParse(await _storage.read(key: _adminIdKey) ?? '');
    final accessToken = await _storage.read(key: _accessTokenKey);
    final refreshToken = await _storage.read(key: _refreshTokenKey);
    final adminName = await _storage.read(key: _adminNameKey);

    if (adminId == null || accessToken == null || refreshToken == null) {
      return null;
    }

    final session = AuthSession(
      adminId: adminId,
      accessToken: accessToken,
      refreshToken: refreshToken,
      adminName: adminName,
    );
    _cachedSession = session;
    return session;
  }

  /// Lấy adminId: ưu tiên bản trong bộ nhớ, không có thì đọc từ máy.
  Future<int?> readAdminId() async {
    final session = _cachedSession ?? await readSession();
    return session?.adminId;
  }

  /// Gọi khi đăng xuất (hoặc khi refresh token hết hạn): xóa adminId, tên và 2 token.
  Future<void> clearSession() async {
    // Xóa bản trong bộ nhớ trước: dù xóa ở máy có lỗi thì cũng không bao giờ
    // còn dùng lại phiên cũ trong lúc app đang chạy.
    _cachedSession = null;
    await Future.wait([
      _storage.delete(key: _adminIdKey),
      _storage.delete(key: _adminNameKey),
      _storage.delete(key: _accessTokenKey),
      _storage.delete(key: _refreshTokenKey),
    ]);
  }
}