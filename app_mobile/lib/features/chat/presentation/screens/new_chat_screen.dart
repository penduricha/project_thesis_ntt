import 'dart:async';

import 'package:flutter/material.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/localization/app_strings.dart';
import '../../application/chat_controller.dart';
import '../../data/api/chat_api_exception.dart';
import '../../data/model/chat_admin_brief.dart';
import '../widgets/custom/chat_avatar.dart';
import '../widgets/custom/chat_status_banner.dart';
import 'chat_detail_screen.dart';

/// Chọn người để bắt đầu chat: mở màn là hiện sẵn danh sách admin khác; gõ để lọc theo tên hoặc mã (debounce 300 ms);
/// chọn nhiều người.
///  - Chọn 1 người: "Nhắn tin" mở chat 1-1 (không có tính năng kết bạn).
///  - Chọn từ 2 người trở lên: "Tạo nhóm" hỏi tên nhóm (tổng cả bạn phải từ 3 người).
class NewChatScreen extends StatefulWidget {
  const NewChatScreen({super.key});

  @override
  State<NewChatScreen> createState() => _NewChatScreenState();
}

class _NewChatScreenState extends State<NewChatScreen> {
  static const Duration _debounce = Duration(milliseconds: 300);

  /// Khớp chat.group.min-members (gồm cả người tạo) và chat.group.max-name-length của server.
  static const int _minGroupMembers = 3;
  static const int _maxGroupName = 100;

  final ChatController _chat = ChatController.instance;
  final TextEditingController _search = TextEditingController();

  Timer? _debounceTimer;
  int _searchToken = 0;
  bool _searching = true; // mở màn là tải ngay danh sách admin khác
  String? _searchError;
  List<ChatAdminBrief> _results = const <ChatAdminBrief>[];

  // Giữ thứ tự chọn; khóa là adminId.
  final Map<int, ChatAdminBrief> _selected = <int, ChatAdminBrief>{};
  bool _submitting = false;

