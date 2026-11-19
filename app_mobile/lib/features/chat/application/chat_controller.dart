import 'dart:async';
import 'dart:collection';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';

import '../../../core/storage/auth_storage.dart';
import '../../../core/utils/uuid_v4.dart';
import '../data/api/chat_api_exception.dart';
import '../data/api/chat_api_service.dart';
import '../data/model/chat_admin_brief.dart';
import '../data/model/chat_conversation.dart';
import '../data/model/chat_event.dart';
import '../data/model/chat_message.dart';
import '../data/socket/chat_socket_service.dart';

/// Loại thông báo một lần mà màn hình cần biết (hiện snackbar, thoát màn).
enum ChatNoticeKind {
  /// Thông tin thường.
  info,

  /// Lỗi cần báo người dùng.
  error,

  /// Cuộc trò chuyện đang mở không còn (bị giải tán hoặc bị kick, không rõ lý do).
  conversationGone,

  /// Mình bị kick khỏi nhóm.
  removedFromGroup,

  /// Nhóm bị giải tán.
  groupDissolved,
}

class ChatNotice {
  const ChatNotice(this.kind, this.message, {this.conversationId});

  final ChatNoticeKind kind;
  final String message;
  final int? conversationId;
}

/// Trạng thái chat dùng chung toàn ứng dụng (singleton). Là chỗ DUY NHẤT giữ danh sách cuộc trò chuyện, tin theo
/// từng cuộc và nhận sự kiện WebSocket, nên tin đến lúc người dùng đang ở tab khác vẫn được xử lý.
/// Màn hình chỉ đọc trạng thái (ListenableBuilder) và gọi hành động.
///
/// Nguyên tắc: REST là nguồn sự thật, WebSocket chỉ để báo. Mỗi lần nối WebSocket xong (nhận "ready") controller
/// kéo bù: tải lại danh sách cuộc và cửa sổ tin mới nhất của cuộc đang mở rồi đối chiếu (mục 11 của thiết kế).
class ChatController extends ChangeNotifier {
  ChatController({ChatApiService? api, ChatSocketService? socket}) {
    _api = api ?? ChatApiService();
    _socket = socket ?? ChatSocketService(api: _api);
  }

  /// Bản dùng chung của ứng dụng.
  static final ChatController instance = ChatController();

  late final ChatApiService _api;
  late final ChatSocketService _socket;

  static const int _pageSize = 30;
  static const Duration _reconnectBannerDelay = Duration(seconds: 3);
  static const Duration _markReadDebounce = Duration(milliseconds: 500);
  static const Duration _listRefreshDebounce = Duration(milliseconds: 300);

  // ------------------------------------------------------------------ trạng thái

  bool _running = false;
  int? _myId;
  String _myName = '';

  List<ChatConversation> _conversations = const <ChatConversation>[];
  final Map<int, List<ChatMessage>> _messages = <int, List<ChatMessage>>{};
  final Map<int, bool> _hasMore = <int, bool>{};
  final Set<int> _loadingOlder = <int>{};
  final Set<int> _loadingLatest = <int>{};
  final Set<int> _deleting = <int>{};
  final Set<int> _loadFailed = <int>{};

  /// Nhóm mà chính máy này đang giải tán: bỏ qua thông báo "nhóm đã bị giải tán" vì chính mình bấm.
  final Set<int> _selfDissolving = <int>{};

  /// id các tin vừa bị xóa (từ sự kiện). Dùng để một trang REST chụp trước lúc xóa không làm tin hiện lại.
  final Set<int> _recentlyDeleted = <int>{};

  bool _listLoading = false;
  bool _listLoaded = false;
  String? _listError;

  /// Số lần tải danh sách đang chạy, và các cuộc được thêm vào máy này TRONG lúc đó (vừa tạo nhóm, vừa mở chat 1-1,
  /// sự kiện conversation.created). Một lần tải bắt đầu trước khi cuộc đó có trên server sẽ không có nó trong kết quả,
  /// nên không được coi là "đã bị giải tán" và gỡ đi.
  int _listRefreshesInFlight = 0;
  final Set<int> _addedDuringRefresh = <int>{};

  int? _openId;
  bool _atBottom = true;
  bool _appResumed = true;

  bool _showReconnecting = false;
  bool _authLost = false;

  final StreamController<ChatNotice> _notices = StreamController<ChatNotice>.broadcast();

  StreamSubscription<ChatEvent>? _eventSubscription;
  AppLifecycleListener? _lifecycle;
  Future<void> _eventQueue = Future<void>.value();
  Timer? _bannerTimer;
  Timer? _markReadTimer;
  Timer? _listRefreshTimer;
  final Set<int> _markReadPending = <int>{};

  // ------------------------------------------------------------------ đọc trạng thái

  bool get isRunning => _running;

  /// adminId của người đang đăng nhập (null khi chưa start).
  int? get myId => _myId;

  /// Mọi cuộc đang giữ, mới hoạt động nhất đứng đầu. Gồm cả cuộc 1-1 chưa có tin (cuộc vừa mở, chưa nhắn).
  List<ChatConversation> get conversations => UnmodifiableListView<ChatConversation>(_conversations);

