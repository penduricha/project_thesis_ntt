import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:my_flutter_app/core/appearance/app_theme.dart';
import 'package:my_flutter_app/core/widgets/qbank_section_header.dart';

void main() {
  test('theme follows web light and dark surface and text tokens', () {
    final light = AppTheme.create(Brightness.light);
    final dark = AppTheme.create(Brightness.dark);
    expect(light.scaffoldBackgroundColor, const Color(0xfff8faff));
    expect(light.colorScheme.onSurface, const Color(0xff1b2a41));
    expect(light.colorScheme.primaryContainer, const Color(0xffe8f1fa));
    expect(dark.scaffoldBackgroundColor, const Color(0xff0b1220));
    expect(dark.colorScheme.surface, const Color(0xff131c2e));
    expect(dark.colorScheme.onSurfaceVariant, const Color(0xff9aabc4));
    expect(dark.colorScheme.primary, const Color(0xff6cb0ea));
  });

  testWidgets('shared section header exposes accessible actions', (tester) async {
    var menus = 0;
    var actions = 0;
    await tester.pumpWidget(MaterialApp(theme: AppTheme.create(Brightness.dark), home: Scaffold(
      body: QBankSectionHeader(title: 'Authenticator', onMenu: () => menus++,
          actionIcon: Icons.add, actionLabel: 'Add account', onAction: () => actions++),
    )));
    expect(find.text('QBank'), findsOneWidget);
    expect(find.text('Authenticator'), findsOneWidget);
    await tester.tap(find.byTooltip('Menu'));
    await tester.tap(find.byTooltip('Add account'));
    expect(menus, 1);
    expect(actions, 1);
  });
}
