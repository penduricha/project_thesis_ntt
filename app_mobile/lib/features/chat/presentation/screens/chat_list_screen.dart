import 'package:flutter/material.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/widgets/qbank_section_header.dart';
import '../../../../core/localization/app_strings.dart';
import '../../../../core/utils/chat_time.dart';
import '../../../main_shell/presentation/widgets/app_menu_bar.dart';
import '../../application/chat_controller.dart';
import '../../data/model/chat_conversation.dart';
import '../widgets/custom/chat_avatar.dart';
import '../widgets/custom/chat_list_item.dart';
import '../widgets/custom/chat_status_banner.dart';
import 'chat_detail_screen.dart';
import 'new_chat_screen.dart';

/// Danh sách cuộc trò chuyện (1-1 và nhóm) của admin đang đăng nhập.
/// Dữ liệu và cập nhật thời gian thực đến từ [ChatController]; màn này chỉ hiển thị.
class ChatListScreen extends StatefulWidget {
  const ChatListScreen({super.key});

  @override
  State<ChatListScreen> createState() => _ChatListScreenState();
}

class _ChatListScreenState extends State<ChatListScreen> {
  static const Color _blueColor = AppColors.primaryBlue;

  final ChatController _chat = ChatController.instance;
  final GlobalKey<ScaffoldState> _scaffoldKey = GlobalKey<ScaffoldState>();
  final TextEditingController _searchController = TextEditingController();

