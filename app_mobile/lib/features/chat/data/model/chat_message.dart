/// Trạng thái gửi của một tin trên máy này.
///  - [sending]: vừa bấm gửi, đang chờ server (tin lạc quan, chưa có id server).
///  - [sent]: server đã lưu (có id).
///  - [failed]: gửi lỗi, chưa chắc server đã nhận; chạm để gửi lại với CÙNG clientMsgId.
enum ChatMessageStatus { sending, sent, failed }

/// Một tin nhắn văn bản. Bất biến: mọi thay đổi tạo bản sao mới qua [copyWith].
///
/// Tin của server luôn có [id]. Tin lạc quan chưa gửi xong có [id] == null và nằm cuối danh sách.
class ChatMessage {
  const ChatMessage({
    required this.id,
    required this.conversationId,
    required this.senderId,
    required this.senderName,
    required this.content,
    required this.createdAt,
    required this.clientMsgId,
    this.status = ChatMessageStatus.sent,
    this.retryable = true,
  });

  /// id do server cấp; null khi tin chưa tới server.
  final int? id;
  final int conversationId;
  final int senderId;
  final String senderName;
  final String content;

  /// Giờ UTC. Với tin lạc quan là giờ máy lúc bấm gửi, chỉ để hiển thị tạm.
  final DateTime createdAt;

  /// UUID do người gửi sinh, dùng để gửi lại không tạo tin trùng và để gộp tin lạc quan với tin server.
  final String clientMsgId;

  final ChatMessageStatus status;

  /// Chỉ có nghĩa khi [status] là failed: false nghĩa là server đã từ chối (400/404), gửi lại vô ích.
  final bool retryable;

  bool get isPending => id == null;

  factory ChatMessage.fromJson(Map<String, dynamic> json) {
    return ChatMessage(
      id: (json['id'] as num).toInt(),
      conversationId: (json['conversationId'] as num).toInt(),
      senderId: (json['senderId'] as num).toInt(),
      senderName: (json['senderName'] as String?) ?? '',
      content: (json['content'] as String?) ?? '',
      createdAt: DateTime.parse(json['createdAt'] as String).toUtc(),
      clientMsgId: (json['clientMsgId'] as String?) ?? '',
    );
  }

  /// Tin lạc quan vừa bấm gửi.
  factory ChatMessage.pending({
    required int conversationId,
    required int senderId,
    required String senderName,
    required String content,
    required String clientMsgId,
  }) {
    return ChatMessage(
      id: null,
      conversationId: conversationId,
      senderId: senderId,
      senderName: senderName,
      content: content,
      createdAt: DateTime.now().toUtc(),
      clientMsgId: clientMsgId,
      status: ChatMessageStatus.sending,
    );
  }

  ChatMessage copyWith({ChatMessageStatus? status, bool? retryable}) {
    return ChatMessage(
      id: id,
      conversationId: conversationId,
      senderId: senderId,
      senderName: senderName,
      content: content,
      createdAt: createdAt,
      clientMsgId: clientMsgId,
      status: status ?? this.status,
      retryable: retryable ?? this.retryable,
    );
  }
}

/// Một trang tin từ server: tin mới nhất đứng đầu; [hasMore] false nghĩa là đã hết lịch sử.
class ChatMessagesPage {
  const ChatMessagesPage({required this.messages, required this.hasMore});

  final List<ChatMessage> messages;
  final bool hasMore;

  factory ChatMessagesPage.fromJson(Map<String, dynamic> json) {
    final raw = (json['messages'] as List<dynamic>? ?? const <dynamic>[]);
    return ChatMessagesPage(
      messages: [
        for (final item in raw) ChatMessage.fromJson(item as Map<String, dynamic>),
      ],
      hasMore: json['hasMore'] == true,
    );
  }
}
