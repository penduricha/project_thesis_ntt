import 'dart:math' as math;

import 'package:crypto/crypto.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../menu/MenuBarApp.dart';

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
//  TOTP (RFC 6238) - HMAC-SHA1, 6 số, chu kỳ 30 giây
//  Cần thêm vào pubspec.yaml:  crypto: ^3.0.3
// ============================================================================

class TotpGenerator {
  TotpGenerator._();

  /// Chuẩn hóa secret: viết hoa, bỏ khoảng trắng, dấu "-" và "="
  /// (vd: "abcd efgh-ijkl" -> "ABCDEFGHIJKL")
  static String normalizeSecret(String input) {
    return input.toUpperCase().replaceAll(RegExp(r'[\s=-]'), '');
  }

  static String generate(
      String base32Secret, {
        int period = 30,
        int digits = 6,
        DateTime? at,
      }) {
    final key = _base32Decode(base32Secret);
    final seconds = (at ?? DateTime.now()).millisecondsSinceEpoch ~/ 1000;
    final counter = seconds ~/ period;

    // Counter 8 byte big-endian (32 bit cao = 0 nên chỉ cần ghi 32 bit thấp)
    final message = Uint8List(8);
    ByteData.view(message.buffer).setUint32(4, counter);

    final hash = Hmac(sha1, key).convert(message).bytes;
    final offset = hash.last & 0x0f;
    final binary = ((hash[offset] & 0x7f) << 24) |
    ((hash[offset + 1] & 0xff) << 16) |
    ((hash[offset + 2] & 0xff) << 8) |
    (hash[offset + 3] & 0xff);

    final otp = binary % math.pow(10, digits).toInt();
    return otp.toString().padLeft(digits, '0');
  }

  static Uint8List _base32Decode(String input) {
    const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
    final clean = normalizeSecret(input);

    final bytes = <int>[];
    var buffer = 0;
    var bits = 0;
    for (final ch in clean.split('')) {
      final value = alphabet.indexOf(ch);
      if (value < 0) {
        throw FormatException('Secret key không hợp lệ: "$ch"');
      }
      buffer = (buffer << 5) | value;
      bits += 5;
      if (bits >= 8) {
        bits -= 8;
        bytes.add((buffer >> bits) & 0xFF);
        buffer &= (1 << bits) - 1; // giữ buffer nhỏ, tránh tràn số
      }
    }
    return Uint8List.fromList(bytes);
  }
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

  static const int _period = 30; // giây

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
      builder: (_) => _ActivateKeyDialog(existingSecrets: existingSecrets),
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

class _ActivateKeyDialog extends StatefulWidget {
  const _ActivateKeyDialog({required this.existingSecrets});

  /// Các khóa đã có trong danh sách (đã chuẩn hóa) để chặn thêm trùng
  final Set<String> existingSecrets;

  @override
  State<_ActivateKeyDialog> createState() => _ActivateKeyDialogState();
}

class _ActivateKeyDialogState extends State<_ActivateKeyDialog> {
  static const Color _brandColor = Color(0xFF004B87); // nút Activate
  static const Color _errorColor = Color(0xFFC61F1F);
  static const Color _fieldFill = Color(0xFFEFEFEF);
  static const int _minKeyLength = 16;

  final TextEditingController _controller = TextEditingController();
  String? _error;

  bool get _hasText => _controller.text.trim().isNotEmpty;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  // Dán nhanh từ clipboard (khóa bí mật thường dài, gõ tay dễ sai)
  Future<void> _paste() async {
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    final text = data?.text?.trim();
    if (text == null || text.isEmpty || !mounted) return;
    setState(() {
      _controller.value = TextEditingValue(
        text: text,
        selection: TextSelection.collapsed(offset: text.length),
      );
      _error = null;
    });
  }

  void _clear() {
    setState(() {
      _controller.clear();
      _error = null;
    });
  }

  String? _validate(String secret) {
    if (secret.isEmpty) return 'Vui lòng nhập khóa bí mật';
    if (!RegExp(r'^[A-Z2-7]+$').hasMatch(secret)) {
      return 'Khóa chỉ gồm chữ A–Z và số 2–7';
    }
    if (secret.length < _minKeyLength) {
      return 'Khóa phải có ít nhất $_minKeyLength ký tự';
    }
    if (widget.existingSecrets.contains(secret)) {
      return 'Khóa này đã được thêm trước đó';
    }
    return null;
  }

  void _onActivate() {
    final secret = TotpGenerator.normalizeSecret(_controller.text);
    final error = _validate(secret);
    if (error != null) {
      setState(() => _error = error);
      return;
    }
    Navigator.pop(context, secret);
  }

