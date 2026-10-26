class UserLogin {
  String email;
  String password;

  UserLogin({this.email = '', this.password = ''});

  // Phương thức kiểm tra hợp lệ dữ liệu cơ bản
  bool validate() {
    return email.trim().isNotEmpty && password.trim().isNotEmpty;
  }
}