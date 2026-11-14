/// Dữ liệu gửi lên API đăng nhập mobile (POST /admin/mobile/auth/login).
///
/// Chỉ là dữ liệu, không còn tài khoản cứng: việc kiểm tra Admin Id + mật khẩu
/// do server (Spring Boot) thực hiện.
class AdminLogin {
  const AdminLogin({
    required this.adminId,
    required this.password,
    required this.deviceName,
    required this.platform,
    this.totpCode,
  });

  final int adminId;
  final String password;
  final String deviceName;
  final String platform;

  /// Mã 6 số hiện tại sinh từ khóa lưu trên máy này. null nếu máy chưa có khóa.
  /// Server chỉ đòi mã khi tài khoản đã bật 2FA; chưa bật thì bỏ qua.
  final String? totpCode;

  Map<String, dynamic> toJson() => {
    'adminId': adminId,
    'password': password,
    'deviceName': deviceName,
    'platform': platform,
    if (totpCode != null) 'totpCode': totpCode,
  };
}