  /// Danh sách hiển thị ở tab Chat: nhóm luôn hiện, cuộc 1-1 chỉ hiện khi đã có ít nhất một tin.
  List<ChatConversation> get visibleConversations => [
        for (final c in _conversations)
          if (c.isGroup || c.lastMessage != null) c,
      ];

  bool get listLoading => _listLoading;
  bool get listLoaded => _listLoaded;
  String? get listError => _listError;

  /// true sau khi mất kết nối quá 3 giây (không nhấp nháy với đứt ngắn).
  bool get showReconnecting => _showReconnecting;

  /// true khi access token bị từ chối dù đã thử làm mới: cần đăng nhập lại.
  bool get authLost => _authLost;

  Stream<ChatNotice> get notices => _notices.stream;

  int get totalUnread => _conversations.fold<int>(0, (sum, c) => sum + c.unreadCount);

  ChatConversation? conversationById(int id) {
    final index = _indexOf(id);
    return index < 0 ? null : _conversations[index];
  }

  /// Tin của một cuộc, cũ nhất đứng đầu: tin server theo id, rồi tin đang gửi / gửi lỗi ở cuối.
  List<ChatMessage> messagesOf(int conversationId) =>
      UnmodifiableListView<ChatMessage>(_messages[conversationId] ?? const <ChatMessage>[]);

  bool hasMore(int conversationId) => _hasMore[conversationId] ?? false;
  bool isLoadingOlder(int conversationId) => _loadingOlder.contains(conversationId);
  bool isLoadingLatest(int conversationId) => _loadingLatest.contains(conversationId);
  bool isDeleting(int messageId) => _deleting.contains(messageId);

  /// Lần tải cửa sổ tin mới nhất của cuộc này vừa thất bại (màn hình hiện nút thử lại).
  bool loadFailed(int conversationId) => _loadFailed.contains(conversationId);

  // ------------------------------------------------------------------ vòng đời

  /// Gọi sau khi đăng nhập thành công hoặc khi mở app mà đã có phiên. Gọi lại nhiều lần an toàn.
  Future<void> start() async {
    final session = AuthStorage.cachedSession ?? await AuthStorage().readSession();
    if (session == null) return;
    if (_running && _myId == session.adminId) return;
    if (_running) await stop(); // đổi tài khoản trên cùng máy: bỏ sạch trạng thái của người trước

    _running = true;
    _myId = session.adminId;
    _myName = (session.adminName ?? '').trim().isEmpty ? 'Tôi' : session.adminName!.trim();
    _authLost = false;
    _appResumed = true;

    _eventSubscription = _socket.events.listen(_enqueue);
    _socket.status.addListener(_onSocketStatus);
    _socket.onSessionRevoked = _onSessionRevoked;
    _socket.onUnauthorized = _onUnauthorized;
    _lifecycle = AppLifecycleListener(onPause: _onAppPaused, onResume: _onAppResumed);

    _socket.start();
    // Tải ngay một lần để danh sách không trống khi WebSocket chưa nối được (hoặc Redis ngừng). Phần chồng lấn với
    // lần kéo bù sau "ready" được khử trùng, nên gọi hai lần là an toàn.
    unawaited(refreshConversations());
    notifyListeners();
  }

  /// Gọi khi đăng xuất: đóng WebSocket (mã 1000) và xóa sạch trạng thái trong bộ nhớ.
  Future<void> stop() async {
    _running = false;
    await _eventSubscription?.cancel();
    _eventSubscription = null;
    _socket.status.removeListener(_onSocketStatus);
    _socket.onSessionRevoked = null;
    _socket.onUnauthorized = null;
    await _socket.stop();
    _lifecycle?.dispose();
    _lifecycle = null;
    _bannerTimer?.cancel();
    _markReadTimer?.cancel();
    _listRefreshTimer?.cancel();
    _bannerTimer = null;
    _markReadTimer = null;
    _listRefreshTimer = null;
    _markReadPending.clear();
    _eventQueue = Future<void>.value();

    _myId = null;
    _myName = '';
    _conversations = const <ChatConversation>[];
    _messages.clear();
    _hasMore.clear();
    _loadingOlder.clear();
    _loadingLatest.clear();
    _deleting.clear();
    _loadFailed.clear();
    _selfDissolving.clear();
    _addedDuringRefresh.clear();
    _listRefreshesInFlight = 0;
    _recentlyDeleted.clear();
    _listLoading = false;
    _listLoaded = false;
    _listError = null;
    _openId = null;
    _atBottom = true;
    _showReconnecting = false;
    _authLost = false;
    notifyListeners();
  }

  void _onAppPaused() {
    _appResumed = false;
    _socket.pause();
  }

  void _onAppResumed() {
    _appResumed = true;
    _socket.resume(); // nối ngay; nhận "ready" xong sẽ kéo bù
    final open = _openId;
    if (open != null) _maybeMarkRead(open);
  }

