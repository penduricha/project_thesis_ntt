import 'package:flutter/material.dart';

/// Bảng màu của toàn app, tương đương khối `:root { --primary-blue: ... }` trong CSS.
///
/// Quy ước:
///  - Màu chỉ được khai báo MỘT lần ở đây. Nơi khác dùng `AppColors.tenMau`, KHÔNG viết `Color(0xFF...)`.
///  - Muốn đổi giao diện: sửa giá trị tại đây, mọi màn hình đổi theo.
///  - Tên theo VAI TRÒ (dùng để làm gì), không theo tên màu, để đổi màu không phải đổi tên.
///
/// Cách dùng:
/// ```dart
/// import '../../../../core/constants/app_colors.dart';   // đường dẫn tương đối tới file này
/// Container(color: AppColors.primaryBlue)
/// ```
class AppColors {
  AppColors._(); // chỉ là hằng số, không tạo đối tượng

  // ---- Thương hiệu (brand) ----
  static const Color primaryBlue = Color(0xFF004B87); // nút chính, tab đang chọn, avatar
  static const Color primaryBlueSoft = Color(0xFFE6EEF5); // nền tròn quanh icon xanh
  static const Color accentBlue = Color(0xFF1D61E7); // checkbox, liên kết nhấn
  static const Color highlightBlue = Color(0xFFF0F6FF); // nền mục được chọn

  // ---- Nguy hiểm / lỗi (danger) ----
  static const Color primaryRed = Color(0xFFC61F1F); // nút Delete/OK lỗi, nút xóa vuông, viền lỗi
  static const Color primaryRedSoft = Color(0x1AC61F1F); // đỏ ~10% độ đậm (nền biểu tượng lỗi)
  static const Color dangerSoft = Color(0xFFFCE9E9); // nền tròn quanh icon đỏ
  static const Color badgeRed = Color(0xFFFF3B30); // chấm / huy hiệu thông báo

  // ---- Nền (background / surface) ----
  static const Color backgroundLight = Color(0xFFF5F5F5);
  static const Color fieldFill = Color(0xFFF9F9F9); // nền ô nhập (đăng nhập, tạo tài khoản)
  static const Color fieldFillDisabled = Color(0xFFF1F1F1); // nền ô nhập khi bị khóa
  static const Color searchFill = Color(0xFFEFEFEF); // nền ô tìm kiếm, ô nhập khóa
  static const Color chatBarFill = Color(0xFFF7F7F8); // thanh nhập tin nhắn
  static const Color chatFieldFill = Color(0xFFEFEFF0); // ô nhập tin nhắn, nền nút phụ
  static const Color modalFill = Color(0xFFF2F2F7); // nền modal tùy chọn chat
  static const Color overlayScrim = Color(0x99000000); // lớp mờ phía sau modal (đen 60%)

  // ---- Viền / đường kẻ (border / divider) ----
  static const Color divider = Color(0xFFEDEDED); // đường kẻ mảnh giữa các mục
  static const Color border = Color(0xFFE5E5EA); // viền nhạt, đường kẻ chat
  static const Color borderIos = Color(0xFFD1D1D6); // viền ô nhập tin nhắn
  static const Color borderStrong = Color(0xFFBDBDBD); // viền nút phụ (Cancel)
  static const Color borderCancel = Color(0xFFCFCFCF); // viền nút Cancel của dialog

  // ---- Chữ (text) ----
  static const Color textDark = Color(0xFF333333);
  static const Color textTitle = Color(0xFF222222); // tiêu đề dialog
  static const Color textHeading = Color(0xFF1F1F1F); // tiêu đề dialog lỗi
  static const Color textMuted = Color(0xFF757575); // nội dung phụ
  static const Color textSecondary = Color(0xFF6B6B6B); // nội dung dialog lỗi
  static const Color textInactive = Color(0xFF8E8E93); // tab chưa chọn, chữ phụ
}
