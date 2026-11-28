import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../../../../core/storage/auth_storage.dart';
import '../../../../core/localization/mobile_server_messages.dart';
import '../../../../core/network/server_connection.dart';
import '../../../config/network/network_configuration.dart';
import '../../presentation/screens/totp_generator.dart';
import '../model/auth_account.dart';
import 'account_store.dart';

// ============================================================================
//  KÍCH HOẠT 2FA TỪ MOBILE  (giai đoạn 3 của luồng Spring Boot)
//
//  Web đã bấm "Kích hoạt" -> server ghi khóa (đã mã hóa AES-GCM) vào MariaDB với
//  confirmed = false. Mobile làm 3 việc, theo đúng thứ tự:
//
//    1. GET  /api/mobile/admin/2fa/enrollment/pending
//         -> { algorithm: "HmacSHA256", digits: 6, periodSeconds: 15|30|45|60, ... }
//         Server KHÔNG trả khóa. Khóa chỉ có do người dùng nhập trong popup.
//    2. Sinh mã từ (khóa người dùng nhập, thông số ở bước 1, giờ hiện tại).
//    3. POST /api/mobile/admin/2fa/enrollment/confirm   { "code": "123456" }
//         Server tự giải mã khóa của mình, tính mã, so khớp (lệch tối đa ±1 chu kỳ).
//         Đúng -> confirmed = true và totp_enabled = true: 2FA bật hoàn toàn.
//
//  Khóa nhập sai => mã sai => 422. Mỗi lần gọi confirm đều bị đếm; quá 5 lần
//  server xóa phần cài dở và trả 429, admin phải bấm "Kích hoạt" lại trên web.
//
//  TẮT 2FA (nút xóa TOTP trong app):
//    POST /api/mobile/admin/2fa/disable   { "code": "123456" }
//    Mã 6 số sinh từ khóa đang lưu trong app. Server đúng mới xóa credential + totp_enabled = false.
//    404 không xác định được đúng credential; phải giữ khóa và báo lỗi.
// ============================================================================

// Địa chỉ Spring Boot lấy từ file .env qua [NetworkConfiguration.requestSpringBoot()]
// (cùng nguồn với màn Login / Logout), nên chỉ cần đổi ở MỘT chỗ:
//   SPRING_HOST        để trống = Android emulator dùng 10.0.2.2, nền tảng khác dùng localhost;
//                      máy thật: IP LAN của máy chạy Spring (cùng Wi-Fi)
//   SPRING_PORT        8080
//   SPRING_API_PREFIX  /api/mobile  (= context-path /api của Spring + nhánh /mobile)
// Các path bên dưới chỉ là phần đuôi "/admin/...", khớp @RequestMapping("/mobile/admin") bên Java.
// Trước đây file này tự ghép --dart-define=API_BASE_URL + "/api/mobile/admin/...", lệch nguồn với
// phần còn lại của app: đổi SPRING_HOST trong .env cho máy thật thì 2FA vẫn gọi vào 10.0.2.2.

// ---------------------------------------------------------------------------
//  Thông tin đăng nhập hiện tại của app  (NỐI VÀO MÀN LOGIN CỦA BẠN)
// ---------------------------------------------------------------------------

class AdminProfile {
  const AdminProfile({required this.adminId, required this.name});

  final String adminId; // vd: "2500021775"
  final String name; // vd: "Từ Quang Nhật"
}

/// A temporary refresh failure must not be reported as an expired session.
class AuthenticationUnavailable implements Exception {
  const AuthenticationUnavailable({this.network = false});
  final bool network;
}

/// App cần 2 thứ từ phiên đăng nhập: access token và thông tin admin.
/// Access token phía Java sống 5 phút, nên nếu có cơ chế refresh thì làm trong
/// [accessToken]: luôn trả về token còn hạn (hoặc null nếu phải đăng nhập lại).
abstract class EnrollmentAuth {
  Future<String?> accessToken();
  Future<AdminProfile?> profile();

  /// Đổi refresh token lấy cặp token mới (lưu lại vào máy) rồi trả access token mới.
  /// null = phiên bị từ chối. Sự cố tạm thời ném AuthenticationUnavailable.
  Future<String?> refreshAccessToken() async => null;
}

