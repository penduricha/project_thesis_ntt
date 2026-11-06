import 'package:flutter/material.dart';
import 'package:flutter_slidable/flutter_slidable.dart';

import '../../../../core/constants/AppColors.dart';
import '../../../menu/MenuBarApp.dart';

import '../widgets/custom/ChatAvatar.dart';
import 'ChatDetailScreen.dart';

String truncateMessage(String text, int limit) {
  if (text.length <= limit) {
    return text;
  }
  return '${text.substring(0, limit)}...';
}

class ChatListScreen extends StatefulWidget {
  const ChatListScreen({super.key});

  @override
  State<ChatListScreen> createState() => _ChatListScreenState();
}

class _ChatListScreenState extends State<ChatListScreen> {
  // ---- Màu sắc: lấy đúng theo AuthenticatorScreen ----
  static const Color _blueColor = Color(0xFF004B87); // nút hành động góc phải
  static const Color _searchFill = Color(0xFFEFEFEF);

  final GlobalKey<ScaffoldState> _scaffoldKey = GlobalKey<ScaffoldState>();
  final TextEditingController _searchController = TextEditingController();

  // Dữ liệu demo (đưa ra khỏi build() để không bị tạo lại mỗi lần rebuild)
  final List<Map<String, String>> _chatList = [
    {
      'name': 'Apple Verification',
      'message':
      'Your verification code is: 849201. Do not share this code with anyone for security reasons.',
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

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  List<Map<String, String>> get _filteredChats {
    final query = _searchController.text.trim().toLowerCase();
    if (query.isEmpty) return _chatList;
    return _chatList.where((c) {
      return c['name']!.toLowerCase().contains(query) ||
          c['message']!.toLowerCase().contains(query);
    }).toList();
  }

  // ---- UI ----

  @override
  Widget build(BuildContext context) {
    final chats = _filteredChats;

    return PopScope(
      canPop: false,
      child: Scaffold(
        key: _scaffoldKey,
        backgroundColor: Colors.white,
        drawer: const MenuBarApp(),
        body: SafeArea(
          child: Column(
            children: [
              _buildTopBar(),
              _buildSearchBar(),
              const SizedBox(height: 8),
              Expanded(
                child: chats.isEmpty
                    ? _buildEmptyState()
                    : ListView.separated(
                  keyboardDismissBehavior:
                  ScrollViewKeyboardDismissBehavior.onDrag,
                  padding: EdgeInsets.zero,
                  itemCount: chats.length,
                  separatorBuilder: (_, __) => const Divider(
                    height: 1,
                    indent: 76,
                    color: Color(0xFFE5E5EA),
                  ),
                  itemBuilder: (context, index) =>
                      _buildChatTile(context, chats[index]),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  // Hàng trên cùng: menu | tiêu đề | nút soạn tin (giống Authenticator)
  Widget _buildTopBar() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(8, 8, 8, 4),
      child: Row(
        children: [
          IconButton(
            onPressed: () => _scaffoldKey.currentState?.openDrawer(),
            icon: const Icon(Icons.menu, color: Colors.black87),
            tooltip: 'Menu',
          ),
          const SizedBox(width: 8),
          const Expanded(
            child: Text(
              'Messages',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontSize: 26,
                fontWeight: FontWeight.w600,
                color: Colors.black,
              ),
            ),
          ),
          IconButton(
            onPressed: () {},
            icon: const Icon(Icons.edit, color: _blueColor, size: 26),
            tooltip: 'Tin nhắn mới',
          ),
        ],
      ),
    );
  }

  // Thanh tìm kiếm bo góc 12, nền xám nhạt (giống Authenticator)
  Widget _buildSearchBar() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
      child: TextField(
        controller: _searchController,
        onChanged: (_) => setState(() {}),
        textInputAction: TextInputAction.search,
        style: const TextStyle(fontSize: 16),
        decoration: InputDecoration(
          hintText: 'Tìm kiếm',
          hintStyle: const TextStyle(color: Colors.grey, fontSize: 16),
          filled: true,
          fillColor: _searchFill,
          isDense: true,
          contentPadding: const EdgeInsets.symmetric(vertical: 13),
          prefixIcon: const Icon(Icons.search, color: Colors.grey),
          suffixIcon: _searchController.text.isEmpty
              ? null
              : IconButton(
            icon: const Icon(Icons.close, color: Colors.grey, size: 20),
            onPressed: () {
              _searchController.clear();
              setState(() {});
            },
          ),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(12),
            borderSide: BorderSide.none,
          ),
        ),
      ),
    );
  }

  Widget _buildEmptyState() {
    return const Center(
      child: Text(
        'Không tìm thấy cuộc trò chuyện',
        style: TextStyle(fontSize: 15, color: Colors.grey),
      ),
    );
  }

  // Một dòng chat (giữ nguyên giao diện cũ)
  Widget _buildChatTile(BuildContext context, Map<String, String> chat) {
    final bool isUnread = chat['unread'] == 'true';

    return Slidable(
      key: ValueKey(chat['name']),
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
              builder: (context) => ChatDetailScreen(senderName: chat['name']!),
            ),
          );
        },
        child: Container(
          height: 80, // Chiều cao cố định cho tất cả các item
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.center,
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
                      color: AppColors.primaryBlue,
                      shape: BoxShape.circle,
                    ),
                  )
                      : const SizedBox.shrink(),
                ),
              ),
              const SizedBox(width: 10),

              // Avatar: một kiểu duy nhất (nền xanh nhạt), trạng thái chưa đọc do chấm xanh + chữ đậm thể hiện
              ChatAvatar(name: chat['name']!),
              const SizedBox(width: 12),

              // Nội dung tên & tin nhắn
              Expanded(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
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
                              fontWeight:
                              isUnread ? FontWeight.bold : FontWeight.w600,
                              color: Colors.black,
                            ),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Text(
                          chat['time']!,
                          style: TextStyle(
                            fontSize: 12,
                            color:
                            isUnread ? AppColors.primaryBlue : Colors.grey,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 4),
                    Text(
                      chat['message']!,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontSize: 14,
                        color: isUnread ? Colors.black87 : Colors.grey[600],
                        fontWeight:
                        isUnread ? FontWeight.w500 : FontWeight.normal,
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
  }
}