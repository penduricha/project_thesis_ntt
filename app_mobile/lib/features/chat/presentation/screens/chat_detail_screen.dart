import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/localization/app_strings.dart';
import '../../../../core/utils/chat_time.dart';
import '../../application/chat_controller.dart';
import '../../data/model/chat_conversation.dart';
import '../../data/model/chat_message.dart';
import '../widgets/custom/chat_composer.dart';
import '../widgets/custom/chat_message_bubble.dart';
import '../widgets/custom/chat_status_banner.dart';
import 'group_info_screen.dart';

/// Màn trò chuyện của một cuộc (1-1 hoặc nhóm). Chỉ văn bản: không ảnh, file, ghi âm.
/// Mọi trạng thái nằm ở [ChatController]; màn hình chỉ vẽ và gọi hành động.
class ChatDetailScreen extends StatefulWidget {
  const ChatDetailScreen({super.key, required this.conversationId});

  final int conversationId;

  @override
  State<ChatDetailScreen> createState() => _ChatDetailScreenState();
}

class _ChatDetailScreenState extends State<ChatDetailScreen> {
  static const int _maxLength = 2000;

  // Cách đáy bao nhiêu điểm ảnh thì vẫn coi là "đang ở cuối danh sách".
  static const double _bottomThreshold = 80;

  // Hai tin liền nhau cách từ 20 giờ thì chèn tiêu đề ngày.
  static const Duration _headerGap = Duration(hours: 20);

  final ChatController _chat = ChatController.instance;
  final TextEditingController _input = TextEditingController();
  final ScrollController _scroll = ScrollController();
  StreamSubscription<ChatNotice>? _noticeSubscription;

  bool _atBottom = true;
  bool _showJump = false;

  // Để nhận biết "có tin mới ở cuối" (khác với trang tin cũ được nối vào đầu danh sách).
  int _lastCount = 0;
  String? _lastTailKey;

  int get _id => widget.conversationId;

