import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

import '../../../config/network/network_configuration.dart';
import '../api/chat_api_exception.dart';
import '../api/chat_api_service.dart';
import '../model/chat_event.dart';

/// Trạng thái kết nối WebSocket.
enum ChatSocketStatus {
  /// Chưa chạy, đã dừng, hoặc đang tạm đóng vì ứng dụng xuống nền.
  stopped,

  /// Đang xin vé / bắt tay / chờ khung "ready" lần đầu.
  connecting,

  /// Đã nhận "ready": kênh sống.
  connected,

  /// Mất kết nối, đang chờ để nối lại theo backoff.
  reconnecting,
}

/// Mở và giữ một kết nối WebSocket tới /api/mobile/chat/ws. Không biết gì về giao diện hay trạng thái chat:
/// chỉ phát [events] (đã parse) và cập nhật [status].
///
/// - Mỗi lần nối: xin vé mới qua REST (vé dùng một lần, sống 30 giây) rồi mở ws(s)://host/api/mobile/chat/ws?ticket=...
///   (Flutter web không đặt được header Authorization khi mở WebSocket, nên dùng vé thay JWT).
/// - Ping JSON mỗi [pingInterval] (mặc định theo "ready"); không thấy khung nào trong 10 giây sau ping thì coi là đứt.
/// - Nối lại: chờ 1, 2, 4, 8, 16 rồi giữ ở 30 giây, cộng ngẫu nhiên tối đa 20%; reset về 1 giây sau khi kết nối
///   ổn định 10 giây.
/// - Mã đóng 4001 (phiên bị thu hồi): không nối lại, gọi [onSessionRevoked]. Mọi mã khác: nối lại.
class ChatSocketService {
  ChatSocketService({required ChatApiService api, Random? random})
      : _api = api,
        _random = random ?? Random();

  final ChatApiService _api;
  final Random _random;

  static const Duration _pongTimeout = Duration(seconds: 10);
  static const Duration _stableAfter = Duration(seconds: 10);
  static const int _maxBackoffSeconds = 30;

  final StreamController<ChatEvent> _events = StreamController<ChatEvent>.broadcast();

  /// Sự kiện server đẩy xuống (không gồm "pong").
  Stream<ChatEvent> get events => _events.stream;

  final ValueNotifier<ChatSocketStatus> status =
      ValueNotifier<ChatSocketStatus>(ChatSocketStatus.stopped);

  /// Gọi khi server đóng với mã 4001 (đăng xuất, đăng nhập nơi khác, tài khoản bị khóa).
  VoidCallback? onSessionRevoked;

  /// Gọi khi không xin được vé vì access token bị từ chối (401) dù đã thử làm mới.
  VoidCallback? onUnauthorized;

  bool _running = false;
  bool _paused = false;
  int _generation = 0;
  int _attempt = 0;

  WebSocketChannel? _channel;
  StreamSubscription<dynamic>? _subscription;
  Timer? _pingTimer;
  Timer? _pongTimer;
  Timer? _reconnectTimer;
  Timer? _stableTimer;

  bool get isRunning => _running;

  /// Bắt đầu giữ kết nối (gọi một lần sau đăng nhập).
  void start() {
    if (_running) return;
    _running = true;
    _paused = false;
    _attempt = 0;
    _connect();
  }

  /// Ứng dụng xuống nền: đóng kết nối, không nối lại cho tới khi [resume].
  void pause() {
    if (!_running || _paused) return;
    _paused = true;
    _dropConnection();
    _setStatus(ChatSocketStatus.stopped);
  }

  /// Ứng dụng trở lại: nối ngay (không chờ backoff).
  void resume() {
    if (!_running || !_paused) return;
    _paused = false;
    _attempt = 0;
    _connect();
  }

  /// Nối lại ngay nếu đang không nối được (nút "Thử lại" hoặc người dùng kéo để làm mới).
  void reconnectNow() {
    if (!_running || _paused || status.value == ChatSocketStatus.connected) return;
    _attempt = 0;
    _connect();
  }

  /// Dừng hẳn (đăng xuất): đóng mã 1000, không nối lại.
  Future<void> stop() async {
    _running = false;
    _paused = false;
    _dropConnection();
    _setStatus(ChatSocketStatus.stopped);
  }

  // ------------------------------------------------------------------ kết nối