  void _onSocketStatus() {
    final status = _socket.status.value;
    if (status == ChatSocketStatus.connected) {
      _bannerTimer?.cancel();
      _bannerTimer = null;
      if (_showReconnecting) {
        _showReconnecting = false;
        notifyListeners();
      }
      return;
    }
    if (!_running || status == ChatSocketStatus.stopped) {
      _bannerTimer?.cancel();
      _bannerTimer = null;
      return;
    }
    // Chưa nối được: chỉ hiện dải "Đang kết nối lại..." khi mất quá 3 giây.
    _bannerTimer ??= Timer(_reconnectBannerDelay, () {
      _bannerTimer = null;
      if (_running && _socket.status.value != ChatSocketStatus.connected) {
        _showReconnecting = true;
        notifyListeners();
      }
    });
  }

  void _onSessionRevoked() {
    // Server thu hồi phiên (đăng xuất nơi khác, tài khoản bị khóa): báo người dùng đăng nhập lại.
    _authLost = true;
    notifyListeners();
  }

  void _onUnauthorized() {
    _authLost = true;
    notifyListeners();
  }

  /// Màn "hết phiên" bấm "Đăng nhập lại": dừng chat; việc chuyển về màn đăng nhập do giao diện làm.
  void acknowledgeAuthLost() {
    _authLost = false;
  }

  /// Người dùng kéo để làm mới hoặc bấm thử lại: nối lại WebSocket (nếu đang đứt) và tải lại danh sách.
  Future<void> refreshAll() async {
    _socket.reconnectNow();
    await refreshConversations();
    final open = _openId;
    if (open != null && _indexOf(open) >= 0) await _loadLatest(open, reconcile: true);
  }

  // ------------------------------------------------------------------ danh sách cuộc trò chuyện

  /// Endpoint 3. Thay danh sách bằng kết quả server: thêm cuộc mới, xóa cuộc không còn, cập nhật tin cuối và số chưa đọc.
  Future<void> refreshConversations() async {
    if (!_running) return;
    if (!_listLoaded) {
      _listLoading = true;
      notifyListeners();
    }
    if (_listRefreshesInFlight == 0) _addedDuringRefresh.clear();
    _listRefreshesInFlight++;
    try {
      final fresh = await _api.listConversations();
      if (!_running) return;
      _applyConversationList(fresh);
      _listError = null;
      _authLost = false;
    } on ChatApiException catch (e) {
      _noteApiError(e);
      _listError = e.userMessage;
    } catch (_) {
      _listError = 'Không tải được danh sách. Kéo để thử lại.';
    } finally {
      _listRefreshesInFlight--;
      if (_listRefreshesInFlight <= 0) {
        _listRefreshesInFlight = 0;
        _addedDuringRefresh.clear();
      }
      if (_running) {
        _listLoading = false;
        _listLoaded = true;
        notifyListeners();
      }
    }
  }

  void _applyConversationList(List<ChatConversation> fresh) {
    final existing = <int, ChatConversation>{for (final c in _conversations) c.id: c};
    final merged = <ChatConversation>[];
    final freshIds = <int>{};
    for (final c in fresh) {
      freshIds.add(c.id);
      final old = existing[c.id];
      // Tóm tắt không có danh sách thành viên: giữ bản đã tải trước đó.
      merged.add(old != null && c.members.isEmpty && old.members.isNotEmpty
          ? c.copyWith(members: old.members)
          : c);
    }
    // Server không trả cuộc 1-1 chưa có tin; cuộc đó có thể vừa được mở ở máy này nên phải giữ lại.
    // Mọi cuộc khác không còn trong kết quả là đã bị giải tán hoặc mình đã bị kick.
    for (final old in _conversations) {
      if (freshIds.contains(old.id)) continue;
      if ((!old.isGroup && old.lastMessage == null) || _addedDuringRefresh.contains(old.id)) {
        merged.add(old);
      } else {
        _dropConversationData(old.id);
        if (_openId == old.id) {
          _emit(ChatNotice(ChatNoticeKind.conversationGone, 'Cuộc trò chuyện không còn.',
              conversationId: old.id));
        }
      }
    }
    _conversations = _sorted(merged);
  }

  /// Thêm hoặc thay một cuộc (giữ danh sách thành viên đã có nếu bản mới không kèm theo).
  void _upsertConversation(ChatConversation incoming) {
    if (_listRefreshesInFlight > 0) _addedDuringRefresh.add(incoming.id);
    final index = _indexOf(incoming.id);
    final list = List<ChatConversation>.of(_conversations);
    if (index < 0) {
      list.add(incoming);
    } else {
      final old = list[index];
      list[index] = incoming.members.isEmpty && old.members.isNotEmpty
          ? incoming.copyWith(members: old.members)
          : incoming;
    }
    _conversations = _sorted(list);
  }

  void _replaceConversation(ChatConversation updated) {
    final index = _indexOf(updated.id);
    if (index < 0) return;
    final list = List<ChatConversation>.of(_conversations);
    list[index] = updated;
    _conversations = _sorted(list);
  }

  List<ChatConversation> _sorted(List<ChatConversation> list) {
    final copy = List<ChatConversation>.of(list);
    copy.sort((a, b) {
      final byTime = b.lastActivity.compareTo(a.lastActivity);
      return byTime != 0 ? byTime : b.id.compareTo(a.id);
    });
    return copy;
  }

