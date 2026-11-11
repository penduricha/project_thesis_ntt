import '../../../../core/storage/auth_storage.dart';

/// Kết quả của một lần gọi API auto login.
sealed class AutoLoginResult {
  const AutoLoginResult();
}

/// HTTP 200. [newSession] chỉ có khi đăng nhập bằng refresh token
/// (server xoay vòng và cấp cặp token mới); bằng access token thì là null.
final class AutoLoginSuccess extends AutoLoginResult {
  const AutoLoginSuccess({this.newSession});

  final AuthSession? newSession;
}

/// HTTP 401: token sai, hết hạn, đã bị thu hồi hoặc phiên mobile đã kết thúc.
final class AutoLoginUnauthorized extends AutoLoginResult {
  const AutoLoginUnauthorized();
}

/// Không xác định được vì lỗi mạng / server (timeout, 5xx, phản hồi sai định dạng).
/// KHÔNG có nghĩa token hỏng nên không được xóa phiên đã lưu.
final class AutoLoginFailure extends AutoLoginResult {
  const AutoLoginFailure();
}
