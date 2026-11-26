import '../../../../core/localization/mobile_server_messages.dart';

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

  /// Translate only documented machine-readable codes; leave unfamiliar server
  /// details intact. HTTP codes, retryability and the original message are unchanged.
  String get userMessage {
    final translated = code == null ? null : MobileServerMessages.lookup(code!);
    if (translated != null) return translated;
    if (isNetwork) return MobileServerMessages.lookup('network')!;
    if (isServerError) return MobileServerMessages.lookup('server')!;
    if (isUnauthorized) return MobileServerMessages.lookup('session')!;
    if (isRateLimited) return MobileServerMessages.lookup('RATE_LIMITED')!;
    if (code != null && message.isNotEmpty) return message;
    if (isNotFound) return MobileServerMessages.lookup('NOT_FOUND')!;
    return message.isEmpty ? MobileServerMessages.lookup('unexpected')! : message;
  }

  @override
  String toString() => 'ChatApiException($statusCode, $code, $message)';
}
