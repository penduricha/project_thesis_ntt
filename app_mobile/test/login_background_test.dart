import 'package:flutter/material.dart';
import 'package:flutter_dotenv/flutter_dotenv.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:my_flutter_app/core/appearance/app_theme.dart';
import 'package:my_flutter_app/features/login/presentation/screens/login_screen.dart';

void main() {
  setUp(() => dotenv.testLoad(fileInput: ''));

  for (final brightness in [Brightness.light, Brightness.dark]) {
    testWidgets('login uses the web background and branded form in $brightness mode', (tester) async {
      final theme = AppTheme.create(brightness);
      await tester.pumpWidget(MaterialApp(theme: theme, home: const LoginScreen()));

      final scaffold = tester.widget<Scaffold>(find.byType(Scaffold));
      final appBar = tester.widget<AppBar>(find.byType(AppBar));
      expect(scaffold.backgroundColor, theme.scaffoldBackgroundColor);
      expect(appBar.backgroundColor, Colors.transparent);
      expect(appBar.systemOverlayStyle?.statusBarColor, Colors.transparent);
      expect(
        appBar.systemOverlayStyle?.statusBarIconBrightness,
        brightness == Brightness.light ? Brightness.dark : Brightness.light,
      );
      expect(find.byType(Card), findsNothing);
      expect(find.text('QBank'), findsOneWidget);
      expect(find.text('Authenticator'), findsOneWidget);
      expect(find.byType(TextFormField), findsNWidgets(2));
    });
  }
}

extension on DotEnv {
  void testLoad({required String fileInput}) {}
}
