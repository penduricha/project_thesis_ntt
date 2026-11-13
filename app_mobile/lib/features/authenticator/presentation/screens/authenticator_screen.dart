import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../main_shell/presentation/widgets/app_menu_bar.dart';
// TOTP code generation class has been separated (in the same folder as this file)
import '../../data/model/auth_account.dart';
import '../../data/service/account_store.dart';
import '../../data/service/enrollment_service.dart';
import '../widgets/activate_key_dialog.dart';
import '../widgets/ring_painter.dart';
import 'totp_generator.dart';

// AuthAccount has been moved to data/model/auth_account.dart. Re-export so other files
// importing AuthAccount from this file still work normally.
export '../../data/model/auth_account.dart';

// ============================================================================
//  SCREEN
// ============================================================================

class AuthenticatorScreen extends StatefulWidget {
  const AuthenticatorScreen({super.key, this.auth});

  /// Login session used to call the 2FA activation API (access token + adminId + name).
  /// If not passed -> read from flutter_secure_storage (see SecureStorageEnrollmentAuth).
  final EnrollmentAuth? auth;

  @override
  State<AuthenticatorScreen> createState() => _AuthenticatorScreenState();
}

class _AuthenticatorScreenState extends State<AuthenticatorScreen>
    with TickerProviderStateMixin, WidgetsBindingObserver {
  // ---- Colors ----
  static const Color _blueColor = Color(0xFF004B87); // "+" button top right
  static const Color _dividerColor = Color(0xFFEDEDED);
  static const Color _searchFill = Color(0xFFEFEFEF);

  final GlobalKey<ScaffoldState> _scaffoldKey = GlobalKey<ScaffoldState>();
  final TextEditingController _searchController = TextEditingController();

  // Each period (15 / 30 / 45 / 60 seconds) has a shared clock: runs 0 -> 1,
  // synchronized with the device's real time. Accounts with the same period share the clock.
  final Map<int, AnimationController> _clocks = {};

  late final EnrollmentAuth _auth;
  late final EnrollmentService _enrollment;

  // Current login session. Only TOTP of _adminId is loaded and displayed.
  // null = not loaded yet / not logged in -> list is empty.
  String? _adminId;
  AccountStore? _store; // key saved separately by _adminId

  // TOTP of the logged-in admin (activated from server, saved on device). No more demo data.
  final List<AuthAccount> _accounts = [];

  // Current 6-digit code of each account (key = id), recalculated only on a new period
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

  // Returning to app from background -> clock might be out of sync, resync immediately
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _syncAllClocks();
  }

  /// Clock for [period] seconds; if not exists, create and run immediately based on real time.
  AnimationController _clockFor(int period) {
    return _clocks.putIfAbsent(period, () {
      final clock = AnimationController(
        vsync: this,
        duration: Duration(seconds: period),
      )..addStatusListener((status) {
        // End of 1 period -> recalculate code and run new period (resync based on real time)
        if (status == AnimationStatus.completed) _onClockCompleted(period);
      });
      clock.forward(from: _elapsedFraction(period));
      return clock;
    });
  }

  /// Elapsed time fraction in the current [period] (0.0 -> 1.0)
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
        result[acc.id] = '------'; // invalid secret format
      }
    }
    return result;
  }

  // Only TOTP of the logged-in admin (account = adminId). Never show other admins' items.
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

  // Only accounts activated from server are saved; demo ones are not
  Iterable<AuthAccount> get _enrolledAccounts =>
      _accounts.where((a) => a.enrolled);

  // ---- Stored Data ----

  Future<void> _loadStoredAccounts() async {
    final profile = await _auth.profile();
    if (!mounted) return;

    // Not logged in: show nothing
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
    final stored = await store.load(); // only read keys of this admin
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

  // ---- Actions ----

  /// Tap "+": open popup to enter secret key. Tap Activate -> call server to verify
  /// key (DB + Redis). If matched, 2FA is fully enabled and the account is added to the list.
  Future<void> _onAddAccount() async {
    FocusScope.of(context).unfocus(); // close search keyboard (if open)

    final store = _store;
    final adminId = _adminId;
    if (store == null || adminId == null) {
      _showToast('Loading login session, please try again');
      return;
    }

    // Existing keys -> popup used to report duplicates
    final existingSecrets =
    _accounts.map((a) => TotpGenerator.normalizeSecret(a.secret)).toSet();

    // Real parameters (period, digits, algorithm) returned by server upon verification
    EnrolledKey? enrolled;

    final secret = await showDialog<String>(
      context: context,
      // Prevent accidental outside tap from losing entered/pasted key; can still close with Cancel button
      barrierDismissible: false,
      builder: (_) => ActivateKeyDialog(
        existingSecrets: existingSecrets,
        onVerify: (enteredKey) async {
          try {
            enrolled = await _enrollment.activate(enteredKey);
            return null; // success
          } on EnrollmentException catch (e) {
            return e.message; // show under input, popup remains open
          }
        },
      ),
    );

    final result = enrolled;
    if (secret == null || result == null || !mounted) return; // tap Cancel

    // Server confirms this key belongs to an admin different from the current session: do not add to list
    if (result.adminId != adminId) {
      _showToast('Key does not belong to the current logged-in account');
      return;
    }

    // New account: admin name + adminId (instead of email), code changes according to selected period
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
      _searchController.clear(); // clear filter to immediately see newly added account
      // Re-setup 2FA for the same adminId -> replace old key, do not leave 2 rows
      _accounts.removeWhere((a) => a.id == account.id);
      _accounts.add(account);
      _codes = _generateCodes();
    });

    try {
      await store.save(_enrolledAccounts);
      if (mounted) _showToast('Activated successfully');
    } catch (_) {
      if (mounted) {
        _showToast('Activated, but failed to save the key to the device');
      }
    }
  }

  Future<void> _copyCode(String code) async {
    await Clipboard.setData(ClipboardData(text: code));
    if (!mounted) return;
    _showToast('Code copied');
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

  // Top row: menu | title | "+" button
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
            tooltip: 'Add account',
          ),
        ],
      ),
    );
  }

  // Rounded search bar, light gray background
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
    // No TOTP yet for this admin (different from "not found" when filtering)
    final noAccountsYet = _ownAccounts.isEmpty;
    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 32),
        child: Text(
          noAccountsYet
              ? 'No authenticator codes yet.\nTap + to add a secret key.'
              : 'Account not found',
          textAlign: TextAlign.center,
          style: const TextStyle(fontSize: 15, color: Colors.grey),
        ),
      ),
    );
  }
}

// ============================================================================
//  1 ACCOUNT ROW: avatar | name + email/adminId + 6-digit code + countdown ring | arrow >
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
  final String code; // 6 contiguous digits, e.g. "940555"
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
      onTap: onTap, // tap to copy code (change to open detail screen if needed)
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 14, 12, 14),
        child: Row(
          // center: arrow is centered in the row height
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            // Main content. Inner Row keeps avatar aligned to top as before.
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
                                // Tabular figures -> code doesn't "jump" when changing
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

            // Gray right arrow (for thinner: Icons.arrow_forward_ios_rounded, size 16)
            const SizedBox(width: 8),
            const Icon(Icons.chevron_right, color: Colors.grey, size: 24),
          ],
        ),
      ),
    );
  }
}

// ============================================================================
//  COUNTDOWN RING (clock effect): blue arc wears down clockwise
//  + seconds in the middle.
//  Only this small area is repainted per frame, the list is not rebuilt.
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
  static const int _warningSeconds = 5; // <= 5s remaining turns red

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