  int _indexOf(int conversationId) {
    for (var i = 0; i < _conversations.length; i++) {
      if (_conversations[i].id == conversationId) return i;
    }
    return -1;
  }

  void _dropConversationData(int conversationId) {
    _messages.remove(conversationId);
    _hasMore.remove(conversationId);
    _loadingOlder.remove(conversationId);
    _loadingLatest.remove(conversationId);
    _loadFailed.remove(conversationId);
  }

  void _scheduleListRefresh() {
    _listRefreshTimer?.cancel();
    _listRefreshTimer = Timer(_listRefreshDebounce, () {
      _listRefreshTimer = null;
      unawaited(refreshConversations());
    });
  }

  // ------------------------------------------------------------------ mở / đóng một cuộc

  /// Màn chi tiết gọi khi mở. Luôn tải lại cửa sổ tin mới nhất (không dùng bản cũ trong bộ nhớ).
  Future<void> openConversation(int conversationId) async {
    _openId = conversationId;
    _atBottom = true;
    // Bản cũ chỉ còn tin chưa gửi xong (đang gửi / lỗi); tin server sẽ được tải lại.
    final held = _messages[conversationId];
    _messages[conversationId] = [
      if (held != null)
        for (final m in held)
          if (m.id == null) m,
    ];
    notifyListeners();

    if (_indexOf(conversationId) < 0) {
      // Mở bằng id mà máy chưa có (vd từ thông báo hoặc cuộc 1-1 mới): lấy chi tiết.
      try {
        _upsertConversation(await _api.getConversation(conversationId));
      } on ChatApiException catch (e) {
        _noteApiError(e);
        if (e.isNotFound) {
          _emit(ChatNotice(ChatNoticeKind.conversationGone, 'Cuộc trò chuyện không còn.',
              conversationId: conversationId));
        }
        return;
      } catch (_) {
        _loadFailed.add(conversationId);
        notifyListeners();
        return;
      }
    }
    await _loadLatest(conversationId, reconcile: false);
  }

  /// Màn chi tiết gọi khi đóng: bỏ tin server trong bộ nhớ, chỉ giữ tin chưa gửi xong.
  void closeConversation(int conversationId) {
    if (_openId == conversationId) _openId = null;
    final held = _messages[conversationId];
    if (held != null) {
      final pending = [for (final m in held) if (m.id == null) m];
      if (pending.isEmpty) {
        _messages.remove(conversationId);
      } else {
        _messages[conversationId] = pending;
      }
    }
    _hasMore.remove(conversationId);
    _markReadPending.remove(conversationId);
    // Không notify: màn hình đang đóng, tránh rebuild trong lúc dispose.
  }

  /// Màn chi tiết báo vị trí cuộn: ở cuối danh sách hay không (điều kiện để tự đánh dấu đã đọc).
  void setViewing(int conversationId, {required bool atBottom}) {
    if (_openId != conversationId) return;
    final changed = _atBottom != atBottom;
    _atBottom = atBottom;
    if (changed && atBottom) _maybeMarkRead(conversationId);
  }

  /// Tải lại cửa sổ tin mới nhất. [reconcile] true (sau khi nối lại) thì đối chiếu với tin đang giữ theo mục 11.
  Future<void> _loadLatest(int conversationId, {required bool reconcile}) async {
    _loadingLatest.add(conversationId);
    notifyListeners();
    try {
      final page = await _api.listMessages(conversationId, limit: _pageSize);
      if (!_running) return;

      // Trang REST là mới nhất trước; đảo thành cũ nhất trước.
      final window = <ChatMessage>[
        for (final m in page.messages.reversed)
          if (!_recentlyDeleted.contains(m.id)) m,
      ];
      final current = _messages[conversationId] ?? const <ChatMessage>[];
      final held = <ChatMessage>[
        for (final m in current)
          if (m.id != null) m,
      ];

      List<ChatMessage> base;
      bool more;
      if (!reconcile || held.isEmpty) {
        base = window;
        more = page.hasMore;
      } else if (window.isEmpty) {
        // Server không còn tin nào: mọi tin có id đang giữ đã bị xóa.
        base = const <ChatMessage>[];
        more = false;
      } else {
        final minWindow = window.first.id!;
        final maxHeld = held.last.id!;
        if (minWindow > maxHeld) {
          // Không chồng lấn với những gì đang giữ: thay cả danh sách bằng cửa sổ mới.
          base = window;
          more = page.hasMore;
        } else {
          // Tin cũ hơn miền chồng lấn giữ nguyên (không biết còn hay không); trong miền chồng lấn, tin có trong cửa sổ
          // thì lấy, tin đang giữ mà cửa sổ không có thì server đã xóa nên gỡ.
          final older = [for (final m in held) if (m.id! < minWindow) m];
          base = [...older, ...window];
          more = older.isNotEmpty ? (_hasMore[conversationId] ?? page.hasMore) : page.hasMore;
        }
      }

      // Tin mới hơn cửa sổ (vừa đến qua sự kiện trong lúc đang tải) vẫn giữ.
      final newer = <ChatMessage>[];
      if (window.isNotEmpty) {
        final maxWindow = window.last.id!;
        for (final m in held) {
          if (m.id! > maxWindow && !_recentlyDeleted.contains(m.id)) newer.add(m);
        }
      }
      final serverSide = [...base, ...newer];

      // Tin đang gửi / lỗi mà server đã có (cùng clientMsgId) thì bỏ bản tạm.
      final confirmed = <String>{for (final m in serverSide) m.clientMsgId};
      final pending = [
        for (final m in current)
          if (m.id == null && !confirmed.contains(m.clientMsgId)) m,
      ];

      _messages[conversationId] = [...serverSide, ...pending];
      _hasMore[conversationId] = more;
      _loadFailed.remove(conversationId);
      if (_openId == conversationId) _maybeMarkRead(conversationId);
    } on ChatApiException catch (e) {
      _noteApiError(e);
      _loadFailed.add(conversationId);
      if (e.isNotFound) {
        _handleConversationNotFound(conversationId);
      } else if (_openId == conversationId) {
        _emit(ChatNotice(ChatNoticeKind.error, e.userMessage, conversationId: conversationId));
      }
    } catch (_) {
      // Phản hồi không đọc được: coi như lần tải thất bại (màn hình hiện nút thử lại), không để lỗi lọt ra ngoài.
      _loadFailed.add(conversationId);
    } finally {
      _loadingLatest.remove(conversationId);
      if (_running) notifyListeners();
    }
  }

