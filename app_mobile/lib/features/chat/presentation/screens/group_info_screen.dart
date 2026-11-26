import 'package:flutter/material.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/localization/app_strings.dart';
import '../../application/chat_controller.dart';
import '../../data/api/chat_api_exception.dart';
import '../../data/model/chat_conversation.dart';
import '../../data/model/chat_member.dart';
import '../widgets/custom/chat_avatar.dart';
import '../widgets/custom/chat_status_banner.dart';

/// Thông tin nhóm: ai cũng xem được danh sách thành viên. Chỉ chủ nhóm thấy "Kick" ở từng thành viên (trừ chính mình)
/// và "Giải tán nhóm", cả hai đều có hộp thoại xác nhận. Server vẫn kiểm tra lại mọi quyền.
class GroupInfoScreen extends StatefulWidget {
  const GroupInfoScreen({super.key, required this.conversationId});

  final int conversationId;

  @override
  State<GroupInfoScreen> createState() => _GroupInfoScreenState();
}

class _GroupInfoScreenState extends State<GroupInfoScreen> {
  /// Nhóm phải còn ít nhất bấy nhiêu người sau khi kick (khớp chat.group.min-members của server).
  static const int _minMembers = 3;

  final ChatController _chat = ChatController.instance;

  bool _loadingDetail = true;
  bool _busy = false;
  int? _kickingId;

  int get _id => widget.conversationId;

