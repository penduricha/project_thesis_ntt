import 'dart:io' show Platform;
import 'package:flutter/foundation.dart' show kIsWeb;

class NetworkConfiguration {
  // Android emulator: 10.0.2.2 = localhost của máy tính host.
  // iOS simulator / desktop / web: localhost dùng bình thường.
  static String get _devHost =>
      (!kIsWeb && Platform.isAndroid) ? '10.0.2.2' : 'localhost';

  // --- Địa chỉ Spring Boot ---
  static const String springProtocol = 'http';
  static String get springHost => _devHost;
  static const int springPort = 8080;
  static const String apiPrefix = '/api/mobile';

  // --- Địa chỉ Node.js ---
  static const String nodeJsProtocol = 'http';
  static String get nodeJsHost => _devHost;
  static const int nodeJsPort = 3000;
  static const String nodeJsApiPrefix = '/api/mobile';

  // --- Thời gian chờ ---
  static const Duration connectTimeout = Duration(seconds: 10);
  static const Duration sendTimeout = Duration(seconds: 15);
  static const Duration receiveTimeout = Duration(seconds: 15);

  String requestSpringBoot() => '$springProtocol://$springHost:$springPort$apiPrefix';
  String requestNodeJS() => '$nodeJsProtocol://$nodeJsHost:$nodeJsPort$nodeJsApiPrefix';
}