  @override
  void initState() {
    super.initState();
    _scroll.addListener(_onScroll);
    _chat.addListener(_onChatChanged);
    _noticeSubscription = _chat.notices.listen(_onNotice);
    // Hoãn tới sau khung hình đầu: openConversation gọi notifyListeners, không được gọi giữa lúc đang build.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _chat.openConversation(_id);
    });
  }

  @override
  void dispose() {
    _noticeSubscription?.cancel();
    _chat.removeListener(_onChatChanged);
    _chat.closeConversation(_id);
    _scroll.removeListener(_onScroll);
    _scroll.dispose();
    _input.dispose();
    super.dispose();
  }

  // ------------------------------------------------------------------ cuộn

  void _onScroll() {
    if (!_scroll.hasClients) return;
    final position = _scroll.position;
    final atBottom = position.pixels <= _bottomThreshold;
    if (atBottom != _atBottom) {
      setState(() {
        _atBottom = atBottom;
        if (atBottom) _showJump = false;
      });
      _chat.setViewing(_id, atBottom: atBottom);
    }
    // Gần cụt danh sách (phía tin cũ): tải trang cũ hơn.
    if (position.pixels >= position.maxScrollExtent - 400) {
      _chat.loadOlder(_id);
    }
  }

  void _scrollToBottom() {
    if (!_scroll.hasClients) return;
    _scroll.animateTo(0, duration: const Duration(milliseconds: 250), curve: Curves.easeOut);
  }

  /// Chạy mỗi khi ChatController đổi trạng thái (trước khi dựng lại). Dùng để xử lý "tin mới đến khi đang cuộn lên đọc".
  void _onChatChanged() {
    final messages = _chat.messagesOf(_id);
    final count = messages.length;
    final tail = messages.isEmpty ? null : messages.last;
    final tailKey = tail == null ? null : '${tail.id ?? tail.clientMsgId}';
    final tailChanged = tailKey != _lastTailKey;
    final grewAtTail = tailChanged && count > _lastCount && tail != null;
    _lastCount = count;
    _lastTailKey = tailKey;

    if (!grewAtTail || tail == null || !_scroll.hasClients || _atBottom) return;
    if (tail.senderId == _chat.myId) return; // tin của mình: _send đã cuộn xuống cuối

    // Đang cuộn lên đọc tin cũ: giữ nguyên vị trí đang xem. Danh sách đảo chiều nên tin mới chèn vào đầu (đáy) làm
    // mọi thứ trượt đi; bù đúng phần độ dài vừa tăng sau khi dựng lại.
    final extentBefore = _scroll.position.maxScrollExtent;
    final pixelsBefore = _scroll.position.pixels;
    if (!_showJump) {
      setState(() => _showJump = true);
    }
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || !_scroll.hasClients || _atBottom) return;
      final delta = _scroll.position.maxScrollExtent - extentBefore;
      if (delta > 0) _scroll.jumpTo(pixelsBefore + delta);
    });
  }

  // ------------------------------------------------------------------ thông báo từ controller

  void _onNotice(ChatNotice notice) {
    if (!mounted) return;
    if (notice.conversationId != null && notice.conversationId != _id) return;
    final messenger = ScaffoldMessenger.of(context);
    switch (notice.kind) {
      case ChatNoticeKind.info:
      case ChatNoticeKind.error:
        messenger.showSnackBar(SnackBar(content: Text(notice.message)));
      case ChatNoticeKind.conversationGone:
      case ChatNoticeKind.removedFromGroup:
      case ChatNoticeKind.groupDissolved:
        // Về danh sách (cả khi đang mở màn thông tin nhóm phía trên) rồi báo lý do.
        Navigator.of(context).popUntil((route) => route.isFirst);
        messenger.showSnackBar(SnackBar(content: Text(notice.message)));
    }
  }

  // ------------------------------------------------------------------ hành động

  Future<void> _send() async {
    final text = _input.text;
    if (text.trim().isEmpty) return;
    _input.clear();
    final accepted = await _chat.sendText(_id, text);
    if (accepted) _scrollToBottom();
  }

  void _showMessageMenu(ChatMessage message, bool isMe) {
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: Theme.of(context).colorScheme.surface,
      builder: (sheetContext) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.copy_outlined),
              title: Text(AppStrings.of(context).text('copyAction')),
              onTap: () {
                Navigator.pop(sheetContext);
                Clipboard.setData(ClipboardData(text: message.content));
                ScaffoldMessenger.of(context)
                  ..hideCurrentSnackBar()
                  ..showSnackBar(SnackBar(content: Text(AppStrings.of(context).text('copiedMessage'))));
              },
            ),
            // Chỉ người gửi mới xóa được, và chỉ tin đã có id server. Chỉ có MỘT kiểu xóa: cho tất cả mọi người.
            if (isMe && message.id != null)
              ListTile(
                leading: const Icon(Icons.delete_outline, color: AppColors.primaryRed),
                title: Text(AppStrings.of(context).text('deleteAction'), style: const TextStyle(color: AppColors.primaryRed)),
                onTap: () {
                  Navigator.pop(sheetContext);
                  _confirmDelete(message);
                },
              ),
          ],
        ),
      ),
    );
  }

  Future<void> _confirmDelete(ChatMessage message) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: Theme.of(context).colorScheme.surface,
        surfaceTintColor: Colors.transparent,
        title: Text(AppStrings.of(context).text('deleteMessageTitle')),
        content: Text(AppStrings.of(context).text('deleteMessageDesc')),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: Text(AppStrings.of(context).text('cancel')),
          ),
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: Text(AppStrings.of(context).text('deleteAction'), style: const TextStyle(color: AppColors.primaryRed)),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    await _chat.deleteMessage(_id, message.id!);
  }

  void _showFailedMenu(ChatMessage message) {
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: Theme.of(context).colorScheme.surface,
      builder: (sheetContext) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (message.retryable)
              ListTile(
                leading: const Icon(Icons.refresh, color: AppColors.primaryBlue),
                title: Text(AppStrings.of(context).text('resend')),
                onTap: () {
                  Navigator.pop(sheetContext);
                  _chat.retrySend(_id, message.clientMsgId);
                },
              ),
            // Tin chưa từng tới server: gỡ khỏi máy này (không phải "xóa chỉ phía mình" của tin đã gửi).
            ListTile(
              leading: const Icon(Icons.close, color: AppColors.primaryRed),
              title: Text(AppStrings.of(context).text('discardMessage'), style: const TextStyle(color: AppColors.primaryRed)),
              onTap: () {
                Navigator.pop(sheetContext);
                _chat.discardFailed(_id, message.clientMsgId);
              },
            ),
          ],
        ),
      ),
    );
  }

  // ------------------------------------------------------------------ giao diện

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: _chat,
      builder: (context, _) {
        final conversation = _chat.conversationById(_id);
        final messages = _chat.messagesOf(_id);
        return Scaffold(
          backgroundColor: Theme.of(context).colorScheme.surface,
          appBar: _buildAppBar(conversation),
          body: Column(
            children: [
              const ChatStatusBanner(),
              Expanded(child: _buildBody(conversation, messages)),
              ChatComposer(
                controller: _input,
                enabled: conversation != null,
                onSend: _send,
                maxLength: _maxLength,
              ),
            ],
          ),
        );
      },
    );
  }

  PreferredSizeWidget _buildAppBar(ChatConversation? conversation) {
    return AppBar(
      backgroundColor: Theme.of(context).colorScheme.surface,
      surfaceTintColor: Colors.transparent,
      elevation: 0.5,
      iconTheme: const IconThemeData(color: AppColors.primaryBlue),
      centerTitle: true,
      title: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            conversation?.displayName ?? AppStrings.of(context).text('loading'),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              color: Theme.of(context).colorScheme.onSurface,
              fontSize: 18,
              fontWeight: FontWeight.w600,
            ),
          ),
          if (conversation != null && conversation.isGroup)
            Text(
              AppStrings.of(context).format('memberCount', {'count': conversation.memberCount}),
              style: const TextStyle(fontSize: 12, color: AppColors.textMuted),
            ),
        ],
      ),
      actions: [
        if (conversation != null && conversation.isGroup)
          IconButton(
            tooltip: AppStrings.of(context).text('groupInfo'),
            icon: const Icon(Icons.info_outline, color: AppColors.primaryBlue),
            onPressed: () {
              Navigator.push(
                context,
                MaterialPageRoute<void>(
                  builder: (_) => GroupInfoScreen(conversationId: _id),
                ),
              );
            },
          ),
      ],
    );
  }

  Widget _buildBody(ChatConversation? conversation, List<ChatMessage> messages) {
    final loadingLatest = _chat.isLoadingLatest(_id);
    if (messages.isEmpty) {
      if (loadingLatest || conversation == null) {
        return const Center(child: CircularProgressIndicator());
      }
      if (_chat.loadFailed(_id)) {
        return Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(AppStrings.of(context).text('messagesFailed'),
                  style: TextStyle(fontSize: 15, color: AppColors.textMuted)),
              const SizedBox(height: 8),
              OutlinedButton(
                onPressed: () => _chat.reloadLatest(_id),
                child: Text(AppStrings.of(context).text('retryAction')),
              ),
            ],
          ),
        );
      }
      return Center(
        child: Text(
          AppStrings.of(context).text('noMessages'),
          style: TextStyle(fontSize: 15, color: AppColors.textMuted),
        ),
      );
    }

    final hasMore = _chat.hasMore(_id);
    final loadingOlder = _chat.isLoadingOlder(_id);
    final myId = _chat.myId;
    final isGroup = conversation?.isGroup ?? false;
    final extra = loadingOlder ? 1 : 0;

    return Stack(
      children: [
        // reverse: true => phần tử 0 là tin mới nhất ở đáy; tin cũ nối vào đuôi nên vị trí đang xem không nhảy.
        ListView.builder(
          controller: _scroll,
          reverse: true,
          keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          itemCount: messages.length + extra,
          itemBuilder: (context, index) {
            if (index >= messages.length) {
              return const Padding(
                padding: EdgeInsets.symmetric(vertical: 12),
                child: Center(
                  child: SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  ),
                ),
              );
            }
            final i = messages.length - 1 - index;
            final message = messages[i];
            final previous = i > 0 ? messages[i - 1] : null; // tin cũ hơn liền kề
            final isMe = message.senderId == myId;

            // Có tin cũ hơn thì xét khoảng cách thời gian; chưa có mà còn trang cũ chưa tải thì không vẽ tiêu đề giả.
            final showHeader = previous != null
                ? message.createdAt.difference(previous.createdAt).abs() >= _headerGap
                : !hasMore;
            final showSender =
                isGroup && !isMe && (showHeader || previous == null || previous.senderId != message.senderId);

            return KeyedSubtree(
              key: ValueKey<String>('${message.id ?? message.clientMsgId}'),
              child: Column(
                children: [
                  if (showHeader) _buildDateHeader(message.createdAt),
                  Padding(
                    padding: const EdgeInsets.only(bottom: 10),
                    child: ChatMessageBubble(
                      message: message,
                      isMe: isMe,
                      senderLabel: showSender
                          ? (message.senderName.isEmpty ? 'Admin ${message.senderId}' : message.senderName)
                          : null,
                      dimmed: message.id != null && _chat.isDeleting(message.id!),
                      onLongPress: message.status == ChatMessageStatus.sent
                          ? () => _showMessageMenu(message, isMe)
                          : null,
                      onTapFailed: () => _showFailedMenu(message),
                    ),
                  ),
                ],
              ),
            );
          },
        ),
        if (_showJump)
          Positioned(
            right: 16,
            bottom: 16,
            child: GestureDetector(
              onTap: _scrollToBottom,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                decoration: BoxDecoration(
                  color: Theme.of(context).colorScheme.surface,
                  borderRadius: BorderRadius.circular(20),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withValues(alpha: 0.15),
                      blurRadius: 8,
                      offset: const Offset(0, 2),
                    ),
                  ],
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.keyboard_arrow_down, color: AppColors.primaryBlue, size: 20),
                    const SizedBox(width: 4),
                    Text(
                      AppStrings.of(context).text('newMessages'),
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                        color: AppColors.primaryBlue,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
      ],
    );
  }

  Widget _buildDateHeader(DateTime createdAt) {
    return Container(
      margin: const EdgeInsets.symmetric(vertical: 16),
      alignment: Alignment.center,
      child: Text(
        ChatTime.headerLabel(createdAt),
        style: TextStyle(
          color: Colors.grey[600],
          fontSize: 13,
          fontWeight: FontWeight.w500,
        ),
      ),
    );
  }
}
