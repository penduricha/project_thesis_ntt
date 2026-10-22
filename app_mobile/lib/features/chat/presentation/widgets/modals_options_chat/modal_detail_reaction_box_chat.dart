import 'package:flutter/material.dart';

class ReactionUser {
  final String userId;
  final String userName;
  final String avatarUrl;
  final String emoji;

  ReactionUser({
    required this.userId,
    required this.userName,
    required this.avatarUrl,
    required this.emoji,
  });
}

class ModalDetailReactionBoxChat extends StatefulWidget {
  final List<ReactionUser> reactions;
  final String currentUserId;
  final VoidCallback onRemoveReaction;

  const ModalDetailReactionBoxChat({
    super.key,
    required this.reactions,
    required this.currentUserId,
    required this.onRemoveReaction,
  });

  @override
  State<ModalDetailReactionBoxChat> createState() =>
      _ModalDetailReactionBoxChatState();
}

class _ModalDetailReactionBoxChatState
    extends State<ModalDetailReactionBoxChat> {
  // 5 loại cảm xúc cơ bản + Tất cả
  final List<String> _allowedEmojis = ['❤️', '👍', '😆', '😲', '😭', '😡'];
  String _selectedFilter = 'ALL';
  late List<ReactionUser> _localReactions;

  @override
  void initState() {
    super.initState();
    _localReactions = List.from(widget.reactions);
  }

  // Đếm số lượng theo emoji
  int _getEmojiCount(String emoji) {
    return _localReactions.where((r) => r.emoji == emoji).length;
  }

  // Danh sách đã lọc theo tab đang chọn
  List<ReactionUser> get _filteredReactions {
    if (_selectedFilter == 'ALL') {
      return _localReactions;
    }
    return _localReactions.where((r) => r.emoji == _selectedFilter).toList();
  }

  void _handleRemoveMyReaction() {
    setState(() {
      _localReactions.removeWhere((r) => r.userId == widget.currentUserId);
    });
    widget.onRemoveReaction();
  }

  @override
  Widget build(BuildContext context) {
    // Thiết lập chiều cao khoảng 1/3 màn hình
    final double modalHeight = MediaQuery.of(context).size.height * 0.35;

    return Container(
      width: double.infinity,
      height: modalHeight,
      decoration: const BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start, // Căn lề trái toàn bộ modal
        children: [
          const SizedBox(height: 8),
          // Thanh kéo (Handle bar) ở giữa
          Center(
            child: Container(
              width: 36,
              height: 4,
              decoration: BoxDecoration(
                color: Colors.grey[300],
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),
          const SizedBox(height: 12),

          // Thanh Tab Bar filter sát lề trái
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.only(left: 16, right: 16),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.start, // Align left
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _buildTabItem(
                  label: 'Tất cả ${_localReactions.length}',
                  isSelected: _selectedFilter == 'ALL',
                  onTap: () => setState(() => _selectedFilter = 'ALL'),
                ),
                ..._allowedEmojis.map((emoji) {
                  final count = _getEmojiCount(emoji);
                  if (count == 0) return const SizedBox.shrink();
                  return _buildTabItem(
                    label: '$emoji $count',
                    isSelected: _selectedFilter == emoji,
                    onTap: () => setState(() => _selectedFilter = emoji),
                  );
                }),
              ],
            ),
          ),
          const Divider(height: 1, thickness: 0.5),

          // Danh sách người dùng đã thả reaction
          Expanded(
            child: _filteredReactions.isEmpty
                ? const Center(
              child: Text(
                'Không có cảm xúc nào',
                style: TextStyle(
                  color: Color(0xFF8E8E93),
                  fontSize: 14,
                  fontWeight: FontWeight.w500,
                ),
              ),
            )
                : ListView.builder(
              padding: EdgeInsets.only(
                bottom: MediaQuery.of(context).padding.bottom + 8,
              ),
              itemCount: _filteredReactions.length,
              itemBuilder: (context, index) {
                final item = _filteredReactions[index];
                final isMe = item.userId == widget.currentUserId;

                return Container(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 16, vertical: 10),
                  color: isMe
                      ? const Color(0xFFF0F6FF)
                      : Colors.transparent,
                  child: Row(
                    children: [
                      // Avatar & Emoji đè góc
                      Stack(
                        children: [
                          CircleAvatar(
                            radius: 20,
                            backgroundImage: NetworkImage(item.avatarUrl),
                            backgroundColor: Colors.grey[300],
                          ),
                          Positioned(
                            right: 0,
                            bottom: 0,
                            child: Container(
                              padding: const EdgeInsets.all(1),
                              decoration: const BoxDecoration(
                                color: Colors.white,
                                shape: BoxShape.circle,
                              ),
                              child: Text(
                                item.emoji,
                                style: const TextStyle(fontSize: 10),
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(width: 12),

                      // Tên người dùng (Căn trái)
                      Expanded(
                        child: Text(
                          item.userName,
                          style: const TextStyle(
                            fontSize: 15,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ),

                      // Nút "Gỡ" chỉ hiển thị với chính người dùng
                      if (isMe)
                        TextButton(
                          onPressed: _handleRemoveMyReaction,
                          style: TextButton.styleFrom(
                            backgroundColor: Colors.grey[200],
                            padding: const EdgeInsets.symmetric(
                                horizontal: 16, vertical: 6),
                            minimumSize: Size.zero,
                            tapTargetSize:
                            MaterialTapTargetSize.shrinkWrap,
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(16),
                            ),
                          ),
                          child: const Text(
                            'Gỡ',
                            style: TextStyle(
                              color: Colors.black87,
                              fontSize: 13,
                            ),
                          ),
                        ),
                    ],
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTabItem({
    required String label,
    required bool isSelected,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        margin: const EdgeInsets.only(right: 16),
        padding: const EdgeInsets.symmetric(vertical: 8),
        decoration: BoxDecoration(
          border: Border(
            bottom: BorderSide(
              color: isSelected ? Colors.black : Colors.transparent,
              width: 2,
            ),
          ),
        ),
        child: Text(
          label,
          textAlign: TextAlign.left,
          style: TextStyle(
            fontSize: 14,
            fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
            color: isSelected ? Colors.black : Colors.grey[600],
          ),
        ),
      ),
    );
  }
}