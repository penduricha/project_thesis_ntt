import 'package:flutter/material.dart';
import '../../../core/appearance/app_preferences.dart';
import '../../../core/localization/app_strings.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final strings = AppStrings.of(context);
    final preferences = AppPreferences.instance;
    final theme = Theme.of(context);
    const themeLabels = {ThemeMode.light: 'light', ThemeMode.dark: 'dark', ThemeMode.system: 'system'};
    const themeIcons = {ThemeMode.light: Icons.light_mode_outlined, ThemeMode.dark: Icons.dark_mode_outlined, ThemeMode.system: Icons.brightness_auto_outlined};
    return Scaffold(
      appBar: AppBar(title: Text(strings.text('settings'))),
      body: ListenableBuilder(
        listenable: preferences,
        builder: (context, _) => ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Card(
              elevation: 0,
              shape: RoundedRectangleBorder(side: BorderSide(color: theme.dividerColor), borderRadius: BorderRadius.circular(12)),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                _SectionHeader(title: strings.text('appearance'), description: strings.text('appearanceHint')),
                for (final mode in [ThemeMode.light, ThemeMode.dark, ThemeMode.system])
                  RadioListTile<ThemeMode>(
                    value: mode, groupValue: preferences.themeMode,
                    onChanged: (value) { if (value != null) preferences.setTheme(value); },
                    secondary: Icon(themeIcons[mode]), title: Text(strings.text(themeLabels[mode]!)),
                  ),
                Divider(height: 1, color: theme.dividerColor),
                _SectionHeader(title: strings.text('language'), description: strings.text('languageHint')),
                for (final locale in AppStrings.locales)
                  RadioListTile<Locale>(
                    value: locale, groupValue: preferences.locale,
                    onChanged: (value) { if (value != null) preferences.setLocale(value); },
                    title: Text(AppStrings.languageNames[locale.languageCode]!),
                    secondary: Text(locale.languageCode.toUpperCase(), style: theme.textTheme.labelLarge?.copyWith(color: theme.colorScheme.primary)),
                  ),
                const SizedBox(height: 8),
              ]),
            ),
          ],
        ),
      ),
    );
  }
}

class _SectionHeader extends StatelessWidget {
  const _SectionHeader({required this.title, required this.description});
  final String title;
  final String description;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(20, 20, 20, 8),
    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text(title, style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
      const SizedBox(height: 4),
      Text(description, style: Theme.of(context).textTheme.bodyMedium?.copyWith(color: Theme.of(context).colorScheme.onSurfaceVariant)),
    ]),
  );
}