  @override
  void initState() {
    super.initState();
    // Thường đã chạy từ khung tab; gọi lại an toàn (không làm gì nếu đã chạy).
    if (!_chat.isRunning) {
      _chat.start();
    }
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  List<ChatConversation> _filtered(List<ChatConversation> all) {
    final query = _searchController.text.trim().toLowerCase();
    if (query.isEmpty) return all;
    return [
      for (final c in all)
        if (c.displayName.toLowerCase().contains(query) ||
            (c.lastMessage?.content.toLowerCase().contains(query) ?? false))
          c,
    ];
  }

  void _openConversation(ChatConversation conversation) {
    Navigator.push(
      context,
      MaterialPageRoute<void>(
        builder: (_) => ChatDetailScreen(conversationId: conversation.id),
      ),
    );
  }

  void _openNewChat() {
    Navigator.push(
      context,
      MaterialPageRoute<void>(builder: (_) => const NewChatScreen()),
    );
  }

  // ------------------------------------------------------------------ giao diện

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      child: Scaffold(
        key: _scaffoldKey,
        backgroundColor: Theme.of(context).scaffoldBackgroundColor,
        drawer: const AppMenuBar(),
        body: SafeArea(
          child: Column(
            children: [
              _buildTopBar(),
              const ChatStatusBanner(),
              _buildSearchBar(),
              const SizedBox(height: 8),
              Expanded(
                child: ListenableBuilder(
                  listenable: _chat,
                  builder: (context, _) => _buildBody(),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildTopBar() => QBankSectionHeader(
    title: AppStrings.of(context).text('messages'),
    onMenu: () => _scaffoldKey.currentState?.openDrawer(),
    actionIcon: Icons.edit_outlined,
    actionLabel: AppStrings.of(context).text('newMessage'),
    onAction: _openNewChat,
  );

  Widget _buildSearchBar() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
      child: TextField(
        controller: _searchController,
        onChanged: (_) => setState(() {}),
        textInputAction: TextInputAction.search,
        style: const TextStyle(fontSize: 16),
        decoration: InputDecoration(
          hintText: AppStrings.of(context).text('search'),
          hintStyle: const TextStyle(color: Colors.grey, fontSize: 16),
          filled: true,
          fillColor: Theme.of(context).colorScheme.surface,
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

  Widget _buildBody() {
    final all = _chat.visibleConversations;
    final chats = _filtered(all);

    // Chưa có gì để hiện: đang tải lần đầu thì quay vòng, tải lỗi thì cho thử lại.
    if (all.isEmpty) {
      if (!_chat.listLoaded || _chat.listLoading) {
        return const Center(child: CircularProgressIndicator());
      }
      final error = _chat.listError;
      if (error != null) {
        return _scrollableMessage(
          icon: Icons.cloud_off_outlined,
          text: error,
          action: TextButton(onPressed: _chat.refreshAll, child: Text(AppStrings.of(context).text('retryAction'))),
        );
      }
    }

    if (chats.isEmpty) {
      final searching = _searchController.text.trim().isNotEmpty;
      return _scrollableMessage(
        icon: searching ? Icons.search_off : Icons.chat_bubble_outline,
        text: searching
            ? AppStrings.of(context).text('noConversationFound')
            : AppStrings.of(context).text('noConversations'),
      );
    }

    return RefreshIndicator(
      onRefresh: _chat.refreshAll,
      child: ListView.builder(
        keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
        physics: const AlwaysScrollableScrollPhysics(),
        padding: EdgeInsets.zero,
        itemCount: chats.length,
        itemBuilder: (context, index) => _buildChatTile(chats[index]),
      ),
    );
  }

  /// Thông báo giữa màn hình nhưng vẫn kéo-để-làm-mới được.
  Widget _scrollableMessage({required IconData icon, required String text, Widget? action}) {
    return RefreshIndicator(
      onRefresh: _chat.refreshAll,
      child: LayoutBuilder(
        builder: (context, constraints) {
          return ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            children: [
              SizedBox(
                height: constraints.maxHeight,
                child: Center(
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 32),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(icon, size: 44, color: Theme.of(context).colorScheme.primary),
                        const SizedBox(height: 12),
                        Text(
                          text,
                          textAlign: TextAlign.center,
                          style: TextStyle(fontSize: 15, color: Theme.of(context).colorScheme.onSurfaceVariant, height: 1.4),
                        ),
                        if (action != null) action,
                      ],
                    ),
                  ),
                ),
              ),
            ],
          );
        },
      ),
    );
  }

  String _preview(ChatConversation c) {
    final last = c.lastMessage;
    if (last == null) return c.isGroup ? AppStrings.of(context).text('groupCreated') : '';
    final text = last.content.replaceAll(RegExp(r'\s+'), ' ').trim();
    if (last.senderId == _chat.myId) return AppStrings.of(context).format('mePrefix', {'text': text});
    // Chat nhóm cần biết ai nói; chat 1-1 thì người kia là tiêu đề rồi.
    if (c.isGroup && last.senderName.isNotEmpty) return '${last.senderName}: $text';
    return text;
  }

  Widget _buildAvatar(ChatConversation c) {
    if (!c.isGroup) return ChatAvatar(name: c.displayName, isUnread: c.unreadCount > 0);
    final unread = c.unreadCount > 0;
    return ExcludeSemantics(
      child: CircleAvatar(
        radius: 24,
        backgroundColor: unread ? _blueColor : Theme.of(context).colorScheme.primaryContainer,
        child: Icon(Icons.group, size: 24, color: unread ? Colors.white : Theme.of(context).colorScheme.onPrimaryContainer),
      ),
    );
  }

  Widget _buildChatTile(ChatConversation c) {
    final bool isUnread = c.unreadCount > 0;
    final last = c.lastMessage;
    final preview = _preview(c);

    return ChatListItem(
      onTap: () => _openConversation(c),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          // Chấm xanh chưa đọc
          SizedBox(
            width: 10,
            child: Center(
              child: isUnread
                  ? Container(
                      width: 8,
                      height: 8,
                      decoration: const BoxDecoration(color: _blueColor, shape: BoxShape.circle),
                    )
                  : const SizedBox.shrink(),
            ),
          ),
          const SizedBox(width: 10),
          _buildAvatar(c),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        c.displayName,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 16,
                          fontWeight: isUnread ? FontWeight.bold : FontWeight.w600,
                          color: Theme.of(context).colorScheme.onSurface,
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Text(
                      ChatTime.listLabel(c.lastActivity),
                      style: TextStyle(fontSize: 12, color: isUnread ? Theme.of(context).colorScheme.primary : Theme.of(context).colorScheme.onSurfaceVariant),
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        preview,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 14,
                          color: isUnread ? Theme.of(context).colorScheme.onSurface : Theme.of(context).colorScheme.onSurfaceVariant,
                          fontWeight: isUnread ? FontWeight.w500 : FontWeight.normal,
                          fontStyle: last == null ? FontStyle.italic : FontStyle.normal,
                        ),
                      ),
                    ),
                    if (isUnread) ...[
                      const SizedBox(width: 8),
                      _UnreadBadge(count: c.unreadCount),
                    ],
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _UnreadBadge extends StatelessWidget {
  const _UnreadBadge({required this.count});

  final int count;

  @override
  Widget build(BuildContext context) {
    final label = count > 99 ? '99+' : '$count';
    return Container(
      constraints: const BoxConstraints(minWidth: 20),
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: AppColors.primaryBlue,
        borderRadius: BorderRadius.circular(10),
      ),
      child: Text(
        label,
        textAlign: TextAlign.center,
        style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: Colors.white),
      ),
    );
  }
}
