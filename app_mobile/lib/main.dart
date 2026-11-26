import 'package:flutter/material.dart';
import 'package:flutter_dotenv/flutter_dotenv.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

import 'core/appearance/app_preferences.dart';
import 'core/appearance/app_theme.dart';
import 'core/localization/app_strings.dart';
import 'core/network/server_connection.dart';
import 'core/network/server_probe.dart';
import 'features/login/presentation/screens/splash_screen.dart';
import 'features/login/presentation/screens/login_screen.dart';
import 'features/main_shell/presentation/widgets/app_tab_bar.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await dotenv.load(fileName: '.env', isOptional: true);
  await AppPreferences.instance.load();
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) ServerProbe.check();
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) ServerProbe.check();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final preferences = AppPreferences.instance;
    return ListenableBuilder(
      listenable: preferences,
      builder: (context, _) => MaterialApp(
        debugShowCheckedModeBanner: false,
        title: 'QBank',
        theme: AppTheme.create(Brightness.light),
        darkTheme: AppTheme.create(Brightness.dark),
        themeMode: preferences.themeMode,
        locale: preferences.locale,
        supportedLocales: AppStrings.locales,
        localizationsDelegates: const [
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        builder: (context, child) => ValueListenableBuilder<bool>(
          valueListenable: ServerConnection.unavailable,
          builder: (context, unavailable, _) => Column(children: [
            if (unavailable) Material(
              color: Theme.of(context).colorScheme.errorContainer,
              child: SafeArea(bottom: false, child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                child: Row(children: [
                  Icon(Icons.wifi_off_rounded, size: 20, color: Theme.of(context).colorScheme.onErrorContainer),
                  const SizedBox(width: 10),
                  Expanded(child: Text(AppStrings.of(context).text('network'),
                    style: TextStyle(color: Theme.of(context).colorScheme.onErrorContainer),
                    semanticsLabel: AppStrings.of(context).text('network'))),
                  TextButton(
                    onPressed: () => ServerProbe.check(),
                    child: Text(AppStrings.of(context).text('retryConnection')),
                  ),
                ]),
              )),
            ),
            Expanded(child: child ?? const SizedBox.shrink()),
          ]),
        ),
        initialRoute: '/',
        routes: {
          '/': (_) => const SplashScreen(),
          '/login': (_) => const LoginScreen(),
          '/home': (_) => const AppTabBar(),
        },
      ),
    );
  }
}
