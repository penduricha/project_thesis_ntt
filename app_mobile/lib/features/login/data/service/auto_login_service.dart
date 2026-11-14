import 'package:dio/dio.dart';

import '../../../../core/storage/auth_storage.dart';
import '../../../config/network/spring_config_header.dart';
import '../model/auto_login_result.dart';

/// Gọi 2 API auto login của Spring Boot (token gửi trong header Bearer):
///  - GET /admin/auth/login/auto/access-token
///  - GET /admin/auth/login/auto/refresh-token
///
/// adminId không cần gửi: server lấy từ chính token. Client có validateStatus < 500
/// nên 401 được đọc như phản hồi bình thường thay vì ném lỗi.
class AutoLoginService {
  AutoLoginService({Dio? dio})
      : _dio = dio ?? SpringConfigHeader().getApiClientNoHeaders();

  static const String _accessTokenPath = '/admin/auth/login/auto/access-token';
  static const String _refreshTokenPath = '/admin/auth/login/auto/refresh-token';

  final Dio _dio;

  Future<AutoLoginResult> loginByAccessToken(String accessToken) async {
    final response = await _get(_accessTokenPath, accessToken);
    if (response == null) return const AutoLoginFailure();
    return _map(response, onSuccess: (_) => const AutoLoginSuccess());
  }

  /// [adminId] chỉ dùng để ghép lại phiên mới (phản hồi refresh chỉ chứa token).
  Future<AutoLoginResult> loginByRefreshToken(
    String refreshToken, {
    required int adminId,
  }) async {
    final response = await _get(_refreshTokenPath, refreshToken);
    if (response == null) return const AutoLoginFailure();
    return _map(
      response,
      onSuccess: (body) {
        final data = body['data'];
        final access = data is Map ? data['accessToken'] : null;
        final refresh = data is Map ? data['refreshToken'] : null;
        if (access is! String || refresh is! String) return const AutoLoginFailure();
        return AutoLoginSuccess(
          newSession: AuthSession(
            adminId: adminId,
            accessToken: access,
            refreshToken: refresh,
          ),
        );
      },
    );
  }

  Future<Response<Map<String, dynamic>>?> _get(String path, String token) async {
    try {
      return await _dio.get<Map<String, dynamic>>(
        path,
        options: Options(headers: {'Authorization': 'Bearer $token'}),
      );
    } on DioException {
      return null;
    }
  }

  AutoLoginResult _map(
    Response<Map<String, dynamic>> response, {
    required AutoLoginResult Function(Map<String, dynamic> body) onSuccess,
  }) {
    final body = response.data;
    return switch (response.statusCode) {
      200 when body != null && body['success'] == true => onSuccess(body),
      401 => const AutoLoginUnauthorized(),
      _ => const AutoLoginFailure(),
    };
  }
}
