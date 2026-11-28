import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:my_flutter_app/features/authenticator/data/service/account_store.dart';
import 'package:my_flutter_app/features/authenticator/data/model/auth_account.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  Map<String, Object> account(String id) => {
    'id': 'admin_$id', 'issuer': 'Test', 'account': id,
    'secret': 'TESTSECRET', 'periodSeconds': 30, 'digits': 6, 'algorithm': 'sha1',
  };

  test('migration retains shared data for other administrators', () async {
    final legacy = jsonEncode([account('1'), account('2')]);
    FlutterSecureStorage.setMockInitialValues({'authenticator_accounts': legacy});
    final storage = const FlutterSecureStorage();
    final one = await AccountStore(adminId: '1', storage: storage).load();
    final two = await AccountStore(adminId: '2', storage: storage).load();
    expect(one.single.account, '1');
    expect(two.single.account, '2');
    expect(await storage.read(key: 'authenticator_accounts'), legacy);
  });

  test('clear does not re-import a migrated legacy key', () async {
    FlutterSecureStorage.setMockInitialValues({'authenticator_accounts': jsonEncode([account('1')])});
    final store = AccountStore(adminId: '1');
    expect(await store.load(), hasLength(1));
    await store.clear();
    expect(await store.load(), isEmpty);
  });

  test('corrupt legacy value is not mistaken for an empty key store', () async {
    FlutterSecureStorage.setMockInitialValues({'authenticator_accounts': '{broken'});
    final storage = const FlutterSecureStorage();
    await expectLater(AccountStore(adminId: '1', storage: storage).load(), throwsA(isA<FormatException>()));
    expect(await storage.read(key: 'authenticator_accounts'), '{broken');
    expect(await storage.read(key: 'authenticator_accounts_1'), isNull);
  });

  test('corrupt current store is not mistaken for an empty key store', () async {
    FlutterSecureStorage.setMockInitialValues({'authenticator_accounts_1': ''});
    await expectLater(AccountStore(adminId: '1').load(), throwsA(isA<FormatException>()));
  });

  test('staging is separate from active keys and never overwrites an uncertain attempt', () async {
    FlutterSecureStorage.setMockInitialValues({});
    final store = AccountStore(adminId: '1');
    const candidate = AuthAccount(id: 'admin_1', issuer: 'Test', account: '1', secret: 'SECRET', credentialId: 42);
    const other = AuthAccount(id: 'admin_1', issuer: 'Test', account: '1', secret: 'OTHER', credentialId: 42);
    await store.stage(42, candidate);
    await store.stage(42, candidate); // same attempt may be retried
    expect(await store.load(), isEmpty);
    expect((await store.loadPending()).single.account.secret, 'SECRET');
    await expectLater(store.stage(42, other), throwsA(isA<StateError>()));
    expect((await store.loadPending()).single.account.secret, 'SECRET');
    await store.save([candidate]);
    await store.clearPending(42);
    expect((await store.load()).single.credentialId, 42);
    expect(await store.loadPending(), isEmpty);
  });
}
