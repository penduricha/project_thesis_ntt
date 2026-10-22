import 'package:flutter/material.dart';
import 'package:flutter_slidable/flutter_slidable.dart';

import '../widgets/menu_bar_chat_list_screen.dart';
import 'screen_detail_chat.dart';


String truncateMessage(String text, int limit) {
  if (text.length <= limit) {
    return text;
  }
  return '${text.substring(0, limit)}...';
}

class ChatListScreen extends StatelessWidget {
  const ChatListScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final List<Map<String, String>> chatList = [
      {
        'name': 'Apple Verification',
        'message': 'Your verification code is: 849201. Do not share this code with anyone for security reasons.',
        'time': '10:42 AM',
        'unread': 'true',
      },
      {
        'name': 'Momo OTP',
        'message': 'Ma xac thuc Momo cua ban la 592104. Hieu luc trong 5 phut.',
        'time': 'Hôm qua',
        'unread': 'false',
      },
      {
        'name': 'Nguyen Van A',
        'message': 'Ê tối nay đi cà phê không ông ơi?',
        'time': 'Hôm qua',
        'unread': 'false',
      },
      {
        'name': 'Viettel Telecom',
        'message': 'Quy khach da thanh toan thanh cong hoa den cuoc thang.',
        'time': '28/08',
        'unread': 'false',
      },
    ];

    return PopScope(
      canPop: false,
      child: Scaffold(
        // Tích hợp Menu vào bên trái Scaffold
        drawer: const MenuBarChatListScreen(),
        appBar: AppBar(
          backgroundColor: Colors.white,
          elevation: 0,
          // Sử dụng Builder để lấy context chính xác mở Drawer
          leading: Builder(
            builder: (context) {
              return IconButton(
                icon: const Icon(Icons.menu, color: Colors.black),
                onPressed: () {
                  Scaffold.of(context).openDrawer();
                  // Mở menu trượt từ trái sang
                },
              );
            },
          ),
          title: const Text(
            'Messages',
            style: TextStyle(
              color: Colors.black,
              fontSize: 28,
              fontWeight: FontWeight.bold,
            ),
          ),
          actions: [
            IconButton(
              icon: const Icon(Icons.edit, color: Color(0xFF007AFF)),
              onPressed: () {},
            ),
          ],
          bottom: PreferredSize(
            preferredSize: const Size.fromHeight(44),
            child: Padding(
              padding: const EdgeInsets.symmetric(
                horizontal: 16.0,
                vertical: 6.0,
              ),
              child: Container(
                height: 36,
                decoration: BoxDecoration(
                  color: const Color(0xFFEFEFF0),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: const TextField(
                  decoration: InputDecoration(
                    hintText: 'Search',
                    hintStyle: TextStyle(color: Colors.grey, fontSize: 15),
                    prefixIcon: Icon(
                      Icons.search,
                      color: Colors.grey,
                      size: 20,
                    ),
                    prefixIconConstraints: BoxConstraints(
                      minWidth: 40,
                      minHeight: 40,
                    ),
                    border: InputBorder.none,
                    isDense: true,
                    contentPadding: EdgeInsets.symmetric(vertical: 8),
                  ),
                ),
              ),
            ),
          ),
        ),

        body: ListView.separated(
          itemCount: chatList.length,
          separatorBuilder: (context, index) =>
              const Divider(height: 1, indent: 76, color: Color(0xFFE5E5EA)),
          itemBuilder: (context, index) {
            final chat = chatList[index];
            final bool isUnread = chat['unread'] == 'true';

            return Slidable(
              key: Key(chat['name']! + index.toString()),
              endActionPane: ActionPane(
                motion: const ScrollMotion(),
                extentRatio: 0.20,
                children: [
                  CustomSlidableAction(
                    onPressed: (context) {
                      // Logic xóa
                    },
                    backgroundColor: const Color(0xFFFF3B30),
                    foregroundColor: Colors.white,
                    child: const Icon(Icons.delete_outline, size: 28),
                  ),
                ],
              ),
              child: InkWell(
                onTap: () {
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (context) =>
                          ChatDetailScreen(senderName: chat['name']!),
                    ),
                  );
                },
                child: Container(
                  height: 80, // 🎯 Đặt chiều cao cố định cho tất cả các item
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: Row(
                    crossAxisAlignment:
                        CrossAxisAlignment.center, // Căn giữa theo chiều dọc
                    children: [
                      // Chấm xanh Unread
                      SizedBox(
                        width: 10,
                        child: Center(
                          child: isUnread
                              ? Container(
                                  width: 8,
                                  height: 8,
                                  decoration: const BoxDecoration(
                                    color: Color(0xFF007AFF),
                                    shape: BoxShape.circle,
                                  ),
                                )
                              : const SizedBox.shrink(),
                        ),
                      ),
                      const SizedBox(width: 10),

                      // Avatar
                      CircleAvatar(
                        radius: 24, // Giảm nhẹ radius để vừa vặn khung 72px
                        backgroundColor: Colors
                            .primaries[index % Colors.primaries.length]
                            .withValues(alpha: 0.2),
                        child: Text(
                          chat['name']![0],
                          style: TextStyle(
                            fontSize: 20,
                            fontWeight: FontWeight.bold,
                            color: Colors
                                .primaries[index % Colors.primaries.length],
                          ),
                        ),
                      ),
                      const SizedBox(width: 12),

                      // Nội dung tên & tin nhắn
                      Expanded(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment
                              .center, // Căn giữa nội dung Column
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              mainAxisAlignment: MainAxisAlignment.spaceBetween,
                              children: [
                                Expanded(
                                  child: Text(
                                    chat['name']!,
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    style: TextStyle(
                                      fontSize: 16,
                                      fontWeight: isUnread
                                          ? FontWeight.bold
                                          : FontWeight.w600,
                                      color: Colors.black,
                                    ),
                                  ),
                                ),
                                const SizedBox(width: 8),
                                Text(
                                  chat['time']!,
                                  style: TextStyle(
                                    fontSize: 12,
                                    color: isUnread
                                        ? const Color(0xFF007AFF)
                                        : Colors.grey,
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 4),
                            Text(
                              chat['message']!, // Bỏ truncateMessage để TextOverflow lo
                              maxLines: 1, // Giới hạn 1 dòng để giữ height 72px không bị bóp nghẹt
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(
                                fontSize: 14,
                                color: isUnread
                                    ? Colors.black87
                                    : Colors.grey[600],
                                fontWeight: isUnread
                                    ? FontWeight.w500
                                    : FontWeight.normal,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            );
          },
        ),
      ),
    );
  }
}
