import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';

class AppTheme {
  static ThemeData create(Brightness brightness) {
    final dark = brightness == Brightness.dark;
    final primary = dark ? const Color(0xff6cb0ea) : const Color(0xff004b87);
    final surface = dark ? const Color(0xff131c2e) : Colors.white;
    final background = dark ? const Color(0xff0b1220) : const Color(0xfff8faff);
    final text = dark ? const Color(0xffe8eef7) : const Color(0xff1b2a41);
    final border = dark ? const Color(0xff243349) : const Color(0xffe3ebf5);
    // Mirror the web's semantic tokens; primary ink and filled brand surfaces
    // are distinct in dark mode (see QBankSectionHeader and filled buttons).
    final scheme = ColorScheme.fromSeed(seedColor: primary, brightness: brightness).copyWith(
      primary: primary,
      onPrimary: dark ? const Color(0xff0b1220) : Colors.white,
      primaryContainer: dark ? const Color(0xff16273d) : const Color(0xffe8f1fa),
      onPrimaryContainer: primary,
      surface: surface,
      surfaceContainerLow: dark ? const Color(0xff131c2e) : const Color(0xfff8faff),
      surfaceContainerHighest: dark ? const Color(0xff1b2740) : const Color(0xfff1f5fa),
      onSurface: text,
      onSurfaceVariant: dark ? const Color(0xff9aabc4) : const Color(0xff5b6b82),
      error: dark ? const Color(0xffff8b8b) : const Color(0xffa51818),
      errorContainer: dark ? const Color(0xff2b1418) : const Color(0xfffdeeee),
      onErrorContainer: dark ? const Color(0xffff8b8b) : const Color(0xffa51818),
      outline: border, outlineVariant: border,
    );
    final outline = OutlineInputBorder(borderRadius: BorderRadius.circular(8), borderSide: BorderSide(color: border));
    return ThemeData(
      useMaterial3: true, fontFamily: 'BeVietnamPro', colorScheme: scheme, scaffoldBackgroundColor: background,
      dividerColor: border,
      appBarTheme: AppBarTheme(backgroundColor: surface, foregroundColor: text, surfaceTintColor: Colors.transparent, elevation: 0),
      inputDecorationTheme: InputDecorationTheme(filled: true, fillColor: surface, border: outline, enabledBorder: outline,
        focusedBorder: outline.copyWith(borderSide: BorderSide(color: primary, width: 1.5)),
        errorBorder: outline.copyWith(borderSide: BorderSide(color: scheme.error)),
        focusedErrorBorder: outline.copyWith(borderSide: BorderSide(color: scheme.error, width: 1.5))),
      pageTransitionsTheme: const PageTransitionsTheme(builders: {
        TargetPlatform.android: CupertinoPageTransitionsBuilder(), TargetPlatform.iOS: CupertinoPageTransitionsBuilder(),
        TargetPlatform.macOS: CupertinoPageTransitionsBuilder(), TargetPlatform.windows: CupertinoPageTransitionsBuilder(),
        TargetPlatform.linux: CupertinoPageTransitionsBuilder(),
      }),
    );
  }
}
