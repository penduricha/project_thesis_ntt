import 'package:flutter/material.dart';

// Màn hình đăng nhập: import THẲNG file chứa class LoginScreen
// (không import features/login/login_export.dart nữa để tránh lấy nhầm class cũ).
import 'features/login/presentation/screens/login_screen.dart';
// Màn hình chờ: kiểm tra phiên đã lưu (auto login) rồi chuyển Home hoặc Login.

// Khung Tab (Authenticator | Chat) - chỉ dùng ở route '/home'.
import 'features/login/presentation/screens/splash_screen.dart';
import 'features/main_shell/presentation/widgets/app_tab_bar.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Login',
      // Mở app: kiểm tra auto login trước, rồi mới quyết định vào Home hay Login
      initialRoute: '/',
      routes: {
        '/': (_) => const SplashScreen(),
        // Chỉ có LoginScreen, KHÔNG có Tab
        '/login': (_) => const LoginScreen(),
        // Vào khi auto login hoặc đăng nhập thành công (xem LoginScreen)
        '/home': (_) => const AppTabBar(),
      },
    );
  }
}