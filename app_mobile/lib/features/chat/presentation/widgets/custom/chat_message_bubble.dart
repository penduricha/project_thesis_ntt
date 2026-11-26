import 'package:flutter/material.dart';

import '../../../../../core/constants/app_colors.dart';
import '../../../../../core/utils/chat_time.dart';
import '../../../data/model/chat_message.dart';

/// Một bong bóng tin nhắn văn bản. Không có reaction, trả lời hay chuyển tiếp (chỉ có "Sao chép" và "Xóa").
class ChatMessageBubble extends StatelessWidget {
  const ChatMessageBubble({
    super.key,
    required this.message,
    required this.isMe,
    this.senderLabel,
    this.dimmed = false,
    this.onLongPress,
    this.onTapFailed,
  });

  final ChatMessage message;
  final bool isMe;

  /// Tên người gửi hiện phía trên bong bóng (nhóm, tin của người khác). null = không hiện.
  final String? senderLabel;

  /// Làm mờ khi đang chờ server xóa (xóa không lạc quan).
  final bool dimmed;

  final VoidCallback? onLongPress;

  /// Chạm vào tin gửi lỗi.
  final VoidCallback? onTapFailed;

  @override
  Widget build(BuildContext context) {
    final failed = message.status == ChatMessageStatus.failed;
    final sending = message.status == ChatMessageStatus.sending;
    final Color bubbleColor = isMe ? AppColors.primaryBlue : Theme.of(context).colorScheme.surfaceContainerHighest;
    final Color textColor = isMe ? Colors.white : Theme.of(context).colorScheme.onSurface;
    final Color metaColor = isMe ? Colors.white70 : Theme.of(context).colorScheme.onSurfaceVariant;

    final bubble = Container(
      constraints: BoxConstraints(maxWidth: MediaQuery.of(context).size.width * 0.75),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: bubbleColor,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          if (senderLabel != null) ...[
            Text(
              senderLabel!,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w700,
                color: isMe ? Colors.white : Theme.of(context).colorScheme.primary,
              ),
            ),
            const SizedBox(height: 2),
          ],
          Text(
            message.content,
            style: TextStyle(fontSize: 15, color: textColor),
          ),
          const SizedBox(height: 2),
          Row(
            mainAxisSize: MainAxisSize.min,
            mainAxisAlignment: MainAxisAlignment.end,
            children: [
              Text(
                ChatTime.hhmm(message.createdAt),
                style: TextStyle(fontSize: 10, color: metaColor),
              ),
              if (sending) ...[
                const SizedBox(width: 4),
                Icon(Icons.schedule, size: 11, color: metaColor),
              ],
            ],
          ),
        ],
      ),
    );

    return Align(
      alignment: isMe ? Alignment.centerRight : Alignment.centerLeft,
      child: Column(
        crossAxisAlignment: isMe ? CrossAxisAlignment.end : CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Opacity(
            opacity: dimmed || sending ? 0.55 : 1,
            child: GestureDetector(
              onLongPress: onLongPress,
              onTap: failed ? onTapFailed : null,
              child: bubble,
            ),
          ),
          if (failed)
            Padding(
              padding: const EdgeInsets.only(top: 2),
              child: GestureDetector(
                onTap: onTapFailed,
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.error_outline, size: 14, color: AppColors.primaryRed),
                    const SizedBox(width: 4),
                    Text(
                      message.retryable ? 'Gửi lỗi. Chạm để thử lại' : 'Không gửi được',
                      style: const TextStyle(fontSize: 11, color: AppColors.primaryRed),
                    ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }
}
