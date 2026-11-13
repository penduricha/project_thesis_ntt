import '../../presentation/screens/totp_generator.dart';

// ============================================================================
//  MODEL TÀI KHOẢN AUTHENTICATOR
//
//  Tài khoản tạo từ server NTT (enrolled = true):
//    issuer  = tên quản trị viên          vd: "Từ Quang Nhật"
//    account = adminId (thay cho email)   vd: "2500021775"
//    periodSeconds / digits / algorithm lấy từ server (API pending), KHÔNG tự đặt.
//  Tài khoản demo giữ mặc định của Google/Microsoft: SHA-1, 6 số, 30 giây.
// ============================================================================

class AuthAccount {
  const AuthAccount({
    required this.id,
    required this.issuer,
    required this.account,
    required this.secret,
    this.periodSeconds = TotpGenerator.defaultPeriod,
    this.digits = TotpGenerator.defaultDigits,
    this.algorithm = TotpAlgorithm.sha1,
    this.enrolled = false,
  });

  final String id;
  final String issuer; // vd: "Microsoft", "Từ Quang Nhật"
  final String account; // vd: email đăng nhập, hoặc adminId
  final String secret; // khóa bí mật dạng Base32
  final int periodSeconds; // 15 | 30 | 45 | 60
  final int digits;
  final TotpAlgorithm algorithm;

  /// true = tạo từ luồng kích hoạt 2FA của server, được lưu vào secure storage.
  final bool enrolled;

  Map<String, dynamic> toJson() => {
    'id': id,
    'issuer': issuer,
    'account': account,
    'secret': secret,
    'periodSeconds': periodSeconds,
    'digits': digits,
    'algorithm': algorithm.name,
  };

  factory AuthAccount.fromJson(Map<String, dynamic> json) {
    return AuthAccount(
      id: json['id'] as String,
      issuer: json['issuer'] as String,
      account: json['account'] as String,
      secret: json['secret'] as String,
      periodSeconds: (json['periodSeconds'] as num).toInt(),
      digits: (json['digits'] as num).toInt(),
      algorithm: TotpAlgorithm.parse(json['algorithm'] as String?),
      enrolled: true, // chỉ tài khoản kích hoạt từ server mới được lưu
    );
  }
}