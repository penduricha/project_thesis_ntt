import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../../presentation/screens/totp_generator.dart';

// ============================================================================
//  KÍCH HOẠT 2FA TỪ MOBILE  (giai đoạn 3 của luồng Spring Boot)
//
//  Web đã bấm "Kích hoạt" -> server ghi khóa (đã mã hóa AES-GCM) vào MariaDB với
//  confirmed = false. Mobile làm 3 việc, theo đúng thứ tự:
//
//    1. GET  /api/admin/mobile/2fa/enrollment/pending
//         -> { algorithm: "HmacSHA256", digits: 6, periodSeconds: 15|30|45|60, ... }
//         Server KHÔNG trả khóa. Khóa chỉ có do người dùng nhập trong popup.
//    2. Sinh mã từ (khóa người dùng nhập, thông số ở bước 1, giờ hiện tại).
//    3. POST /api/admin/mobile/2fa/enrollment/confirm   { "code": "123456" }
//         Server tự giải mã khóa của mình, tính mã, so khớp (lệch tối đa ±1 chu kỳ).
//         Đúng -> confirmed = true và totp_enabled = true: 2FA bật hoàn toàn.
//
//  Khóa nhập sai => mã sai => 422. Mỗi lần gọi confirm đều bị đếm; quá 5 lần
//  server xóa phần cài dở và trả 429, admin phải bấm "Kích hoạt" lại trên web.
// ============================================================================

/// Địa chỉ Spring Boot. Đổi khi chạy:
///   flutter run --dart-define=API_BASE_URL=http://192.168.1.10:8080
/// Android emulator: 10.0.2.2 chính là localhost của máy tính.
/// Máy thật: dùng IP LAN của máy chạy Spring (cùng Wi-Fi).
const String kApiBaseUrl = String.fromEnvironment(
  'API_BASE_URL',
  defaultValue: 'http://10.0.2.2:8080',
);

/// Context-path của Spring (comment bên Java ghi /api/...). Đổi nếu khác.
const String kApiPrefix = '/api';

// ---------------------------------------------------------------------------
//  Thông tin đăng nhập hiện tại của app  (NỐI VÀO MÀN LOGIN CỦA BẠN)
// ---------------------------------------------------------------------------

class AdminProfile {
  const AdminProfile({required this.adminId, required this.name});

  final String adminId; // vd: "2500021775"
  final String name; // vd: "Từ Quang Nhật"
}

/// App cần 2 thứ từ phiên đăng nhập: access token và thông tin admin.
/// Access token phía Java sống 5 phút, nên nếu có cơ chế refresh thì làm trong
/// [accessToken]: luôn trả về token còn hạn (hoặc null nếu phải đăng nhập lại).
abstract class EnrollmentAuth {
  Future<String?> accessToken();
  Future<AdminProfile?> profile();
}

/// Bản mặc định: đọc từ flutter_secure_storage.
/// CHỈ CẦN ĐỔI 3 TÊN KEY cho khớp với key màn Login của bạn đang ghi.
class SecureStorageEnrollmentAuth implements EnrollmentAuth {
  const SecureStorageEnrollmentAuth({
    this.tokenKey = 'access_token',
    this.adminIdKey = 'admin_id',
    this.adminNameKey = 'admin_name',
    FlutterSecureStorage storage = const FlutterSecureStorage(),
  }) : _storage = storage;

  final String tokenKey;
  final String adminIdKey;
  final String adminNameKey;
  final FlutterSecureStorage _storage;

  @override
  Future<String?> accessToken() => _storage.read(key: tokenKey);

  @override
  Future<AdminProfile?> profile() async {
    final id = await _storage.read(key: adminIdKey);
    if (id == null || id.isEmpty) return null;
    final name = await _storage.read(key: adminNameKey);
    return AdminProfile(
      adminId: id,
      name: (name == null || name.isEmpty) ? 'Admin' : name,
    );
  }
}

// ---------------------------------------------------------------------------
//  Kết quả và lỗi
// ---------------------------------------------------------------------------

/// Kết quả khi server đã bật 2FA: thông số thật để app sinh mã về sau.
class EnrolledKey {
  const EnrolledKey({
    required this.adminId,
    required this.adminName,
    required this.periodSeconds,
    required this.digits,
    required this.algorithm,
  });

  final String adminId;
  final String adminName;
  final int periodSeconds;
  final int digits;
  final TotpAlgorithm algorithm;
}

/// Lỗi đã được dịch sang câu ngắn gọn để hiện dưới ô nhập khóa.
class EnrollmentException implements Exception {
  const EnrollmentException(this.message);

  final String message;

