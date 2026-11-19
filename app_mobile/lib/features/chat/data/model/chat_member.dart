/// Thành viên của một cuộc trò chuyện. [isOwner] chỉ có ý nghĩa với nhóm.
class ChatMember {
  const ChatMember({
    required this.adminId,
    required this.adminName,
    required this.isOwner,
  });

  final int adminId;
  final String adminName;
  final bool isOwner;

  factory ChatMember.fromJson(Map<String, dynamic> json) {
    return ChatMember(
      adminId: (json['adminId'] as num).toInt(),
      adminName: (json['adminName'] as String?)?.trim() ?? '',
      isOwner: json['isOwner'] == true,
    );
  }

  String get displayName => adminName.isEmpty ? 'Admin $adminId' : adminName;
}
