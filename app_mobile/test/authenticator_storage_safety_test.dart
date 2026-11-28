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

  test('foreign account in a per-admin store fails closed without overwriting', () async {
    final raw = jsonEncode([account('2')]);
    FlutterSecureStorage.setMockInitialValues({'authenticator_accounts_1': raw});
    final storage = const FlutterSecureStorage();
    final store = AccountStore(adminId: '1', storage: storage);
    const candidate = AuthAccount(id: 'admin_1', issuer: 'Test', account: '1', secret: 'SECRET', credentialId: 42);
    await expectLater(store.stage(42, candidate), throwsA(isA<StateError>()));
    expect(await storage.read(key: 'authenticator_accounts_1'), raw);
    expect(await storage.read(key: 'authenticator_pending_1_42'), isNull);
  });

  test('staging refuses an existing different or legacy authenticator key', () async {
    FlutterSecureStorage.setMockInitialValues({});
    final store = AccountStore(adminId: '1');
    const old = AuthAccount(id: 'admin_1', issuer: 'Test', account: '1', secret: 'OLD');
    const replacement = AuthAccount(id: 'admin_1', issuer: 'Test', account: '1', secret: 'NEW', credentialId: 42);
    await store.save([old]);
    await expectLater(store.stage(42, replacement), throwsA(isA<StateError>()));
    expect((await store.load()).single.secret, 'OLD');
    expect(await store.loadPending(), isEmpty);
  });

  test('promotion cannot overwrite a key saved after staging', () async {
    FlutterSecureStorage.setMockInitialValues({});
    final store = AccountStore(adminId: '1');
    const pending = AuthAccount(id: 'admin_1', issuer: 'Test', account: '1', secret: 'PENDING', credentialId: 42);
    const newer = AuthAccount(id: 'admin_1', issuer: 'Test', account: '1', secret: 'NEWER', credentialId: 43);
    await store.stage(42, pending);
    await store.save([newer]);
    await expectLater(store.promotePending(42, pending), throwsA(isA<StateError>()));
    expect((await store.load()).single.secret, 'NEWER');
    expect((await store.loadPending()).single.account.secret, 'PENDING');
  });

  test('concurrent old disable and new promotion keep the new key', () async {
    FlutterSecureStorage.setMockInitialValues({});
    final store = AccountStore(adminId: '1');
    const old = AuthAccount(id: 'admin_1', issuer: 'Test', account: '1', secret: 'OLD', credentialId: 41);
    const newer = AuthAccount(id: 'admin_1', issuer: 'Test', account: '1', secret: 'NEW', credentialId: 42);
    await store.stage(42, newer);
    await Future.wait([store.promotePending(42, newer), store.removeConfirmed(old)]);
    expect((await store.load()).single.secret, 'NEW');
  });

  test('old disable cannot delete a newer key; promotion only clears after save', () async {
    FlutterSecureStorage.setMockInitialValues({});
    final store = AccountStore(adminId: '1');
    const old = AuthAccount(id: 'admin_1', issuer: 'Test', account: '1', secret: 'OLD', credentialId: 41);
    const newer = AuthAccount(id: 'admin_1', issuer: 'Test', account: '1', secret: 'NEW', credentialId: 42);
    await store.stage(42, newer);
    expect((await store.promotePending(42, newer)).single.secret, 'NEW');
    expect(await store.loadPending(), isEmpty);
    expect((await store.removeConfirmed(old)).single.secret, 'NEW');
    expect((await store.removeConfirmed(newer)), isEmpty);
    expect(await store.load(), isEmpty);
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
