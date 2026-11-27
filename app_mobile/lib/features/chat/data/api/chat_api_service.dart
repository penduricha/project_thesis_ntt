import 'package:dio/dio.dart';

import '../../../authenticator/data/service/enrollment_service.dart'
    show EnrollmentAuth, SecureStorageEnrollmentAuth, AuthenticationUnavailable;
import '../../../config/network/spring_config_header.dart';
import '../model/chat_admin_brief.dart';
import '../model/chat_conversation.dart';
import '../model/chat_message.dart';
import 'chat_api_exception.dart';

/// Vé một lần để mở WebSocket (POST /chat/ws-ticket).
class ChatWsTicket {
  const ChatWsTicket({required this.ticket, required this.expiresInSec});

  final String ticket;
  final int expiresInSec;
}

/// Gọi REST chat của Spring Boot: /api/mobile/chat/... (phần /api/mobile nằm trong baseUrl của Dio).
///
/// - Mỗi endpoint một hàm, trả đối tượng đã parse hoặc ném [ChatApiException].
/// - Dio của dự án coi mọi mã < 500 là thành công, nên ở đây tự kiểm tra statusCode trước khi đọc body.
/// - Token lấy từ cùng cơ chế làm mới token của app ([SecureStorageEnrollmentAuth]): access token Java chỉ sống
///   5 phút; gặp 401 thì làm mới rồi gửi lại ĐÚNG MỘT LẦN. Dùng chung bộ khóa "một lần làm mới tại một thời điểm"
///   vì server xoay vòng refresh token (hai lần làm mới song song thì lần sau chắc chắn bị từ chối).
class ChatApiService {
  ChatApiService({EnrollmentAuth? auth, Dio? dio})
      : _auth = auth ?? const SecureStorageEnrollmentAuth(),
        _dio = dio ?? SpringConfigHeader().getApiClientNoHeaders();

  final EnrollmentAuth _auth;
  final Dio _dio;

  static const String _base = '/chat';

  // ------------------------------------------------------------------ endpoint

  /// 1. Vé mở WebSocket.
  Future<ChatWsTicket> requestWsTicket() async {
    final response = await _call('POST', '$_base/ws-ticket');
    _expectStatus(response, const {200});
    final body = _asMap(response.data);
    return ChatWsTicket(
      ticket: body['ticket'] as String,
      expiresInSec: (body['expiresInSec'] as num?)?.toInt() ?? 30,
    );
  }

  /// 2. Tìm admin (không gồm chính mình).
  Future<List<ChatAdminBrief>> searchAdmins(String query, {int limit = 50}) async {
    // Từ khóa trống: không gửi q, server trả danh sách admin khác (để màn chọn người có sẵn danh sách).
    final trimmed = query.trim();
    final response = await _call(
      'GET',
      '$_base/admins',
      query: {if (trimmed.isNotEmpty) 'q': trimmed, 'limit': limit},
    );
    _expectStatus(response, const {200});
    return [
      for (final item in _asList(response.data))
        ChatAdminBrief.fromJson(item as Map<String, dynamic>),
    ];
  }

  /// 3. Danh sách cuộc trò chuyện của tôi (tóm tắt, chưa có danh sách thành viên).
  Future<List<ChatConversation>> listConversations() async {
    final response = await _call('GET', '$_base/conversations');
    _expectStatus(response, const {200});
    return [
      for (final item in _asList(response.data))
        ChatConversation.fromJson(item as Map<String, dynamic>),
    ];
  }

  /// 4. Chi tiết một cuộc kèm thành viên.
  Future<ChatConversation> getConversation(int id) async {
    final response = await _call('GET', '$_base/conversations/$id');
    _expectStatus(response, const {200});
    return ChatConversation.fromJson(_asMap(response.data));
  }

  /// 5. Mở chat 1-1 (idempotent: đã có thì trả lại cuộc cũ).
  Future<ChatConversation> openDirect(int peerId) async {
    final response = await _call(
      'POST',
      '$_base/conversations/direct',
      body: {'peerId': peerId},
    );
    _expectStatus(response, const {200, 201});
    return ChatConversation.fromJson(_asMap(response.data));
  }

  /// 6. Tạo nhóm. [memberIds] không gồm chính mình.
  Future<ChatConversation> createGroup(String name, List<int> memberIds) async {
    final response = await _call(
      'POST',
      '$_base/conversations/group',
      body: {'name': name, 'memberIds': memberIds},
    );
    _expectStatus(response, const {201});
    return ChatConversation.fromJson(_asMap(response.data));
  }

  /// 7. Giải tán nhóm (chủ nhóm).
  Future<void> dissolveGroup(int id) async {
    final response = await _call('DELETE', '$_base/conversations/$id');
    _expectStatus(response, const {204});
  }

  /// 8. Kick một thành viên (chủ nhóm). Trả cuộc sau khi kick.
  Future<ChatConversation> kickMember(int id, int adminId) async {
    final response = await _call('DELETE', '$_base/conversations/$id/members/$adminId');
    _expectStatus(response, const {200});
    return ChatConversation.fromJson(_asMap(response.data));
  }

  /// 9. Một trang tin, tin mới nhất đứng đầu. [before] là id của tin cũ nhất đang có.
  Future<ChatMessagesPage> listMessages(int id, {int? before, int limit = 30}) async {
    final response = await _call(
      'GET',
      '$_base/conversations/$id/messages',
      query: {if (before != null) 'before': before, 'limit': limit},
    );
    _expectStatus(response, const {200});
    return ChatMessagesPage.fromJson(_asMap(response.data));
  }

