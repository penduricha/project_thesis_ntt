import 'dart:convert';

import 'chat_conversation.dart';
import 'chat_message.dart';

/// Sự kiện server đẩy xuống qua WebSocket (JSON {type, data}). Chỉ để BÁO: dữ liệu thật luôn lấy qua REST,
/// nên mất một sự kiện không làm mất dữ liệu, chỉ chậm cho tới lần kéo bù tiếp theo.
sealed class ChatEvent {
  const ChatEvent();

  /// Trả null nếu khung không đọc được hoặc là loại chưa biết (bỏ qua, không làm hỏng kết nối).
  static ChatEvent? parse(String raw) {
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! Map<String, dynamic>) return null;
      final type = decoded['type'];
      final dataRaw = decoded['data'];
      final data = dataRaw is Map<String, dynamic> ? dataRaw : const <String, dynamic>{};
      switch (type) {
        case 'ready':
          return ChatReadyEvent(
            adminId: (data['adminId'] as num).toInt(),
            pingIntervalSec: (data['pingIntervalSec'] as num?)?.toInt() ?? 25,
          );
        case 'pong':
          return const ChatPongEvent();
        case 'message.new':
          return ChatMessageNewEvent(
            ChatMessage.fromJson(data['message'] as Map<String, dynamic>),
          );
        case 'message.deleted':
          return ChatMessageDeletedEvent(
            conversationId: (data['conversationId'] as num).toInt(),
            messageId: (data['messageId'] as num).toInt(),
          );
        case 'conversation.created':
          return ChatConversationCreatedEvent(
            ChatConversation.fromJson(data['conversation'] as Map<String, dynamic>),
          );
        case 'conversation.dissolved':
          return ChatConversationDissolvedEvent(
            (data['conversationId'] as num).toInt(),
          );
        case 'member.removed':
          return ChatMemberRemovedEvent(
            conversationId: (data['conversationId'] as num).toInt(),
            adminId: (data['adminId'] as num).toInt(),
          );
        case 'conversation.read':
          return ChatConversationReadEvent(
            conversationId: (data['conversationId'] as num).toInt(),
            lastReadMessageId: (data['lastReadMessageId'] as num).toInt(),
          );
        case 'error':
          return ChatErrorEvent(
            code: (data['code'] as String?) ?? '',
            message: (data['message'] as String?) ?? '',
          );
      }
    } catch (_) {
      // Khung hỏng: bỏ qua.
    }
    return null;
  }
}

final class ChatReadyEvent extends ChatEvent {
  const ChatReadyEvent({required this.adminId, required this.pingIntervalSec});

  final int adminId;
  final int pingIntervalSec;
}

final class ChatPongEvent extends ChatEvent {
  const ChatPongEvent();
}

final class ChatMessageNewEvent extends ChatEvent {
  const ChatMessageNewEvent(this.message);

  final ChatMessage message;
}

final class ChatMessageDeletedEvent extends ChatEvent {
  const ChatMessageDeletedEvent({
    required this.conversationId,
    required this.messageId,
  });

  final int conversationId;
  final int messageId;
}

final class ChatConversationCreatedEvent extends ChatEvent {
  const ChatConversationCreatedEvent(this.conversation);

  final ChatConversation conversation;
}

final class ChatConversationDissolvedEvent extends ChatEvent {
  const ChatConversationDissolvedEvent(this.conversationId);

  final int conversationId;
}

final class ChatMemberRemovedEvent extends ChatEvent {
  const ChatMemberRemovedEvent({
    required this.conversationId,
    required this.adminId,
  });

  final int conversationId;
  final int adminId;
}

final class ChatConversationReadEvent extends ChatEvent {
  const ChatConversationReadEvent({
    required this.conversationId,
    required this.lastReadMessageId,
  });

  final int conversationId;
  final int lastReadMessageId;
}

final class ChatErrorEvent extends ChatEvent {
  const ChatErrorEvent({required this.code, required this.message});

  final String code;
  final String message;
}
