/// Lỗi khi gọi REST chat. Client Dio của dự án coi mọi mã < 500 là "thành công" nên ChatApiService tự đổi
/// các mã lỗi thành ngoại lệ này.
class ChatApiException implements Exception {
  const ChatApiException({
    this.statusCode,
    this.code,
    required this.message,
    this.retryAfterSeconds,
  });

  /// null = không tới được server (mất mạng, quá thời gian chờ...).
  final int? statusCode;

  /// Mã lỗi ổn định của server (NOT_OWNER, GROUP_MIN_SIZE, RATE_LIMITED...). Client dựa vào đây, không dựa vào câu chữ.
  final String? code;

  /// Câu thô từ server hoặc câu mặc định. Dùng [userMessage] để hiện cho người dùng.
  final String message;

  /// Chỉ có với 429: số giây nên chờ (header Retry-After).
  final int? retryAfterSeconds;

  bool get isNetwork => statusCode == null;
  bool get isUnauthorized => statusCode == 401;
  bool get isNotFound => statusCode == 404;
  bool get isRateLimited => statusCode == 429;
  bool get isServerError => (statusCode ?? 0) >= 500;

  /// Gửi lại có thể thành công: lỗi mạng hoặc 5xx. 400/404 thì server đã từ chối hẳn.
  bool get isRetryable => isNetwork || isServerError;

  /// Câu hiển thị cho người dùng, theo bảng ở mục 12 của thiết kế.
  String get userMessage {
    switch (code) {
      case 'CONTENT_TOO_LONG':
        return 'Tin nhắn quá dài (tối đa 2000 ký tự).';
      case 'EMPTY_CONTENT':
        return 'Tin nhắn không được để trống.';
      case 'RATE_LIMITED':
        return 'Bạn gửi quá nhanh, thử lại sau ít giây.';
      case 'GROUP_MIN_SIZE':
        return 'Nhóm phải có ít nhất 3 thành viên.';
      case 'GROUP_MAX_SIZE':
        return 'Nhóm vượt quá số thành viên tối đa.';
      case 'UNKNOWN_MEMBER':
        return 'Có thành viên không tồn tại.';
      case 'NOT_OWNER':
        return 'Chỉ chủ nhóm mới làm được việc này.';
      case 'NOT_SENDER':
        return 'Bạn chỉ xóa được tin nhắn của chính mình.';
      case 'TARGET_IS_OWNER':
        return 'Không thể kick chủ nhóm.';
      case 'NOT_A_MEMBER':
        return 'Người này không còn trong nhóm.';
      case 'CANNOT_CHAT_SELF':
        return 'Không thể tự nhắn tin cho chính mình.';
      case 'PEER_NOT_FOUND':
        return 'Không tìm thấy người dùng này.';
    }
    if (isUnauthorized) {
      return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
    }
    if (isNotFound) return 'Cuộc trò chuyện không còn.';
    if (isRateLimited) return 'Bạn thao tác quá nhanh, thử lại sau ít giây.';
    if (isNetwork) return 'Không kết nối được máy chủ. Kiểm tra mạng và thử lại.';
    if (isServerError) return 'Máy chủ gặp lỗi. Vui lòng thử lại.';
    return message.isEmpty ? 'Có lỗi xảy ra. Vui lòng thử lại.' : message;
  }

  @override
  String toString() => 'ChatApiException($statusCode, $code, $message)';
}
