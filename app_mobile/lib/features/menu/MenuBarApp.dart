import 'package:flutter/material.dart';

import '../../core/constants/AppColors.dart';
import '../login/presentation/screens/LoginScreen.dart';

class MenuBarApp extends StatelessWidget {
  const MenuBarApp({
    super.key,
    this.adminName = 'Tu Quang Nhat',
    this.subtitle, // vd: email hoặc "Admin Id: 123456", để null thì không hiện
  });

  final String adminName;
  final String? subtitle;

  // Màu viền/đường kẻ trung tính (đồng bộ với viền ô nhập ở màn Login)
  static const Color _dividerColor = Color(0xFFEDEDED);

  // Màu nút "Đăng xuất" trong popup xác nhận
  static const Color _logoutColor = Color(0xFFC61F1F);

  @override
  Widget build(BuildContext context) {
    return Drawer(
      backgroundColor: Colors.white,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.zero, // Giữ góc vuông chuẩn menu side
      ),
      child: SafeArea(
        child: Column(
          children: [
            _buildHeader(),

            const Divider(
              height: 1,
              thickness: 1,
              indent: 16,
              endIndent: 16,
              color: _dividerColor,
            ),

            // ---------- Nhóm điều hướng ----------
            // Dùng ListView để màn hình thấp / xoay ngang vẫn cuộn được, không bị overflow
            Expanded(
              child: ListView(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
                children: [
                  _MenuItem(
                    icon: Icons.person_outline,
                    label: 'Tài khoản',
                    onTap: () {
                      Navigator.pop(context); // Đóng drawer
                      // TODO: Chuyển sang màn hình Tài khoản
                    },
                  ),
                  _MenuItem(
                    icon: Icons.shield_outlined,
                    label: 'Xác thực 6 số',
                    onTap: () {
                      Navigator.pop(context); // Đóng drawer
                      // TODO: Chuyển sang màn hình Xác thực 6 số
                    },
                  ),
                  _MenuItem(
                    icon: Icons.settings_outlined,
                    label: 'Cài đặt',
                    onTap: () {
                      Navigator.pop(context); // Đóng drawer
                      // TODO: Chuyển sang màn hình Cài đặt
                    },
                  ),
                ],
              ),
            ),

            // ---------- Đăng xuất: nút đỏ ghim ở đáy, tách khỏi nhóm điều hướng ----------
            // Nằm trong vùng ngón cái với tới và cách xa các mục thường dùng -> khó bấm nhầm
            const Divider(
              height: 1,
              thickness: 1,
              indent: 16,
              endIndent: 16,
              color: _dividerColor,
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
              child: SizedBox(
                width: double.infinity,
                height: 48,
                child: ElevatedButton.icon(
                  onPressed: () => _onLogout(context),
                  //icon: const Icon(Icons.logout_outlined, size: 20),
                  label: const Text('Đăng xuất'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppColors.primaryRed,
                    foregroundColor: Colors.white, // áp dụng cho cả icon và text
                    elevation: 0,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                    textStyle: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  // Header: avatar + tên (tên dài sẽ cắt "...", không tràn khỏi drawer)
  Widget _buildHeader() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 24, 16, 16),
      child: Row(
        children: [
          const CircleAvatar(
            radius: 24,
            backgroundColor: AppColors.primaryBlue,
            child: Icon(Icons.person, color: Colors.white, size: 28),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  adminName,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                if (subtitle != null) ...[
                  const SizedBox(height: 2),
                  Text(
                    subtitle!,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 13, color: Colors.grey),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }

  // Đăng xuất là thao tác "phá hủy phiên" -> luôn hỏi xác nhận trước khi thực hiện
  Future<void> _onLogout(BuildContext context) async {
    // Lấy navigator trước các lệnh await để không dùng context sau khoảng chờ
    final navigator = Navigator.of(context);

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => _buildLogoutDialog(dialogContext),
    );

    // Bấm Hủy hoặc chạm ra ngoài -> giữ nguyên drawer đang mở
    if (confirmed != true) return;

    // 1. Đóng Drawer
    navigator.pop();

    // TODO: 2. Gọi API đăng xuất hoặc xóa Token/SharedPreferences ở đây
    // Ví dụ: await SharedPreferences.getInstance().then((prefs) => prefs.clear());
    // Ví dụ: await AuthService.logout();

    // 3. Chuyển về LoginScreen và xóa toàn bộ các màn hình trước đó
    navigator.pushAndRemoveUntil(
      MaterialPageRoute(builder: (context) => const LoginScreen()),
          (Route<dynamic> route) => false,
    );
  }

  // Popup xác nhận đăng xuất: mọi thứ căn giữa, 2 nút ngang hàng, cùng chiều rộng
  Widget _buildLogoutDialog(BuildContext dialogContext) {
    return Dialog(
      backgroundColor: Colors.white,
      surfaceTintColor: Colors.transparent,
      insetPadding: const EdgeInsets.symmetric(horizontal: 32, vertical: 24),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(24, 28, 24, 24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            // Icon tròn nền đỏ nhạt (xóa khối này nếu không muốn hiển thị icon)
            Container(
              width: 60,
              height: 60,
              decoration: const BoxDecoration(
                color: Color(0xFFFCE9E9),
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.logout_rounded,
                color: _logoutColor,
                size: 28,
              ),
            ),
            const SizedBox(height: 16),
            const Text(
              'Xác nhận đăng xuất',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 20,
                fontWeight: FontWeight.w700,
                color: Colors.black87,
              ),
            ),
            const SizedBox(height: 8),
            const Text(
              'Bạn có chắc chắn đăng xuất không?',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 15,
                height: 1.4,
                color: Colors.black54,
              ),
            ),
            const SizedBox(height: 24),
            Row(
              children: [
                // Nút Hủy: nền trắng, viền xám đậm, chữ đen -> nhìn rõ trên nền trắng
                Expanded(
                  child: OutlinedButton(
                    onPressed: () => Navigator.pop(dialogContext, false),
                    style: OutlinedButton.styleFrom(
                      minimumSize: const Size(0, 48),
                      foregroundColor: Colors.black87,
                      side: const BorderSide(
                        color: Color(0xFFBDBDBD),
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
                    child: const Text('Hủy'),
                  ),
                ),
                const SizedBox(width: 12),
                // Nút Đăng xuất: nền đỏ 0xFFC61F1F, chữ trắng
                Expanded(
                  child: ElevatedButton(
                    onPressed: () => Navigator.pop(dialogContext, true),
                    style: ElevatedButton.styleFrom(
                      minimumSize: const Size(0, 48),
                      backgroundColor: _logoutColor,
                      foregroundColor: Colors.white,
                      elevation: 0,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(10),
                      ),
                      textStyle: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    child: const Text('Đăng xuất'),
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

// Một dòng menu dùng chung: icon + tên + mũi tên. Vùng chạm cao 56dp, ripple bo góc 8
class _MenuItem extends StatelessWidget {
  const _MenuItem({
    required this.icon,
    required this.label,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      leading: Icon(icon, color: Colors.black87),
      title: Text(
        label,
        style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w500),
      ),
      trailing: const Icon(Icons.chevron_right, color: Colors.grey),
      onTap: onTap,
    );
  }
}