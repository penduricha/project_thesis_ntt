# -*- coding: utf-8 -*-
"""
Thay chuỗi tiếng Việt trong <template> của các SFC bằng $t('khoá').

Vì sao cần bộ quét thẻ thật thay vì regex `>([^<>]+)<`:
  Thuộc tính HTML có thể chứa dấu `>` (vd. v-if="a > b"), và regex thô sẽ coi phần
  sau dấu `>` đó là text node rồi cắt nát câu lệnh. Bộ quét dưới đây đi từng ký tự,
  khi gặp `"` hoặc `'` thì nhảy qua hết chuỗi thuộc tính.
"""
import re
import pathlib
import sys
import html

ROOT = pathlib.Path('src')

# ---------------------------------------------------------------- nạp bảng dịch
def load_value_map():
    src = (ROOT / 'i18n/locales/vi.js').read_text(encoding='utf-8')
    pairs = re.findall(r"^\s*'([^']+)':\s*'((?:[^'\\]|\\.)*)',", src, re.M)
    m = {}
    for k, v in pairs:
        v = v.replace("\\'", "'")
        m.setdefault(v, k)
    return m

VAL2KEY = load_value_map()

# Khoá KHÔNG được dùng cho thay thế tự động vì quá ngắn / dễ trùng nhầm.
# (Đã không đưa các mảnh như 'n', 'Z', 'Ctrl' vào bảng dịch nên không cần lọc thêm,
#  nhưng giữ danh sách này để sau này thêm khoá mới vẫn an toàn.)
NEVER = set()

def norm(s):
    return re.sub(r'[ \t\r\n]+', ' ', s).strip()

# ------------------------------------------------------------- tách khối template
def template_span(src):
    """Trả (start, end) của phần BÊN TRONG thẻ <template> ngoài cùng, hoặc None."""
    m = re.search(r'<template(\s[^>]*)?>', src)
    if not m:
        return None
    i = m.end()
    depth = 1
    for mm in re.finditer(r'<template\b|</template\s*>', src[i:]):
        depth += 1 if mm.group(0).startswith('<template') else -1
        if depth == 0:
            return (i, i + mm.start())
    return None

# ------------------------------------------------------------------- quét thẻ
def scan(tpl):
    """
    Sinh ra danh sách ('text'|'tag', start, end, nội dung) cho một khối template.
    Nhảy qua comment <!-- --> và biểu thức {{ }}.
    """
    out = []
    i, n = 0, len(tpl)
    buf_start = i
    while i < n:
        c = tpl[i]
        if c == '<':
            if tpl.startswith('<!--', i):
                j = tpl.find('-->', i + 4)
                j = n if j < 0 else j + 3
                out.append(('other', i, j, tpl[i:j]))
                i = j
            else:
                # quét tới '>' thật, bỏ qua '>' nằm trong dấu nháy
                j = i + 1
                q = None
                while j < n:
                    ch = tpl[j]
                    if q:
                        if ch == q:
                            q = None
                    elif ch in '"\'':
                        q = ch
                    elif ch == '>':
                        break
                    j += 1
                j = min(j + 1, n)
                out.append(('tag', i, j, tpl[i:j]))
                i = j
        else:
            j = i
            while j < n and tpl[j] != '<':
                j += 1
            seg = tpl[i:j]
            # tách {{ }} ra khỏi text để không thay vào biểu thức
            pos = 0
            for mm in re.finditer(r'\{\{.*?\}\}', seg, re.S):
                if mm.start() > pos:
                    out.append(('text', i + pos, i + mm.start(), seg[pos:mm.start()]))
                out.append(('mustache', i + mm.start(), i + mm.end(), mm.group(0)))
                pos = mm.end()
            if pos < len(seg):
                out.append(('text', i + pos, i + j, seg[pos:]))
            elif pos == 0 and seg:
                out.append(('text', i, j, seg))
            i = j
    return out

# ------------------------------------------------------------ thay trong thuộc tính
ATTRS = ('placeholder', 'aria-label', 'title', 'alt')

def rewrite_tag(tag):
    """Đổi placeholder="Câu tiếng Việt" -> :placeholder="$t('khoá')" (chỉ khi tĩnh)."""
    def sub(m):
        name, q, val = m.group(1), m.group(2), m.group(3)
        key = VAL2KEY.get(html.unescape(val).strip()) or VAL2KEY.get(val.strip())
        if not key or key in NEVER:
            return m.group(0)
        return f':{name}={q}$t(\'{key}\'){q}'
    # thuộc tính TĨNH: không có dấu ':' hoặc 'v-bind' phía trước
    pattern = r'(?<![:@\w-])(' + '|'.join(ATTRS) + r')=(["\'])((?:(?!\2).)*)\2'
    return re.sub(pattern, sub, tag)

# ------------------------------------------------------------------ chạy
def process(path):
    src = path.read_text(encoding='utf-8')
    span = template_span(src)
    if not span:
        return 0
    a, b = span
    tpl = src[a:b]
    toks = scan(tpl)
    pieces = []
    changed = 0
    for kind, s, e, val in toks:
        if kind == 'text':
            raw = val
            key = VAL2KEY.get(norm(raw)) or VAL2KEY.get(html.unescape(raw).strip())
            if key and key not in NEVER:
                lead = raw[:len(raw) - len(raw.lstrip())]
                trail = raw[len(raw.rstrip()):]
                pieces.append(f'{lead}{{{{ $t(\'{key}\') }}}}{trail}')
                changed += 1
                continue
            pieces.append(raw)
        elif kind == 'tag':
            new = rewrite_tag(val)
            if new != val:
                changed += 1
            pieces.append(new)
        else:
            pieces.append(val)
    if not changed:
        return 0
    new_src = src[:a] + ''.join(pieces) + src[b:]
    path.write_text(new_src, encoding='utf-8')
    return changed

def main():
    dry = '--write' not in sys.argv
    total_f = 0
    total_c = 0
    for f in sorted(ROOT.rglob('*.vue')):
        c = process(f) if not dry else count_only(f)
        if c:
            total_f += 1
            total_c += c
            print(f'{c:4}  {f}')
    print(f'\n{total_f} file, {total_c} chỗ thay' + ('  [DRY-RUN]' if dry else '  [ĐÃ GHI]'))

def count_only(path):
    """Đếm mà không ghi: chạy process trên bản sao trong bộ nhớ."""
    src = path.read_text(encoding='utf-8')
    span = template_span(src)
    if not span:
        return 0
    a, b = span
    tpl = src[a:b]
    c = 0
    for kind, s, e, val in scan(tpl):
        if kind == 'text':
            if VAL2KEY.get(norm(val)) or VAL2KEY.get(html.unescape(val).strip()):
                c += 1
        elif kind == 'tag':
            if rewrite_tag(val) != val:
                c += 1
    return c

if __name__ == '__main__':
    main()
