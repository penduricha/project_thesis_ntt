import 'package:flutter/material.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/localization/app_strings.dart';
import '../../../settings/presentation/settings_screen.dart';
import '../../../../core/storage/auth_storage.dart';
import '../../../authenticator/data/service/enrollment_service.dart';
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
  static const Color _dividerColor = AppColors.divider;

  // Đầy đủ: GET /api/mobile/admin/information/admin-name (Bearer access token).
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
  /// GET /api/mobile/admin/information/admin-name một lần rồi lưu lại cho các lần sau.
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

      final fetchedName = await _fetchAdminName(session.accessToken);
      if (fetchedName == null) return _fallbackName; // lỗi: lần sau sẽ thử lại
      await authStorage.saveAdminNameIfCurrent(session.refreshToken, fetchedName);
      return fetchedName;
    } catch (_) {
      return _fallbackName; // không để menu bị trống
    }
  }

  /// Server trả { success, message, data: { adminName } }; adminId lấy từ access token nên không gửi kèm.
  /// Nếu access token hết hạn (401), tự làm mới bằng refresh token rồi gọi lại 1 lần.
  Future<String?> _fetchAdminName(String accessToken) async {
    var response = await SpringConfigHeader()
        .getApiClientWithHeadersToken(accessToken)
        .get<Map<String, dynamic>>(_adminNamePath);
    if (response.statusCode == 401) {
      final refreshed = await const SecureStorageEnrollmentAuth().refreshAccessToken();
      if (refreshed == null || refreshed.isEmpty) return null;
      response = await SpringConfigHeader()
          .getApiClientWithHeadersToken(refreshed)
          .get<Map<String, dynamic>>(_adminNamePath);
    }
    final data = response.data?['data'];
    final name = data is Map ? data['adminName'] : null;
    if (response.statusCode != 200 || name is! String) return null;
    final trimmed = name.trim();
    return trimmed.isEmpty ? null : trimmed;
  }

  @override
  Widget build(BuildContext context) {
    return Drawer(
      backgroundColor: Theme.of(context).colorScheme.surface,
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
                    label: AppStrings.of(context).text('account'),
                    onTap: () => _showAccountInfo(context),
                  ),
                  _MenuItem(
                    icon: Icons.settings_outlined,
                    label: AppStrings.of(context).text('settings'),
                    onTap: () {
                      final navigator = Navigator.of(context);
                      navigator.pop();
                      navigator.push(MaterialPageRoute<void>(builder: (_) => const SettingsScreen()));
                    },
                  ),
                  _MenuItem(
                    icon: Icons.feedback_outlined,
                    label: AppStrings.of(context).text('feedback'),
                    onTap: () => _showInfoMessage(
                      context,
                      AppStrings.of(context).text('feedbackHint'),
                    ),
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
                  label: Text(AppStrings.of(context).text('logout')),
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

  void _showAccountInfo(BuildContext context) {
    final navigator = Navigator.of(context);
    final adminId = AuthStorage.cachedAdminId;
    final displayName = _adminName ?? AuthStorage.cachedAdminName ?? _fallbackName;
    navigator.pop();
    showDialog<void>(
      context: navigator.context,
      builder: (dialogContext) => AlertDialog(
        title: Text(AppStrings.of(dialogContext).text('account')),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('${AppStrings.of(dialogContext).text('name')}: $displayName'),
            if (adminId != null) ...[
              const SizedBox(height: 6),
              Text('Admin ID: $adminId'),
            ],
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(),
            child: Text(AppStrings.of(dialogContext).text('close')),
          ),
        ],
      ),
    );
  }

  void _showInfoMessage(BuildContext context, String message) {
    final messenger = ScaffoldMessenger.maybeOf(context);
    Navigator.pop(context);
    messenger?.showSnackBar(SnackBar(content: Text(message)));
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
      leading: Icon(icon, color: Theme.of(context).colorScheme.onSurface),
      title: Text(
        label,
        style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w500),
      ),
      trailing: Icon(Icons.chevron_right, color: Theme.of(context).colorScheme.onSurfaceVariant),
      onTap: onTap,
    );
  }
}