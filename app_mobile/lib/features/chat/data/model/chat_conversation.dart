import 'chat_member.dart';
import 'chat_message.dart';

enum ChatConversationType { direct, group }

/// Một cuộc trò chuyện (1-1 hoặc nhóm). Bất biến, đổi bằng [copyWith].
///
/// Danh sách (GET /chat/conversations) chỉ có tóm tắt nên [members] rỗng; chi tiết (GET /chat/conversations/{id})
/// có đủ thành viên. Với 1-1, [name] là tên người còn lại (server đã tính cho người xem).
class ChatConversation {
  const ChatConversation({
    required this.id,
    required this.type,
    required this.name,
    required this.ownerId,
    required this.memberCount,
    required this.lastMessage,
    required this.unreadCount,
    required this.createdAt,
    this.members = const <ChatMember>[],
  });

  final int id;
  final ChatConversationType type;
  final String name;
  final int? ownerId;
  final int memberCount;
  final ChatMessage? lastMessage;
  final int unreadCount;
  final DateTime createdAt;
  final List<ChatMember> members;

  bool get isGroup => type == ChatConversationType.group;

  bool isOwnedBy(int? adminId) => adminId != null && ownerId == adminId;

  /// Thời điểm hoạt động gần nhất: tin cuối nếu có, không thì lúc tạo. Dùng để sắp danh sách.
  DateTime get lastActivity => lastMessage?.createdAt ?? createdAt;

  String get displayName => name.trim().isEmpty ? 'Cuộc trò chuyện' : name;

  factory ChatConversation.fromJson(Map<String, dynamic> json) {
    final lastRaw = json['lastMessage'];
    final membersRaw = json['members'] as List<dynamic>?;
    return ChatConversation(
      id: (json['id'] as num).toInt(),
      type: json['type'] == 'GROUP'
          ? ChatConversationType.group
          : ChatConversationType.direct,
      name: (json['name'] as String?) ?? '',
      ownerId: (json['ownerId'] as num?)?.toInt(),
      memberCount: (json['memberCount'] as num?)?.toInt() ?? 0,
      lastMessage: lastRaw is Map<String, dynamic>
          ? ChatMessage.fromJson(lastRaw)
          : null,
      unreadCount: (json['unreadCount'] as num?)?.toInt() ?? 0,
      createdAt: DateTime.parse(json['createdAt'] as String).toUtc(),
      members: membersRaw == null
          ? const <ChatMember>[]
          : [
              for (final m in membersRaw)
                ChatMember.fromJson(m as Map<String, dynamic>),
            ],
    );
  }

  ChatConversation copyWith({
    String? name,
    int? memberCount,
    ChatMessage? lastMessage,
    bool clearLastMessage = false,
    int? unreadCount,
    List<ChatMember>? members,
  }) {
    return ChatConversation(
      id: id,
      type: type,
      name: name ?? this.name,
      ownerId: ownerId,
      memberCount: memberCount ?? this.memberCount,
      lastMessage: clearLastMessage ? null : (lastMessage ?? this.lastMessage),
      unreadCount: unreadCount ?? this.unreadCount,
      createdAt: createdAt,
      members: members ?? this.members,
    );
  }
}
