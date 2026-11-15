import 'package:flutter/material.dart';
import 'package:flutter_dotenv/flutter_dotenv.dart';

// Màn hình chờ: kiểm tra phiên đã lưu (auto login) rồi chuyển Home hoặc Login.
import 'features/login/presentation/screens/splash_screen.dart';

// Màn hình đăng nhập: import THẲNG file chứa class LoginScreen
// (không import features/login/login_export.dart để tránh lấy nhầm class cũ).
import 'features/login/presentation/screens/login_screen.dart';

// Khung Tab (Authenticator | Chat) - chỉ dùng ở route '/home'.
import 'features/main_shell/presentation/widgets/app_tab_bar.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Nạp cấu hình mạng (host, port, giao thức, timeout) từ file .env.
  // isOptional: true -> thiếu file .env vẫn chạy, NetworkConfiguration
  // sẽ dùng giá trị mặc định (fallback).
  await dotenv.load(fileName: '.env', isOptional: true);

  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Login',
      // Mở app: kiểm tra auto login trước, rồi mới quyết định vào Home hay Login.
      initialRoute: '/',
      routes: {
        '/': (_) => const SplashScreen(),
        // Chỉ có LoginScreen, KHÔNG có Tab.
        '/login': (_) => const LoginScreen(),
        // Vào khi auto login hoặc đăng nhập thành công (xem LoginScreen).
        '/home': (_) => const AppTabBar(),
      },
    );
  }
}