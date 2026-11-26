import 'dart:convert';

import 'package:dio/dio.dart';

import 'network_configuration.dart';
import '../../../core/network/server_connection.dart';

/// Tạo các HTTP client (Dio) gọi RESTful API của Spring Boot.
/// Mọi thông số (host, port, giao thức, timeout, Basic Auth) đọc từ .env
/// thông qua [NetworkConfiguration].
/// Tương đương SpringConfigHeader.js ở Web (Axios -> Dio).
class SpringConfigHeader {
  Dio _createClient({Map<String, String>? extraHeaders}) {
    final client = Dio(
      BaseOptions(
        baseUrl: NetworkConfiguration.requestSpringBoot(),
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
    client.interceptors.add(ServerConnection.interceptor());
    return client;
  }

  /// Không có header xác thực (đăng nhập, gọi API công khai).
  Dio getApiClientNoHeaders() => _createClient();

  /// Basic Auth (username/password lấy từ .env) gửi trong header Authorization.
  /// Nếu .env không khai báo username/password thì không gắn header.
  Dio getApiClientWithBasicAuth() {
    return _createClient(
      extraHeaders: {
        if (NetworkConfiguration.hasSpringBasicAuth)
          'Authorization': _basicAuthHeader(),
      },
    );
  }

  /// Có JWT access token: Authorization: Bearer <token>.
  /// withBasicAuth = true thì đẩy thêm Basic Auth vào header riêng X-Client-Auth
  /// (giống phần comment trong SpringConfigHeader.js, tránh đè Authorization).
  Dio getApiClientWithHeadersToken(String token, {bool withBasicAuth = false}) {
    return _createClient(
      extraHeaders: {
        'Authorization': 'Bearer $token',
        if (withBasicAuth && NetworkConfiguration.hasSpringBasicAuth)
          'X-Client-Auth': _basicAuthHeader(),
      },
    );
  }

  String _basicAuthHeader() {
    final credentials = base64Encode(
      utf8.encode(
        '${NetworkConfiguration.springAuthUsername}:'
            '${NetworkConfiguration.springAuthPassword}',
      ),
    );
    return 'Basic $credentials';
  }
}