import 'package:flutter/material.dart';

import '../widgets/modals_options_chat/modal_detail_reaction_box_chat.dart';
import '../widgets/modals_options_chat/modal_options_box_chat.dart';
import '../widgets/modals_options_chat/modal_icons_reaction.dart';

class ChatMessage {
  final String id;
  final String text;
  final bool isMe;
  final DateTime timestamp;
  List<ReactionUser> reactions;

  ChatMessage({
    required this.id,
    required this.text,
    required this.isMe,
    required this.timestamp,
    List<ReactionUser>? reactions,
  }) : reactions = reactions ?? [];
}

class ChatDetailScreen extends StatefulWidget {
  final String senderName;
  const ChatDetailScreen({super.key, required this.senderName});

  @override
  State<ChatDetailScreen> createState() => _ChatDetailScreenState();
}

class _ChatDetailScreenState extends State<ChatDetailScreen> {
  final String currentUserId = '1'; // Giả lập ID tài khoản hiện tại là 1

  final TextEditingController _messageController = TextEditingController();
  final ScrollController _scrollController = ScrollController();
  final GlobalKey<AnimatedListState> _listKey = GlobalKey<AnimatedListState>();

  // Biến quản lý trạng thái hiển thị nút cuộn xuống
  bool _showScrollToBottom = false;

  static DateTime _getHanoiNow() {
    return DateTime.now().toUtc().add(const Duration(hours: 7));
  }

  late final List<ChatMessage> _messages;

  @override
  void initState() {
    super.initState();
    final hanoiNow = _getHanoiNow();
    _messages = [
      ChatMessage(
        id: '1',
        text:
        'Your verification code is: 849201. Do not share this code with anyone for security reasons.',
        isMe: false,
        timestamp: hanoiNow.subtract(const Duration(days: 2)),
        reactions: [
          ReactionUser(
            userId: '2',
            userName: widget.senderName,
            avatarUrl: 'https://i.pravatar.cc/150?img=1',
            emoji: '👍',
          ),
        ],
      ),
      ChatMessage(
        id: '2',
        text: 'Hello, this is yesterday message.',
        isMe: false,
        timestamp: hanoiNow.subtract(const Duration(hours: 26)),
        reactions: [
          ReactionUser(
            userId: currentUserId,
            userName: 'Tôi',
            avatarUrl: 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcR7va_1LkI6WgHBaCHixrXsAbCZndHfZTdaXhhkSM_G3g&s=10',
            emoji: '❤️',
          ),
        ],
      ),
    ];

    // Lắng nghe sự kiện cuộn để bật/tắt nút cuộn xuống
    _scrollController.addListener(_scrollListener);
  }

