import 'package:flutter/material.dart';
import '../../../../../core/constants/app_colors.dart';

/// Avatar chữ cái đầu cho danh sách chat.
///
/// - Chưa đọc: nền xanh đặc 0xFF004B87 + chữ trắng (đúng Figma) -> điểm nhấn.
/// - Đã đọc: nền xanh nhạt + chữ xanh đậm -> nhẹ mắt, không cạnh tranh với nội dung.
///
/// Màu dùng để báo trạng thái, nên người dùng quét danh sách là thấy ngay tin chưa đọc.
class ChatAvatar extends StatelessWidget {
  const ChatAvatar({
    super.key,
    required this.name,
    this.isUnread = false,
    this.radius = 24,
  });

  static const Color _blue = AppColors.primaryBlue;

  final String name;
  final bool isUnread;
  final double radius;

  // Lấy ký tự đầu theo rune để không vỡ với emoji / ký tự ngoài BMP.
  String get _initial {
    final trimmed = name.trim();
    if (trimmed.isEmpty) return '?';
    return String.fromCharCode(trimmed.runes.first).toUpperCase();
  }

  @override
  Widget build(BuildContext context) {
    final Color background = isUnread ? _blue : Theme.of(context).colorScheme.primaryContainer;
    final Color foreground = isUnread ? Colors.white : Theme.of(context).colorScheme.onPrimaryContainer;

    // Tên đã nằm ngay cạnh avatar nên không để screen reader đọc thêm chữ cái.
    return ExcludeSemantics(
      // CircleAvatar tự animate khi đổi màu: chưa đọc -> đã đọc chuyển mượt.
      child: CircleAvatar(
        radius: radius,
        backgroundColor: background,
        child: Text(
          _initial,
          // Khóa cỡ chữ để không tràn vòng tròn khi người dùng tăng font hệ thống.
          textScaler: TextScaler.noScaling,
          style: TextStyle(
            fontSize: radius * 0.8333, // = 20 khi radius 24
            fontWeight: FontWeight.w700,
            color: foreground,
          ),
        ),
      ),
    );
  }
}