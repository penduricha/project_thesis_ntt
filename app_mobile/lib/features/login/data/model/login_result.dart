import '../../../../core/storage/auth_storage.dart';

/// Kết quả của một lần đăng nhập. Dùng sealed class để màn hình buộc phải xử lý
/// đủ mọi trường hợp (trình biên dịch báo lỗi nếu thiếu một nhánh).
sealed class LoginResult {
  const LoginResult();
}

/// Trường hợp 1: đăng nhập thành công, server trả adminId và cặp token.
final class LoginSuccess extends LoginResult {
  const LoginSuccess(this.session);

  final AuthSession session;
}

/// Trường hợp 2: sai Admin Id hoặc mật khẩu (hoặc server từ chối vì lý do khác).
final class LoginRejected extends LoginResult {
  const LoginRejected(this.message);

  final String message;
}

/// Trường hợp 3: mật khẩu đúng nhưng tài khoản đã đăng nhập ở thiết bị khác (HTTP 409).
final class LoginBlockedByOtherDevice extends LoginResult {
  const LoginBlockedByOtherDevice({
    required this.message,
    this.deviceName,
    this.platform,
    this.loggedInAt,
  });

  final String message;
  final String? deviceName;
  final String? platform;
  final DateTime? loggedInAt;
}

/// Không gọi được server: mất mạng, quá thời gian chờ, lỗi 5xx...
final class LoginFailure extends LoginResult {
  const LoginFailure(this.message);

  final String message;
}