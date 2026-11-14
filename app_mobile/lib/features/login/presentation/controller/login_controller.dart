import 'package:flutter/foundation.dart';

import '../../../../core/device/device_info_service.dart';
import '../../../../core/storage/auth_storage.dart';
import '../../../authenticator/data/service/account_store.dart';
import '../../../authenticator/presentation/screens/totp_generator.dart';
import '../../data/model/admin_account.dart';
import '../../data/model/login_result.dart';
import '../../data/service/login_service.dart';

/// Điều phối đăng nhập: lấy thông tin máy -> gọi API -> lưu phiên (adminId + token).
/// Màn hình chỉ lắng nghe [isLoading] và nhận [LoginResult], không biết chi tiết mạng.
class LoginController extends ChangeNotifier {
  LoginController({
    LoginService? loginService,
    AuthStorage? authStorage,
    DeviceInfoService? deviceInfoService,
  })  : _loginService = loginService ?? LoginService(),
        _authStorage = authStorage ?? AuthStorage(),
        _deviceInfoService = deviceInfoService ?? DeviceInfoService();

  final LoginService _loginService;
  final AuthStorage _authStorage;
  final DeviceInfoService _deviceInfoService;

  bool _isLoading = false;
  bool _isDisposed = false;

  /// true khi đang xử lý API: màn hình dùng để hiện spinner và khóa form.
  bool get isLoading => _isLoading;

  Future<LoginResult> login({
    required int adminId,
    required String password,
  }) async {
    _setLoading(true);
    try {
      final admin = AdminLogin(
        adminId: adminId,
        password: password,
        deviceName: await _deviceInfoService.getDeviceName(),
        platform: _deviceInfoService.platform,
        totpCode: await _currentTotpCode(adminId),
      );

      final result = await _loginService.login(admin);

      // Trường hợp 1: lưu adminId + cặp token để làm auto login và dùng adminId toàn app.
      if (result is LoginSuccess) {
        await _authStorage.saveSession(result.session);
      }
      return result;
    } catch (_) {
      return const LoginFailure('Something went wrong.\nPlease try again.');
    } finally {
      // Dù thành công hay thất bại đều mở khóa form.
      _setLoading(false);
    }
  }

  /// Mã TOTP hiện tại từ khóa đã cài trên máy này cho adminId (nếu có).
  /// Server bắt buộc mã khi tài khoản đã bật 2FA, và bỏ qua khi chưa bật.
  /// Lỗi đọc kho an toàn / khóa hỏng: coi như không có khóa, để server trả TOTP_REQUIRED.
  Future<String?> _currentTotpCode(int adminId) async {
    try {
      final accounts = await AccountStore(adminId: '$adminId').load();
      if (accounts.isEmpty) return null;
      final acc = accounts.first;
      return TotpGenerator.generate(
        acc.secret,
        period: acc.periodSeconds,
        digits: acc.digits,
        algorithm: acc.algorithm,
      );
    } catch (_) {
      return null;
    }
  }

  void _setLoading(bool value) {
    if (_isDisposed || _isLoading == value) return;
    _isLoading = value;
    notifyListeners();
  }

  @override
  void dispose() {
    _isDisposed = true;
    super.dispose();
  }
}