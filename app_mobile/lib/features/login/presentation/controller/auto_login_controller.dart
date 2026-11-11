import '../../../../core/storage/auth_storage.dart';
import '../../data/model/auto_login_result.dart';
import '../../data/service/auto_login_service.dart';

/// Màn hình đầu tiên cần mở sau khi kiểm tra phiên đã lưu.
enum StartDestination { home, login }

/// Quy trình khi mở app:
///   1. Không có phiên lưu trên máy                  -> Login
///   2. Auto login bằng access token: 200            -> Home
///   3. Access token trả 401 -> thử refresh token: 200 -> lưu cặp token mới -> Home
///   4. Refresh token cũng 401                       -> xóa sạch phiên trên máy -> Login
///   5. Lỗi mạng / server (không phải 401)           -> Login, GIỮ NGUYÊN phiên đã lưu
///      (mất mạng không có nghĩa là token hỏng nên không được xóa).
class AutoLoginController {
  AutoLoginController({
    AutoLoginService? autoLoginService,
    AuthStorage? authStorage,
  })  : _service = autoLoginService ?? AutoLoginService(),
        _authStorage = authStorage ?? AuthStorage();

  final AutoLoginService _service;
  final AuthStorage _authStorage;

  /// Không bao giờ ném lỗi: mọi sự cố ngoài ý muốn đều dẫn về trang Login.
  Future<StartDestination> resolveStartDestination() async {
    try {
      final session = await _authStorage.readSession(); // đồng thời nạp cache adminId
      if (session == null) return StartDestination.login;

      final byAccess = await _service.loginByAccessToken(session.accessToken);
      switch (byAccess) {
        case AutoLoginSuccess():
          return StartDestination.home;
        case AutoLoginFailure():
          return StartDestination.login;
        case AutoLoginUnauthorized():
          break; // access token không dùng được -> thử refresh token
      }

      final byRefresh = await _service.loginByRefreshToken(
        session.refreshToken,
        adminId: session.adminId,
      );
      switch (byRefresh) {
        case AutoLoginSuccess(:final newSession):
          if (newSession != null) {
            // Phản hồi refresh chỉ chứa token -> giữ lại adminName đã lưu.
            final name = session.adminName;
            await _authStorage.saveSession(
              name == null ? newSession : newSession.withAdminName(name),
            );
          }
          return StartDestination.home;
        case AutoLoginUnauthorized():
          await _authStorage.clearSession();
          return StartDestination.login;
        case AutoLoginFailure():
          return StartDestination.login;
      }
    } catch (_) {
      return StartDestination.login;
    }
  }
}