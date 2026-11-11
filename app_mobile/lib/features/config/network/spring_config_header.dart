import 'dart:convert';

import 'package:dio/dio.dart';

import 'network_configuration.dart';

/// Tạo các HTTP client (Dio) gọi RESTful API của Spring Boot.
/// Tương đương SpringConfigHeader.js ở Web (Axios -> Dio).
class SpringConfigHeader {
  final NetworkConfiguration _networkConfiguration = NetworkConfiguration();

  // Basic Auth (username/password). Chưa dùng .env nên đặt thẳng tại đây.
  // Để rỗng nếu server không yêu cầu Basic Auth.
  static const String _authUsername = '';
  static const String _authPassword = '';

  Dio _createClient({Map<String, String>? extraHeaders}) {
    return Dio(
      BaseOptions(
        baseUrl: _networkConfiguration.requestSpringBoot(),
        connectTimeout: NetworkConfiguration.connectTimeout,
        sendTimeout: NetworkConfiguration.sendTimeout,
        receiveTimeout: NetworkConfiguration.receiveTimeout,
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json',
          ...?extraHeaders,
        },
        // Spring trả JSON có ý nghĩa cho cả 4xx (401, 409...) nên không ném lỗi với 4xx,
        // để đọc được response.data. Chỉ lỗi 5xx và lỗi mạng mới ném DioException.
        validateStatus: (status) => status != null && status < 500,
      ),
    );
  }

  /// Không có header xác thực (đăng nhập, gọi API công khai).
  Dio getApiClientNoHeaders() => _createClient();

  /// Có username/password: Basic Auth gửi trong header Authorization.
  Dio getApiClientWithBasicAuth() {
    return _createClient(extraHeaders: {'Authorization': _basicAuthHeader()});
  }

  /// Có JWT access token: Authorization: Bearer <token>.
  /// withBasicAuth = true thì đẩy thêm Basic Auth vào header riêng X-Client-Auth
  /// (giống phần comment trong SpringConfigHeader.js, tránh đè Authorization).
  Dio getApiClientWithHeadersToken(String token, {bool withBasicAuth = false}) {
    return _createClient(
      extraHeaders: {
        'Authorization': 'Bearer $token',
        if (withBasicAuth) 'X-Client-Auth': _basicAuthHeader(),
      },
    );
  }

  String _basicAuthHeader() {
    final credentials = base64Encode(utf8.encode('$_authUsername:$_authPassword'));
    return 'Basic $credentials';
  }
}
