import 'package:flutter_test/flutter_test.dart';
import 'package:my_flutter_app/features/config/network/network_configuration.dart';

void main() {
  test('release Spring URL requires HTTPS and rejects local emulator defaults', () {
    expect(NetworkConfiguration.isSafeReleaseSpringUrl('https://api.example.org:443/api/mobile'), isTrue);
    for (final url in [
      'http://api.example.org:8080/api/mobile',
      'https://localhost:8080/api/mobile',
      'https://10.0.2.2:8080/api/mobile',
      'https://127.0.0.1:8080/api/mobile',
      'https://name:password@api.example.org/api/mobile',
      'https://api.example.org/api/mobile?token=secret',
    ]) {
      expect(NetworkConfiguration.isSafeReleaseSpringUrl(url), isFalse, reason: url);
    }
  });
}
