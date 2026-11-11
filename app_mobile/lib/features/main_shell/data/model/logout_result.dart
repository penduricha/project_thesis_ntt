/// Kết quả của một lần đăng xuất.
/// Dùng sealed class để màn hình buộc phải xử lý đủ mọi trường hợp.
sealed class LogoutResult {
  const LogoutResult();
}

/// Server xác nhận đã hủy token và phiên mobile (hoặc máy chưa có phiên nào để hủy).
final class LogoutSuccess extends LogoutResult {
  const LogoutSuccess();
}

/// Server từ chối (HTTP 4xx), ví dụ không xóa được token hoặc phiên.
final class LogoutRejected extends LogoutResult {
  const LogoutRejected();
}

/// Không gọi được server: mất mạng, quá thời gian chờ, lỗi 5xx...
final class LogoutFailure extends LogoutResult {
  const LogoutFailure();
}
