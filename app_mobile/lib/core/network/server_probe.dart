import 'package:dio/dio.dart';
import '../../features/config/network/spring_config_header.dart';
import 'server_connection.dart';

/// A read-only request to Spring's public auto-login endpoint without credentials.
/// A 401 (or any HTTP response) proves the server was reachable; it does not
/// create a session, refresh a token, or touch authenticated business state.
class ServerProbe {
  ServerProbe._();
  static Future<void>? _pending;

  static Future<void> check({Dio? client}) =>
      _pending ??= _check(client).whenComplete(() => _pending = null);

  static Future<void> _check(Dio? injected) async {
    final client = injected ?? SpringConfigHeader().getApiClientNoHeaders();
    try {
      await client.get<dynamic>('/admin/auth/login/auto/access-token');
      // Custom test clients need not have the shared interceptor.
      if (injected != null) ServerConnection.unavailable.value = false;
    } on DioException catch (error) {
      // A 401, 404 or 5xx is still an HTTP response, not an offline signal.
      if (injected != null) {
        if (error.response != null) {
          ServerConnection.unavailable.value = false;
        } else if (ServerConnection.isConnectivityFailure(error)) {
          ServerConnection.unavailable.value = true;
        }
      }
    } finally {
      if (injected == null) client.close();
    }
  }
}