/// Bản mặc định: đọc từ flutter_secure_storage.
/// CHỈ CẦN ĐỔI 3 TÊN KEY cho khớp với key màn Login của bạn đang ghi.
class SecureStorageEnrollmentAuth implements EnrollmentAuth {
  const SecureStorageEnrollmentAuth({
    this.tokenKey = AuthStorage.accessTokenKey,
    this.adminIdKey = AuthStorage.adminIdKey,
    this.adminNameKey = AuthStorage.adminNameKey,
    FlutterSecureStorage storage = const FlutterSecureStorage(),
  }) : _storage = storage;

  final String tokenKey;
  final String adminIdKey;
  final String adminNameKey;
  final FlutterSecureStorage _storage;

  /// Đường dẫn làm mới token của Spring (đầy đủ: /api/mobile/admin/auth/login/auto/refresh-token).
  static const String _refreshPath = '/admin/auth/login/auto/refresh-token';

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
          baseUrl: NetworkConfiguration.requestSpringBoot(),
          connectTimeout: const Duration(seconds: 10),
          receiveTimeout: const Duration(seconds: 10),
          headers: {'Accept': 'application/json'},
          validateStatus: (s) => s != null && s < 500,
        ),
      );
      dio.interceptors.add(ServerConnection.interceptor());
      final res = await dio.get<Map<String, dynamic>>(
        _refreshPath,
        options: Options(headers: {'Authorization': 'Bearer ${session.refreshToken}'}),
      );
      final data = res.data?['data'];
      final access = data is Map ? data['accessToken'] : null;
      final refresh = data is Map ? data['refreshToken'] : null;
      if (res.statusCode == 401) return null;
      if (res.statusCode != 200 || access is! String || refresh is! String || access.isEmpty || refresh.isEmpty) {
        throw const AuthenticationUnavailable();
      }
      // Giữ nguyên adminId và tên hiển thị đã lưu, chỉ thay cặp token.
      final saved = await authStorage.saveSessionIfCurrent(
        session.refreshToken, session.withTokens(accessToken: access, refreshToken: refresh),
      );
      return saved ? access : null;
    } on AuthenticationUnavailable {
      rethrow;
    } on DioException catch (error) {
      throw AuthenticationUnavailable(network: error.response == null && ServerConnection.isConnectivityFailure(error));
    } catch (_) {
      throw const AuthenticationUnavailable();
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
    required this.credentialId,
    required this.periodSeconds,
    required this.digits,
    required this.algorithm,
  });

  final String adminId;
  final String adminName;
  final int credentialId;
  final int periodSeconds;
  final int digits;
  final TotpAlgorithm algorithm;
}

/// Lỗi đã được dịch sang câu ngắn gọn để hiện dưới ô nhập khóa.
class EnrollmentException implements Exception {
  const EnrollmentException(this.message, {this.messageKey, this.params = const {}});

  /// Original diagnostic text remains available to callers.
  final String message;
  final String? messageKey;
  final Map<String, Object> params;
  String get userMessage => messageKey == null
      ? message
      : MobileServerMessages.lookup(messageKey!, params: params) ?? message;

  @override
  String toString() => message;
}

/// Kết quả tắt 2FA được server xác nhận rõ ràng.
enum DisableOutcome {
  disabled,
}

class _PendingParams {
  const _PendingParams({
    required this.credentialId,
    required this.algorithm,
    required this.digits,
    required this.periodSeconds,
  });