  /// 10. Gửi tin. Gửi lại cùng [clientMsgId] thì server trả tin cũ (200), không tạo tin trùng.
  Future<ChatMessage> sendMessage(
      int id, {
        required String clientMsgId,
        required String content,
      }) async {
    final response = await _call(
      'POST',
      '$_base/conversations/$id/messages',
      body: {'clientMsgId': clientMsgId, 'content': content},
    );
    _expectStatus(response, const {200, 201});
    return ChatMessage.fromJson(_asMap(response.data));
  }

  /// 11. Xóa tin cho mọi người. 404 (tin đã không còn) được coi là thành công: trạng thái mong muốn đã đạt.
  Future<void> deleteMessage(int id, int messageId) async {
    final response = await _call('DELETE', '$_base/conversations/$id/messages/$messageId');
    if (response.statusCode == 404) {
      final error = _error(response);
      // Chỉ coi là đã xóa khi cuộc còn mà tin đã mất. 404 vì cuộc không còn thì cũng không còn gì để xóa.
      if (error.code == null || error.code == 'NOT_FOUND') return;
    }
    _expectStatus(response, const {204});
  }

  /// 12. Đánh dấu đã đọc tới [lastReadMessageId].
  Future<void> markRead(int id, int lastReadMessageId) async {
    final response = await _call(
      'POST',
      '$_base/conversations/$id/read',
      body: {'lastReadMessageId': lastReadMessageId},
    );
    _expectStatus(response, const {204});
  }

  // ------------------------------------------------------------------ nền

  Future<Response<dynamic>> _call(
      String method,
      String path, {
        Object? body,
        Map<String, dynamic>? query,
      }) async {
    final token = await _readToken();
    if (token == null || token.isEmpty) {
      throw const ChatApiException(
        statusCode: 401,
        code: 'UNAUTHORIZED',
        message: 'Chưa đăng nhập.',
      );
    }
    var response = await _send(method, path, token, body, query);
    if (response.statusCode == 401) {
      // Access token chỉ sống 5 phút: làm mới rồi gửi lại ĐÚNG MỘT LẦN.
      final fresh = await _tokenAfterUnauthorized(token);
      if (fresh != null && fresh.isNotEmpty) {
        response = await _send(method, path, fresh, body, query);
      }
    }
    return response;
  }

  /// Nếu máy đã có token mới hơn token vừa bị từ chối (lần làm mới trước đó đã lưu) thì dùng luôn, KHÔNG làm mới
  /// thêm lần nữa: làm mới liên tiếp rất dễ bị server từ chối.
  Future<String?> _tokenAfterUnauthorized(String sentToken) async {
    final current = await _readToken();
    if (current != null && current.isNotEmpty && current != sentToken) {
      return current;
    }
    try {
      return await _auth.refreshAccessToken();
    } on AuthenticationUnavailable catch (failure) {
      throw ChatApiException(statusCode: failure.network ? null : 503,
          code: 'AUTH_UNAVAILABLE', message: 'Authentication temporarily unavailable.');
    }
  }

  /// Đọc access token. Kho lưu trữ bảo mật có thể ném lỗi (trên web: "OperationError" khi dữ liệu đã mã hóa trong
  /// trình duyệt không còn giải mã được, ví dụ khóa mã hóa bị xóa hoặc đổi). Coi như chưa có phiên để app hiện
  /// "Phiên đăng nhập đã hết hạn" với nút đăng nhập lại, thay vì lỗi lạ khó hiểu.
  Future<String?> _readToken() async {
    try {
      return await _auth.accessToken();
    } on AuthenticationUnavailable catch (failure) {
      throw ChatApiException(statusCode: failure.network ? null : 503,
          code: 'AUTH_UNAVAILABLE', message: 'Authentication temporarily unavailable.');
    } catch (_) {
      return null;
    }
  }

  Future<Response<dynamic>> _send(
      String method,
      String path,
      String token,
      Object? body,
      Map<String, dynamic>? query,
      ) async {
    try {
      return await _dio.request<dynamic>(
        path,
        data: body,
        queryParameters: query,
        options: Options(
          method: method,
          headers: {'Authorization': 'Bearer $token'},
        ),
      );
    } on DioException catch (e) {
      final response = e.response;
      if (response != null) return response; // 5xx: đọc như phản hồi bình thường để dựng lỗi
      throw const ChatApiException(
        message: 'Không kết nối được máy chủ.',
      );
    }
  }

  void _expectStatus(Response<dynamic> response, Set<int> ok) {
    if (response.statusCode != null && ok.contains(response.statusCode)) return;
    throw _error(response);
  }

  ChatApiException _error(Response<dynamic> response) {
    final data = response.data;
    String? code;
    String message = '';
    if (data is Map) {
      // Thân lỗi có thể đến từ lớp khác (proxy, trang lỗi mặc định của Spring) nên không giả định kiểu trường.
      final rawCode = data['code'];
      final rawMessage = data['message'];
      code = rawCode is String ? rawCode : null;
      message = rawMessage is String ? rawMessage : '';
    }
    int? retryAfter;
    final header = response.headers.value('retry-after');
    if (header != null) retryAfter = int.tryParse(header.trim());
    return ChatApiException(
      statusCode: response.statusCode,
      code: code,
      message: message,
      retryAfterSeconds: retryAfter,
    );
  }

  Map<String, dynamic> _asMap(dynamic data) {
    if (data is Map<String, dynamic>) return data;
    throw const ChatApiException(
      statusCode: 502,
      code: 'BAD_RESPONSE',
      message: 'Phản hồi của máy chủ không hợp lệ.',
    );
  }

  List<dynamic> _asList(dynamic data) {
    if (data is List<dynamic>) return data;
    throw const ChatApiException(
      statusCode: 502,
      code: 'BAD_RESPONSE',
      message: 'Phản hồi của máy chủ không hợp lệ.',
    );
  }
}