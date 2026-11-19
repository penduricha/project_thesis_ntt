/// Giờ hiển thị của chat: server luôn trả UTC; app hiển thị theo UTC+7 (giờ Việt Nam) cố định,
/// không phụ thuộc múi giờ của máy, để mọi người trong tổ chức thấy cùng một giờ.
class ChatTime {
  ChatTime._();

  static const Duration _offset = Duration(hours: 7);

  /// Đổi một thời điểm UTC sang "đồng hồ" UTC+7 (đối tượng DateTime có year/month/day/hour theo giờ Việt Nam).
  static DateTime toDisplay(DateTime utc) => utc.toUtc().add(_offset);

  static DateTime nowDisplay() => toDisplay(DateTime.now());

  static String hhmm(DateTime utc) {
    final d = toDisplay(utc);
    return '${d.hour.toString().padLeft(2, '0')}:${d.minute.toString().padLeft(2, '0')}';
  }

  static int _daysAgo(DateTime utc) {
    final now = nowDisplay();
    final d = toDisplay(utc);
    final today = DateTime.utc(now.year, now.month, now.day);
    final day = DateTime.utc(d.year, d.month, d.day);
    return today.difference(day).inDays;
  }

  /// Giờ ngắn cho danh sách cuộc trò chuyện: hôm nay "HH:mm", hôm qua "Hôm qua", cũ hơn "dd/MM".
  static String listLabel(DateTime utc) {
    final days = _daysAgo(utc);
    if (days <= 0) return hhmm(utc);
    if (days == 1) return 'Hôm qua';
    final d = toDisplay(utc);
    return '${d.day.toString().padLeft(2, '0')}/${d.month.toString().padLeft(2, '0')}';
  }

  /// Tiêu đề ngày giữa các tin: "HH:mm, Hôm nay" / "HH:mm, Hôm qua" / "HH:mm, d/M/yyyy".
  static String headerLabel(DateTime utc) {
    final days = _daysAgo(utc);
    final time = hhmm(utc);
    if (days <= 0) return '$time, Hôm nay';
    if (days == 1) return '$time, Hôm qua';
    final d = toDisplay(utc);
    return '$time, ${d.day}/${d.month}/${d.year}';
  }
}
