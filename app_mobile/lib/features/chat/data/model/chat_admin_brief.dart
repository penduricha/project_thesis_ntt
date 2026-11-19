/// Một admin trong kết quả tìm kiếm để bắt đầu chat (GET /chat/admins).
class ChatAdminBrief {
  const ChatAdminBrief({required this.adminId, required this.adminName});

  final int adminId;
  final String adminName;

  factory ChatAdminBrief.fromJson(Map<String, dynamic> json) {
    return ChatAdminBrief(
      adminId: (json['adminId'] as num).toInt(),
      adminName: (json['adminName'] as String?)?.trim() ?? '',
    );
  }

  /// Tên hiển thị: có tên thì dùng tên, không thì dùng mã admin.
  String get displayName => adminName.isEmpty ? 'Admin $adminId' : adminName;
}
