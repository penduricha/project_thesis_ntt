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
    final scheme = ColorScheme.fromSeed(seedColor: primary, brightness: brightness).copyWith(
      primary: primary, surface: surface, onSurface: text,
      error: dark ? const Color(0xffff8b8b) : const Color(0xffa51818),
      outline: border,
    );
    final outline = OutlineInputBorder(borderRadius: BorderRadius.circular(8), borderSide: BorderSide(color: border));
    return ThemeData(
      useMaterial3: true, colorScheme: scheme, scaffoldBackgroundColor: background,
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
