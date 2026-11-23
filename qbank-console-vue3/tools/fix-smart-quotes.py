#!/usr/bin/env python3
"""
Sửa dấu nháy "thông minh" do copy-paste từ chat/web vào code JS/Vue.

Triệu chứng: `npm run build` báo
    SyntaxError: Error parsing JavaScript expression: Unterminated string constant
hoặc  `[vue/compiler-sfc] ... Unterminated string constant`
Nguyên nhân: ký tự ' (U+0027) bị thay bằng ’ (U+2019) — Visual Studio Code,
Word, Zalo, Discord, ChatGPT... đều có thể tự đổi khi copy.

Cách dùng:
    python3 tools/fix-smart-quotes.py                      # sửa file mặc định
    python3 tools/fix-smart-quotes.py path/a.vue path/b.js  # sửa nhiều file
    python3 tools/fix-smart-quotes.py --check              # CHỈ kiểm tra, không ghi

Mã thoát: 0 = sạch/đã sửa xong, 1 = còn lỗi (khi dùng --check).
"""
from __future__ import annotations

import pathlib
import re
import sys

DEFAULT_TARGETS = ["qbank-console-vue3/src/views/AccountView.vue"]

# Chỉ map những ký tự THỰC SỰ gây lỗi cú pháp. Không đụng — – … • để giữ nguyên
# nội dung hiển thị tiếng Việt trong template.
QUOTE_MAP = {
    "\u2018": "'",  # ‘
    "\u2019": "'",  # ’
    "\u201c": '"',  # “
    "\u201d": '"',  # ”
    "\u00a0": " ",  # NBSP
}

CONFLICT_PATTERNS = [
    re.compile(r"^<{7}[^\n]*\n?", re.M),  # <<<<<<< HEAD
    re.compile(r"^={7}\n?", re.M),        # =======
    re.compile(r"^>{7}[^\n]*\n?", re.M),  # >>>>>>> branch
]


def scan(text: str) -> list[tuple[int, str]]:
    """Trả về [(số dòng, ký tự)] của mọi smart quote còn lại."""
    found = []
    for lineno, line in enumerate(text.splitlines(), 1):
        for ch in QUOTE_MAP:
            if ch in line:
                found.append((lineno, f"U+{ord(ch):04X}"))
    return found


def fix(text: str) -> tuple[str, int, int]:
    quotes = 0
    for bad, good in QUOTE_MAP.items():
        quotes += text.count(bad)
        text = text.replace(bad, good)
    markers = 0
    for pattern in CONFLICT_PATTERNS:
        text, n = pattern.subn("", text)
        markers += n
    return text, quotes, markers


def main(argv: list[str]) -> int:
    check_only = "--check" in argv
    targets = [a for a in argv if not a.startswith("--")] or DEFAULT_TARGETS
    failed = False

    for target in targets:
        path = pathlib.Path(target)
        if not path.exists():
            print(f"[bỏ qua] không tồn tại: {target}")
            continue

        original = path.read_text(encoding="utf-8")

        if check_only:
            problems = scan(original)
            if problems:
                failed = True
                print(f"[LỖI] {target}: {len(problems)} smart quote")
                for lineno, code in problems[:10]:
                    print(f"        dòng {lineno}: {code}")
            else:
                print(f"[OK]   {target}")
            continue

        fixed, quotes, markers = fix(original)
        if fixed == original:
            print(f"[OK]   {target} — không cần sửa")
            continue

        path.write_text(fixed, encoding="utf-8")
        detail = []
        if quotes:
            detail.append(f"{quotes} dấu nháy")
        if markers:
            detail.append(f"{markers} dòng conflict marker")
        print(f"[SỬA]  {target} — {', '.join(detail) or 'nội dung'}")

    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
