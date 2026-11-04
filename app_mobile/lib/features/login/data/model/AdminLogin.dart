class AdminLogin {
  // Private fields
  int? _adminId;
  String? _password;

  // Constructor
  AdminLogin({this._adminId, this._password});

  // Constructor từ JSON
  factory AdminLogin.fromJson(Map<String, dynamic> json) {
    return AdminLogin(
      adminId: json['adminId'] != null
          ? int.tryParse(json['adminId'].toString())
          : null,
      password: json['password'] as String?,
    );
  }

  // ==================== GETTERS ====================
  int? get adminId => _adminId;
  String? get password => _password;

  // ==================== SETTERS ====================
  set adminId(int? value) => _adminId = value;
  set password(String? value) => _password = value;

  // ==================== METHODS ====================

  /// Kiểm tra dữ liệu hợp lệ cơ bản
  bool validate() {
    return _adminId != null &&
        _adminId! > 0 &&
        _password != null &&
        _password!.trim().isNotEmpty;
  }

  /// Chuyển đổi sang JSON
  Map<String, dynamic> toJson() {
    return {
      'adminId': _adminId,
      'password': _password,
    };
  }

  /// Copy object với các field được ghi đè
  AdminLogin copyWith({int? adminId, String? password}) {
    return AdminLogin(
      adminId: adminId ?? _adminId,
      password: password ?? _password,
    );
  }

  // ==================== OVERRIDES ====================
  @override
  String toString() {
    return 'AdminLogin(adminId: $_adminId, password: ${_password != null ? '***' : null})';
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is AdminLogin &&
        other._adminId == _adminId &&
        other._password == _password;
  }

  @override
  int get hashCode => Object.hash(_adminId, _password);
}