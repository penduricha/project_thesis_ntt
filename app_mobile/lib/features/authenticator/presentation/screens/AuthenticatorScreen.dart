import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../menu/MenuBarApp.dart';
// Class sinh mã TOTP đã được tách riêng (cùng thư mục với file này)
import '../widgets/ActivateKeyDialog.dart';
import '../widgets/RingPainter.dart';
import 'TotpGenerator.dart';

// ============================================================================
//  MODEL
// ============================================================================

class AuthAccount {
  const AuthAccount({
    required this.id,
    required this.issuer,
    required this.account,
    required this.secret,
  });

  final String id;
  final String issuer; // vd: "Microsoft", "PAM360"
  final String account; // vd: email đăng nhập
  final String secret; // khóa bí mật dạng Base32
}

// ============================================================================
//  MÀN HÌNH
// ============================================================================

class AuthenticatorScreen extends StatefulWidget {
  const AuthenticatorScreen({super.key});

  @override
  State<AuthenticatorScreen> createState() => _AuthenticatorScreenState();
}

class _AuthenticatorScreenState extends State<AuthenticatorScreen>
    with SingleTickerProviderStateMixin, WidgetsBindingObserver {
  // ---- Màu sắc ----
  static const Color _blueColor = Color(0xFF004B87); // nút "+" góc trên phải
  static const Color _dividerColor = Color(0xFFEDEDED);
  static const Color _searchFill = Color(0xFFEFEFEF);

  static const int _period = TotpGenerator.defaultPeriod; // 30 giây

  final GlobalKey<ScaffoldState> _scaffoldKey = GlobalKey<ScaffoldState>();
  final TextEditingController _searchController = TextEditingController();

  // Đồng hồ chung: chạy 0 -> 1 trong 30s, đồng bộ theo giờ thật của máy
  late final AnimationController _clock;

  // TODO: lấy danh sách từ nơi lưu an toàn (vd: flutter_secure_storage / API)
  // Đây chỉ là dữ liệu demo. (Không dùng const cho list vì cần thêm tài khoản mới)
  final List<AuthAccount> _accounts = [
    const AuthAccount(
      id: '1',
      issuer: 'PAM360',
      account: 'tuquangnhat@company.com',
      secret: 'JBSWY3DPEHPK3PXP',
    ),
    const AuthAccount(
      id: '2',
      issuer: 'Microsoft',
      account: 'ben10siuuu41@outlook.com',
      secret: 'GEZDGNBVGY3TQOJQ',
    ),
    const AuthAccount(
      id: '3',
      issuer: 'Google',
      account: 'nhat.tu@gmail.com',
      secret: 'MFRGGZDFMZTWQ2LK',
    ),
  ];

  // Mã 6 số hiện tại của từng tài khoản (key = id), chỉ tính lại khi sang chu kỳ mới
  Map<String, String> _codes = {};

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);

    _codes = _generateCodes();

    _clock = AnimationController(
      vsync: this,
      duration: const Duration(seconds: _period),
    )..addStatusListener((status) {
      // Hết 30s -> tính lại mã và chạy chu kỳ mới (đồng bộ lại theo giờ thật)
      if (status == AnimationStatus.completed) _syncClock();
    });

    _clock.forward(from: _elapsedFraction);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _clock.dispose();
    _searchController.dispose();
    super.dispose();
  }

  // Quay lại app từ nền -> đồng hồ có thể đã lệch, đồng bộ lại ngay
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _syncClock();
  }

  /// Tỉ lệ thời gian đã trôi qua trong chu kỳ hiện tại (0.0 -> 1.0)
  double get _elapsedFraction {
    const periodMs = _period * 1000;
    final ms = DateTime.now().millisecondsSinceEpoch % periodMs;
    return ms / periodMs;
  }

  void _syncClock() {
    if (!mounted) return;
    setState(() => _codes = _generateCodes());
    _clock.forward(from: _elapsedFraction);
  }

  Map<String, String> _generateCodes() {
    final result = <String, String>{};
    for (final acc in _accounts) {
      try {
        result[acc.id] = TotpGenerator.generate(acc.secret, period: _period);
      } on FormatException {
        result[acc.id] = '------'; // secret sai định dạng
      }
    }
    return result;
  }

  List<AuthAccount> get _filteredAccounts {
    final query = _searchController.text.trim().toLowerCase();
    if (query.isEmpty) return _accounts;
    return _accounts.where((a) {
      return a.issuer.toLowerCase().contains(query) ||
          a.account.toLowerCase().contains(query);
    }).toList();
  }

  // ---- Hành động ----

  /// Bấm dấu "+": mở popup nhập khóa bí mật, bấm Activate để thêm tài khoản
  Future<void> _onAddAccount() async {
    FocusScope.of(context).unfocus(); // đóng bàn phím của ô tìm kiếm (nếu đang mở)

    // Các khóa đã có -> popup dùng để báo trùng
    final existingSecrets =
    _accounts.map((a) => TotpGenerator.normalizeSecret(a.secret)).toSet();

    final secret = await showDialog<String>(
      context: context,
      // Tránh lỡ chạm ra ngoài làm mất khóa vừa nhập/dán; vẫn đóng được bằng nút Hủy
      barrierDismissible: false,
      builder: (_) => ActivateKeyDialog(existingSecrets: existingSecrets),
    );

    if (secret == null || !mounted) return; // bấm Hủy

    // TODO: gọi API kích hoạt khóa (nếu có) và lưu secret vào flutter_secure_storage
    setState(() {
      _searchController.clear(); // bỏ lọc để thấy ngay tài khoản vừa thêm
      _accounts.add(
        AuthAccount(
          id: DateTime.now().microsecondsSinceEpoch.toString(),
          issuer: 'Tài khoản ${_accounts.length + 1}',
          account: 'Thêm bằng khóa bí mật',
          secret: secret,
        ),
      );
      _codes = _generateCodes();
    });

    _showToast('Kích hoạt thành công');
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
      drawer: const MenuBarApp(),
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
                    clock: _clock,
                    period: _period,
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
        'Không tìm thấy tài khoản',
        style: TextStyle(fontSize: 15, color: Colors.grey),
      ),
    );
  }
}

// ============================================================================
//  POPUP NHẬP KHÓA BÍ MẬT + NÚT ACTIVATE
//  Trả về secret đã chuẩn hóa (String) khi bấm Activate, hoặc null khi bấm Hủy.
// ============================================================================



// ============================================================================
//  1 DÒNG TÀI KHOẢN: avatar | tên + email + mã 6 số + vòng đếm ngược | mũi tên >
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

