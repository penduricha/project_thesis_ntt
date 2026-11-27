import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:my_flutter_app/core/storage/auth_storage.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() => FlutterSecureStorage.setMockInitialValues({}));

  test('late refresh cannot restore a cleared session', () async {
    final storage = AuthStorage();
    final original = AuthSession(adminId: 1, accessToken: 'a', refreshToken: 'r');
    final rotated = original.withTokens(accessToken: 'new-a', refreshToken: 'new-r');
    await storage.saveSession(original);
    await storage.clearSession();
    expect(await storage.saveSessionIfCurrent('r', rotated), isFalse);
    expect(await storage.readSession(), isNull);
  });

  test('stale refresh rejection cannot clear a newer session', () async {
    final storage = AuthStorage();
    await storage.saveSession(AuthSession(adminId: 1, accessToken: 'a', refreshToken: 'new-r'));
    expect(await storage.clearSessionIfCurrent('old-r'), isFalse);
    expect((await storage.readSession())?.refreshToken, 'new-r');
  });
}
