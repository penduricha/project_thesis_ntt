import 'package:flutter/material.dart';

// Import màn hình login vào
import 'features/login/login.dart';
// Khung Tab (Authenticator | Chat) - chỉ hiển thị sau khi đăng nhập thành công
import 'features/menu/TabBarApp.dart';
// import 'features/chat/setting.dart';

void main() {
  runApp(const MyApp());
  //debugPrint("Server run in http://localhost:8005/");
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  // @override
  // Widget build(BuildContext context) {
  //   return MaterialApp(
  //     debugShowCheckedModeBanner: false,
  //     title: 'Chat',
  //     home: const ChatListScreen(),
  //   );
  // }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Login',
      // Mở app: vào màn hình đăng nhập trước
      initialRoute: '/login',
      routes: {
        '/login': (_) => const LoginScreen(),
        // Chỉ được gọi khi đăng nhập thành công (xem LoginScreen)
        '/home': (_) => const TabBarApp(),
      },
    );
  }
}