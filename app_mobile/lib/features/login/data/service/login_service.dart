import 'package:dio/dio.dart';
import '../../../../core/network/server_connection.dart';


import '../../../config/network/spring_config_header.dart';
import '../model/admin_account.dart';
import '../model/login_result.dart';
import '../../../../core/storage/auth_storage.dart';

/// Gọi API đăng nhập của Spring Boot và đổi phản hồi thành [LoginResult].
///
/// Client được cấu hình với validateStatus < 500 nên các mã 4xx (401, 409...)
/// không ném lỗi: ta đọc được JSON của server để phân loại 3 trường hợp.
class LoginService {
  LoginService({Dio? dio})
      : _dio = dio ?? SpringConfigHeader().getApiClientNoHeaders();

  static const String _loginPath = '/admin/auth/login';
  static const String _alreadyLoggedInErrorCode =
      'ACCOUNT_ALREADY_LOGGED_IN_ON_ANOTHER_DEVICE';
  static const String _defaultRejectedMessage =
      'Admin Id or password is incorrect.';

  final Dio _dio;

  Future<LoginResult> login(AdminLogin admin) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        _loginPath,
        data: admin.toJson(),
      );
      return _mapResponse(response, admin.adminId);
    } on DioException catch (error) {
      return LoginFailure(_describeNetworkError(error), isNetworkFailure: ServerConnection.isConnectivityFailure(error));
    }
  }

  LoginResult _mapResponse(
      Response<Map<String, dynamic>> response,
      int requestedAdminId,
      ) {
    final statusCode = response.statusCode;
    final body = response.data;
    if (body == null) {
      return const LoginFailure('Empty response from server.');
    }

    // Trường hợp 1: thành công.
    if (statusCode == 200 && body['success'] == true) {
      return _parseSuccess(body, requestedAdminId);
    }

    // Trường hợp 3: đã đăng nhập ở thiết bị khác.
    if (statusCode == 409 && body['errorCode'] == _alreadyLoggedInErrorCode) {
      return _parseBlockedByOtherDevice(body);
    }

    // Trường hợp 2a: mật khẩu đúng nhưng tài khoản đã bật 2FA.
    final totpResult = _mapTwoFaResult(body);
    if (totpResult != null) return totpResult;

    // Trường hợp 2: sai Admin Id / mật khẩu hoặc server từ chối.
    return LoginRejected(_readString(body['message']) ?? _defaultRejectedMessage);
  }

  /// data.code do server trả (cùng quy ước với Web): TOTP_REQUIRED | TOTP_INVALID | LOCKED.
  LoginResult? _mapTwoFaResult(Map<String, dynamic> body) {
    final data = body['data'];
    if (data is! Map<String, dynamic>) return null;
    switch (data['code']) {
      case 'TOTP_REQUIRED':
        return const LoginTotpRequired(
          reason: LoginTotpFailureReason.required,
          message: 'Two-factor authentication is enabled on this account. '
              'Enter the 6-digit code from your Authenticator app.',
        );
      case 'TOTP_INVALID':
        return const LoginTotpRequired(
          reason: LoginTotpFailureReason.invalid,
          message: 'The 6-digit code was incorrect or expired. '
              'Wait for the next code and try again.',
        );
      case 'LOCKED':
        final secs = data['retryAfterSeconds'];
        final retry = secs is num ? secs.toInt() : 0;
        return LoginTotpRequired(
          reason: LoginTotpFailureReason.locked,
          retryAfterSeconds: retry,
          message: retry > 0
              ? 'Too many attempts. Try again in $retry seconds.'
              : 'Too many attempts. Try again later.',
        );
    }
    return null;
  }

  LoginResult _parseSuccess(Map<String, dynamic> body, int requestedAdminId) {
    final data = body['data'];
    if (data is Map<String, dynamic>) {
      final accessToken = _readString(data['accessToken']);
      final refreshToken = _readString(data['refreshToken']);
      if (accessToken != null && refreshToken != null) {
        // Ưu tiên adminId server trả về; thiếu thì dùng adminId đã gửi lên.
        final adminIdFromServer = data['adminId'];
        final adminId =
        adminIdFromServer is num ? adminIdFromServer.toInt() : requestedAdminId;

        return LoginSuccess(
          AuthSession(
            adminId: adminId,
            accessToken: accessToken,
            refreshToken: refreshToken,
            adminName: _readString(data['adminName']),
          ),
        );
      }
    }
    return const LoginFailure('Invalid login response from server.');
  }

  LoginResult _parseBlockedByOtherDevice(Map<String, dynamic> body) {
    final data = body['data'];
    final info = data is Map<String, dynamic> ? data : const <String, dynamic>{};

    return LoginBlockedByOtherDevice(
      message: _readString(body['message']) ??
          'Account is already logged in on another device.',
      deviceName: _readString(info['deviceName']),
      platform: _readString(info['platform']),
      loggedInAt: DateTime.tryParse(_readString(info['loggedInAt']) ?? ''),
    );
  }

  String _describeNetworkError(DioException error) {
    return switch (error.type) {
      DioExceptionType.connectionTimeout ||
      DioExceptionType.sendTimeout ||
      DioExceptionType.receiveTimeout =>
      'The server is taking too long to respond.\nPlease try again.',
      DioExceptionType.connectionError =>
      'Cannot connect to the server.\nPlease check your network connection.',
      DioExceptionType.badResponse =>
      'Server error (${error.response?.statusCode}).\nPlease try again later.',
      _ => 'Something went wrong.\nPlease try again.',
    };
  }

  static String? _readString(Object? value) {
    return value is String && value.isNotEmpty ? value : null;
  }
}