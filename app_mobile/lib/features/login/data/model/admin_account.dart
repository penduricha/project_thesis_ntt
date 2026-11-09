/// Model đăng nhập admin.
///
/// Hiện tại dùng tài khoản cứng để test. Khi có API thật, chỉ cần thay
/// phần bên trong [validate] (hoặc chuyển thành hàm async gọi server).
class AdminLogin {
  // TODO: bỏ dữ liệu cứng khi nối API.
  static const int _validAdminId = 2500021770;
  static const String _validPassword = '1234';

  int? adminId;
  String password;

  AdminLogin({this.adminId, this.password= ''});

  /// true khi Admin Id và mật khẩu đều đúng.
  bool validate() {
    return adminId == _validAdminId && password.trim() == _validPassword;
  }
}