  @override
  void dispose() {
    _scrollController.removeListener(_scrollListener);
    _messageController.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  void _scrollListener() {
    if (!_scrollController.hasClients) return;

    // Tính khoảng cách từ vị trí hiện tại đến đáy danh sách
    final double distanceToBottom =
        _scrollController.position.maxScrollExtent - _scrollController.offset;

    // Ngưỡng 300px tương đương khoảng 3 lần cuộn ngón tay lên
    const double scrollThreshold = 500.0;

    if (distanceToBottom > scrollThreshold) {
      if (!_showScrollToBottom) {
        setState(() {
          _showScrollToBottom = true;
        });
      }
    } else {
      if (_showScrollToBottom) {
        setState(() {
          _showScrollToBottom = false;
        });
      }
    }
  }

  String _trimLeadingTrailing(String text) {
    return text.trim();
  }

  String _formatTime(DateTime? dateTime) {
    if (dateTime == null) return '';
    final String hour = dateTime.hour.toString().padLeft(2, '0');
    final String minute = dateTime.minute.toString().padLeft(2, '0');
    return '$hour:$minute';
  }

  String _formatDateHeader(DateTime timestamp) {
    final now = _getHanoiNow();
    final String timeStr = _formatTime(timestamp);

    final DateTime todayDate = DateTime(now.year, now.month, now.day);
    final DateTime messageDate =
    DateTime(timestamp.year, timestamp.month, timestamp.day);
    final int differenceInDays = todayDate.difference(messageDate).inDays;

    if (differenceInDays == 0) {
      return '$timeStr, Today';
    } else if (differenceInDays == 1) {
      return '$timeStr, Yesterday';
    } else {
      return '$timeStr, ${timestamp.day}/${timestamp.month}/${timestamp.year}';
    }
  }

  bool _shouldShowDateHeader(int index) {
    if (index == 0) return true;

    final currentTimestamp = _messages[index].timestamp;
    final previousTimestamp = _messages[index - 1].timestamp;

    final difference = currentTimestamp.difference(previousTimestamp).abs();
    return difference.inHours >= 20;
  }

  double _calculateBubbleHeight(String text, double maxContentWidth) {
    final textPainter = TextPainter(
      text: TextSpan(
        text: text,
        style: const TextStyle(fontSize: 15),
      ),
      textDirection: TextDirection.ltr,
      maxLines: null,
    )..layout(maxWidth: maxContentWidth);

    double contentHeight = textPainter.size.height;
    const double verticalPadding = 16.0;
    const double spacing = 2.0;
    const double timestampHeight = 12.0;

    return contentHeight + verticalPadding + spacing + timestampHeight;
  }

  List<String> _takeFitChunk(
      String remainingText, double maxContentWidth, double maxHeightThreshold) {
    List<String> words = remainingText.split(' ');

    if (_calculateBubbleHeight(remainingText, maxContentWidth) <=
        maxHeightThreshold) {
      return [remainingText, ""];
    }

    int low = 1;
    int high = words.length;
    int bestFitIndex = 1;

    while (low <= high) {
      int mid = (low + high) ~/ 2;
      String testChunk = words.sublist(0, mid).join(' ');

      if (_calculateBubbleHeight(testChunk, maxContentWidth) <=
          maxHeightThreshold) {
        bestFitIndex = mid;
        low = mid + 1;
      } else {
        high = mid - 1;
      }
    }

    String fitPart = words.sublist(0, bestFitIndex).join(' ');
    String restPart = words.sublist(bestFitIndex).join(' ');

    return [fitPart, restPart];
  }

  List<String> _splitMessageIfNeeded(
      String originalText, double maxContentWidth, double maxHeightThreshold) {
    if (_calculateBubbleHeight(originalText, maxContentWidth) <=
        maxHeightThreshold) {
      return [originalText];
    }

    List<String> resultChunks = [];
    String currentText = originalText;

    while (currentText.isNotEmpty) {
      List<String> splitResult =
      _takeFitChunk(currentText, maxContentWidth, maxHeightThreshold);
      resultChunks.add(splitResult[0]);
      currentText = splitResult[1].trim();
    }

    return resultChunks;
  }

  void _handleSendMessage() {
    String rawText = _messageController.text;
    if (rawText.trim().isEmpty) return;

    String trimmedText = _trimLeadingTrailing(rawText);

    double screenHeight = MediaQuery.of(context).size.height;
    double maxHeightThreshold = screenHeight * 0.66;
    double maxBubbleWidth = MediaQuery.of(context).size.width * 0.75;
    double maxContentWidth = maxBubbleWidth - 24.0;

    List<String> parts = _splitMessageIfNeeded(
        trimmedText, maxContentWidth, maxHeightThreshold);

    _messageController.clear();
    setState(() {});

    final now = _getHanoiNow();

    for (int i = 0; i < parts.length; i++) {
      Future.delayed(Duration(milliseconds: i * 200), () {
        final newMessage = ChatMessage(
          id: '${now.millisecondsSinceEpoch}_$i',
          text: parts[i],
          isMe: true,
          timestamp: now,
        );

        _messages.add(newMessage);
        _listKey.currentState?.insertItem(
          _messages.length - 1,
          duration: const Duration(milliseconds: 350),
        );

        _scrollToBottom();
      });
    }
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollController.hasClients) {
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeOut,
        );
      }
    });
  }

  // Hiển thị Overlay Popup khi giữ tin nhắn
  void _showContextMenu(ChatMessage message, BuildContext itemContext) {
    final renderBox = itemContext.findRenderObject() as RenderBox;
    final offset = renderBox.localToGlobal(Offset.zero);
    final size = renderBox.size;

    final screenHeight = MediaQuery.of(context).size.height;
    final screenWidth = MediaQuery.of(context).size.width;

    bool isBottomOverflow = offset.dy + size.height + 260 > screenHeight;
    double topPosition = isBottomOverflow
        ? (screenHeight - 320).clamp(20.0, screenHeight)
        : offset.dy;

    showGeneralDialog(
      context: context,
      barrierDismissible: true,
      barrierLabel: 'Dismiss',
      barrierColor: const Color(0x99000000),
      transitionDuration: const Duration(milliseconds: 150),
      pageBuilder: (context, animation, secondaryAnimation) {
        return Material(
          color: Colors.transparent,
          child: Stack(
            children: [
              GestureDetector(
                onTap: () => Navigator.pop(context),
                behavior: HitTestBehavior.opaque,
                child: const SizedBox.expand(),
              ),
              Positioned(
                top: topPosition,
                left: message.isMe ? null : offset.dx,
                right: message.isMe ? (screenWidth - offset.dx - size.width) : null,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: message.isMe
                      ? CrossAxisAlignment.end
                      : CrossAxisAlignment.start,
                  children: [
                    Container(
                      width: size.width,
                      padding:
                      const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                      decoration: BoxDecoration(
                        color: message.isMe
                            ? const Color(0xFF007AFF)
                            : const Color(0xFFE5E5EA),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(
                            message.text,
                            style: TextStyle(
                              fontSize: 15,
                              color: message.isMe ? Colors.white : Colors.black87,
                            ),
                          ),
                          const SizedBox(height: 2),
                          Row(
                            mainAxisAlignment: message.isMe
                                ? MainAxisAlignment.end
                                : MainAxisAlignment.start,
                            mainAxisSize: MainAxisSize.max,
                            children: [
                              Text(
                                _formatTime(message.timestamp),
                                style: TextStyle(
                                  fontSize: 10,
                                  color: message.isMe
                                      ? Colors.white54
                                      : Colors.black54,
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 8),

                    // Modal Reactions
                    ModalIconsReaction(
                      onReactionSelected: (selectedReaction) {
                        setState(() {
                          message.reactions
                              .removeWhere((r) => r.userId == currentUserId);
                          message.reactions.add(
                            ReactionUser(
                              userId: currentUserId,
                              userName: 'Tôi',
                              avatarUrl: 'https://i.pravatar.cc/150?img=5',
                              emoji: selectedReaction.value,
                            ),
                          );
                        });
                        Navigator.pop(context);
                      },
                    ),
                    const SizedBox(height: 8),

                    // Modal Options
                    ModalIconOptionsChat(
                      onReply: () => Navigator.pop(context),
                      onForward: () => Navigator.pop(context),
                      onCopy: () => Navigator.pop(context),
                      onDelete: () {
                        Navigator.pop(context);
                        setState(() {
                          int index = _messages.indexOf(message);
                          if (index != -1) {
                            final removedItem = _messages.removeAt(index);
                            _listKey.currentState?.removeItem(
                              index,
                                  (context, animation) => SizeTransition(
                                sizeFactor: animation,
                                child: _buildChatBubble(removedItem),
                              ),
                            );
                          }
                        });
                      },
                    ),
                  ],
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  // Hiển thị Bottom Sheet kéo lên xem chi tiết reactions
  void _showReactionDetailModal(ChatMessage message) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) {
        return ModalDetailReactionBoxChat(
          reactions: message.reactions,
          currentUserId: currentUserId,
          onRemoveReaction: () {
            setState(() {
              message.reactions.removeWhere((r) => r.userId == currentUserId);
            });
          },
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0.5,
        iconTheme: const IconThemeData(color: Color(0xFF007AFF)),
        title: Column(
          children: [
            const SizedBox(height: 2),
            Text(
              widget.senderName,
              style: const TextStyle(
                color: Colors.black,
                fontSize: 18,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
        centerTitle: true,
        actions: [
          IconButton(
            icon: const Icon(Icons.info_outline, color: Colors.blue),
            onPressed: () {},
          ),
        ],
      ),
      body: Column(
        children: [
          Expanded(
            child: Stack(
              children: [
                // 1. Danh sách tin nhắn chính
                AnimatedList(
                  key: _listKey,
                  controller: _scrollController,
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                  initialItemCount: _messages.length,
                  itemBuilder: (context, index, animation) {
                    final message = _messages[index];
                    final showHeader = _shouldShowDateHeader(index);

                    final slideAnimation = Tween<Offset>(
                      begin: const Offset(0, 0.4),
                      end: Offset.zero,
                    ).animate(
                      CurvedAnimation(
                        parent: animation,
                        curve: Curves.easeOutBack,
                      ),
                    );

                    return SlideTransition(
                      position: slideAnimation,
                      child: FadeTransition(
                        opacity: animation,
                        child: Column(
                          children: [
                            if (showHeader) _buildDateHeader(message.timestamp),
                            Padding(
                              padding: const EdgeInsets.only(bottom: 12.0),
                              child: _buildChatBubble(message),
                            ),
                          ],
                        ),
                      ),
                    );
                  },
                ),

                // 2. Nút tròn chứa icon mũi tên cuộn xuống khi người dùng scroll lên
                if (_showScrollToBottom)
                  Positioned(
                    right: 16,
                    bottom: 16,
                    child: AnimatedOpacity(
                      duration: const Duration(milliseconds: 200),
                      opacity: _showScrollToBottom ? 1.0 : 0.0,
                      child: GestureDetector(
                        onTap: _scrollToBottom,
                        child: Container(
                          width: 40,
                          height: 40,
                          decoration: BoxDecoration(
                            color: Colors.white,
                            shape: BoxShape.circle,
                            boxShadow: [
                              BoxShadow(
                                color: Colors.black.withOpacity(0.15),
                                blurRadius: 8,
                                offset: const Offset(0, 2),
                              ),
                            ],
                          ),
                          child: const Icon(
                            Icons.keyboard_arrow_down,
                            color: Color(0xFF007AFF),
                            size: 26,
                          ),
                        ),
                      ),
                    ),
                  ),
              ],
            ),
          ),
          _buildInputArea(),
        ],
      ),
    );
  }

  Widget _buildDateHeader(DateTime timestamp) {
    return Container(
      margin: const EdgeInsets.symmetric(vertical: 16.0),
      alignment: Alignment.center,
      child: Text(
        _formatDateHeader(timestamp),
        style: TextStyle(
          color: Colors.grey[600],
          fontSize: 13,
          fontWeight: FontWeight.w500,
        ),
      ),
    );
  }

  Widget _buildChatBubble(ChatMessage message) {
    return Align(
      alignment: message.isMe ? Alignment.centerRight : Alignment.centerLeft,
      child: Builder(
        builder: (bubbleContext) {
          bool isLongPressHandled = false;

          return GestureDetector(
            onTapDown: (_) {
              isLongPressHandled = false;
              Future.delayed(const Duration(milliseconds: 800), () {
                if (!isLongPressHandled) {
                  isLongPressHandled = true;
                  _showContextMenu(message, bubbleContext);
                }
              });
            },
            onTapUp: (_) => isLongPressHandled = true,
            onTapCancel: () => isLongPressHandled = true,
            child: Stack(
              clipBehavior: Clip.none,
              children: [
                Container(
                  constraints: BoxConstraints(
                    maxWidth: MediaQuery.of(context).size.width * 0.75,
                  ),
                  padding:
                  const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  decoration: BoxDecoration(
                    color: message.isMe
                        ? const Color(0xFF007AFF)
                        : const Color(0xFFE5E5EA),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: IntrinsicWidth(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          message.text,
                          style: TextStyle(
                            fontSize: 15,
                            color: message.isMe ? Colors.white : Colors.black87,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Row(
                          mainAxisAlignment: message.isMe
                              ? MainAxisAlignment.end
                              : MainAxisAlignment.start,
                          mainAxisSize: MainAxisSize.max,
                          children: [
                            Text(
                              _formatTime(message.timestamp),
                              style: TextStyle(
                                fontSize: 10,
                                color: message.isMe
                                    ? Colors.white54
                                    : Colors.black54,
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),

                // Icon Reaction hiển thị trên tin nhắn
                if (message.reactions.isNotEmpty)
                  Positioned(
                    bottom: -8,
                    right: message.isMe ? null : -5,
                    left: message.isMe ? -5 : null,
                    child: GestureDetector(
                      onTap: () => _showReactionDetailModal(message),
                      behavior: HitTestBehavior.opaque,
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(12),
                          boxShadow: const [
                            BoxShadow(
                              color: Colors.black12,
                              blurRadius: 4,
                              offset: Offset(0, 2),
                            )
                          ],
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          crossAxisAlignment: CrossAxisAlignment.center,
                          children: [
                            Text(
                              message.reactions.last.emoji,
                              style: const TextStyle(fontSize: 12),
                            ),
                            if (message.reactions.length > 1) ...[
                              const SizedBox(width: 3),
                              Text(
                                '${message.reactions.length}',
                                style: const TextStyle(
                                  fontSize: 11,
                                  fontWeight: FontWeight.bold,
                                  color: Colors.black87,
                                ),
                              ),
                            ],
                          ],
                        ),
                      ),
                    ),
                  ),
              ],
            ),
          );
        },
      ),
    );
  }

  Widget _buildInputArea() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      color: Colors.white,
      child: SafeArea(
        child: Row(
          children: [
            Container(
              decoration: const BoxDecoration(
                color: Color(0xFFEFEFF0),
                shape: BoxShape.circle,
              ),
              child: IconButton(
                icon: const Icon(Icons.add, color: Color(0xFF007AFF)),
                onPressed: () {},
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 14),
                decoration: BoxDecoration(
                  color: const Color(0xFFEFEFF0),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: const Color(0xFFD1D1D6)),
                ),
                child: TextField(
                  controller: _messageController,
                  onChanged: (_) => setState(() {}),
                  onSubmitted: (_) => _handleSendMessage(),
                  decoration: const InputDecoration(
                    hintText: 'iMessage',
                    border: InputBorder.none,
                  ),
                ),
              ),
            ),
            const SizedBox(width: 8),
            ValueListenableBuilder<TextEditingValue>(
              valueListenable: _messageController,
              builder: (context, value, child) {
                final hasText = value.text.trim().isNotEmpty;
                return Container(
                  decoration: BoxDecoration(
                    color:
                    hasText ? const Color(0xFF007AFF) : Colors.grey[300],
                    shape: BoxShape.circle,
                  ),
                  child: IconButton(
                    icon: const Icon(
                      Icons.arrow_upward,
                      color: Colors.white,
                      size: 22,
                    ),
                    onPressed: hasText ? _handleSendMessage : null,
                  ),
                );
              },
            ),
          ],
        ),
      ),
    );
  }
}