  /// Thử tải lại cửa sổ tin mới nhất của cuộc đang mở (nút "Thử lại" khi lần tải đầu thất bại).
  Future<void> reloadLatest(int conversationId) async {
    _socket.reconnectNow();
    await _loadLatest(conversationId, reconcile: false);
  }

  /// Tải trang tin cũ hơn (cuộn gần cụt danh sách). Chỉ một lời gọi chạy cùng lúc cho mỗi cuộc.
  Future<void> loadOlder(int conversationId) async {
    if (_loadingOlder.contains(conversationId) || !hasMore(conversationId)) return;
    final held = _messages[conversationId];
    if (held == null) return;
    int? before;
    for (final m in held) {
      if (m.id != null) {
        before = m.id;
        break;
      }
    }
    if (before == null) return;

    _loadingOlder.add(conversationId);
    notifyListeners();
    try {
      final page = await _api.listMessages(conversationId, before: before, limit: _pageSize);
      if (!_running) return;
      final current = _messages[conversationId] ?? const <ChatMessage>[];
      final have = <int>{
        for (final m in current)
          if (m.id != null) m.id!,
      };
      final older = <ChatMessage>[
        for (final m in page.messages.reversed)
          if (!have.contains(m.id) && !_recentlyDeleted.contains(m.id)) m,
      ];
      _messages[conversationId] = [...older, ...current];
      _hasMore[conversationId] = page.hasMore;
    } on ChatApiException catch (e) {
      _noteApiError(e);
      if (e.isNotFound) {
        _handleConversationNotFound(conversationId);
      } else {
        _emit(ChatNotice(ChatNoticeKind.error, e.userMessage, conversationId: conversationId));
      }
    } catch (_) {
      _emit(ChatNotice(ChatNoticeKind.error, 'Không tải được tin cũ hơn.', conversationId: conversationId));
    } finally {
      _loadingOlder.remove(conversationId);
      if (_running) notifyListeners();
    }
  }

  // ------------------------------------------------------------------ gửi tin

  /// Gửi tin theo kiểu lạc quan: chèn ngay tin "đang gửi" rồi gọi REST. Trả false nếu nội dung rỗng.
  Future<bool> sendText(int conversationId, String text) async {
    final content = text.trim();
    final me = _myId;
    if (content.isEmpty || me == null || !_running) return false;

    final pending = ChatMessage.pending(
      conversationId: conversationId,
      senderId: me,
      senderName: _myName,
      content: content,
      clientMsgId: generateUuidV4(),
    );
    _messages[conversationId] = [...(_messages[conversationId] ?? const <ChatMessage>[]), pending];
    notifyListeners();
    unawaited(_transmit(pending));
    return true;
  }

  /// Gửi lại một tin lỗi với CÙNG clientMsgId (server không tạo tin trùng nếu lần trước thực ra đã lưu).
  Future<void> retrySend(int conversationId, String clientMsgId) async {
    final list = _messages[conversationId];
    if (list == null) return;
    ChatMessage? target;
    for (final m in list) {
      if (m.id == null && m.clientMsgId == clientMsgId && m.status == ChatMessageStatus.failed) {
        target = m;
        break;
      }
    }
    if (target == null || !target.retryable) return;
    _setPendingStatus(conversationId, clientMsgId, ChatMessageStatus.sending);
    await _transmit(target.copyWith(status: ChatMessageStatus.sending));
  }