  @override
  void initState() {
    super.initState();
    // Hoãn tới sau khung hình đầu: tải chi tiết sẽ gọi notifyListeners, không được gọi giữa lúc đang build.
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadDetail());
  }

  Future<void> _loadDetail() async {
    try {
      await _chat.loadConversationDetail(_id);
    } on ChatApiException catch (e) {
      _toast(e.userMessage);
    } catch (_) {
      if (mounted) _toast(AppStrings.of(context).text('groupLoadFailed'));
    } finally {
      if (mounted) setState(() => _loadingDetail = false);
    }
  }

  void _toast(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  Future<bool> _confirm({
    required String title,
    required String message,
    required String actionLabel,
  }) async {
    final result = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: Theme.of(context).colorScheme.surface,
        surfaceTintColor: Colors.transparent,
        title: Text(title),
        content: Text(message),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: Text(AppStrings.of(context).text('cancel')),
          ),
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: Text(actionLabel, style: const TextStyle(color: AppColors.primaryRed)),
          ),
        ],
      ),
    );
    return result == true;
  }

  Future<void> _kick(ChatMember member) async {
    final confirmed = await _confirm(
      title: AppStrings.of(context).text('removeMemberTitle'),
      message: AppStrings.of(context).format('removeMemberDesc', {'name': member.displayName}),
      actionLabel: AppStrings.of(context).text('removeMember'),
    );
    if (!confirmed || !mounted) return;
    setState(() {
      _busy = true;
      _kickingId = member.adminId;
    });
    try {
      await _chat.kickMember(_id, member.adminId);
      if (mounted) _toast(AppStrings.of(context).format('memberRemoved', {'name': member.displayName}));
    } on ChatApiException catch (e) {
      _toast(e.userMessage);
      // Có thể danh sách thành viên đã đổi (người khác bị kick trước): tải lại cho đúng.
      if (!e.isNotFound && !e.isNetwork) _loadDetail();
    } catch (_) {
      if (mounted) _toast(AppStrings.of(context).text('removeMemberFailed'));
    } finally {
      if (mounted) {
        setState(() {
          _busy = false;
          _kickingId = null;
        });
      }
    }
  }

  Future<void> _dissolve(ChatConversation conversation) async {
    final confirmed = await _confirm(
      title: AppStrings.of(context).text('dissolveTitle'),
      message: AppStrings.of(context).format('dissolveDesc', {'name': conversation.displayName}),
      actionLabel: AppStrings.of(context).text('dissolve'),
    );
    if (!confirmed || !mounted) return;
    setState(() => _busy = true);
    // Lấy sẵn trước khi chờ mạng: sau khi giải tán màn này có thể không còn trong cây.
    final messenger = ScaffoldMessenger.of(context);
    final doneMessage = AppStrings.of(context).text('dissolved');
    final navigator = Navigator.of(context);
    try {
      await _chat.dissolveGroup(_id);
      navigator.popUntil((route) => route.isFirst);
      messenger.showSnackBar(SnackBar(content: Text(doneMessage)));
    } on ChatApiException catch (e) {
      _toast(e.userMessage);
    } catch (_) {
      if (mounted) _toast(AppStrings.of(context).text('dissolveFailed'));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: _chat,
      builder: (context, _) {
        final conversation = _chat.conversationById(_id);
        return Scaffold(
          backgroundColor: Theme.of(context).colorScheme.surface,
          appBar: AppBar(
            backgroundColor: Theme.of(context).colorScheme.surface,
            surfaceTintColor: Colors.transparent,
            elevation: 0.5,
            iconTheme: const IconThemeData(color: AppColors.primaryBlue),
            centerTitle: true,
            title: Text(
              AppStrings.of(context).text('groupInfo'),
              style: TextStyle(color: Theme.of(context).colorScheme.onSurface, fontSize: 18, fontWeight: FontWeight.w600),
            ),
          ),
          body: Column(
            children: [
              const ChatStatusBanner(),
              Expanded(
                child: conversation == null
                    ? const Center(child: CircularProgressIndicator())
                    : _buildContent(conversation),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildContent(ChatConversation conversation) {
    final myId = _chat.myId;
    final isOwner = conversation.isOwnedBy(myId);
    final members = conversation.members;
    // Kick bị tắt khi nhóm chỉ còn đúng số tối thiểu: sau khi kick sẽ dưới mức cho phép.
    final canKick = isOwner && conversation.memberCount > _minMembers;

    return ListView(
      padding: EdgeInsets.zero,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 24, 16, 16),
          child: Column(
            children: [
              ChatAvatar(name: conversation.displayName, radius: 36),
              const SizedBox(height: 12),
              Text(
                conversation.displayName,
                textAlign: TextAlign.center,
                style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 4),
              Text(
                AppStrings.of(context).format('memberCount', {'count': conversation.memberCount}),
                style: const TextStyle(fontSize: 14, color: AppColors.textMuted),
              ),
            ],
          ),
        ),
        const Divider(height: 1, color: AppColors.border),
        if (isOwner && !canKick)
          Container(
            width: double.infinity,
            color: Theme.of(context).scaffoldBackgroundColor,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
            child: Text(
              AppStrings.of(context).format('minMembers', {'count': _minMembers}),
              style: TextStyle(fontSize: 13, color: AppColors.textMuted),
            ),
          ),
        if (_loadingDetail && members.isEmpty)
          const Padding(
            padding: EdgeInsets.all(24),
            child: Center(child: CircularProgressIndicator()),
          ),
        for (final member in members)
          _buildMemberTile(member, isMe: member.adminId == myId, showKick: isOwner, canKick: canKick),
        if (isOwner) ...[
          const SizedBox(height: 24),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: OutlinedButton.icon(
              onPressed: _busy ? null : () => _dissolve(conversation),
              icon: const Icon(Icons.delete_forever_outlined),
              label: Text(AppStrings.of(context).text('dissolveGroup')),
              style: OutlinedButton.styleFrom(
                minimumSize: const Size.fromHeight(48),
                foregroundColor: AppColors.primaryRed,
                side: const BorderSide(color: AppColors.primaryRed),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
              ),
            ),
          ),
        ],
        const SizedBox(height: 32),
      ],
    );
  }

  Widget _buildMemberTile(
    ChatMember member, {
    required bool isMe,
    required bool showKick,
    required bool canKick,
  }) {
    final isKicking = _kickingId == member.adminId;
    return ListTile(
      leading: ChatAvatar(name: member.displayName),
      title: Text(
        isMe ? AppStrings.of(context).format('youSuffix', {'name': member.displayName}) : member.displayName,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      subtitle: Text(
        member.isOwner ? AppStrings.of(context).text('groupOwner') : 'Admin ${member.adminId}',
        style: const TextStyle(fontSize: 12, color: AppColors.textMuted),
      ),
      // Chủ nhóm không tự kick mình và không kick được chính mình hay chủ nhóm.
      trailing: showKick && !isMe && !member.isOwner
          ? (isKicking
              ? const SizedBox(
                  width: 20,
                  height: 20,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : TextButton(
                  onPressed: (!canKick || _busy) ? null : () => _kick(member),
                  child: Text(AppStrings.of(context).text('removeMember'), style: const TextStyle(color: AppColors.primaryRed)),
                ))
          : null,
    );
  }
}
