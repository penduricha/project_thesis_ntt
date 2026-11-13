import 'package:dio/dio.dart';
import 'package:flutter/material.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/storage/auth_storage.dart';
import '../../../config/network/spring_config_header.dart';
import 'logout_confirm_dialog.dart';

class AppMenuBar extends StatefulWidget {
  const AppMenuBar({
    super.key,
    this.adminName, // null (mặc định) -> tự lấy tên từ server theo adminId đã lưu
    this.subtitle, // vd: email hoặc "Admin Id: 123456", để null thì không hiện
  });

  final String? adminName;
  final String? subtitle;

  @override
  State<AppMenuBar> createState() => _AppMenuBarState();
}

class _AppMenuBarState extends State<AppMenuBar> {
  // Màu viền/đường kẻ trung tính (đồng bộ với viền ô nhập ở màn Login)
  static const Color _dividerColor = Color(0xFFEDEDED);

  static const String _adminNamePath = '/admin/information/admin-name';
  static const String _fallbackName = 'Administrator';

  // null = đang tải: hiện khung xám thay vì một cái tên sai rồi đổi.
  String? _adminName;

  @override
  void initState() {
    super.initState();
    final presetName = widget.adminName;
    if (presetName != null) {
      _adminName = presetName;
    } else {
      _loadAdminName();
    }
  }

  /// Tên đã lưu lúc đăng nhập thì dùng luôn, KHÔNG gọi API.
  /// Chỉ khi chưa có tên (phiên cũ lưu trước khi có tính năng này) mới gọi
  /// GET /admin/information/admin-name một lần rồi lưu lại cho các lần sau.
  Future<void> _loadAdminName() async {
    final name = await _resolveAdminName();
    if (!mounted) return;
    setState(() => _adminName = name);
  }

  Future<String> _resolveAdminName() async {
    try {
      final authStorage = AuthStorage();
      final session = AuthStorage.cachedSession ?? await authStorage.readSession();
      if (session == null) return _fallbackName;

      final storedName = session.adminName;
      if (storedName != null && storedName.isNotEmpty) return storedName;

      final fetchedName = await _fetchAdminName(session.adminId);
      if (fetchedName == null) return _fallbackName; // lỗi: lần sau sẽ thử lại
      await authStorage.saveAdminName(fetchedName);
      return fetchedName;
    } catch (_) {
      return _fallbackName; // không để menu bị trống
    }
  }

  /// Server trả chữ thuần. Trả null nếu lỗi mạng / server / tên rỗng.
  Future<String?> _fetchAdminName(int adminId) async {
    final response = await SpringConfigHeader().getApiClientNoHeaders().get<String>(
      _adminNamePath,
      queryParameters: {'adminId': adminId},
      options: Options(responseType: ResponseType.plain),
    );
    final name = response.data?.trim();
    return response.statusCode == 200 && name != null && name.isNotEmpty ? name : null;
  }

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
                    label: 'Account',
                    onTap: () {
                      Navigator.pop(context); // Đóng drawer
                      // TODO: Chuyển sang màn hình Tài khoản
                    },
                  ),
                  _MenuItem(
                    icon: Icons.settings_outlined,
                    label: 'Settings',
                    onTap: () {
                      Navigator.pop(context); // Đóng drawer
                      // TODO: Chuyển sang màn hình Cài đặt
                    },
                  ),
                  _MenuItem(
                    icon: Icons.feedback_outlined,
                    label: 'Send Feedback',
                    onTap: () {
                      Navigator.pop(context); // Đóng drawer
                      // TODO: Chuyển sang màn hình gửi phản hồi
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
                  label: const Text('Log out'),
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
                _buildNameText(),
                if (widget.subtitle != null) ...[
                  const SizedBox(height: 2),
                  Text(
                    widget.subtitle!,
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

  // Đang tải: khung xám cùng chiều cao chữ (menu không bị giật). Xong: tên admin.
  Widget _buildNameText() {
    final name = _adminName;
    if (name == null) {
      return Container(
        width: 140,
        height: 16,
        decoration: BoxDecoration(
          color: _dividerColor,
          borderRadius: BorderRadius.circular(4),
        ),
      );
    }
    return Text(
      name,
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
    );
  }

  // Đăng xuất là thao tác "phá hủy phiên" -> luôn hỏi xác nhận trước khi thực hiện.
  // Popup tự gọi API đăng xuất và chỉ trả về true khi server xác nhận thành công.
  Future<void> _onLogout(BuildContext context) async {
    final loggedOut = await showDialog<bool>(
      context: context,
      builder: (_) => const LogoutConfirmDialog(),
    );

    // Bấm Cancel, chạm ra ngoài hoặc đăng xuất thất bại -> giữ nguyên drawer đang mở
    if (loggedOut != true || !context.mounted) return;

    // rootNavigator: true -> dùng Navigator GỐC của MaterialApp (không phải
    // Navigator lồng trong TabBarApp), nên xóa sạch stack gồm cả route '/home'
    // chứa TabBarApp. Sau đó chỉ còn LoginScreen, không còn thanh Tab.
    Navigator.of(context, rootNavigator: true)
        .pushNamedAndRemoveUntil('/login', (route) => false);
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