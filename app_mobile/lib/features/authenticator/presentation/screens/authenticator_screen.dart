import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../main_shell/presentation/widgets/app_menu_bar.dart';
// Class sinh mã TOTP đã được tách riêng (cùng thư mục với file này)
import '../../data/model/auth_account.dart';
import '../../data/service/account_store.dart';
import '../../data/service/enrollment_service.dart';
import '../widgets/activate_key_dialog.dart';
import '../widgets/ring_painter.dart';
import 'totp_generator.dart';

// AuthAccount đã chuyển sang data/model/auth_account.dart. Export lại để các file khác
// đang import AuthAccount từ file này vẫn chạy bình thường.
export '../../data/model/auth_account.dart';

// ============================================================================
//  MÀN HÌNH
// ============================================================================

class AuthenticatorScreen extends StatefulWidget {
  const AuthenticatorScreen({super.key, this.auth});

  /// Phiên đăng nhập dùng để gọi API kích hoạt 2FA (access token + adminId + tên).
  /// Không truyền -> đọc từ flutter_secure_storage (xem SecureStorageEnrollmentAuth).
  final EnrollmentAuth? auth;

  @override
  State<AuthenticatorScreen> createState() => _AuthenticatorScreenState();
}

class _AuthenticatorScreenState extends State<AuthenticatorScreen>
    with TickerProviderStateMixin, WidgetsBindingObserver {
  // ---- Màu sắc ----
  static const Color _blueColor = Color(0xFF004B87); // nút "+" góc trên phải
  static const Color _dividerColor = Color(0xFFEDEDED);
  static const Color _searchFill = Color(0xFFEFEFEF);

  final GlobalKey<ScaffoldState> _scaffoldKey = GlobalKey<ScaffoldState>();
  final TextEditingController _searchController = TextEditingController();

  // Mỗi chu kỳ (15 / 30 / 45 / 60 giây) có 1 đồng hồ chung: chạy 0 -> 1,
  // đồng bộ theo giờ thật của máy. Tài khoản cùng chu kỳ dùng chung đồng hồ.
  final Map<int, AnimationController> _clocks = {};

  late final EnrollmentAuth _auth;
  late final EnrollmentService _enrollment;

  // Phiên đăng nhập hiện tại. Chỉ TOTP của _adminId mới được nạp và hiển thị.
  // null = chưa nạp xong / chưa đăng nhập -> danh sách để trống.
  String? _adminId;
  AccountStore? _store; // key lưu riêng theo _adminId

  // TOTP của admin đang đăng nhập (kích hoạt từ server, được lưu máy). Không còn dữ liệu demo.
  final List<AuthAccount> _accounts = [];

  // Mã 6 số hiện tại của từng tài khoản (key = id), chỉ tính lại khi sang chu kỳ mới
  Map<String, String> _codes = {};

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);

    _auth = widget.auth ?? const SecureStorageEnrollmentAuth();
    _enrollment = EnrollmentService(auth: _auth);

    _codes = _generateCodes();
    _loadStoredAccounts();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    for (final clock in _clocks.values) {
      clock.dispose();
    }
    _searchController.dispose();
    super.dispose();
  }

  // Quay lại app từ nền -> đồng hồ có thể đã lệch, đồng bộ lại ngay
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _syncAllClocks();
  }

  /// Đồng hồ của chu kỳ [period] giây; chưa có thì tạo và chạy ngay theo giờ thật.
  AnimationController _clockFor(int period) {
    return _clocks.putIfAbsent(period, () {
      final clock = AnimationController(
        vsync: this,
        duration: Duration(seconds: period),
      )..addStatusListener((status) {
        // Hết 1 chu kỳ -> tính lại mã và chạy chu kỳ mới (đồng bộ lại theo giờ thật)
        if (status == AnimationStatus.completed) _onClockCompleted(period);
      });
      clock.forward(from: _elapsedFraction(period));
      return clock;
    });
  }

  /// Tỉ lệ thời gian đã trôi qua trong chu kỳ [period] hiện tại (0.0 -> 1.0)
  double _elapsedFraction(int period) {
    final periodMs = period * 1000;
    final ms = DateTime.now().millisecondsSinceEpoch % periodMs;
    return ms / periodMs;
  }

  void _onClockCompleted(int period) {
    if (!mounted) return;
    setState(() => _codes = _generateCodes());
    _clocks[period]?.forward(from: _elapsedFraction(period));
  }

  void _syncAllClocks() {
    if (!mounted) return;
    setState(() => _codes = _generateCodes());
    _clocks.forEach((period, clock) {
      clock.forward(from: _elapsedFraction(period));
    });
  }

  Map<String, String> _generateCodes() {
    final result = <String, String>{};
    for (final acc in _accounts) {
      try {
        result[acc.id] = TotpGenerator.generate(
          acc.secret,
          period: acc.periodSeconds,
          digits: acc.digits,
          algorithm: acc.algorithm,
        );
      } on FormatException {
        result[acc.id] = '------'; // secret sai định dạng
      }
    }
    return result;
  }

  // Chỉ TOTP của admin đang đăng nhập (account = adminId). Không bao giờ hiện mục của admin khác.
  List<AuthAccount> get _ownAccounts =>
      _accounts.where((a) => a.account == _adminId).toList();

  List<AuthAccount> get _filteredAccounts {
    final query = _searchController.text.trim().toLowerCase();
    final own = _ownAccounts;
    if (query.isEmpty) return own;
    return own.where((a) {
      return a.issuer.toLowerCase().contains(query) ||
          a.account.toLowerCase().contains(query);
    }).toList();
  }

  // Chỉ tài khoản kích hoạt từ server mới được lưu; demo thì không
  Iterable<AuthAccount> get _enrolledAccounts =>
      _accounts.where((a) => a.enrolled);

  // ---- Dữ liệu đã lưu ----

  Future<void> _loadStoredAccounts() async {
    final profile = await _auth.profile();
    if (!mounted) return;

    // Chưa đăng nhập: không hiện gì cả
    if (profile == null) {
      setState(() {
        _adminId = null;
        _store = null;
        _accounts.clear();
        _codes = {};
      });
      return;
    }

    final store = AccountStore(adminId: profile.adminId);
    final stored = await store.load(); // chỉ đọc key của admin này
    if (!mounted) return;

    setState(() {
      _adminId = profile.adminId;
      _store = store;
      _accounts
        ..clear()
        ..addAll(stored);
      _codes = _generateCodes();
    });
  }

  // ---- Hành động ----

  /// Bấm dấu "+": mở popup nhập khóa bí mật. Bấm Activate -> gọi server đối chiếu
  /// khóa (DB + Redis). Khớp thì 2FA được bật hoàn toàn và tài khoản được thêm vào danh sách.
  Future<void> _onAddAccount() async {
    FocusScope.of(context).unfocus(); // đóng bàn phím của ô tìm kiếm (nếu đang mở)

    final store = _store;
    final adminId = _adminId;
    if (store == null || adminId == null) {
      _showToast('Đang tải phiên đăng nhập, vui lòng thử lại');
      return;
    }

    // Các khóa đã có -> popup dùng để báo trùng
    final existingSecrets =
    _accounts.map((a) => TotpGenerator.normalizeSecret(a.secret)).toSet();

    // Thông số thật (chu kỳ, số chữ số, thuật toán) do server trả về lúc xác nhận
    EnrolledKey? enrolled;

    final secret = await showDialog<String>(
      context: context,
      // Tránh lỡ chạm ra ngoài làm mất khóa vừa nhập/dán; vẫn đóng được bằng nút Hủy
      barrierDismissible: false,
      builder: (_) => ActivateKeyDialog(
        existingSecrets: existingSecrets,
        onVerify: (enteredKey) async {
          try {
            enrolled = await _enrollment.activate(enteredKey);
            return null; // thành công
          } on EnrollmentException catch (e) {
            return e.message; // hiện dưới ô nhập, popup vẫn mở
          }
        },
      ),
    );

    final result = enrolled;
    if (secret == null || result == null || !mounted) return; // bấm Cancel

    // Server xác nhận khóa này thuộc admin khác với phiên đang đăng nhập: không thêm vào danh sách
    if (result.adminId != adminId) {
      _showToast('Khóa không thuộc tài khoản đang đăng nhập');
      return;
    }

    // Tài khoản mới: tên quản trị viên + adminId (thay cho email), mã đổi theo chu kỳ đã chọn
    final account = AuthAccount(
      id: 'admin_${result.adminId}',
      issuer: result.adminName,
      account: result.adminId,
      secret: secret,
      periodSeconds: result.periodSeconds,
      digits: result.digits,
      algorithm: result.algorithm,
      enrolled: true,
    );

    setState(() {
      _searchController.clear(); // bỏ lọc để thấy ngay tài khoản vừa thêm
      // Cài lại 2FA cho cùng adminId -> thay khóa cũ, không để 2 dòng
      _accounts.removeWhere((a) => a.id == account.id);
      _accounts.add(account);
      _codes = _generateCodes();
    });

    try {
      await store.save(_enrolledAccounts);
      if (mounted) _showToast('Kích hoạt thành công');
    } catch (_) {
      if (mounted) {
        _showToast('Đã kích hoạt, nhưng không lưu được khóa vào máy');
      }
    }
  }

  Future<void> _copyCode(String code) async {
    await Clipboard.setData(ClipboardData(text: code));
    if (!mounted) return;
    _showToast('Đã sao chép mã');
  }

  void _showToast(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: Text(message),
          duration: const Duration(milliseconds: 1500),
          behavior: SnackBarBehavior.floating,
        ),
      );
  }

  // ---- UI ----

  @override
  Widget build(BuildContext context) {
    final accounts = _filteredAccounts;

    return Scaffold(
      key: _scaffoldKey,
      backgroundColor: Colors.white,
      drawer: const AppMenuBar(),
      body: SafeArea(
        child: Column(
          children: [
            _buildTopBar(),
            _buildSearchBar(),
            const SizedBox(height: 8),
            Expanded(
              child: accounts.isEmpty
                  ? _buildEmptyState()
                  : ListView.separated(
                keyboardDismissBehavior:
                ScrollViewKeyboardDismissBehavior.onDrag,
                padding: EdgeInsets.zero,
                itemCount: accounts.length,
                separatorBuilder: (_, __) => const Divider(
                  height: 1,
                  thickness: 1,
                  indent: 80,
                  color: _dividerColor,
                ),
                itemBuilder: (context, index) {
                  final acc = accounts[index];
                  return _AccountTile(
                    key: ValueKey(acc.id),
                    account: acc,
                    code: _codes[acc.id] ?? '------',
                    clock: _clockFor(acc.periodSeconds),
                    period: acc.periodSeconds,
                    codeColor: _blueColor,
                    onTap: () => _copyCode(_codes[acc.id] ?? ''),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }

  // Hàng trên cùng: menu | tiêu đề | dấu "+"
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
              'Authenticator',
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
            onPressed: _onAddAccount,
            icon: const Icon(Icons.add, color: _blueColor, size: 30),
            tooltip: 'Thêm tài khoản',
          ),
        ],
      ),
    );
  }

  // Thanh tìm kiếm bo góc, nền xám nhạt
  Widget _buildSearchBar() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
      child: TextField(
        controller: _searchController,
        onChanged: (_) => setState(() {}),
        textInputAction: TextInputAction.search,
        style: const TextStyle(fontSize: 16),
        decoration: InputDecoration(
          hintText: 'Search',
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
    // Chưa có TOTP nào của admin này (khác với "tìm không ra" khi đang lọc)
    final noAccountsYet = _ownAccounts.isEmpty;
    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 32),
        child: Text(
          noAccountsYet
              ? 'Chưa có mã xác thực nào.\nBấm dấu + để thêm khóa bí mật.'
              : 'Không tìm thấy tài khoản',
          textAlign: TextAlign.center,
          style: const TextStyle(fontSize: 15, color: Colors.grey),
        ),
      ),
    );
  }
}

