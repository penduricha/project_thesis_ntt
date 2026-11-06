import 'package:flutter/material.dart';

/// Hiện popup lỗi dùng chung.
///
/// Bố cục theo popup mẫu: icon tròn ở đầu -> tiêu đề + nội dung căn giữa
/// -> nút OK màu đỏ nằm ở footer.
Future<void> showErrorDialog(
    BuildContext context, {
      required String title,
      required String message,
      String buttonText = 'OK',
      IconData icon = Icons.error_outline_rounded,
    }) {
  return showDialog<void>(
    context: context,
    builder: (_) => ErrorDialog(
      title: title,
      message: message,
      buttonText: buttonText,
      icon: icon,
    ),
  );
}

class ErrorDialog extends StatelessWidget {
  const ErrorDialog({
    super.key,
    required this.title,
    required this.message,
    this.buttonText = 'OK',
    this.icon = Icons.error_outline_rounded,
  });

  final String title;
  final String message;
  final String buttonText;
  final IconData icon;

  static const Color danger = Color(0xFFC61F1F); // màu nút OK
  static const Color _dangerSoft = Color(0x1AC61F1F); // danger ~10% opacity
  static const Color _titleColor = Color(0xFF1F1F1F);
  static const Color _messageColor = Color(0xFF6B6B6B);

  @override
  Widget build(BuildContext context) {
    return Dialog(
      backgroundColor: Colors.white,
      surfaceTintColor: Colors.transparent,
      insetPadding: const EdgeInsets.symmetric(horizontal: 40, vertical: 24),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      child: SingleChildScrollView(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              // ---------- Header: icon tròn ----------
              Container(
                width: 64,
                height: 64,
                decoration: const BoxDecoration(
                  color: _dangerSoft,
                  shape: BoxShape.circle,
                ),
                child: Icon(icon, color: danger, size: 30),
              ),

              const SizedBox(height: 16),

              // ---------- Body: tiêu đề + nội dung ----------
              Text(
                title,
                textAlign: TextAlign.center,
                style: const TextStyle(
                  fontSize: 20,
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

              // ---------- Footer: nút OK đỏ ----------
              SizedBox(
                width: double.infinity,
                height: 48,
                child: ElevatedButton(
                  autofocus: true,
                  onPressed: () => Navigator.of(context).pop(),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: danger,
                    foregroundColor: Colors.white,
                    elevation: 0,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(10),
                    ),
                  ),
                  child: Text(
                    buttonText,
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}