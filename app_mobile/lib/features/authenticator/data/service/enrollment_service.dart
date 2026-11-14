import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../../../../core/storage/auth_storage.dart';
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
//
//  TẮT 2FA (nút xóa TOTP trong app):
//    POST /api/admin/mobile/2fa/disable   { "code": "123456" }
//    Mã 6 số sinh từ khóa đang lưu trong app. Server đúng mới xóa credential + totp_enabled = false.
//    Chỉ khi server báo thành công (200) hoặc "chưa bật" (404) app mới được xóa khóa cục bộ.
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

  /// Đổi refresh token lấy cặp token mới (lưu lại vào máy) rồi trả access token mới.
  /// null = không làm mới được (hết phiên, mạng lỗi...). Mặc định không hỗ trợ.
  Future<String?> refreshAccessToken() async => null;
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

  /// Đường dẫn làm mới token của Spring: context-path /api + @RequestMapping("/mobile/admin").
  static const String _refreshPath = '$kApiPrefix/mobile/admin/auth/login/auto/refresh-token';

  /// Access token phía Java chỉ sống 5 phút. Còn dưới ngần này thì làm mới trước khi gọi API.
  static const Duration _refreshMargin = Duration(seconds: 60);

  // Một lần làm mới tại một thời điểm: server xoay vòng refresh token (token cũ bị xóa ngay),
  // hai lần làm mới song song bằng cùng một refresh token thì lần sau chắc chắn bị từ chối.
  static Future<String?>? _refreshing;

  @override
  Future<String?> accessToken() async {
    final token = await _storage.read(key: tokenKey);
    if (token == null || token.isEmpty) return token;
    if (!_expiresSoon(token)) return token;
    // Sắp / đã hết hạn: thử làm mới; không được thì vẫn trả token cũ để server quyết định (401).
    return (await refreshAccessToken()) ?? token;
  }

  @override
  Future<String?> refreshAccessToken() {
    return _refreshing ??= _refresh().whenComplete(() => _refreshing = null);
  }

  Future<String?> _refresh() async {
    try {
      final authStorage = AuthStorage(storage: _storage);
      final session = await authStorage.readSession();
      if (session == null) return null;

      final dio = Dio(
        BaseOptions(
          baseUrl: kApiBaseUrl,
          connectTimeout: const Duration(seconds: 10),
          receiveTimeout: const Duration(seconds: 10),
          headers: {'Accept': 'application/json'},
          validateStatus: (s) => s != null && s < 500,
        ),
      );
      final res = await dio.get<Map<String, dynamic>>(
        _refreshPath,
        options: Options(headers: {'Authorization': 'Bearer ${session.refreshToken}'}),
      );
      final data = res.data?['data'];
      final access = data is Map ? data['accessToken'] : null;
      final refresh = data is Map ? data['refreshToken'] : null;
      if (res.statusCode != 200 || access is! String || refresh is! String) {
        return null;
      }
      // Giữ nguyên adminId và tên hiển thị đã lưu, chỉ thay cặp token.
      await authStorage.saveSession(
        session.withTokens(accessToken: access, refreshToken: refresh),
      );
      return access;
    } catch (_) {
      return null;
    }
  }

  /// Đọc `exp` trong JWT (không kiểm chữ ký, chỉ để biết còn hạn bao lâu). Đọc không được thì coi như còn hạn.
  static bool _expiresSoon(String jwt) {
    try {
      final parts = jwt.split('.');
      if (parts.length != 3) return false;
      final payload = utf8.decode(base64Url.decode(base64Url.normalize(parts[1])));
      final exp = (jsonDecode(payload) as Map<String, dynamic>)['exp'];
      if (exp is! num) return false;
      final expiresAt = DateTime.fromMillisecondsSinceEpoch(exp.toInt() * 1000);
      return DateTime.now().isAfter(expiresAt.subtract(_refreshMargin));
    } catch (_) {
      return false;
    }
  }

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

/// Kết quả tắt 2FA trên server. Cả hai đều cho phép app xóa khóa cục bộ.
enum DisableOutcome {
  /// Server vừa tắt 2FA (200).
  disabled,

