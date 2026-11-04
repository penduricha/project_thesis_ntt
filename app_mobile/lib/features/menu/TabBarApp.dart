import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

// TODO: chỉnh lại 2 đường dẫn import cho đúng với cấu trúc thư mục của dự án
import '../authenticator/presentation/screens/AuthenticatorScreen.dart';
import '../chat/presentation/screens/ChatListScreen.dart';

// ============================================================================
//  DANH SÁCH TAB: nhãn + icon (thường / đang chọn)
// ============================================================================

enum AppTab {
  authenticator(
    label: 'Authenticator',
    icon: Icons.verified_user_outlined,
    activeIcon: Icons.shield_outlined,
  ),
  chat(
    label: 'Chat',
    icon: Icons.chat_bubble_outline,
    activeIcon: Icons.messenger,
  );

  const AppTab({
    required this.label,
    required this.icon,
    required this.activeIcon,
  });

  final String label;
  final IconData icon;
  final IconData activeIcon;
}

// ============================================================================
//  KHUNG APP: Tab nằm cố định ở Footer, nội dung phía trên đổi theo tab.
//
//  Cách hoạt động: mỗi tab có 1 Navigator riêng (Navigator lồng nhau).
//  -> Navigator.push(...) bên trong một màn hình chỉ thay đổi vùng phía trên,
//     thanh Tab ở dưới luôn được giữ nguyên.
//  -> IndexedStack giữ trạng thái từng tab (vị trí cuộn, mã đang đếm ngược...)
//     khi chuyển qua lại.
//
//  Cách dùng:
//    - Mở khung app:        home: const TabBarApp()
//    - Chuyển tab từ bất kỳ màn hình con nào:
//          TabBarApp.navigate(context, AppTab.chat);
//          TabBarApp.navigate(context, AppTab.authenticator);
// ============================================================================

class TabBarApp extends StatefulWidget {
  const TabBarApp({super.key, this.initialTab = AppTab.authenticator});

  /// Tab hiển thị đầu tiên
  final AppTab initialTab;

  static TabBarAppState? maybeOf(BuildContext context) {
    return context.findAncestorStateOfType<TabBarAppState>();
  }

  /// Hàm Navigate: chuyển sang tab [tab] từ bất kỳ đâu trong cây widget.
  /// [popToRoot] = true: đưa tab đó về màn hình gốc (xóa các màn con đã push).
  static void navigate(
      BuildContext context,
      AppTab tab, {
        bool popToRoot = false,
      }) {
    maybeOf(context)?.navigate(tab, popToRoot: popToRoot);
  }

  @override
  State<TabBarApp> createState() => TabBarAppState();
}

class TabBarAppState extends State<TabBarApp> {
  // ---- Màu sắc ----
  static const Color _brandColor = Color(0xFF004B87); // tab đang chọn
  static const Color _inactiveColor = Color(0xFF8E8E93); // tab chưa chọn
  static const Color _borderColor = Color(0xFFEDEDED);

  late AppTab _currentTab;

  // Chỉ dựng tab khi người dùng mở lần đầu (tiết kiệm tài nguyên lúc khởi động)
  final Set<AppTab> _visited = {};

  // Mỗi tab 1 Navigator riêng để Tab ở footer không bị đè khi push màn hình mới
  final Map<AppTab, GlobalKey<NavigatorState>> _navKeys = {
    for (final tab in AppTab.values)
      tab: GlobalKey<NavigatorState>(debugLabel: 'tab_${tab.name}'),
  };

  AppTab get currentTab => _currentTab;

  @override
  void initState() {
    super.initState();
    _currentTab = widget.initialTab;
    _visited.add(_currentTab);
  }

  // ---- Hành động ----

  /// Chuyển tab. Nếu đang ở đúng tab đó và [popToRoot] = true thì về màn gốc.
  void navigate(AppTab tab, {bool popToRoot = false}) {
    final nav = _navKeys[tab]!.currentState;

    if (tab == _currentTab) {
      if (popToRoot) nav?.popUntil((route) => route.isFirst);
      return;
    }

    FocusManager.instance.primaryFocus?.unfocus(); // đóng bàn phím nếu đang mở
    setState(() {
      _currentTab = tab;
      _visited.add(tab);
    });
    if (popToRoot) nav?.popUntil((route) => route.isFirst);
  }

  /// Nút Back của hệ thống (Android):
  ///  1) Tab hiện tại còn màn con  -> quay lại màn con
  ///  2) Đang ở tab khác tab đầu    -> về tab đầu tiên (Authenticator)
  ///  3) Còn lại                    -> thoát app
  void _onBack(bool didPop, Object? result) {
    if (didPop) return;

    final nav = _navKeys[_currentTab]!.currentState;
    if (nav != null && nav.canPop()) {
      nav.pop();
      return;
    }
    if (_currentTab != AppTab.authenticator) {
      navigate(AppTab.authenticator);
      return;
    }
    SystemNavigator.pop();
  }

  // ---- UI ----

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false, // tự xử lý nút Back ở _onBack
      onPopInvokedWithResult: _onBack,
      child: Scaffold(
        backgroundColor: Colors.white,
        body: IndexedStack(
          index: _currentTab.index,
          children: [
            for (final tab in AppTab.values)
              _visited.contains(tab)
                  ? _buildTabNavigator(tab)
                  : const SizedBox.shrink(),
          ],
        ),
        bottomNavigationBar: _buildBottomBar(),
      ),
    );
  }

  Widget _buildTabNavigator(AppTab tab) {
    return TickerMode(
      // Tab đang ẩn thì tạm dừng animation (vd: vòng đếm ngược) cho đỡ tốn pin
      enabled: tab == _currentTab,
      child: Navigator(
        key: _navKeys[tab],
        onGenerateRoute: (settings) => MaterialPageRoute<void>(
          settings: settings,
          builder: (_) => _rootScreen(tab),
        ),
      ),
    );
  }

  // Màn hình gốc của từng tab
  Widget _rootScreen(AppTab tab) {
    return switch (tab) {
      AppTab.authenticator => const AuthenticatorScreen(),
      AppTab.chat => ChatListScreen(),
    };
  }

  Widget _buildBottomBar() {
    return DecoratedBox(
      decoration: const BoxDecoration(
        color: Colors.white,
        border: Border(top: BorderSide(color: _borderColor, width: 1)),
      ),
      child: BottomNavigationBar(
        currentIndex: _currentTab.index,
        // Chạm lại đúng tab đang mở -> về màn hình gốc của tab đó
        onTap: (index) {
          final tab = AppTab.values[index];
          navigate(tab, popToRoot: tab == _currentTab);
        },
        type: BottomNavigationBarType.fixed,
        backgroundColor: Colors.white,
        elevation: 0,
        selectedItemColor: _brandColor,
        unselectedItemColor: _inactiveColor,
        selectedFontSize: 12,
        unselectedFontSize: 12,
        selectedLabelStyle: const TextStyle(fontWeight: FontWeight.w600),
        items: [
          for (final tab in AppTab.values)
            BottomNavigationBarItem(
              icon: Icon(tab.icon),
              activeIcon: Icon(tab.activeIcon),
              label: tab.label,
            ),
        ],
      ),
    );
  }
}