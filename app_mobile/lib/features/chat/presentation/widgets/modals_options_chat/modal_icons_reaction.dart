import 'package:animated_emoji/animated_emoji.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../../../core/constants/app_colors.dart';

// Enum quản lý các loại Reaction.
// Chỉ THÊM vào cuối: dữ liệu chat lưu chuỗi emoji (value) nên không phụ thuộc thứ tự enum.
enum MessageReaction { heart, like, haha, wow, sad, angry, fire, party }

extension MessageReactionExtension on MessageReaction {
  /// Chuỗi emoji lưu vào tin nhắn và hiển thị ở badge dưới bong bóng chat.
  String get value {
    switch (this) {
      case MessageReaction.heart:
        return '❤️';
      case MessageReaction.like:
        return '👍';
      case MessageReaction.haha:
        return '😆';
      case MessageReaction.wow:
        return '😮';
      case MessageReaction.sad:
        return '😭';
      case MessageReaction.angry:
        return '😡';
      case MessageReaction.fire:
        return '🔥';
      case MessageReaction.party:
        return '🎉';
    }
  }
}

/// Thanh chọn reaction bằng emoji ĐỘNG (Noto Animated Emoji, gói `animated_emoji`).
/// Giữ nguyên API cũ: `ModalIconsReaction(onReactionSelected: ...)`.
///  - Mỗi emoji tự chạy hoạt ảnh một lần khi thanh hiện ra (so le từng cái).
///  - Chạm giữ: phóng to + chạy lại hoạt ảnh; chạm: rung nhẹ + trả về reaction.
///  - `selected`: reaction hiện tại của bạn trên tin nhắn này sẽ được tô nền.
class ModalIconsReaction extends StatelessWidget {
  final ValueChanged<MessageReaction> onReactionSelected;
  final MessageReaction? selected;

  const ModalIconsReaction({
    super.key,
    required this.onReactionSelected,
    this.selected,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(28),
        boxShadow: const [
          BoxShadow(
            color: Colors.black26,
            blurRadius: 10,
            offset: Offset(0, 4),
          )
        ],
      ),
      // 8 emoji vẫn vừa màn hình hẹp: thu nhỏ đều nếu thiếu chỗ thay vì tràn (overflow).
      child: FittedBox(
        fit: BoxFit.scaleDown,
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            for (final reaction in MessageReaction.values)
              _ReactionItem(
                reaction: reaction,
                index: reaction.index,
                isSelected: reaction == selected,
                onTap: () {
                  HapticFeedback.selectionClick();
                  onReactionSelected(reaction);
                },
              ),
          ],
        ),
      ),
    );
  }
}

class _ReactionItem extends StatefulWidget {
  final MessageReaction reaction;
  final int index;
  final bool isSelected;
  final VoidCallback onTap;

  const _ReactionItem({
    required this.reaction,
    required this.index,
    required this.isSelected,
    required this.onTap,
  });

  @override
  State<_ReactionItem> createState() => _ReactionItemState();
}

class _ReactionItemState extends State<_ReactionItem>
    with SingleTickerProviderStateMixin {
  static const double _size = 34;

  late final AnimationController _lottie; // điều khiển hoạt ảnh của emoji
  bool _pressed = false;

  @override
  void initState() {
    super.initState();
    _lottie = AnimationController(vsync: this);
  }

  @override
  void dispose() {
    _lottie.dispose();
    super.dispose();
  }

  void _replay() {
    if (_lottie.duration != null) _lottie.forward(from: 0);
  }

  @override
  Widget build(BuildContext context) {
    // Tra emoji động theo chuỗi unicode, nên không phụ thuộc tên hằng của gói.
    // Không có bản động (null) thì rơi về emoji tĩnh để thanh không bị trống.
    final data = AnimatedEmojis.fromEmojiString(widget.reaction.value);

    final Widget emoji = data == null
        ? Text(widget.reaction.value, style: const TextStyle(fontSize: _size - 6))
        : AnimatedEmoji(
            data,
            size: _size,
            controller: _lottie,
            onLoaded: (duration) {
              _lottie.duration = duration;
              _lottie.forward(from: 0); // tự chạy một lần khi vừa hiện
            },
          );

    // Hiện lần lượt từ trái sang phải (so le 45ms mỗi emoji).
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0, end: 1),
      duration: Duration(milliseconds: 260 + widget.index * 45),
      curve: Curves.easeOutBack,
      builder: (context, t, child) => Opacity(
        opacity: t.clamp(0.0, 1.0).toDouble(),
        child: Transform.scale(scale: t, child: child),
      ),
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: widget.onTap,
        onTapDown: (_) {
          setState(() => _pressed = true);
          _replay();
        },
        onTapUp: (_) => setState(() => _pressed = false),
        onTapCancel: () => setState(() => _pressed = false),
        child: AnimatedScale(
          scale: _pressed ? 1.35 : 1.0,
          duration: const Duration(milliseconds: 120),
          curve: Curves.easeOut,
          child: Container(
            margin: const EdgeInsets.symmetric(horizontal: 2),
            padding: const EdgeInsets.all(5),
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: widget.isSelected
                  ? AppColors.primaryBlue.withValues(alpha: 0.12)
                  : Colors.transparent,
            ),
            child: emoji,
          ),
        ),
      ),
    );
  }
}