// ============================================================================
//  1 DÒNG TÀI KHOẢN: avatar | tên + email/adminId + mã 6 số + vòng đếm ngược | mũi tên >
// ============================================================================

class _AccountTile extends StatelessWidget {
  const _AccountTile({
    super.key,
    required this.account,
    required this.code,
    required this.clock,
    required this.period,
    required this.codeColor,
    required this.onTap,
  });

  final AuthAccount account;
  final String code; // 6 số liền nhau, vd "940555"
  final Animation<double> clock;
  final int period;
  final Color codeColor;
  final VoidCallback onTap;

  // "940555" -> "940 555"
  String get _formattedCode {
    if (code.length != 6) return code;
    return '${code.substring(0, 3)} ${code.substring(3)}';
  }

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap, // chạm để sao chép mã (đổi thành mở màn chi tiết nếu cần)
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 14, 12, 14),
        child: Row(
          // center: mũi tên nằm giữa chiều cao của cả dòng
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            // Nội dung chính. Row lồng bên trong giữ avatar bám mép trên như cũ.
            Expanded(
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const CircleAvatar(
                    radius: 24,
                    backgroundColor: Color(0xFF004B87),
                    child: Icon(
                      Icons.person_rounded,
                      color: Colors.white,
                      size: 28,
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          account.issuer,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w500,
                            color: Colors.black,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          account.account,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 14,
                            color: Colors.grey,
                          ),
                        ),
                        const SizedBox(height: 6),
                        Row(
                          crossAxisAlignment: CrossAxisAlignment.center,
                          children: [
                            Text(
                              _formattedCode,
                              style: TextStyle(
                                fontSize: 28,
                                fontWeight: FontWeight.w400,
                                color: codeColor,
                                letterSpacing: 1,
                                // Số cùng độ rộng -> mã không bị "nhảy" khi đổi
                                fontFeatures: const [
                                  FontFeature.tabularFigures(),
                                ],
                              ),
                            ),
                            const SizedBox(width: 12),
                            _CountdownRing(
                              clock: clock,
                              period: period,
                              color: codeColor,
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),

            // Mũi tên phải màu xám (muốn mảnh hơn: Icons.arrow_forward_ios_rounded, size 16)
            const SizedBox(width: 8),
            const Icon(Icons.chevron_right, color: Colors.grey, size: 24),
          ],
        ),
      ),
    );
  }
}

// ============================================================================
//  VÒNG ĐẾM NGƯỢC (hiệu ứng đồng hồ): cung xanh mòn dần theo chiều kim đồng hồ
//  + số giây ở giữa.
//  Chỉ vùng nhỏ này được vẽ lại theo từng frame, danh sách không bị rebuild.
// ============================================================================

class _CountdownRing extends StatelessWidget {
  const _CountdownRing({
    required this.clock,
    required this.period,
    required this.color,
  });

  final Animation<double> clock;
  final int period;
  final Color color;

  static const double _size = 28;
  static const int _warningSeconds = 5; // còn <= 5s thì chuyển đỏ

  @override
  Widget build(BuildContext context) {
    return RepaintBoundary(
      child: AnimatedBuilder(
        animation: clock,
        builder: (context, _) {
          final remaining = 1.0 - clock.value; // 1.0 -> 0.0
          final seconds = (remaining * period).ceil().clamp(1, period);
          final ringColor = seconds <= _warningSeconds ? Colors.red : color;

          return SizedBox(
            width: _size,
            height: _size,
            child: CustomPaint(
              painter: RingPainter(progress: remaining, color: ringColor),
              child: Center(
                child: Text(
                  '$seconds',
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w500,
                    color: ringColor,
                  ),
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}