import 'package:dio/dio.dart';

import '../../../config/network/spring_config_header.dart';
import '../model/logout_result.dart';

/// Gọi API đăng xuất của Spring Boot: POST /admin/mobile/auth/logout.
///
/// Server nhận cặp token trong body, hủy chúng ở Redis và xóa phiên đăng nhập mobile
/// để thiết bị khác có thể đăng nhập. Client được cấu hình validateStatus < 500 nên
/// mã 4xx không ném lỗi mà được đổi thành [LogoutRejected].
class LogoutService {
  LogoutService({Dio? dio})
      : _dio = dio ?? SpringConfigHeader().getApiClientNoHeaders();

  static const String _logoutPath = '/admin/auth/logout';

  final Dio _dio;

  Future<LogoutResult> logout({
    required String accessToken,
    required String refreshToken,
  }) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        _logoutPath,
        data: {
          'accessToken': accessToken,
          'refreshToken': refreshToken,
        },
      );
      return response.statusCode == 200
          ? const LogoutSuccess()
          : const LogoutRejected();
    } on DioException {
      return const LogoutFailure();
    }
  }
}
