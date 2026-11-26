import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

/// Observes actual Spring requests without changing responses, retries or authentication.
/// An HTTP response (including 4xx/5xx) means the server was reachable.
class ServerConnection {
  ServerConnection._();
  static final unavailable = ValueNotifier<bool>(false);

  static bool isConnectivityFailure(DioException error) => const {
    DioExceptionType.connectionError,
    DioExceptionType.connectionTimeout,
    DioExceptionType.sendTimeout,
    DioExceptionType.receiveTimeout,
  }.contains(error.type);

  static Interceptor interceptor() => InterceptorsWrapper(
    onResponse: (response, handler) {
      unavailable.value = false;
      handler.next(response);
    },
    onError: (error, handler) {
      if (error.response != null) {
        unavailable.value = false;
      } else if (isConnectivityFailure(error)) {
        unavailable.value = true;
      }
      handler.next(error);
    },
  );
}