  /// Server vốn đã tắt (404): khóa trong app không còn tác dụng.
  alreadyOff,
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
            ) {
    // 401 (access token Java chỉ sống 5 phút) -> làm mới token rồi gửi lại ĐÚNG MỘT LẦN.
    // An toàn cho cả confirm: server kiểm tra token trước khi đếm số lần nhập sai.
    _dio.interceptors.add(
      InterceptorsWrapper(
        onError: (err, handler) async {
          final req = err.requestOptions;
          if (err.response?.statusCode == 401 && req.extra['authRetried'] != true) {
            // Request này gửi token CŨ nhưng máy đã có token mới hơn (lần làm mới trước đó đã lưu):
            // dùng luôn token mới, KHÔNG làm mới lần nữa. Server xoay vòng refresh token, làm mới
            // liên tiếp rất dễ bị từ chối.
            final sent = req.headers['Authorization'];
            final current = await _auth.accessToken();
            final String? fresh = (current != null &&
                    current.isNotEmpty &&
                    'Bearer $current' != sent)
                ? current
                : await _auth.refreshAccessToken();
            if (fresh != null && fresh.isNotEmpty) {
              req.extra['authRetried'] = true;
              req.headers['Authorization'] = 'Bearer $fresh';
              try {
                return handler.resolve(await _dio.fetch<dynamic>(req));
              } on DioException catch (e) {
                return handler.next(e);
              }
            }
          }
          handler.next(err);
        },
      ),
    );
  }

  final EnrollmentAuth _auth;
  final Dio _dio;

  static const String _pendingPath = '$kApiPrefix/admin/mobile/2fa/enrollment/pending';
  static const String _confirmPath = '$kApiPrefix/admin/mobile/2fa/enrollment/confirm';
  static const String _disablePath = '$kApiPrefix/admin/mobile/2fa/disable';

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

    // Token có thể vừa được làm mới ở bước pending: lấy lại token mới nhất cho bước confirm.
    await _confirm((await _auth.accessToken()) ?? token, code);

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

  // ---- Tắt 2FA ----

  /// Tắt 2FA trên server bằng mã TOTP hiện tại sinh từ [secret] đang lưu trong app.
  /// Ném [EnrollmentException] nếu thất bại (khi đó KHÔNG được xóa khóa cục bộ).
  /// Mỗi lần gọi = đúng MỘT request (không tự thử lại, vì server đếm từng lần sai).
  Future<DisableOutcome> disable({
    required String secret,
    required int periodSeconds,
    required int digits,
    required TotpAlgorithm algorithm,
  }) async {
    final token = await _auth.accessToken();
    if (token == null || token.isEmpty) {
      throw const EnrollmentException(_msgSession);
    }

    final String code;
    try {
      code = TotpGenerator.generate(
        secret,
        period: periodSeconds,
        digits: digits,
        algorithm: algorithm,
      );
    } on FormatException {
      throw const EnrollmentException('Invalid key format.');
    }

    try {
      await _dio.post<Map<String, dynamic>>(
        _disablePath,
        data: {'code': code},
        options: _bearer(token),
      );
      return DisableOutcome.disabled;
    } on DioException catch (e) {
      if (e.response?.statusCode == 404) return DisableOutcome.alreadyOff;
      throw _mapDisable(e);
    }
  }

  EnrollmentException _mapDisable(DioException e) {
    final status = e.response?.statusCode;
    if (status == null) return const EnrollmentException(_msgNetwork);
    if (status == 401) return const EnrollmentException(_msgSession);
    if (status == 422) {
      final data = e.response?.data;
      final code = (data is Map && data['data'] is Map)
          ? (data['data'] as Map)['code']
          : null;
      if (code == 'TOTP_REPLAYED') {
        return const EnrollmentException(
          'This code was just used. Wait for the next code and try again.',
        );
      }
      return const EnrollmentException(
        'Code rejected. Check that this device time is correct and try again.',
      );
    }
    if (status == 429) {
      final data = e.response?.data;
      final seconds = (data is Map && data['data'] is Map)
          ? (data['data'] as Map)['retryAfterSeconds']
          : null;
      final minutes = seconds is num ? (seconds / 60).ceil() : 5;
      return EnrollmentException(
        'Too many wrong attempts. Try again in $minutes minute(s).',
      );
    }
    if (status == 400) return const EnrollmentException('Invalid code.');
    return const EnrollmentException(_msgServer);
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