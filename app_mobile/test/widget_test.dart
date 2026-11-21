import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:my_flutter_app/features/authenticator/presentation/screens/totp_generator.dart';
import 'package:my_flutter_app/features/login/presentation/screens/login_screen.dart';

void main() {
  group('TotpGenerator (RFC 6238)', () {
    const sha1Secret = 'GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ';
    const sha256Secret =
        'GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZA';

    test('matches RFC 6238 SHA-1 test vectors', () {
      expect(
        TotpGenerator.generate(
          sha1Secret,
          digits: 8,
          algorithm: TotpAlgorithm.sha1,
          at: DateTime.fromMillisecondsSinceEpoch(59 * 1000, isUtc: true),
        ),
        '94287082',
      );
      expect(
        TotpGenerator.generate(
          sha1Secret,
          digits: 6,
          algorithm: TotpAlgorithm.sha1,
          at: DateTime.fromMillisecondsSinceEpoch(59 * 1000, isUtc: true),
        ),
        '287082',
      );
      expect(
        TotpGenerator.generate(
          sha1Secret,
          digits: 8,
          algorithm: TotpAlgorithm.sha1,
          at: DateTime.fromMillisecondsSinceEpoch(1234567890 * 1000, isUtc: true),
        ),
        '89005924',
      );
    });

    test('matches RFC 6238 SHA-256 test vectors', () {
      expect(
        TotpGenerator.generate(
          sha256Secret,
          digits: 8,
          algorithm: TotpAlgorithm.sha256,
          at: DateTime.fromMillisecondsSinceEpoch(59 * 1000, isUtc: true),
        ),
        '46119246',
      );
      expect(
        TotpGenerator.generate(
          sha256Secret,
          digits: 8,
          algorithm: TotpAlgorithm.sha256,
          at: DateTime.fromMillisecondsSinceEpoch(1234567890 * 1000, isUtc: true),
        ),
        '91819424',
      );
    });

    test('normalizes secret and rejects invalid Base32 characters', () {
      expect(TotpGenerator.normalizeSecret(' abcd-efgh= '), 'ABCDEFGH');
      expect(
        () => TotpGenerator.generate('INVALID*KEY!'),
        throwsFormatException,
      );
      expect(TotpAlgorithm.parse('HmacSHA256'), TotpAlgorithm.sha256);
    });
  });

  testWidgets('LoginScreen validates empty credentials on submit',
      (WidgetTester tester) async {
    await tester.pumpWidget(const MaterialApp(home: LoginScreen()));

    expect(find.text('Authenticator'), findsOneWidget);
    expect(find.text('Admin Id'), findsOneWidget);
    expect(find.text('Password'), findsOneWidget);
    expect(find.text('Login'), findsOneWidget);

    await tester.tap(find.text('Login'));
    await tester.pump();

    expect(find.text('Enter your admin Id'), findsOneWidget);
    expect(find.text('Enter your password'), findsOneWidget);
  });
}
