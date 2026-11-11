import 'package:flutter/material.dart';

import '../controller/auto_login_controller.dart';

/// Màn hình chờ lúc mở app: kiểm tra phiên đã lưu rồi tự chuyển sang Home hoặc Login.
/// Dùng route riêng để người dùng không thấy màn hình Login nháy lên rồi biến mất.
class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key});

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen> {
  @override
  void initState() {
    super.initState();
    _decideRoute();
  }

  Future<void> _decideRoute() async {
    final destination = await AutoLoginController().resolveStartDestination();
    if (!mounted) return;

    final route = switch (destination) {
      StartDestination.home => '/home',
      StartDestination.login => '/login',
    };
    Navigator.of(context).pushReplacementNamed(route);
  }

  @override
  Widget build(BuildContext context) {
    // Chặn nút Back khi đang kiểm tra phiên
    return const PopScope(
      canPop: false,
      child: Scaffold(
        backgroundColor: Colors.white,
        body: Center(
          child: CircularProgressIndicator(semanticsLabel: 'Đang kiểm tra phiên đăng nhập'),
        ),
      ),
    );
  }
}
