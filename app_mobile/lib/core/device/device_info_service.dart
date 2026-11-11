import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter/foundation.dart';

/// Lấy tên thiết bị và nền tảng để gửi kèm khi đăng nhập.
class DeviceInfoService {
  DeviceInfoService({DeviceInfoPlugin? plugin})
      : _plugin = plugin ?? DeviceInfoPlugin();

  static const String _unknownDeviceName = 'Unknown device';

  final DeviceInfoPlugin _plugin;

  /// Mặc định là ANDROID; chỉ trả IOS khi chạy trên iOS.
  String get platform =>
      defaultTargetPlatform == TargetPlatform.iOS ? 'IOS' : 'ANDROID';

  /// Ví dụ máy ảo Android: "Google sdk_gphone64_x86_64".
  /// Không đọc được thì trả về "Unknown device" (không làm hỏng việc đăng nhập).
  Future<String> getDeviceName() async {
    try {
      switch (defaultTargetPlatform) {
        case TargetPlatform.android:
          final info = await _plugin.androidInfo;
          return '${info.manufacturer} ${info.model}'.trim();
        case TargetPlatform.iOS:
          final info = await _plugin.iosInfo;
          return info.name;
        default:
          return _unknownDeviceName;
      }
    } catch (_) {
      return _unknownDeviceName;
    }
  }
}