  /// Bỏ một tin chưa từng tới server (gửi lỗi, không gửi lại được). Đây không phải "xóa chỉ phía mình".
  void discardFailed(int conversationId, String clientMsgId) {
    final list = _messages[conversationId];
    if (list == null) return;
    _messages[conversationId] = [
      for (final m in list)
        if (!(m.id == null && m.clientMsgId == clientMsgId)) m,
    ];
    notifyListeners();
  }

  Future<void> _transmit(ChatMessage pending, {bool autoRetried = false}) async {
    final conversationId = pending.conversationId;
    try {
      final sent = await _api.sendMessage(
        conversationId,
        clientMsgId: pending.clientMsgId,
        content: pending.content,
      );
      if (!_running) return;
      _authLost = false;
      _applyServerMessage(sent, fromSend: true);
    } on ChatApiException catch (e) {
      if (!_running) return;
      _noteApiError(e);
      if (e.isRateLimited && !autoRetried) {
        // Chờ theo Retry-After rồi tự thử lại đúng một lần.
        await Future<void>.delayed(Duration(seconds: (e.retryAfterSeconds ?? 3).clamp(1, 15).toInt()));
        if (!_running) return;
        await _transmit(pending, autoRetried: true);
        return;
      }
      final retryable = e.isRetryable || e.isRateLimited;
      _setPendingStatus(conversationId, pending.clientMsgId, ChatMessageStatus.failed,
          retryable: retryable);
      if (e.isNotFound) {
        _handleConversationNotFound(conversationId);
      } else if (!e.isNetwork && !e.isServerError) {
        _emit(ChatNotice(ChatNoticeKind.error, e.userMessage, conversationId: conversationId));
      }
    } catch (_) {
      if (!_running) return;
      _setPendingStatus(conversationId, pending.clientMsgId, ChatMessageStatus.failed);
    }
  }

  void _setPendingStatus(int conversationId, String clientMsgId, ChatMessageStatus status,
      {bool? retryable}) {
    final list = _messages[conversationId];
    if (list == null) return;
    _messages[conversationId] = [
      for (final m in list)
        if (m.id == null && m.clientMsgId == clientMsgId)
          m.copyWith(status: status, retryable: retryable)
        else
          m,
    ];
    notifyListeners();
  }

  // ------------------------------------------------------------------ xóa tin

  /// Xóa tin cho mọi người. KHÔNG lạc quan: bubble làm mờ trong lúc chờ, chỉ gỡ khi server trả 204 hoặc 404.
  /// Trả true khi tin đã được gỡ.
  Future<bool> deleteMessage(int conversationId, int messageId) async {
    if (!_deleting.add(messageId)) return false;
    notifyListeners();
    try {
      await _api.deleteMessage(conversationId, messageId);
      if (!_running) return true;
      _removeMessageLocally(conversationId, messageId);
      return true;
    } on ChatApiException catch (e) {
      _noteApiError(e);
      if (e.isNotFound) {
        _handleConversationNotFound(conversationId);
      } else {
        _emit(ChatNotice(
          ChatNoticeKind.error,
          e.isRetryable ? 'Không xóa được, thử lại.' : e.userMessage,
          conversationId: conversationId,
        ));
      }
      return false;
    } finally {
      _deleting.remove(messageId);
      if (_running) notifyListeners();
    }
  }

  void _removeMessageLocally(int conversationId, int messageId) {
    final list = _messages[conversationId];
    if (list != null) {
      _messages[conversationId] = [
        for (final m in list)
          if (m.id != messageId) m,
      ];
    }
    final conv = conversationById(conversationId);
    // Sự kiện không mang "tin cuối mới": nếu vừa gỡ đúng tin cuối thì hỏi lại danh sách (gộp 300 ms).
    if (conv != null && conv.lastMessage?.id == messageId) _scheduleListRefresh();
    notifyListeners();
  }

  // ------------------------------------------------------------------ đã đọc

  void _maybeMarkRead(int conversationId) {
    if (_openId != conversationId || !_atBottom || !_appResumed) return;
    final conv = conversationById(conversationId);
    if (conv != null && conv.unreadCount > 0) {
      _replaceConversation(conv.copyWith(unreadCount: 0));
      notifyListeners();
    }
    _markReadPending.add(conversationId);
    _markReadTimer?.cancel();
    _markReadTimer = Timer(_markReadDebounce, _flushMarkRead);
  }

  Future<void> _flushMarkRead() async {
    _markReadTimer = null;
    final ids = _markReadPending.toList();
    _markReadPending.clear();
    for (final conversationId in ids) {
      if (!_running || _openId != conversationId || !_atBottom || !_appResumed) continue;
      final lastId = _latestServerId(conversationId);
      if (lastId == null) continue;
      try {
        await _api.markRead(conversationId, lastId);
      } on ChatApiException catch (e) {
        _noteApiError(e);
        if (e.isNotFound) _handleConversationNotFound(conversationId);
      } catch (_) {
        // Không đánh dấu được lần này: lần cuộn/nhận tin sau sẽ thử lại.
      }
    }
  }

