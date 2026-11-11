import 'package:flutter/material.dart';

/// Vòng xoay trắng đặt trong nút (thay cho chữ) khi đang xử lý API.
/// Kích thước cố định để nút không bị nhảy layout khi chuyển qua lại với chữ.
class ButtonSpinner extends StatelessWidget {
  const ButtonSpinner({super.key, this.semanticsLabel = 'Loading'});

  /// Nội dung trình đọc màn hình sẽ đọc khi spinner xuất hiện.
  final String semanticsLabel;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 22,
      height: 22,
      child: CircularProgressIndicator(
        strokeWidth: 2.5,
        color: Colors.white,
        semanticsLabel: semanticsLabel,
      ),
    );
  }
}