  OutlineInputBorder _border(Color color, [double width = 1]) {
    return OutlineInputBorder(
      borderRadius: BorderRadius.circular(12),
      borderSide: BorderSide(color: color, width: width),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Dialog(
      backgroundColor: Colors.white,
      surfaceTintColor: Colors.transparent,
      insetPadding: const EdgeInsets.symmetric(horizontal: 24, vertical: 24),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 400),
        // Cuộn được để không bị overflow khi bàn phím mở hoặc màn hình xoay ngang
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(24, 28, 24, 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Center(
                child: Container(
                  width: 60,
                  height: 60,
                  decoration: const BoxDecoration(
                    color: Color(0xFFE6EEF5),
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(
                    Icons.vpn_key_outlined,
                    color: _brandColor,
                    size: 28,
                  ),
                ),
              ),
              const SizedBox(height: 16),
              const Text(
                'Nhập khóa bí mật',
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontSize: 20,
                  fontWeight: FontWeight.w700,
                  color: Colors.black87,
                ),
              ),
              const SizedBox(height: 8),
              const Text(
                'Nhập hoặc dán khóa bí mật được cấp để kích hoạt mã xác thực 6 số.',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 14, height: 1.4, color: Colors.black54),
              ),
              const SizedBox(height: 20),
              _buildKeyField(),
              const SizedBox(height: 24),
              _buildFooter(),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildKeyField() {
    return TextField(
      maxLength: 30,
      controller: _controller,
      autofocus: true, // mở bàn phím ngay khi popup hiện
      minLines: 1,
      maxLines: 3, // khóa dài sẽ xuống dòng để nhìn được toàn bộ
      // Bàn phím ký tự thuần: tắt gợi ý/tự sửa, tự viết hoa
      keyboardType: TextInputType.visiblePassword,
      textCapitalization: TextCapitalization.characters,
      autocorrect: false,
      enableSuggestions: false,
      textInputAction: TextInputAction.done,
      onChanged: (_) => setState(() => _error = null),
      onSubmitted: (_) => _hasText ? _onActivate() : null,
      style: const TextStyle(fontSize: 16, letterSpacing: 1),
      decoration: InputDecoration(
        hintText: 'Nhập khóa bí mật',
        hintStyle: const TextStyle(
          color: Colors.grey,
          fontSize: 16,
          letterSpacing: 0,
        ),
        errorText: _error,
        errorMaxLines: 2,
        filled: true,
        fillColor: _fieldFill,
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        // Rỗng -> nút Dán; có chữ -> nút Xóa
        suffixIcon: _hasText
            ? IconButton(
          icon: const Icon(Icons.close, color: Colors.grey, size: 20),
          tooltip: 'Xóa',
          onPressed: _clear,
        )
            : IconButton(
          icon: const Icon(
            Icons.content_paste_rounded,
            color: _brandColor,
            size: 22,
          ),
          tooltip: 'Dán',
          onPressed: _paste,
        ),
        border: _border(Colors.transparent),
        enabledBorder: _border(Colors.transparent),
        focusedBorder: _border(_brandColor, 1.5),
        errorBorder: _border(_errorColor),
        focusedErrorBorder: _border(_errorColor, 1.5),
      ),
    );
  }

  // Footer: Hủy (viền) | Activate (nền xanh 0xFF004B87), 2 nút cùng kích thước
  Widget _buildFooter() {
    return Row(
      children: [
        Expanded(
          child: OutlinedButton(
            onPressed: () => Navigator.pop(context),
            style: OutlinedButton.styleFrom(
              minimumSize: const Size(0, 48),
              foregroundColor: Colors.black87,
              side: const BorderSide(color: Color(0xFFBDBDBD), width: 1.2),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(10),
              ),
              textStyle: const TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w600,
              ),
            ),
            child: const Text('Hủy'),
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: ElevatedButton(
            // Chưa nhập gì -> nút mờ, không bấm được
            onPressed: _hasText ? _onActivate : null,
            style: ElevatedButton.styleFrom(
              minimumSize: const Size(0, 48),
              backgroundColor: _brandColor,
              foregroundColor: Colors.white,
              disabledBackgroundColor: const Color(0x66004B87),
              disabledForegroundColor: Colors.white,
              elevation: 0,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(10),
              ),
              textStyle: const TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w600,
              ),
            ),
            child: const Text('Activate'),
          ),
        ),
      ],
    );
  }
}

// ============================================================================
//  1 DÒNG TÀI KHOẢN: avatar | tên + email + mã 6 số + vòng đếm ngược
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
        padding: const EdgeInsets.fromLTRB(16, 14, 16, 14),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const CircleAvatar(
              radius: 24,
              backgroundColor: Color(0xFF004B87),
              child: Icon(
                Icons.person_outline,
                color: Colors.white,
                size: 32,
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
                    style: const TextStyle(fontSize: 14, color: Colors.grey),
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
                          fontFeatures: const [FontFeature.tabularFigures()],
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
    );
  }
}

// ============================================================================
//  VÒNG ĐẾM NGƯỢC (hiệu ứng đồng hồ): cung tròn thu nhỏ dần + số giây ở giữa
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
              painter: _RingPainter(progress: remaining, color: ringColor),
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

class _RingPainter extends CustomPainter {
  _RingPainter({required this.progress, required this.color});

  final double progress; // phần còn lại: 1.0 (đầy) -> 0.0 (hết)
  final Color color;

  static const double _stroke = 2.5;

  @override
  void paint(Canvas canvas, Size size) {
    final rect = (Offset.zero & size).deflate(_stroke / 2);

    // Vòng nền mờ
    final track = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = _stroke
      ..color = color.withAlpha(46);
    canvas.drawArc(rect, 0, math.pi * 2, false, track);

    // Cung còn lại: bắt đầu từ 12 giờ, quay theo chiều kim đồng hồ
    final arc = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = _stroke
      ..strokeCap = StrokeCap.round
      ..color = color;
    canvas.drawArc(rect, -math.pi / 2, math.pi * 2 * progress, false, arc);
  }

  @override
  bool shouldRepaint(_RingPainter old) =>
      old.progress != progress || old.color != color;
}