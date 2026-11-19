import 'dart:math';

final Random _secureRandom = Random.secure();

/// Sinh UUID phiên bản 4 (ngẫu nhiên), dạng chữ thường 36 ký tự, khớp CHAR(36) của server.
/// Tự viết thay vì thêm gói `uuid` để không phải đổi pubspec.lock.
String generateUuidV4() {
  final bytes = List<int>.generate(16, (_) => _secureRandom.nextInt(256));
  bytes[6] = (bytes[6] & 0x0F) | 0x40; // phiên bản 4
  bytes[8] = (bytes[8] & 0x3F) | 0x80; // biến thể RFC 4122
  final hex = bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
  return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-'
      '${hex.substring(16, 20)}-${hex.substring(20)}';
}
