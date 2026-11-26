import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../localization/app_strings.dart';

/// Appearance preferences have separate keys from the authentication session.
class AppPreferences extends ChangeNotifier {
  AppPreferences({FlutterSecureStorage? storage}) : _storage = storage ?? const FlutterSecureStorage();
  static final instance = AppPreferences();
  final FlutterSecureStorage _storage;
  ThemeMode themeMode = ThemeMode.system;
  Locale locale = const Locale('vi');
  Future<void> _pendingWrites = Future.value();

  Future<void> load() async {
    try {
      final theme = await _storage.read(key: 'ui_theme');
      final language = await _storage.read(key: 'ui_language');
      themeMode = ThemeMode.values.where((value) => value.name == theme).firstOrNull ?? ThemeMode.system;
      locale = AppStrings.locales.where((value) => value.languageCode == language).firstOrNull ?? const Locale('vi');
    } catch (_) {
      // Preferences remain usable for this session when platform storage is unavailable.
    }
    notifyListeners();
  }

  Future<void> _persist(String key, String value) {
    // Serialise writes so a rapid series of selections persists the last choice.
    _pendingWrites = _pendingWrites.then((_) async {
      try { await _storage.write(key: key, value: value); } catch (_) {}
    });
    return _pendingWrites;
  }

  Future<void> setTheme(ThemeMode value) async {
    themeMode = value;
    notifyListeners();
    await _persist('ui_theme', value.name);
  }

  Future<void> setLocale(Locale value) async {
    if (!AppStrings.locales.contains(value)) return;
    locale = value;
    notifyListeners();
    await _persist('ui_language', value.languageCode);
  }
}