  @override
  void initState() {
    super.initState();
    // Từ khóa trống = liệt kê admin khác, không bắt người dùng gõ trước. Hoãn tới sau khung hình đầu vì _runSearch gọi
    // setState (không gọi giữa lúc đang dựng).
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _runSearch('');
    });
  }

  @override
  void dispose() {
    _debounceTimer?.cancel();
    _search.dispose();
    super.dispose();
  }

  void _onQueryChanged(String value) {
    _debounceTimer?.cancel();
    final query = value.trim();
    // Xóa hết chữ thì quay về danh sách đầy đủ (cũng qua debounce để không gọi liên tục khi gõ/xóa nhanh).
    _debounceTimer = Timer(_debounce, () => _runSearch(query));
  }

  Future<void> _runSearch(String query) async {
    final token = ++_searchToken;
    setState(() {
      _searching = true;
      _searchError = null;
    });
    try {
      final found = await _chat.searchAdmins(query);
      if (!mounted || token != _searchToken) return; // đã có lần tìm mới hơn
      setState(() {
        _results = found;
        _searching = false;
      });
    } on ChatApiException catch (e) {
      if (!mounted || token != _searchToken) return;
      setState(() {
        _searching = false;
        _searchError = e.userMessage;
      });
    } catch (_) {
      if (!mounted || token != _searchToken) return;
      setState(() {
        _searching = false;
        _searchError = AppStrings.of(context).text('searchFailed');
      });
    }
  }

  void _toggle(ChatAdminBrief admin) {
    setState(() {
      if (_selected.containsKey(admin.adminId)) {
        _selected.remove(admin.adminId);
      } else {
        _selected[admin.adminId] = admin;
      }
    });
  }

  void _toast(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _submit() async {
    if (_submitting || _selected.isEmpty) return;
    if (_selected.length == 1) {
      await _openDirect(_selected.values.first);
    } else {
      await _createGroup();
    }
  }

  Future<void> _openDirect(ChatAdminBrief peer) async {
    setState(() => _submitting = true);
    try {
      final conversation = await _chat.openDirect(peer.adminId);
      if (!mounted) return;
      _goToConversation(conversation.id);
    } on ChatApiException catch (e) {
      _toast(e.userMessage);
    } catch (_) {
      if (mounted) _toast(AppStrings.of(context).text('openFailed'));
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  Future<void> _createGroup() async {
    // Tổng cả người tạo phải từ _minGroupMembers; nút đã bị tắt nếu chưa đủ, kiểm tra lại cho chắc.
    if (_selected.length + 1 < _minGroupMembers) return;
    final name = await _askGroupName();
    if (name == null || !mounted) return;

    setState(() => _submitting = true);
    try {
      final conversation = await _chat.createGroup(name, _selected.keys.toList());
      if (!mounted) return;
      _goToConversation(conversation.id);
    } on ChatApiException catch (e) {
      _toast(e.userMessage);
    } catch (_) {
      if (mounted) _toast(AppStrings.of(context).text('createGroupFailed'));
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  /// Thay màn này bằng màn chat để nút Back từ chat về thẳng danh sách.
  void _goToConversation(int conversationId) {
    Navigator.pushReplacement(
      context,
      MaterialPageRoute<void>(
        builder: (_) => ChatDetailScreen(conversationId: conversationId),
      ),
    );
  }

  Future<String?> _askGroupName() {
    // Không dispose controller ngay khi hộp thoại đóng: hiệu ứng mờ dần của hộp thoại còn dựng TextField thêm vài
    // trăm mili giây, dispose sớm sẽ gây lỗi "controller used after being disposed". Controller không có listener
    // ngoài nên để thu gom rác là an toàn.
    final controller = TextEditingController();
    return showDialog<String>(
      context: context,
      builder: (dialogContext) {
        return StatefulBuilder(
          builder: (context, setDialogState) {
            final valid = controller.text.trim().isNotEmpty;
            return AlertDialog(
              backgroundColor: Theme.of(context).colorScheme.surface,
              surfaceTintColor: Colors.transparent,
              title: Text(AppStrings.of(context).text('groupName')),
              content: TextField(
                controller: controller,
                autofocus: true,
                maxLength: _maxGroupName,
                textCapitalization: TextCapitalization.sentences,
                onChanged: (_) => setDialogState(() {}),
                decoration: InputDecoration(hintText: AppStrings.of(context).text('enterGroupName')),
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(dialogContext),
                  child: Text(AppStrings.of(context).text('cancel')),
                ),
                TextButton(
                  onPressed: valid ? () => Navigator.pop(dialogContext, controller.text.trim()) : null,
                  child: Text(AppStrings.of(context).text('createGroup')),
                ),
              ],
            );
          },
        );
      },
    );
  }

  // ------------------------------------------------------------------ giao diện

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Theme.of(context).colorScheme.surface,
      appBar: AppBar(
        backgroundColor: Theme.of(context).colorScheme.surface,
        surfaceTintColor: Colors.transparent,
        elevation: 0.5,
        iconTheme: const IconThemeData(color: AppColors.primaryBlue),
        centerTitle: true,
        title: Text(
          AppStrings.of(context).text('newMessage'),
          style: TextStyle(color: Theme.of(context).colorScheme.onSurface, fontSize: 18, fontWeight: FontWeight.w600),
        ),
      ),
      body: Column(
        children: [
          const ChatStatusBanner(),
          _buildSearchField(),
          if (_selected.isNotEmpty) _buildSelectedChips(),
          Expanded(child: _buildResults()),
          _buildActionBar(),
        ],
      ),
    );
  }

  Widget _buildSearchField() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
      child: TextField(
        controller: _search,
        autofocus: true,
        onChanged: _onQueryChanged,
        textInputAction: TextInputAction.search,
        style: const TextStyle(fontSize: 16),
        decoration: InputDecoration(
          hintText: AppStrings.of(context).text('searchAdmin'),
          hintStyle: const TextStyle(color: Colors.grey, fontSize: 16),
          filled: true,
          fillColor: Theme.of(context).colorScheme.surface,
          isDense: true,
          contentPadding: const EdgeInsets.symmetric(vertical: 13),
          prefixIcon: const Icon(Icons.search, color: Colors.grey),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(12),
            borderSide: BorderSide.none,
          ),
        ),
      ),
    );
  }

  Widget _buildSelectedChips() {
    return SizedBox(
      height: 48,
      child: ListView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
        children: [
          for (final admin in _selected.values)
            Padding(
              padding: const EdgeInsets.only(right: 8),
              child: InputChip(
                label: Text(admin.displayName),
                backgroundColor: Theme.of(context).colorScheme.primaryContainer,
                side: BorderSide.none,
                onDeleted: () => _toggle(admin),
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildResults() {
    if (_searchError != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text(
            _searchError!,
            textAlign: TextAlign.center,
            style: const TextStyle(fontSize: 15, color: AppColors.textMuted),
          ),
        ),
      );
    }
    if (_searching && _results.isEmpty) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_results.isEmpty) {
      final hasQuery = _search.text.trim().isNotEmpty;
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text(
            AppStrings.of(context).text(hasQuery ? 'noAdminFound' : 'noOtherAdmin'),
            textAlign: TextAlign.center,
            style: const TextStyle(fontSize: 15, color: AppColors.textMuted),
          ),
        ),
      );
    }
    return ListView.separated(
      keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
      itemCount: _results.length,
      separatorBuilder: (_, __) => const Divider(height: 1, indent: 76, color: AppColors.border),
      itemBuilder: (context, index) {
        final admin = _results[index];
        final selected = _selected.containsKey(admin.adminId);
        return ListTile(
          onTap: () => _toggle(admin),
          leading: ChatAvatar(name: admin.displayName),
          title: Text(admin.displayName, maxLines: 1, overflow: TextOverflow.ellipsis),
          subtitle: Text(
            'Admin ${admin.adminId}',
            style: const TextStyle(fontSize: 12, color: AppColors.textMuted),
          ),
          trailing: Icon(
            selected ? Icons.check_circle : Icons.radio_button_unchecked,
            color: selected ? AppColors.primaryBlue : AppColors.borderStrong,
          ),
        );
      },
    );
  }

  Widget _buildActionBar() {
    final count = _selected.length;
    // 1 người: nhắn tin. Từ 2 người: tạo nhóm, bật khi tổng (cả mình) đủ mức tối thiểu.
    final isGroup = count >= 2;
    final enabled = !_submitting && count >= 1 && (!isGroup || count + 1 >= _minGroupMembers);
    final label = count == 0
        ? AppStrings.of(context).text('selectPerson')
        : (isGroup ? AppStrings.of(context).format('createGroupCount', {'count': count + 1}) : AppStrings.of(context).text('sendMessage'));

    return Container(
      color: Theme.of(context).colorScheme.surface,
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
          child: ElevatedButton(
            onPressed: enabled ? _submit : null,
            style: ElevatedButton.styleFrom(
              minimumSize: const Size.fromHeight(48),
              backgroundColor: AppColors.primaryBlue,
              foregroundColor: Colors.white,
              disabledBackgroundColor: AppColors.chatFieldFill,
              disabledForegroundColor: AppColors.textInactive,
              elevation: 0,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
              textStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
            ),
            child: _submitting
                ? const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                  )
                : Text(label),
          ),
        ),
      ),
    );
  }
}