  @override
  String toString() => message;
}

class _PendingParams {
  const _PendingParams({
    required this.algorithm,
    required this.digits,
    required this.periodSeconds,
  });

  final TotpAlgorithm algorithm;
  final int digits;
  final int periodSeconds;
}

// ---------------------------------------------------------------------------
//  Service
// ---------------------------------------------------------------------------

class EnrollmentService {
  EnrollmentService({required EnrollmentAuth auth, Dio? dio})
      : _auth = auth,
        _dio = dio ??
            Dio(
              BaseOptions(
                baseUrl: kApiBaseUrl,
                connectTimeout: const Duration(seconds: 10),
                receiveTimeout: const Duration(seconds: 10),
                headers: {'Accept': 'application/json'},
              ),
            );

  final EnrollmentAuth _auth;
  final Dio _dio;

  static const String _pendingPath = '$kApiPrefix/admin/mobile/2fa/enrollment/pending';
  static const String _confirmPath = '$kApiPrefix/admin/mobile/2fa/enrollment/confirm';

  static const String _msgSession = 'Session expired. Please sign in again.';
  static const String _msgNetwork = 'Cannot reach the server. Check your connection.';
  static const String _msgServer = 'Server error. Please try again.';

  /// Gửi khóa người dùng nhập lên để kích hoạt. Ném [EnrollmentException] nếu thất bại.
  /// Mỗi lần gọi = đúng MỘT request confirm (không tự thử lại, vì server đếm từng lần sai).
  Future<EnrolledKey> activate(String secret) async {
    final token = await _auth.accessToken();
    if (token == null || token.isEmpty) {
      throw const EnrollmentException(_msgSession);
    }

    // Kiểm tra TRƯỚC khi gọi confirm: không để server bật 2FA mà app thiếu thông tin tạo tài khoản.
    final profile = await _auth.profile();
    if (profile == null) {
      throw const EnrollmentException('Admin info not found. Please sign in again.');
    }

    final pending = await _fetchPending(token);

    final String code;
    try {
      code = TotpGenerator.generate(
        secret,
        period: pending.periodSeconds,
        digits: pending.digits,
        algorithm: pending.algorithm,
      );
    } on FormatException {
      throw const EnrollmentException('Invalid key format.');
    }

    await _confirm(token, code);

    return EnrolledKey(
      adminId: profile.adminId,
      adminName: profile.name,
      periodSeconds: pending.periodSeconds,
      digits: pending.digits,
      algorithm: pending.algorithm,
    );
  }

  // ---- Bước 1 ----
  Future<_PendingParams> _fetchPending(String token) async {
    try {
      final res = await _dio.get<Map<String, dynamic>>(
        _pendingPath,
        options: _bearer(token),
      );
      final data = res.data?['data'] as Map<String, dynamic>?;
      if (data == null) throw const EnrollmentException(_msgServer);

      return _PendingParams(
        algorithm: TotpAlgorithm.parse(data['algorithm'] as String?),
        digits: (data['digits'] as num).toInt(),
        periodSeconds: (data['periodSeconds'] as num).toInt(),
      );
    } on DioException catch (e) {
      throw _map(e);
    } on FormatException {
      throw const EnrollmentException('Unsupported algorithm from server.');
    }
  }

  // ---- Bước 3 ----
  Future<void> _confirm(String token, String code) async {
    try {
      await _dio.post<Map<String, dynamic>>(
        _confirmPath,
        data: {'code': code},
        options: _bearer(token),
      );
    } on DioException catch (e) {
      throw _map(e);
    }
  }

  Options _bearer(String token) =>
      Options(headers: {'Authorization': 'Bearer $token'});

  EnrollmentException _map(DioException e) {
    final status = e.response?.statusCode;
    if (status == null) return const EnrollmentException(_msgNetwork);
    if (status == 401) return const EnrollmentException(_msgSession);
    if (status == 404) {
      return const EnrollmentException(
        'Nothing to activate. Tap "Activate 2FA" on the web first.',
      );
    }
    if (status == 410) {
      return const EnrollmentException(
        'Activation expired (10 minutes). Tap "Activate 2FA" on the web again.',
      );
    }
    if (status == 422) {
      return const EnrollmentException('Incorrect key. Check it and try again.');
    }
    if (status == 429) {
      // Server đã xóa phần cài dở sau khi nhập sai quá số lần.
      return const EnrollmentException(
        'Too many wrong attempts. Tap "Activate 2FA" on the web to start again.',
      );
    }
    if (status == 400) return const EnrollmentException('Invalid code.');
    return const EnrollmentException(_msgServer);
  }
}