  Future<void> _connect() async {
    if (!_running || _paused) return;
    // Đóng cả kênh cũ nếu còn (ví dụ reconnectNow() lúc đang nối dở), không chỉ hủy bộ hẹn giờ: nếu không, kênh cũ
    // đã mở nhưng chưa nhận "ready" sẽ bị bỏ rơi và nằm mở cho tới khi server dọn.
    _dropConnection();
    final generation = ++_generation;
    _setStatus(_attempt == 0 ? ChatSocketStatus.connecting : ChatSocketStatus.reconnecting);

    WebSocketChannel? channel;
    try {
      final ticket = await _api.requestWsTicket();
      if (!_isCurrent(generation)) return;

      channel = WebSocketChannel.connect(_uri(ticket.ticket));
      await channel.ready;
      if (!_isCurrent(generation)) {
        _closeQuietly(channel);
        return;
      }

      _channel = channel;
      _subscription = channel.stream.listen(
        (dynamic data) => _onData(generation, data),
        onError: (Object error) => _onClosed(generation),
        onDone: () => _onClosed(generation),
        cancelOnError: true,
      );
      // Giữ trạng thái "connecting" cho tới khi nhận khung "ready" của server.
    } on ChatApiException catch (e) {
      if (!_isCurrent(generation)) return;
      if (e.isUnauthorized) onUnauthorized?.call();
      _scheduleReconnect();
    } catch (e) {
      // Bắt tay bị từ chối (401, 429, 503), mất mạng, hay lỗi lúc mở.
      if (channel != null) _closeQuietly(channel);
      if (!_isCurrent(generation)) return;
      debugPrint('Chat WS: không nối được: $e');
      _scheduleReconnect();
    }
  }

  bool _isCurrent(int generation) => _running && !_paused && generation == _generation;

  Uri _uri(String ticket) {
    // base = http(s)://host:port/api/mobile -> ws(s)://host:port/api/mobile/chat/ws
    final base = NetworkConfiguration.requestSpringBoot();
    final wsBase = base.startsWith('https') ? 'wss${base.substring(5)}' : 'ws${base.substring(4)}';
    return Uri.parse('$wsBase/chat/ws').replace(queryParameters: {'ticket': ticket});
  }

  void _onData(int generation, dynamic data) {
    if (generation != _generation) return;
    // Bất kỳ khung nào cũng chứng tỏ kênh còn sống.
    _pongTimer?.cancel();
    _pongTimer = null;
    if (data is! String) return;

    final event = ChatEvent.parse(data);
    if (event == null) return;
    switch (event) {
      case ChatReadyEvent(:final pingIntervalSec):
        _setStatus(ChatSocketStatus.connected);
        _startPing(generation, Duration(seconds: max(5, pingIntervalSec)));
        _stableTimer?.cancel();
        _stableTimer = Timer(_stableAfter, () {
          if (generation == _generation) _attempt = 0;
        });
        _events.add(event);
      case ChatPongEvent():
        break;
      default:
        _events.add(event);
    }
  }

  void _onClosed(int generation) {
    if (generation != _generation) return;
    final code = _channel?.closeCode;
    _dropConnection();
    if (code == 4001) {
      // Phiên bị thu hồi: không nối lại.
      _running = false;
      _setStatus(ChatSocketStatus.stopped);
      onSessionRevoked?.call();
      return;
    }
    _scheduleReconnect();
  }

  // ------------------------------------------------------------------ ping / nối lại

  void _startPing(int generation, Duration interval) {
    _pingTimer?.cancel();
    _pingTimer = Timer.periodic(interval, (_) {
      if (generation != _generation) return;
      try {
        _channel?.sink.add(jsonEncode({
          'type': 'ping',
          'data': {'t': DateTime.now().millisecondsSinceEpoch},
        }));
      } catch (_) {
        _onClosed(generation);
        return;
      }
      _pongTimer ??= Timer(_pongTimeout, () {
        if (generation != _generation) return;
        debugPrint('Chat WS: quá $_pongTimeout không thấy phản hồi, nối lại.');
        _dropConnection();
        _scheduleReconnect();
      });
    });
  }

  void _scheduleReconnect() {
    if (!_running || _paused) return;
    _cancelTimers();
    _attempt++;
    final base = min(_maxBackoffSeconds, 1 << min(_attempt - 1, 5)); // 1,2,4,8,16,30
    final jitterMs = (base * 1000 * 0.2 * _random.nextDouble()).round();
    _setStatus(ChatSocketStatus.reconnecting);
    _reconnectTimer = Timer(Duration(milliseconds: base * 1000 + jitterMs), _connect);
  }

  // ------------------------------------------------------------------ dọn dẹp

  /// Đóng kênh hiện tại và vô hiệu mọi callback còn treo của kết nối cũ (tăng generation).
  void _dropConnection() {
    _generation++;
    _cancelTimers();
    final subscription = _subscription;
    _subscription = null;
    if (subscription != null) unawaited(subscription.cancel());
    final channel = _channel;
    _channel = null;
    if (channel != null) _closeQuietly(channel);
  }

  void _cancelTimers() {
    _pingTimer?.cancel();
    _pongTimer?.cancel();
    _reconnectTimer?.cancel();
    _stableTimer?.cancel();
    _pingTimer = null;
    _pongTimer = null;
    _reconnectTimer = null;
    _stableTimer = null;
  }

  void _closeQuietly(WebSocketChannel channel) {
    try {
      unawaited(channel.sink.close(1000).catchError((Object _) {}));
    } catch (_) {
      // Kênh đã đóng.
    }
  }

  void _setStatus(ChatSocketStatus value) {
    if (status.value != value) status.value = value;
  }
}
