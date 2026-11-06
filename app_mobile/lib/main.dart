import 'package:flutter/material.dart';

// Màn hình đăng nhập: import THẲNG file chứa class LoginScreen
// (không import features/login/login.dart nữa để tránh lấy nhầm class cũ).
import 'features/login/presentation/screens/LoginScreen.dart';
// Khung Tab (Authenticator | Chat) - chỉ dùng ở route '/home'.
import 'features/menu/TabBarApp.dart';

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
      // Mở app: vào màn hình đăng nhập trước
      initialRoute: '/login',
      routes: {
        // Chỉ có LoginScreen, KHÔNG có Tab
        '/login': (_) => const LoginScreen(),
        // Chỉ được gọi khi đăng nhập thành công (xem LoginScreen)
        '/home': (_) => const TabBarApp(),
      },
    );
  }
}