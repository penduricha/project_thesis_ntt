import 'package:flutter/material.dart';

class MenuBarChatListScreen extends StatelessWidget {
  const MenuBarChatListScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Drawer(
      backgroundColor: Colors.white,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.zero,
        // Giữ góc vuông chuẩn menu side
      ),
      child: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,

          children: [
            const SizedBox(height: 20),
            // Header menu (Tùy chọn hiển thị tên người dùng)
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
              child: Row(
                children: [
                  CircleAvatar(
                    radius: 24,
                    backgroundColor: Color(0xFF007AFF),
                    child: Icon(Icons.person, color: Colors.white, size: 28),
                  ),
                  SizedBox(width: 12),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'User account',
                        style: TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      Text(
                        'user@example.com',
                        style: TextStyle(fontSize: 13, color: Colors.grey),
                      ),
                    ],
                  ),
                ],
              ),
            ),

            const Divider(height: 10, thickness: 1, indent: 16, endIndent: 16),

            // Item: Tài khoản
            ListTile(
              leading: const Icon(Icons.person_outline, color: Colors.black87),
              title: const Text(
                'Tài khoản',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.w500),
              ),
              trailing: const Icon(Icons.chevron_right, color: Colors.grey),
              onTap: () {
                Navigator.pop(context);
                // Đóng drawer
                // TODO: Chuyển sang màn hình Tài khoản
              },
            ),

            // Item: Cài đặt
            ListTile(
              leading: const Icon(
                Icons.settings_outlined,
                color: Colors.black87,
              ),
              title: const Text(
                'Cài đặt',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.w500),
              ),
              trailing: const Icon(Icons.chevron_right, color: Colors.grey),
              onTap: () {
                Navigator.pop(context); // Đóng drawer
                // TODO: Chuyển sang màn hình Cài đặt
              },
            ),

            //const Spacer(), // Đẩy nút Đăng xuất xuống đáy
            //const Divider(height: 1),

            // Item: Đăng xuất (Màu đỏ đặc trưng)
            ListTile(
              leading: const Icon(Icons.logout_sharp, color: Colors.redAccent),
              title: const Text(
                'Đăng xuất',
                style: TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w600,
                  color: Colors.redAccent,
                ),
              ),
              onTap: () {
                Navigator.pop(context);
                // Đóng drawer
                // TODO: Thêm logic đăng xuất hoặc gọi hàm chuyển hướng về Login
              },
            ),
            const SizedBox(height: 12),
          ],
        ),
      ),
    );
  }
}
