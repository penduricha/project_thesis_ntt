import 'package:flutter/material.dart';

/// Red used for destructive actions (delete button, swipe-to-delete background).
const Color primaryRed = Color(0xFFC61F1F);

/// Shared confirm dialog. Same look as the "Enter secret key" dialog:
/// white rounded card | icon in a light-blue circle | bold centered title |
/// grey centered message | two equal buttons side by side
/// (outlined "Cancel" + filled confirm button).
///
/// Usage:
/// ```dart
/// final ok = await AppConfirmDeleteDialog.show(
///   context,
///   title: 'Delete authenticator?',
///   message: 'The key of ... will be removed from this device.',
/// );
/// ```
class AppConfirmDeleteDialog extends StatelessWidget {
  const AppConfirmDeleteDialog({
    super.key,
    required this.title,
    required this.message,
    this.icon = Icons.delete_outline_rounded,
    this.cancelLabel = 'Cancel',
    this.confirmLabel = 'Delete',
    this.confirmColor = primaryRed,
  });

  final String title;
  final String message;
  final IconData icon;
  final String cancelLabel;
  final String confirmLabel;

  /// Background of the confirm button (text is always white).
  final Color confirmColor;

  // ---- Style tokens (match the secret-key dialog) ----
  static const Color _brandBlue = Color(0xFF004B87);
  static const Color _iconCircle = Color(0xFFE6EEF5);
  static const Color _titleColor = Color(0xFF222222);
  static const Color _messageColor = Color(0xFF757575);
  static const Color _cancelBorder = Color(0xFFCFCFCF);
  static const double _radius = 20;
  static const double _buttonRadius = 12;
  static const double _buttonHeight = 46;

  /// Shows the dialog. Returns true only when the confirm button is tapped.
  static Future<bool> show(
      BuildContext context, {
        required String title,
        required String message,
        IconData icon = Icons.delete_outline_rounded,
        String cancelLabel = 'Cancel',
        String confirmLabel = 'Delete',
        Color confirmColor = primaryRed,
      }) async {
    final result = await showDialog<bool>(
      context: context,
      builder: (_) => AppConfirmDeleteDialog(
        title: title,
        message: message,
        icon: icon,
        cancelLabel: cancelLabel,
        confirmLabel: confirmLabel,
        confirmColor: confirmColor,
      ),
    );
    return result == true;
  }

  @override
  Widget build(BuildContext context) {
    final buttonShape = RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(_buttonRadius),
    );
    const buttonText = TextStyle(fontSize: 15, fontWeight: FontWeight.w700);

    return Dialog(
      backgroundColor: Colors.white,
      surfaceTintColor: Colors.transparent,
      insetPadding: const EdgeInsets.symmetric(horizontal: 30, vertical: 24),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(_radius),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 24, 20, 20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            // Icon in a light-blue circle
            Container(
              width: 52,
              height: 52,
              decoration: const BoxDecoration(
                color: _iconCircle,
                shape: BoxShape.circle,
              ),
              child: Icon(icon, color: _brandBlue, size: 26),
            ),
            const SizedBox(height: 14),
            Text(
              title,
              textAlign: TextAlign.center,
              style: const TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.w700,
                color: _titleColor,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              message,
              textAlign: TextAlign.center,
              style: const TextStyle(
                fontSize: 14,
                height: 1.4,
                color: _messageColor,
              ),
            ),
            const SizedBox(height: 24),
            // Two equal buttons side by side
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: () => Navigator.of(context).pop(false),
                    style: OutlinedButton.styleFrom(
                      minimumSize: const Size.fromHeight(_buttonHeight),
                      foregroundColor: Colors.black87,
                      backgroundColor: Colors.white,
                      side: const BorderSide(color: _cancelBorder),
                      shape: buttonShape,
                      textStyle: buttonText,
                    ),
                    child: Text(cancelLabel),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: FilledButton(
                    onPressed: () => Navigator.of(context).pop(true),
                    style: FilledButton.styleFrom(
                      minimumSize: const Size.fromHeight(_buttonHeight),
                      backgroundColor: confirmColor,
                      foregroundColor: Colors.white,
                      elevation: 0,
                      shape: buttonShape,
                      textStyle: buttonText,
                    ),
                    child: Text(confirmLabel),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}