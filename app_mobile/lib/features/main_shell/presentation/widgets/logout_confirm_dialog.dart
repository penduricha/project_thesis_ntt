import 'package:flutter/material.dart';

import '../../../../core/widgets/button_spinner.dart';
import '../../../../core/localization/app_strings.dart';

import '../../../login/presentation/widgets/error_dialog.dart';
import '../../data/model/logout_result.dart';
import '../controller/logout_controller.dart';
import '../../../../core/constants/app_colors.dart';

// Màu đỏ của nút "Log out" (đồng bộ với nút OK của popup lỗi)
const Color _logoutColor = AppColors.primaryRed;

/// Popup xác nhận đăng xuất. Tự gọi API khi bấm "Log out" và chỉ đóng với
/// kết quả `true` khi server xác nhận thành công; bấm Cancel trả về `false`.
///
/// Đang gọi API: chữ "Log out" -> spinner, nút Cancel bị khóa, không đóng được
/// bằng nút Back hay chạm ra ngoài. Xong (kể cả thất bại) thì mọi thứ trở lại ban đầu.
class LogoutConfirmDialog extends StatefulWidget {
  const LogoutConfirmDialog({super.key});

  @override
  State<LogoutConfirmDialog> createState() => _LogoutConfirmDialogState();
}

class _LogoutConfirmDialogState extends State<LogoutConfirmDialog> {
  final LogoutController _controller = LogoutController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _onConfirm() async {
    if (_controller.isLoading) return;

    final result = await _controller.logout();
    if (!mounted) return;

    switch (result) {
      case LogoutSuccess():
        Navigator.pop(context, true); // báo cho menu: được phép về trang đăng nhập
      case LogoutRejected():
        await _showError(
          icon: Icons.error_outline_rounded,
          title: 'Logout failed',
          message: 'The server could not log you out.\nPlease try again.',
        );
      case LogoutFailure():
        await _showError(
          icon: Icons.wifi_off_rounded,
          title: AppStrings.of(context).text('connectionError'),
          message: (result as LogoutFailure).isNetworkFailure ? AppStrings.of(context).text('network') : 'The server could not process the request. Please try again.',
        );
    }
  }

  Future<void> _showError({
    required IconData icon,
    required String title,
    required String message,
  }) {
    return showErrorDialog(
      context,
      icon: icon,
      title: title,
      message: message,
      buttonText: 'OK',
    );
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: _controller,
      builder: (context, _) {
        final isLoading = _controller.isLoading;

        return PopScope(
          // Khóa nút Back và chạm ra ngoài khi đang gọi API
          canPop: !isLoading,
          child: Dialog(
            backgroundColor: Theme.of(context).colorScheme.surface,
            surfaceTintColor: Colors.transparent,
            insetPadding: const EdgeInsets.symmetric(horizontal: 32, vertical: 24),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            child: Padding(
              padding: const EdgeInsets.fromLTRB(24, 28, 24, 24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  // Icon tròn nền đỏ nhạt
                  Container(
                    width: 60,
                    height: 60,
                    decoration: const BoxDecoration(
                      color: AppColors.dangerSoft,
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(
                      Icons.logout_rounded,
                      color: _logoutColor,
                      size: 28,
                    ),
                  ),
                  const SizedBox(height: 16),
                  Text(
                    '${AppStrings.of(context).text('logout')}',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontSize: 20,
                      fontWeight: FontWeight.w700,
                      color: Theme.of(context).colorScheme.onSurface,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    AppStrings.of(context).text('logoutConfirm'),
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontSize: 15,
                      height: 1.4,
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(height: 24),
                  Row(
                    children: [
                      // Nút Cancel: nền trắng, viền xám đậm, chữ đen. Bị khóa khi đang gọi API.
                      Expanded(
                        child: OutlinedButton(
                          onPressed:
                          isLoading ? null : () => Navigator.pop(context, false),
                          style: OutlinedButton.styleFrom(
                            minimumSize: const Size(0, 48),
                            foregroundColor: Theme.of(context).colorScheme.onSurface,
                            side: const BorderSide(
                              color: AppColors.borderStrong,
                              width: 1.2,
                            ),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(10),
                            ),
                            textStyle: const TextStyle(
                              fontSize: 16,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                          child: Text(AppStrings.of(context).text('cancel')),
                        ),
                      ),
                      const SizedBox(width: 12),
                      // Nút Log out: nền đỏ, chữ trắng. Đang gọi API: chữ -> spinner, bị khóa.
                      Expanded(
                        child: ElevatedButton(
                          onPressed: isLoading ? null : _onConfirm,
                          style: ElevatedButton.styleFrom(
                            minimumSize: const Size(0, 48),
                            backgroundColor: _logoutColor,
                            foregroundColor: Colors.white,
                            // Giữ nguyên màu đỏ khi bị khóa (mặc định sẽ xám)
                            disabledBackgroundColor: _logoutColor,
                            disabledForegroundColor: Colors.white,
                            elevation: 0,
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(10),
                            ),
                            textStyle: const TextStyle(
                              fontSize: 16,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                          child: AnimatedSwitcher(
                            duration: const Duration(milliseconds: 200),
                            child: isLoading
                                ? const ButtonSpinner(
                              key: ValueKey('spinner'),
                              semanticsLabel: 'Logging out',
                            )
                                : Text(AppStrings.of(context).text('logout'), key: const ValueKey('label')),
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}