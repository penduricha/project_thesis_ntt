# -*- coding: utf-8 -*-
"""
Pha 2: thay chuỗi tiếng Việt nằm TRONG biểu thức template
(vd. {{ a ? 'Quản trị viên' : 'Giảng viên' }}, :aria-label="'Bỏ ' + code(q)").

Chỉ đụng vào literal chuỗi nằm trong thẻ hoặc {{ }}; KHÔNG đụng <script> và <style>
(tầng đó là chuỗi trong JS, dịch sau nếu cần và phải theo ngữ cảnh).

Bắt buộc chạy `npx vite build` sau khi chạy script này. Script in ra bảng thay thế
để soát lại bằng mắt.
"""
import importlib.util
import pathlib
import re
import sys

spec = importlib.util.spec_from_file_location('app', 'scripts/i18n-apply.py')
app = importlib.util.module_from_spec(spec)
spec.loader.exec_module(app)

VI = re.compile(r'[À-ỹĐđ]')
# Chuỗi kỹ thuật: KHÔNG dịch, không bọc $t.
SKIP_VALUES = {'VITE_DEMO_MODE', 'qbank-export.xml', 'admin', 'gv', 'all', 'ok', 'wa', 'er', 'admin_id'}

LIT = re.compile(r"'((?:[^'\\]|\\.)*)'")


# Chuỗi trùng mặt chữ nhưng khác ngữ cảnh -> chọn khoá đúng theo file.
# (Bảng vi.js tra theo giá trị sẽ ra khoá ĐẦU TIÊN trùng giá trị, có thể sai ngữ cảnh.)
CONTEXT_OVERRIDE = {
    ('CourseDialog.vue', 'Cập nhật'): 'course.update',
    ('UserConfirmDialog.vue', 'Vô hiệu hóa'): 'admin.disableAccount',
    ('UserDetailDialog.vue', 'Vô hiệu hóa'): 'admin.disableAccount',
    ('UserConfirmDialog.vue', 'Xóa tài khoản'): 'admin.deleteAccount',
    ('UserDetailDialog.vue', 'Xóa tài khoản'): 'admin.deleteAccount',
    ('UserConfirmDialog.vue', 'Cấp lại mật khẩu'): 'admin.resetPwShort',
    ('UserDetailDialog.vue', 'Hoạt động'): 'admin.activeStatus',
    ('UserConfirmDialog.vue', ' tài khoản?'): 'admin.accountSuffixQ',
}


def rewrite_expr(expr, path, counter):
    base = path.name

    def sub(m):
        raw = m.group(1)
        key = CONTEXT_OVERRIDE.get((base, raw))
        if key is None and raw in app.VAL2KEY and raw not in SKIP_VALUES:
            key = app.VAL2KEY[raw]
        if key:
            counter.append((str(path), raw, key))
            return "$t('%s')" % key
        return m.group(0)
    return LIT.sub(sub, expr)


def process(path, write=False):
    src = path.read_text(encoding='utf-8')
    span = app.template_span(src)
    if not span:
        return []
    a, b = span
    tpl = src[a:b]
    pieces = []
    counter = []
    for kind, s, e, val in app.scan(tpl):
        if kind == 'tag':
            # chỉ đổi phần giá trị thuộc tính / biểu thức trong thẻ, giữ nguyên dấu nháy
            new = re.sub(
                r'((?::|@|v-[\w-]+:)[\w.-]+=")((?:[^"\\]|\\.)*)(")',
                lambda m: m.group(1) + rewrite_expr(m.group(2), path, counter) + m.group(3),
                val)
            pieces.append(new)
        elif kind == 'mustache':
            pieces.append('{{' + rewrite_expr(val[2:-2], path, counter) + '}}')
        else:
            pieces.append(val)
    if counter and write:
        path.write_text(src[:a] + ''.join(pieces) + src[b:], encoding='utf-8')
    return counter


def main():
    write = '--write' in sys.argv
    all_changes = []
    for f in sorted(pathlib.Path('src').rglob('*.vue')):
        all_changes += process(f, write)
    for p, old, key in all_changes:
        print(f"{p.replace('src/', ''):38} {old[:52]!r:56} -> {key}")
    print(f'\n{len(all_changes)} chỗ thay' + ('  [ĐÃ GHI]' if write else '  [DRY-RUN]'))


if __name__ == '__main__':
    main()