  int? _latestServerId(int conversationId) {
    int? latest;
    for (final m in _messages[conversationId] ?? const <ChatMessage>[]) {
      final id = m.id;
      if (id != null && (latest == null || id > latest)) latest = id;
    }
    return latest ?? conversationById(conversationId)?.lastMessage?.id;
  }

  // ------------------------------------------------------------------ nhóm và chat 1-1

  /// Tìm admin để bắt đầu chat. Ném [ChatApiException] khi lỗi.
  Future<List<ChatAdminBrief>> searchAdmins(String query) => _api.searchAdmins(query);

  /// Mở (hoặc tạo) chat 1-1. Ném [ChatApiException] khi lỗi.
  Future<ChatConversation> openDirect(int peerId) async {
    final conversation = await _api.openDirect(peerId);
    _upsertConversation(conversation);
    notifyListeners();
    return conversation;
  }

  /// Tạo nhóm: [memberIds] không gồm chính mình. Ném [ChatApiException] khi lỗi.
  Future<ChatConversation> createGroup(String name, List<int> memberIds) async {
    final conversation = await _api.createGroup(name, memberIds);
    _upsertConversation(conversation);
    notifyListeners();
    return conversation;
  }

  /// Tải chi tiết (danh sách thành viên) của một cuộc.
  Future<ChatConversation?> loadConversationDetail(int conversationId) async {
    try {
      final conversation = await _api.getConversation(conversationId);
      _upsertConversation(conversation);
      notifyListeners();
      return conversation;
    } on ChatApiException catch (e) {
      _noteApiError(e);
      if (e.isNotFound) _handleConversationNotFound(conversationId);
      rethrow;
    }
  }

  /// Kick một thành viên (chủ nhóm). Ném [ChatApiException] khi lỗi.
  Future<void> kickMember(int conversationId, int adminId) async {
    try {
      final updated = await _api.kickMember(conversationId, adminId);
      _upsertConversation(updated);
      notifyListeners();
    } on ChatApiException catch (e) {
      _noteApiError(e);
      if (e.isNotFound) _handleConversationNotFound(conversationId);
      rethrow;
    }
  }

  /// Giải tán nhóm (chủ nhóm). Ném [ChatApiException] khi lỗi.
  Future<void> dissolveGroup(int conversationId) async {
    _selfDissolving.add(conversationId);
    try {
      await _api.dissolveGroup(conversationId);
    } on ChatApiException catch (e) {
      _noteApiError(e);
      if (e.isNotFound) _handleConversationNotFound(conversationId);
      rethrow;
    } finally {
      _selfDissolving.remove(conversationId);
    }
    _removeConversation(conversationId);
    notifyListeners();
  }

  void _removeConversation(int conversationId) {
    _conversations = [
      for (final c in _conversations)
        if (c.id != conversationId) c,
    ];
    _dropConversationData(conversationId);
  }

  /// 404 ở một cuộc: cuộc không còn (bị giải tán, bị kick, hoặc không tồn tại). Gỡ khỏi máy và báo.
  void _handleConversationNotFound(int conversationId) {
    final wasKnown = _indexOf(conversationId) >= 0;
    _removeConversation(conversationId);
    if (wasKnown || _openId == conversationId) {
      _emit(ChatNotice(ChatNoticeKind.conversationGone, 'Cuộc trò chuyện không còn.',
          conversationId: conversationId));
    }
    notifyListeners();
  }

  // ------------------------------------------------------------------ sự kiện WebSocket

  /// Xử lý tuần tự từng sự kiện (mỗi sự kiện có thể cần gọi REST) để không chạy chồng chéo.
  void _enqueue(ChatEvent event) {
    _eventQueue = _eventQueue.then((_) => _handleEvent(event)).catchError((Object error) {
      debugPrint('Chat: lỗi khi xử lý sự kiện: $error');
    });
  }

  Future<void> _handleEvent(ChatEvent event) async {
    if (!_running) return;
    switch (event) {
      case ChatReadyEvent():
        await _resync();
      case ChatMessageNewEvent(:final message):
        await _onMessageNew(message);
      case ChatMessageDeletedEvent(:final conversationId, :final messageId):
        _onMessageDeleted(conversationId, messageId);
      case ChatConversationCreatedEvent(:final conversation):
        _upsertConversation(conversation);
        notifyListeners();
      case ChatConversationDissolvedEvent(:final conversationId):
        _onConversationRemoved(conversationId, ChatNoticeKind.groupDissolved, 'Nhóm đã bị giải tán.');
      case ChatMemberRemovedEvent(:final conversationId, :final adminId):
        _onMemberRemoved(conversationId, adminId);
      case ChatConversationReadEvent(:final conversationId, :final lastReadMessageId):
        _onConversationRead(conversationId, lastReadMessageId);
      case ChatErrorEvent(:final code, :final message):
        debugPrint('Chat WS error: $code $message');
      case ChatPongEvent():
        break;
    }
  }

  /// Kéo bù sau mỗi lần nối: danh sách cuộc, rồi cửa sổ tin mới nhất của cuộc đang mở.
  Future<void> _resync() async {
    await refreshConversations();
    final open = _openId;
    if (open != null && _indexOf(open) >= 0) {
      await _loadLatest(open, reconcile: true);
    }
    _recentlyDeleted.clear();
  }

