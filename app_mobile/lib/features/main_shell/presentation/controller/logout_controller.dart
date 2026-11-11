import 'package:flutter/foundation.dart';

import '../../../../core/storage/auth_storage.dart';
import '../../data/model/logout_result.dart';
import '../../data/service/logout_service.dart';

/// Điều phối đăng xuất: lấy token đã lưu -> gọi API -> xóa phiên cục bộ.
/// Giao diện chỉ lắng nghe [isLoading] và nhận [LogoutResult].
class LogoutController extends ChangeNotifier {
  LogoutController({
    LogoutService? logoutService,
    AuthStorage? authStorage,
  })  : _logoutService = logoutService ?? LogoutService(),
        _authStorage = authStorage ?? AuthStorage();

  final LogoutService _logoutService;
  final AuthStorage _authStorage;

  bool _isLoading = false;
  bool _isDisposed = false;

  /// true khi đang xử lý API: giao diện dùng để hiện spinner và khóa các nút.
  bool get isLoading => _isLoading;

  Future<LogoutResult> logout() async {
    _setLoading(true);
    final result = await _performLogout();

    // Thất bại: mở khóa để người dùng thử lại.
    // Thành công: giữ nguyên spinner cho tới khi hộp thoại đóng, tránh chữ
    // "Đăng xuất" nháy lại trong lúc hộp thoại đang mờ dần.
    if (result is! LogoutSuccess) _setLoading(false);
    return result;
  }

  /// Không bao giờ ném lỗi: mọi sự cố ngoài ý muốn đều đổi thành [LogoutFailure].
  Future<LogoutResult> _performLogout() async {
    try {
      final session =
          AuthStorage.cachedSession ?? await _authStorage.readSession();

      // Có phiên thì phải được server xác nhận hủy thành công.
      // Không có phiên (máy chưa lưu token) thì không còn gì để hủy ở server.
      if (session != null) {
        final result = await _logoutService.logout(
          accessToken: session.accessToken,
          refreshToken: session.refreshToken,
        );
        if (result is! LogoutSuccess) return result;
      }

      await _clearLocalSession();
      return const LogoutSuccess();
    } catch (_) {
      return const LogoutFailure();
    }
  }

  /// Server đã hủy token nên lỗi xóa ở máy không chặn việc đăng xuất.
  Future<void> _clearLocalSession() async {
    try {
      await _authStorage.clearSession();
    } catch (error) {
      debugPrint('Cannot clear local session: $error');
    }
  }

  void _setLoading(bool value) {
    if (_isDisposed || _isLoading == value) return;
    _isLoading = value;
    notifyListeners();
  }

  @override
  void dispose() {
    _isDisposed = true;
    super.dispose();
  }
}
