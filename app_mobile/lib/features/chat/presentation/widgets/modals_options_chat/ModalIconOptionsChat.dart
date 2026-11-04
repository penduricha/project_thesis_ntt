import 'package:flutter/material.dart';

class ModalIconOptionsChat extends StatelessWidget {
  final VoidCallback? onReply;
  final VoidCallback? onForward;
  final VoidCallback? onCopy;
  final VoidCallback? onDelete;

  const ModalIconOptionsChat({
    super.key,
    this.onReply,
    this.onForward,
    this.onCopy,
    this.onDelete,
  });

  Widget _buildOptionItem({
    required IconData icon,
    required String title,
    required VoidCallback? onTap,
    bool isDestructive = false,
  }) {
    final color = isDestructive ? Colors.red : Colors.black87;
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(8),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.start,
          children: [
            Icon(icon, color: color, size: 20),
            const SizedBox(width: 12), // Khoảng cách giữa icon và text
            Text(
              title,
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w400,
                color: color,
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 220,
      decoration: BoxDecoration(
        color: const Color(0xFFF2F2F7),
        borderRadius: BorderRadius.circular(8),
        boxShadow: const [
          BoxShadow(
            color: Colors.black26,
            blurRadius: 10,
            offset: Offset(0, 4),
          )
        ],
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          _buildOptionItem(
            icon: Icons.reply_outlined,
            title: 'Trả lời',
            onTap: onReply,
          ),
          _buildOptionItem(
            icon: Icons.shortcut_outlined,
            title: 'Chuyển tiếp',
            onTap: onForward,
          ),
          _buildOptionItem(
            icon: Icons.copy_outlined,
            title: 'Sao chép',
            onTap: onCopy,
          ),
          _buildOptionItem(
            icon: Icons.delete_outline,
            title: 'Xóa',
            isDestructive: true,
            onTap: onDelete,
          ),
        ],
      ),
    );
  }
}