  Future<void> _onMessageNew(ChatMessage message) async {
    final messageId = message.id;
    if (messageId == null || _recentlyDeleted.contains(messageId)) return;
    final conversationId = message.conversationId;

    if (_indexOf(conversationId) < 0) {
      // Cuộc chưa có trên máy (vd cuộc 1-1 mà người kia nhắn lần đầu): lấy chi tiết. Chi tiết đã gồm tin cuối và số
      // chưa đọc do server tính, nên không cộng thêm.
      try {
        _upsertConversation(await _api.getConversation(conversationId));
        notifyListeners();
      } on ChatApiException catch (e) {
        _noteApiError(e);
      }
      return;
    }
    _applyServerMessage(message, fromSend: false);
  }

  /// Gộp một tin do server xác nhận vào danh sách tin và tóm tắt cuộc. Dùng cho cả sự kiện message.new lẫn
  /// phản hồi REST của chính tin mình gửi (hai nguồn này có thể đến theo thứ tự nào cũng được).
  void _applyServerMessage(ChatMessage message, {required bool fromSend}) {
    final messageId = message.id;
    if (messageId == null) return;
    final conversationId = message.conversationId;
    if (_recentlyDeleted.contains(messageId)) return;

    // 1) Danh sách tin (chỉ khi cuộc này đang được tải).
    final list = _messages[conversationId];
    if (list != null) {
      final server = <ChatMessage>[
        for (final m in list)
          if (m.id != null) m,
      ];
      final pending = <ChatMessage>[
        for (final m in list)
          // Tin tạm cùng clientMsgId được thay bằng tin server.
          if (m.id == null && m.clientMsgId != message.clientMsgId) m,
      ];
      if (!server.any((m) => m.id == messageId)) {
        server.add(message);
        server.sort((a, b) => a.id!.compareTo(b.id!));
      }
      _messages[conversationId] = [...server, ...pending];
    }

    // 2) Tóm tắt cuộc: tin cuối, số chưa đọc.
    final conv = conversationById(conversationId);
    if (conv != null) {
      final lastId = conv.lastMessage?.id ?? 0;
      final isNewest = messageId > lastId;
      var updated = conv;
      if (isNewest) updated = updated.copyWith(lastMessage: message);
      final mine = message.senderId == _myId;
      if (isNewest && !mine && !fromSend) {
        final viewing = _openId == conversationId && _atBottom && _appResumed;
        if (viewing) {
          updated = updated.copyWith(unreadCount: 0);
          _maybeMarkRead(conversationId);
        } else {
          updated = updated.copyWith(unreadCount: conv.unreadCount + 1);
        }
      }
      _replaceConversation(updated);
    }
    notifyListeners();
  }

  void _onMessageDeleted(int conversationId, int messageId) {
    _recentlyDeleted.add(messageId);
    if (_recentlyDeleted.length > 1000) _recentlyDeleted.clear();
    _removeMessageLocally(conversationId, messageId);
  }

  void _onConversationRemoved(int conversationId, ChatNoticeKind kind, String message) {
    final wasOpen = _openId == conversationId;
    final selfInitiated = kind == ChatNoticeKind.groupDissolved && _selfDissolving.contains(conversationId);
    _removeConversation(conversationId);
    notifyListeners();
    // Chỉ báo khi người dùng đang ở trong cuộc đó (màn chi tiết hoặc thông tin nhóm), và không báo lại việc
    // chính mình vừa làm (người giải tán đã có thông báo riêng).
    if (wasOpen && !selfInitiated) _emit(ChatNotice(kind, message, conversationId: conversationId));
  }

  void _onMemberRemoved(int conversationId, int adminId) {
    if (adminId == _myId) {
      _onConversationRemoved(
          conversationId, ChatNoticeKind.removedFromGroup, 'Bạn đã bị mời khỏi nhóm.');
      return;
    }
    final conv = conversationById(conversationId);
    if (conv == null) return;
    _replaceConversation(conv.copyWith(
      memberCount: conv.memberCount > 0 ? conv.memberCount - 1 : 0,
      members: [
        for (final m in conv.members)
          if (m.adminId != adminId) m,
      ],
    ));
    notifyListeners();
  }

  void _onConversationRead(int conversationId, int lastReadMessageId) {
    final conv = conversationById(conversationId);
    if (conv == null) return;
    // Máy khác của mình đã đọc tới tin này. Chỉ về 0 nếu không còn tin nào mới hơn mà ta biết.
    final lastId = conv.lastMessage?.id;
    if (conv.unreadCount > 0 && (lastId == null || lastId <= lastReadMessageId)) {
      _replaceConversation(conv.copyWith(unreadCount: 0));
      notifyListeners();
    }
  }

  // ------------------------------------------------------------------ tiện ích

  void _noteApiError(ChatApiException e) {
    if (e.isUnauthorized && !_authLost) {
      _authLost = true;
      notifyListeners();
    }
  }

  void _emit(ChatNotice notice) {
    if (!_notices.isClosed) _notices.add(notice);
  }
}
