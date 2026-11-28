import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:my_flutter_app/core/appearance/app_theme.dart';
import 'package:my_flutter_app/features/chat/presentation/widgets/custom/chat_composer.dart';
import 'package:my_flutter_app/features/chat/presentation/widgets/custom/chat_list_item.dart';

void main() {
  for (final brightness in [Brightness.light, Brightness.dark]) {
    testWidgets('composer is usable and has a single border in $brightness', (tester) async {
      tester.view.physicalSize = const Size(320, 640);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final controller = TextEditingController();
      addTearDown(controller.dispose);
      var sent = 0;
      final theme = AppTheme.create(brightness);
      await tester.pumpWidget(MaterialApp(
        theme: theme,
        home: Scaffold(body: Align(
          alignment: Alignment.bottomCenter,
          child: ChatComposer(controller: controller, enabled: true, onSend: () => sent++, maxLength: 2000),
        )),
      ));

      final field = tester.widget<TextField>(find.byType(TextField));
      expect(field.minLines, 1);
      expect(field.maxLines, 5);
      expect(field.decoration!.filled, isFalse);
      expect(field.decoration!.enabledBorder, InputBorder.none);
      expect(field.decoration!.focusedBorder, InputBorder.none);
      final send = find.byType(IconButton);
      expect(tester.getSize(send), const Size(48, 48));
      expect(tester.widget<IconButton>(send).onPressed, isNull);

      final singleLineHeight = tester.getSize(find.byType(TextField)).height;
      await tester.enterText(find.byType(TextField), '  ');
      await tester.pump();
      expect(tester.widget<IconButton>(send).onPressed, isNull);
      await tester.enterText(find.byType(TextField), 'Hello\nWorld');
      await tester.pump();
      expect(tester.widget<IconButton>(send).onPressed, isNotNull);
      expect(tester.getSize(find.byType(TextField)).height, greaterThan(singleLineHeight));
      await tester.tap(send);
      expect(sent, 1);
      expect(controller.text, 'Hello\nWorld'); // The screen owns draft clearing.
      expect(tester.takeException(), isNull);
      await tester.enterText(find.byType(TextField), 'a' * 2100);
      expect(controller.text.length, 2000);

      await tester.pumpWidget(MaterialApp(
        theme: theme,
        home: Scaffold(body: ChatComposer(controller: controller, enabled: false, onSend: () => sent++, maxLength: 2000)),
      ));
      expect(tester.widget<TextField>(find.byType(TextField)).enabled, isFalse);
      expect(tester.widget<IconButton>(find.byType(IconButton)).onPressed, isNull);
    });

    testWidgets('each conversation including the last has an inset bottom divider in $brightness', (tester) async {
      final theme = AppTheme.create(brightness);
      var tapped = 0;
      await tester.pumpWidget(MaterialApp(
        theme: theme,
        home: Scaffold(body: ListView(children: [
          ChatListItem(onTap: () => tapped++, child: const Text('First')),
          ChatListItem(onTap: () => tapped++, child: const Text('Last')),
        ])),
      ));
      final items = find.byType(ChatListItem);
      expect(items, findsNWidgets(2));
      for (var i = 0; i < 2; i++) {
        final divider = tester.widget<Divider>(find.descendant(of: items.at(i), matching: find.byType(Divider)).first);
        expect(divider.color, theme.dividerColor);
        expect(divider.indent, 76);
        expect(divider.height, 1);
      }
      await tester.tap(find.text('Last'));
      expect(tapped, 1);
    });
  }
}