  final int credentialId;
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
                baseUrl: NetworkConfiguration.requestSpringBoot(),
                connectTimeout: const Duration(seconds: 10),
                receiveTimeout: const Duration(seconds: 10),
                headers: {'Accept': 'application/json'},
              ),
            ) {
    // Keep the existing 401 retry and validateStatus semantics unchanged.
    if (dio == null) _dio.interceptors.add(ServerConnection.interceptor());
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
            try {
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
            } on AuthenticationUnavailable catch (failure) {
              return handler.next(DioException(
                requestOptions: req,
                type: failure.network ? DioExceptionType.connectionError : DioExceptionType.badResponse,
                response: failure.network ? null : Response<dynamic>(requestOptions: req, statusCode: 503),
              ));
            }
          }
          handler.next(err);
        },
      ),
    );
  }

  final EnrollmentAuth _auth;
  final Dio _dio;

  // Đầy đủ: /api/mobile/admin/2fa/... (phần /api/mobile nằm trong baseUrl).
  static const String _pendingPath = '/admin/2fa/enrollment/pending';
  static const String _confirmPath = '/admin/2fa/enrollment/confirm';
  static const String _disablePath = '/admin/2fa/disable';

  static const String _msgSession = 'Session expired. Please sign in again.';
  static const String _msgNetwork = 'Cannot reach the server. Check your connection.';
  static const String _msgServer = 'Server error. Please try again.';

  Future<String?> _accessToken() async {
    try {
      return await _auth.accessToken();
    } on AuthenticationUnavailable catch (failure) {
      throw EnrollmentException(failure.network ? _msgNetwork : _msgServer,
          messageKey: failure.network ? 'network' : 'server');
    }
  }

  /// Gửi khóa người dùng nhập lên để kích hoạt. Ném [EnrollmentException] nếu thất bại.
  /// Mỗi lần gọi = đúng MỘT request confirm (không tự thử lại, vì server đếm từng lần sai).
  Future<EnrolledKey> activate(String secret) async {
    final token = await _accessToken();
    if (token == null || token.isEmpty) {
      throw const EnrollmentException(_msgSession, messageKey: 'session');
    }

    // Kiểm tra TRƯỚC khi gọi confirm: không để server bật 2FA mà app thiếu thông tin tạo tài khoản.
    final profile = await _auth.profile();
    if (profile == null) {
      throw const EnrollmentException('Admin info not found. Please sign in again.', messageKey: 'adminMissing');
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
      throw const EnrollmentException('Invalid key format.', messageKey: 'invalidKey');
    }

    final candidate = AuthAccount(
      id: 'admin_${profile.adminId}', issuer: profile.name, account: profile.adminId,
      secret: secret, periodSeconds: pending.periodSeconds, digits: pending.digits,
      algorithm: pending.algorithm, enrolled: true, credentialId: pending.credentialId,
    );
    try {
      await AccountStore(adminId: profile.adminId).stage(pending.credentialId, candidate);
    } catch (_) {
      // Never confirm if the key could not be securely saved and read back.
      throw const EnrollmentException('Could not safely save the pending key on this device.');
    }
    final currentProfile = await _auth.profile();
    if (currentProfile?.adminId != profile.adminId) {
      throw const EnrollmentException(_msgSession, messageKey: 'session');
    }
    // Confirm is tied to the saved generation. An ambiguous result keeps the staged key.
    await _confirm((await _accessToken()) ?? token, code, pending.credentialId);

    return EnrolledKey(
      adminId: profile.adminId,
      adminName: profile.name,
      credentialId: pending.credentialId,
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
      if (data == null) throw const EnrollmentException(_msgServer, messageKey: 'server');

      // Ba tham số này quyết định mã đúng/sai, nên phải kiểm trước khi dùng thay vì tin server:
      //  - periodSeconds = 0  -> AuthenticatorScreen tính `ms % (period * 1000)` = chia cho 0
      //    => crash ngay khi mở tab Xác thực (Dart ném IntegerDivisionByZeroException/UnsupportedError).
      //  - digits = 0         -> mã là chuỗi rỗng, người dùng không nhập được gì.
      //  - digits > 9         -> `math.pow(10, digits)` vượt độ chính xác số nguyên của Dart.
      // Assert trong TotpGenerator.generate() KHÔNG cứu được: assert bị bỏ ở bản release.
      final credentialId = data['credentialId'];
      if (credentialId is! int || credentialId <= 0) {
        throw const EnrollmentException('Server does not support safe enrollment.', messageKey: 'server');
      }
      final digits = (data['digits'] as num).toInt();
      final periodSeconds = (data['periodSeconds'] as num).toInt();
      if (periodSeconds <= 0) {
        throw const EnrollmentException('Server trả chu kỳ TOTP không hợp lệ.', messageKey: 'invalidPeriod');
      }
      if (digits < 6 || digits > 9) {
        throw const EnrollmentException('Server trả số chữ số TOTP không hợp lệ.', messageKey: 'invalidDigits');
      }

      return _PendingParams(
        credentialId: credentialId,
        algorithm: TotpAlgorithm.parse(data['algorithm'] as String?),
        digits: digits,
        periodSeconds: periodSeconds,
      );
    } on DioException catch (e) {
      throw _map(e);
    } on FormatException {
      throw const EnrollmentException('Unsupported algorithm from server.', messageKey: 'invalidAlgorithm');
    }
  }

  // ---- Bước 3 ----
  Future<void> _confirm(String token, String code, int credentialId) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        _confirmPath,
        data: {'code': code, 'credentialId': credentialId},
        options: _bearer(token),
      );
      final data = response.data?['data'];
      if (response.statusCode != 200 || response.data?['success'] != true ||
          data is! Map || data['totpEnabled'] != true || data['credentialId'] != credentialId) {
        throw const EnrollmentException(_msgServer, messageKey: 'server');
      }
    } on DioException catch (e) {
      throw _map(e);
    }
  }

  /// An authenticated receipt only identifies the currently confirmed credential.
  /// A 404/temporary failure never authorizes deleting a staged secret.
  Future<bool> isConfirmedCredential(int credentialId, AuthAccount account) async {
    try {
      final token = await _accessToken();
      if (token == null || token.isEmpty) return false;
      final code = TotpGenerator.generate(account.secret, period: account.periodSeconds,
          digits: account.digits, algorithm: account.algorithm);
      final response = await _dio.post<Map<String, dynamic>>(
        '/admin/2fa/enrollment/receipt/$credentialId', data: {'code': code}, options: _bearer(token));
      final data = response.data?['data'];
      return response.statusCode == 200 && response.data?['success'] == true &&
          data is Map && data['totpEnabled'] == true && data['credentialId'] == credentialId;
    } on DioException {
      return false;
    } on EnrollmentException {
      return false;
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
    int? credentialId,
  }) async {
    final token = await _accessToken();
    if (token == null || token.isEmpty) {
      throw const EnrollmentException(_msgSession, messageKey: 'session');
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
      throw const EnrollmentException('Invalid key format.', messageKey: 'invalidKey');
    }

    try {
      final response = await _dio.post<Map<String, dynamic>>(
        _disablePath,
        data: {'code': code, if (credentialId != null) 'credentialId': credentialId},
        options: _bearer(token),
      );
      final body = response.data;
      final data = body?['data'];
      if (response.statusCode != 200 || body?['success'] != true ||
          data is! Map || data['totpEnabled'] != false ||
          (credentialId != null && data['credentialId'] != credentialId)) {
        // An unrelated or malformed success must not authorize local key deletion.
        throw const EnrollmentException(_msgServer, messageKey: 'server');
      }
      return DisableOutcome.disabled;
    } on DioException catch (e) {
      // A generic 404 may be a missing route, account or an older server.
      // Without a credential generation it cannot prove this local key is safe to delete.
      throw _mapDisable(e);
    }
  }

  EnrollmentException _mapDisable(DioException e) {
    final status = e.response?.statusCode;
    if (status == null) return const EnrollmentException(_msgNetwork, messageKey: 'network');
    if (status == 401) return const EnrollmentException(_msgSession, messageKey: 'session');
    if (status == 422) {
      final data = e.response?.data;
      final code = (data is Map && data['data'] is Map)
          ? (data['data'] as Map)['code']
          : null;
      if (code == 'TOTP_REPLAYED') {
        return const EnrollmentException(
          'This code was just used. Wait for the next code and try again.',
          messageKey: 'replayed',
        );
      }
      return const EnrollmentException(
        'Code rejected. Check that this device time is correct and try again.',
        messageKey: 'disableInvalid',
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
        messageKey: 'disableLocked', params: {'minutes': minutes},
      );
    }
    if (status == 400) return const EnrollmentException('Invalid code.', messageKey: 'invalidCode');
    return const EnrollmentException(_msgServer, messageKey: 'server');
  }

  Options _bearer(String token) =>
      Options(headers: {'Authorization': 'Bearer $token'});

  EnrollmentException _map(DioException e) {
    final status = e.response?.statusCode;
    if (status == null) return const EnrollmentException(_msgNetwork, messageKey: 'network');
    if (status == 401) return const EnrollmentException(_msgSession, messageKey: 'session');
    if (status == 404) {
      return const EnrollmentException(
        'Nothing to activate. Tap "Activate 2FA" on the web first.',
        messageKey: 'noPending',
      );
    }
    if (status == 410) {
      return const EnrollmentException(
        'Activation expired (10 minutes). Tap "Activate 2FA" on the web again.',
        messageKey: 'expired',
      );
    }
    if (status == 422) {
      return const EnrollmentException('Incorrect key. Check it and try again.', messageKey: 'wrongKey');
    }
    if (status == 429) {
      // Server đã xóa phần cài dở sau khi nhập sai quá số lần.
      return const EnrollmentException(
        'Too many wrong attempts. Tap "Activate 2FA" on the web to start again.',
        messageKey: 'confirmLocked',
      );
    }
    if (status == 400) return const EnrollmentException('Invalid code.', messageKey: 'invalidCode');
    return const EnrollmentException(_msgServer, messageKey: 'server');
  }
}