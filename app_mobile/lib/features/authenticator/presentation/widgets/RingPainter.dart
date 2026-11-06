import 'dart:math' as math;

import 'package:flutter/material.dart';


class RingPainter extends CustomPainter {
  RingPainter({required this.progress, required this.color});

  final double progress; // phần còn lại: 1.0 (đầy) -> 0.0 (hết)
  final Color color;

  static const double _stroke = 2.5;

  @override
  void paint(Canvas canvas, Size size) {
    final rect = (Offset.zero & size).deflate(_stroke / 2);

    // Vòng nền mờ
    final track = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = _stroke
      ..color = color.withAlpha(46);
    canvas.drawArc(rect, 0, math.pi * 2, false, track);

    if (progress <= 0) return;

    // Cung xanh còn lại. Mòn dần THEO CHIỀU KIM ĐỒNG HỒ:
    //  - Đuôi cung cố định ở 12 giờ.
    //  - Đầu cung lùi dần theo chiều kim đồng hồ: phần vừa mất đi bắt đầu từ
    //    12 giờ, đi sang phải (phía trên vòng: từ trái sang phải), rồi xuống
    //    dưới, sang trái.
    // Quy ước góc của Flutter: 0 = 3 giờ, dương = chiều kim đồng hồ, -pi/2 = 12 giờ.
    final sweep = math.pi * 2 * progress;
    final start = -math.pi / 2 + (math.pi * 2 - sweep);

    final arc = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = _stroke
      ..strokeCap = StrokeCap.round
      ..color = color;
    canvas.drawArc(rect, start, sweep, false, arc);
  }

  @override
  bool shouldRepaint(RingPainter old) =>
      old.progress != progress || old.color != color;
}