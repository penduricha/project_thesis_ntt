import 'package:flutter/material.dart';

import '../../../../../core/constants/app_colors.dart';
import '../../../../../core/storage/auth_storage.dart';
import '../../../application/chat_controller.dart';

/// Dải trạng thái ở đầu màn chat:
///  - Mất kết nối quá 3 giây: "Đang kết nối lại..." (đứt ngắn hơn thì không hiện, tránh nhấp nháy).
///  - Access token bị từ chối dù đã thử làm mới: "Phiên đăng nhập đã hết hạn" kèm nút đăng nhập lại.
/// Tự lắng nghe [ChatController], không cần tham số trạng thái.
class ChatStatusBanner extends StatelessWidget {
  const ChatStatusBanner({super.key});

  Future<void> _relogin(BuildContext context) async {
    final navigator = Navigator.of(context, rootNavigator: true);
    final chat = ChatController.instance;
    chat.acknowledgeAuthLost();
    await chat.stop();
    try {
      await AuthStorage().clearSession();
    } catch (_) {
      // Không xóa được ở máy thì vẫn đưa về màn đăng nhập.
    }
    navigator.pushNamedAndRemoveUntil('/login', (route) => false);
  }

  @override
  Widget build(BuildContext context) {
    final chat = ChatController.instance;
    return ListenableBuilder(
      listenable: chat,
      builder: (context, _) {
        if (chat.authLost) {
          return Material(
            color: AppColors.dangerSoft,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: Row(
                children: [
                  const Icon(Icons.lock_outline, size: 18, color: AppColors.primaryRed),
                  const SizedBox(width: 8),
                  const Expanded(
                    child: Text(
                      'Phiên đăng nhập đã hết hạn.',
                      style: TextStyle(fontSize: 13, color: AppColors.primaryRed),
                    ),
                  ),
                  TextButton(
                    onPressed: () => _relogin(context),
                    child: const Text('Đăng nhập lại'),
                  ),
                ],
              ),
            ),
          );
        }
        if (chat.showReconnecting) {
          return Material(
            color: AppColors.backgroundLight,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: Row(
                children: const [
                  SizedBox(
                    width: 14,
                    height: 14,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  ),
                  SizedBox(width: 10),
                  Text(
                    'Đang kết nối lại...',
                    style: TextStyle(fontSize: 13, color: AppColors.textMuted),
                  ),
                ],
              ),
            ),
          );
        }
        return const SizedBox.shrink();
      },
    );
  }
}
