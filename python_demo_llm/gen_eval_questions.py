# -*- coding: utf-8 -*-
"""
Sinh bộ 1000 câu hỏi trắc nghiệm tiếng Việt/Anh CÓ GÁN NHÃN (gold label) cho demo đánh giá AI.

gold_issue_type: none | spelling | grammar | logic | distractor
  - none       : câu hỏi sạch (không lỗi)
  - spelling   : sai chính tả tất định (bỏ/đổi dấu tiếng Việt hoặc lỗi chính tả tiếng Anh phổ biến)
  - grammar    : lỗi ngữ pháp tất định (tiếng Anh: chia động từ, mạo từ, giới từ, thì...) — stem/phương án vẫn giữ nghĩa
  - logic      : đáp án đúng (correct_label) bị đổi sang phương án sai  -> không còn đáp án đúng
  - distractor : một phương án sai bị thay bằng phương án kém (trùng/đồng nghĩa đáp án đúng, hoặc lạc phạm trù)

Mục đích: nạp vào bảng ntt_demo_questions của server-ai-worker (chỉ demo, KHÔNG tích hợp ER v4).
Chạy:  python3 gen_eval_questions.py
Output: server-ai-worker/src/main/resources/demo/questions-1000.json
"""
import hashlib
import json
import random
import re
import sys
from collections import Counter, OrderedDict

SEED = 20261120
rng = random.Random(SEED)

OUT_PATH = "server-ai-worker/src/main/resources/demo/questions-1000.json"

# ----------------------------------------------------------------------
# Danh mục lĩnh vực (12 subject x ~83 câu = 1000)
# ----------------------------------------------------------------------
SUBJECTS = OrderedDict([
    ("INT3202", ("Cơ sở dữ liệu", ["Mô hình quan hệ & Khóa", "Ngôn ngữ truy vấn SQL",
                                     "Chuẩn hóa dữ liệu", "NoSQL & Lưu trữ phân tán"])),
    ("INT3304", ("Mạng máy tính", ["Kiến trúc phân tầng OSI/TCP-IP", "Giao thức ứng dụng HTTP/REST",
                                     "Bảo mật mạng & Xác thực"])),
    ("INT3306", ("Lập trình Java", ["Cú pháp & OOP cơ bản", "Ngoại lệ & Collections",
                                     "Spring Boot & REST API"])),
    ("INT3401", ("Trí tuệ nhân tạo", ["Tìm kiếm & Biểu diễn tri thức", "Học máy & Mạng nơ-ron",
                                        "Mô hình ngôn ngữ lớn (LLM)"])),
    ("TOEIC", ("Tiếng Anh TOEIC", ["Part 5 - Ngữ pháp & Từ loại", "Part 6 - Hoàn thành đoạn văn"])),
    ("MATH", ("Toán học", ["Đại số & Giải phương trình", "Số học & Tổ hợp", "Hình học & Vectơ"])),
    ("PHYS", ("Vật lý", ["Cơ học & Chuyển động", "Điện học & Ohm", "Sóng & Quang"])),
    ("CHEM", ("Hóa học", ["Bảng tuần hoàn & Cấu hình electron", "Mol & Dung dịch", "Liên kết & Phản ứng"])),
    ("HIST", ("Lịch sử Việt Nam", ["Thời kỳ phong kiến & Kháng Pháp Mông", "Kháng chiến chống Pháp",
                                     "Hiện đại 1945-1975"])),
    ("BIO", ("Sinh học", ["Tế bào & Di truyền", "Sinh thái & Tiến hóa", "Cơ thể người"])),
])

# Hạn ngạch nhãn lỗi TOÀN CỤC (tổng 1000)
LABEL_QUOTA = OrderedDict([("none", 250), ("spelling", 170), ("grammar", 165),
                           ("logic", 200), ("distractor", 215)])
assert sum(LABEL_QUOTA.values()) == 1000

# Hạn ngạch nhãn lỗi / mỗi môn (9 môn Việt 100 câu + TOEIC 100 câu), nới tay để khớp quota toàn cục
PER_DOMAIN_VN = OrderedDict([("none", 25), ("spelling", 17), ("grammar", 15),
                             ("logic", 21), ("distractor", 22)])   # = 100
PER_DOMAIN_TOEIC = OrderedDict([("none", 25), ("spelling", 17), ("grammar", 30),
                                ("logic", 11), ("distractor", 17)])  # = 100
assert sum(PER_DOMAIN_VN.values()) == 100 and sum(PER_DOMAIN_TOEIC.values()) == 100
assert sum(9 * v for v in PER_DOMAIN_VN.values()) + sum(PER_DOMAIN_TOEIC.values()) == 1000

# ----------------------------------------------------------------------
# Từ điển lỗi chính tả tiếng Việt (chỉ giữ cặp AN TOÀN: dạng sai không phải từ có nghĩa)
# ----------------------------------------------------------------------
VN_MISSPELL = {
    "được": "đươc", "không": "khong", "làm": "lam", "nước": "nươc", "người": "ngươi",
    "học": "hoc", "với": "vơi", "biết": "biêt", "đến": "đên", "giữa": "giưa",
    "chính": "chinh", "viết": "viêt", "thiếu": "thieu", "hút": "hut", "muối": "muôi",
    "núi": "nui", "mùi": "mui", "rượu": "rươu", "hiệu": "hiêu", "dùng": "dung",
    "nhưng": "nhung", "phải": "phai", "thưởng": "thương", "thương": "thương",  # placeholder sẽ lọc
    "điện": "điên", "càng": "cang", "bằng": "bặng", "thường": "thương", "quanh": "quanh",
    "gian": "gian", "quang": "quang", "tác": "tac", "thức": "thưc", "cần": "can",
    "công": "cong", "trình": "trinh", "nhiệt": "nhiêt", "khối": "khôi", "đổi": "đôi",
    "máy": "may", "cháy": "chay", "thấy": "thay", "này": "nảy", "vấn": "vân",
    "đề": "để", "trễ": "tre", "khỏe": "khoe", "trưa": "trua", "xảy": "xay",
    "lỗi": "lổi", "mỗi": "mổi", "tối": "tổi", "buổi": "buỗi", "cuối": "cuỗi",
    "dữ": "dử", "ngữ": "ngử", "thỏa": "thõa", "nhỏ": "như", "nhó": "nhỏ",
    "cây": "cày", "trái": "trãi", "đấy": "đây", "tại": "tai",
}
# Lọc cặp không an toàn (dạng "sai" trùng từ có nghĩa hoặc không đổi ký tự)
VN_MISSPELL = {k: v for k, v in VN_MISSPELL.items()
               if k != v and v not in {"thưởng", "thương", "quanh", "gian", "quang",
                                       "điện", "tối", "nhỏ", "nhó", "cây", "đấy"}}

# Bổ sung cặp bỏ-dấu AN TOÀN cho từ vựng hay gặp trong stem (dạng sai không phải từ có nghĩa)
VN_MISSPELL.update({
    "nguyên": "ngyên", "diện": "diên", "giá": "gía", "gia": "gía", "tổng": "tỗng",
    "điểm": "điem", "biểu": "bieu", "hàm": "ham", "số": "sô", "tam": "tảm",
    "dịch": "dich", "phản": "phan", "bảng": "bang", "nhất": "nhât", "năng": "nang",
    "thương": "thuong", "quang": "qang", "lượng": "lươn",
    "vận": "vân", "bóng": "bong",
    "tiếng": "tieng", "thời": "thoi",
    "lớp": "lơp",
    "viên": "vien", "nhân": "nhan",
    "hỏi": "hoi", "trả": "tra", "lời": "lơi",
    "mềm": "mem",
})
# loại bỏ các cặp bị trùng khóa/không an toàn lần nữa
VN_MISSPELL.update({"của": "cùa", "nghiệm": "ngiem"})
VN_MISSPELL = {k: v for k, v in VN_MISSPELL.items() if k != v}

EN_MISSPELL = {
    "receive": "recieve", "separate": "seperate", "because": "beacuse",
    "occurred": "occured", "accommodation": "accomodation", "necessary": "neccessary",
    "definitely": "definately", "environment": "enviroment", "recommend": "reccomend",
    "tomorrow": "tommorrow", "beginning": "begining", "business": "buisness",
    "calendar": "calender", "colleague": "collegue", "guarantee": "guarentee",
    "immediately": "immediatly", "maintenance": "maintainance", "occurrence": "occurence",
    "personnel": "personel", "successful": "succesful", "writing": "writting",
}

# Từ vô tri ngoài phạm trù cho lỗi "distractor lạc"
ABSURD_OPTIONS = [
    "Màu của hoàng hôn trên biển", "Hương vị của cà phê phin", "Tiếng vỗ cánh của chim bồ câu",
    "Cơm gà xối mỡ ăn kèm nước mắm", "Nhiệt độ sôi của nước là 100 độ C", "Bảy ngày trong một tuần",
    "Mùi hương của hoa nhài buổi sớm", "Âm thanh của còi tàu hỏa", "Vị của nước chanh đường",
    "Mẫu áo dài truyền thống Việt Nam",
]

def esc(text):
    return re.escape(text)

# ======================================================================
# CẤU TRÚC ITEM
# ======================================================================
def mk(subject, topic, diff, expl, stem, options, correct, stem_en=None, explanation=None):
    assert correct in (0, 1, 2, 3)
    assert len(options) == 4
    seen = {o.strip() for o in options}
    if len(seen) != 4:
        raise ValueError("Trùng đáp án trong item: %s" % stem[:60])
    return {
        "subject_code": subject,
        "subject_name": SUBJECTS[subject][0],
        "topic_name": topic,
        "stem": stem,
        "option_a": options[0], "option_b": options[1],
        "option_c": options[2], "option_d": options[3],
        "correct_idx": correct,
        "difficulty": diff,
        "explanation": explanation or expl,
        "stem_en": stem_en,
    }

# ======================================================================
# MATH (parameterized, an toàn)
# ======================================================================
def pool_math():
    items = []
    A = "MATH"
    # 1) Giải phương trình bậc nhất ax + b = c (a in 2..9, x in 2..19)
    used = set()
    for _ in range(34):
        a = rng.randint(2, 9); x = rng.randint(2, 19); b = rng.randint(1, 30)
        c = a * x + b
        stem = "Giải phương trình %dx + %d = %d." % (a, b, c)
        opts = sorted({x, x + 1, x - 1, x + 2})
        while len(opts) < 4:
            opts = sorted(set(opts) | {x + rng.randint(3, 6)})
        opts = [str(v) for v in opts[:4]]
        items.append(mk(A, "Đại số & Giải phương trình", 2,
                        "x = (c - b)/a = (%d - %d)/%d = %d." % (c, b, a, x),
                        stem, opts, opts.index(str(x)),
                        stem_en="Solve the equation %dx + %d = %d." % (a, b, c)))
    # 2) Phần trăm của một số
    for p, n in [(5, 240), (10, 350), (15, 200), (20, 450), (25, 360), (30, 300),
                 (40, 150), (50, 88), (60, 125), (75, 120), (80, 75), (12, 250),
                 (35, 400), (45, 160), (16, 175), (55, 180), (26, 250), (11, 500)]:
        val = p * n // 100
        stem = "Tính %d%% của %d." % (p, n)
        cand = sorted({val, val + 5, val - 5, val * 2, val + 10, val + 2, val - 10})
        opts = [str(v) for v in cand[:4]]
        items.append(mk(A, "Đại số & Giải phương trình", 1,
                        "%d%% của %d = %d." % (p, n, val),
                        stem, opts, opts.index(str(val)),
                        stem_en="What is %d%% of %d?" % (p, n)))
    # 3) ƯCLN / BCNN
    for a, b in [(12, 18), (14, 21), (16, 24), (18, 30), (20, 25), (15, 25),
                 (24, 36), (28, 42), (9, 15), (45, 60)]:
        from math import gcd
        g = gcd(a, b)
        stem = "Ước chung lớn nhất (ƯCLN) của %d và %d là bao nhiêu?" % (a, b)
        cand = sorted({g, g * 2, g + 1, g + 2, 1, 2})
        opts = [str(v) for v in cand[:4]]
        items.append(mk(A, "Số học & Tổ hợp", 3,
                        "ƯCLN(%d, %d) = %d." % (a, b, g),
                        stem, opts, opts.index(str(g)),
                        stem_en="What is the greatest common divisor of %d and %d?" % (a, b)))
    # 4) BCNN
    from math import gcd
    for a, b in [(4, 6), (6, 9), (8, 12), (10, 15), (12, 18), (6, 10), (9, 12), (14, 21)]:
        l = a * b // gcd(a, b)
        stem = "Bội chung nhỏ nhất (BCNN) của %d và %d là bao nhiêu?" % (a, b)
        cand = sorted({l, l + a, l - b, a * b if a * b != l else l + 2, l // 2})
        opts = [str(v) for v in cand if v > 0][:4]
        items.append(mk(A, "Số học & Tổ hợp", 3,
                        "BCNN(%d, %d) = %d." % (a, b, l),
                        stem, opts, opts.index(str(l)),
                        stem_en="What is the least common multiple of %d and %d?" % (a, b)))
    # 5) Tổ hợp
    from math import comb
    for n, k in [(10, 2), (8, 2), (12, 2), (9, 2), (7, 2), (11, 2), (6, 3), (10, 3)]:
        v = comb(n, k)
        stem = "Có bao nhiêu cách chọn %d phần tử từ %d phần tử phân biệt (không kể thứ tự)?" % (k, n)
        cand = sorted({v, comb(n, k) + n, v + 10, v - 5, v + 5, v * 2})
        opts = [str(x) for x in cand if x > 0][:4]
        items.append(mk(A, "Số học & Tổ hợp", 4,
                        "C(%d, %d) = %d." % (n, k, v),
                        stem, opts, opts.index(str(v)),
                        stem_en="How many ways are there to choose %d elements from %d?" % (k, n)))
    # 6) Diện tích tam giác
    for base, h in [(6, 5), (8, 7), (10, 4), (12, 9), (16, 3), (14, 6), (20, 11)]:
        v = base * h // 2
        stem = "Tam giác có cạnh đáy %d cm và chiều cao tương ứng %d cm thì diện tích là bao nhiêu?" % (base, h)
        cand = sorted({v, v + 8, v - 6, base * h, v + 10})
        opts = ["%d cm²" % x for x in cand[:4]]
        items.append(mk(A, "Hình học & Vectơ", 2,
                        "S = đáy × cao / 2 = %d × %d / 2 = %d." % (base, h, v),
                        stem, opts, opts.index("%d cm²" % v),
                        stem_en="A triangle has a base of %d cm and a height of %d cm. What is its area?" % (base, h)))
    # 7) Vectơ tích vô hướng
    for (x1, y1), (x2, y2) in [((3, 4), (1, 2)), ((2, -1), (4, 3)), ((5, 2), (3, 6)), ((1, 7), (2, 5))]:
        v = x1 * x2 + y1 * y2
        stem = "Cho u = (%d; %d) và v = (%d; %d). Tích vô hướng u·v bằng bao nhiêu?" % (x1, y1, x2, y2)
        cand = sorted({v, v + 3, v - 4, x1 * y1 + x2 * y2, v + 6})[:4]
        opts = [str(x) for x in cand]
        items.append(mk(A, "Hình học & Vectơ", 4,
                        "u·v = %d×%d + %d×%d = %d." % (x1, x2, y1, y2, v),
                        stem, opts, opts.index(str(v)),
                        stem_en="Given u = (%d, %d) and v = (%d, %d), what is the dot product u·v?" % (x1, y1, x2, y2)))
    # 8) Số trung bình
    for nums in [[3, 5, 7, 9, 11], [2, 4, 6, 8, 10], [10, 20, 30, 40], [5, 15, 25], [1, 3, 5, 7]]:
        v = sum(nums) / len(nums)
        v = int(v) if v == int(v) else v
        stem = "Số trung bình cộng của dãy số %s là bao nhiêu?" % ", ".join(map(str, nums))
        cand = sorted({float(v) + d for d in (0, 1, 2, -1, 5)})[:4]
        opts = [("%g" % c) for c in cand]
        items.append(mk(A, "Số học & Tổ hợp", 2,
                        "Trung bình = (%d) ÷ %d = %s." % (sum(nums), len(nums), ("%g" % v)),
                        stem, opts, opts.index("%g" % float(v)),
                        stem_en="What is the arithmetic mean of %s?" % ", ".join(map(str, nums))))
    # 9) Modulo
    for a, m in [(7, 5), (13, 4), (29, 6), (100, 7), (17, 3), (256, 9)]:
        r = a % m
        stem = "Số dư khi chia %d cho %d là bao nhiêu?" % (a, m)
        cand = sorted({r, r + 1, r + 2, r + 3})
        opts = [str(v) for v in cand]
        items.append(mk(A, "Số học & Tổ hợp", 3,
                        "%d = %d × %d + %d nên số dư là %d." % (a, m, a // m, r, r),
                        stem, opts, opts.index(str(r)),
                        stem_en="What is the remainder when %d is divided by %d?" % (a, m)))
    # 10) Xác suất xúc xắc tổng 7
    items.append(mk(A, "Số học & Tổ hợp", 4,
                    "6 mặt cặp có tổng 7 (1,6),(2,5),(3,4),(4,3),(5,2),(6,1) = 6/36 = 1/6.",
                    "Gieo 2 con xúc xắc công bằng một lần. Xác suất để tổng số chấm hai mặt bằng 7 là bao nhiêu?",
                    ["1/6", "1/12", "5/36", "7/36"], 0,
                    stem_en="Two fair dice are rolled once. What is the probability that the sum is 7?"))
    # 11) Log cơ số 2 (lũy thừa 2)
    for n in [8, 16, 32, 64, 128, 512, 1024, 2048]:
        from math import log2
        k = int(log2(n))
        stem = "Giá trị của log₂(%d) là bao nhiêu?" % n
        cand = sorted({k, k + 1, k - 1, k + 2})
        opts = [str(v) for v in cand]
        items.append(mk(A, "Đại số & Giải phương trình", 2,
                        "log₂(%d) = %d vì 2^%d = %d." % (n, k, k, n),
                        stem, opts, opts.index(str(k)),
                        stem_en="What is the base-2 logarithm of %d?" % n))
    return items

# ======================================================================
# PHYS
# ======================================================================
def pool_phys():
    items = []
    A = "PHYS"
    # 1) v = s/t
    for s, t in [(120, 4), (180, 6), (240, 8), (300, 10), (150, 5), (420, 7), (500, 20), (90, 3)]:
        v = s // t
        stem = "Một vật chuyển động thẳng đều, đi được %d m trong %d s. Vận tốc của vật là bao nhiêu?" % (s, t)
        cand = sorted({v, v + 5, v - 3, v + 10})
        opts = ["%d m/s" % x for x in cand]
        items.append(mk(A, "Cơ học & Chuyển động", 2,
                        "v = s/t = %d/%d = %d." % (s, t, v),
                        stem, opts, opts.index("%d m/s" % v),
                        stem_en="An object moves uniformly, covering %d m in %d s. What is its speed?" % (s, t)))
    # 2) F = ma
    for m, a in [(2, 3), (4, 5), (5, 2), (10, 4), (8, 3), (3, 7), (6, 5)]:
        f = m * a
        stem = "Một vật khối lượng %d kg được kéo bởi lực có gia tốc %d m/s². Độ lớn hợp lực tác dụng lên vật là bao nhiêu?" % (m, a)
        cand = sorted({f, f + 4, f - 3, f + 10})
        opts = ["%d N" % x for x in cand]
        items.append(mk(A, "Cơ học & Chuyển động", 2,
                        "F = m·a = %d × %d = %d." % (m, a, f),
                        stem, opts, opts.index("%d N" % f),
                        stem_en="A %d kg mass accelerates at %d m/s². What is the net force?" % (m, a)))
    # 3) Tự do rơi (g = 10 m/s^2): v = g*t
    for t in [1, 2, 3, 4, 5, 6]:
        v = 10 * t
        stem = "Một vật rơi tự do từ độ cao đủ lớn (g = 10 m/s²). Sau %d s kể từ lúc thả, vận tốc của vật là bao nhiêu?" % t
        cand = sorted({v, v + 10, v - 5, v + 20})
        opts = ["%d m/s" % x for x in cand]
        items.append(mk(A, "Cơ học & Chuyển động", 3,
                        "v = g·t = 10 × %d = %d." % (t, v),
                        stem, opts, opts.index("%d m/s" % v),
                        stem_en="An object falls freely (g = 10 m/s²). After %d s, what is its velocity?" % t))
    # 4) Điện: V = I·R
    for v, i in [(12, 2), (24, 3), (9, 3), (12, 4), (24, 6), (6, 2), (36, 4)]:
        r = v // i
        stem = "Đặt hiệu điện thế %d V vào hai đầu điện trở, cường độ dòng điện qua điện trở là %d A. Điện trở có giá trị bao nhiêu?" % (v, i)
        cand = sorted({r, r + 2, r - 2, r + 4})
        opts = ["%d Ω" % x for x in cand]
        items.append(mk(A, "Điện học & Ohm", 2,
                        "R = V/I = %d/%d = %d Ω." % (v, i, r),
                        stem, opts, opts.index("%d Ω" % r),
                        stem_en="A voltage of %d V produces a current of %d A. What is the resistance?" % (v, i)))
    # 5) Công suất P = U·I
    for u, i in [(220, 5), (110, 2), (12, 2), (24, 3), (220, 10), (110, 10)]:
        p = u * i
        stem = "Một thiết bị điện hoạt động ở hiệu điện thế %d V, dòng điện %d A. Công suất tiêu thụ là bao nhiêu?" % (u, i)
        cand = sorted({p, p + 100, p - 50, p + 200})
        opts = ["%d W" % x for x in cand]
        items.append(mk(A, "Điện học & Ohm", 2,
                        "P = U·I = %d × %d = %d." % (u, i, p),
                        stem, opts, opts.index("%d W" % p),
                        stem_en="A device runs at %d V with a current of %d A. What is its power?" % (u, i)))
    # 6) Định luật Ôm - dây khách song song tổng trở
    items.append(mk(A, "Điện học & Ohm", 4,
                    "R_song_song = (R1·R2)/(R1+R2) = (2·2)/(2+2) = 1 Ω.",
                    "Hai điện trở giống nhau, mỗi chiếc 2 Ω, mắc song song. Điện trở tương đương của đoạn mạch là bao nhiêu?",
                    ["1 Ω", "2 Ω", "4 Ω", "0,5 Ω"], 0,
                    stem_en="Two identical 2 Ω resistors are connected in parallel. What is the equivalent resistance?"))
    items.append(mk(A, "Điện học & Ohm", 3,
                    "R_nối_tiếp = R1 + R2 = 3 + 6 = 9 Ω.",
                    "Hai điện trở 3 Ω và 6 Ω mắc nối tiếp. Điện trở tương đương của đoạn mạch là bao nhiêu?",
                    ["9 Ω", "2 Ω", "18 Ω", "3 Ω"], 0,
                    stem_en="Two resistors of 3 Ω and 6 Ω are connected in series. What is the equivalent resistance?"))
    # 7) Sóng: f = v/λ
    for v, lam in [(340, 2), (340, 0.5), (340, 1), (300, 0.6), (1500, 3), (340, 0.25)]:
        f = v / lam
        f = int(f) if f == int(f) else round(f, 1)
        stem = "Một sóng âm truyền trong không khí với tốc độ %s m/s, bước sóng %s m. Tần số của sóng là bao nhiêu?" % (
            ("%g" % v), ("%g" % lam))
        cand = sorted({f, f + 20, f - 10, f + 50})
        opts = ["%g Hz" % x for x in cand]
        items.append(mk(A, "Sóng & Quang", 3,
                        "f = v/λ = %g/%g = %g Hz." % (v, lam, f),
                        stem, opts, opts.index("%g Hz" % f),
                        stem_en="A sound wave travels at %g m/s with a wavelength of %g m. What is its frequency?" % (v, lam)))
    # 8) Chu kỳ T = 1/f
    for f in [2, 4, 5, 10, 20, 50]:
        t = 1.0 / f
        stem = "Một sóng có tần số %d Hz. Chu kỳ của sóng là bao nhiêu giây?" % f
        cand = [t]
        for c in [t + 0.1, t * 2, t + 0.25, t * 3, t + 0.5]:
            if all(abs(c - x) > 1e-9 for x in cand):
                cand.append(c)
            if len(cand) == 4:
                break
        opts = ["%g s" % x for x in cand]
        items.append(mk(A, "Sóng & Quang", 3,
                        "T = 1/f = 1/%d = %g s." % (f, t),
                        stem, opts, 0,
                        stem_en="A wave has a frequency of %d Hz. What is its period?" % f))
    # 9) Định luật III Newton
    items.append(mk(A, "Cơ học & Chuyển động", 3,
                    "Lực và phản lực luôn cùng phương, ngược chiều, cùng độ lớn.",
                    "Theo định luật III của Newton, khi bàn chân đá vào quả bóng thì lực nào xuất hiện?",
                    ["Lực của chân tác dụng lên bóng và phản lực của bóng tác dụng lên chân",
                     "Chỉ có lực của chân tác dụng lên bóng",
                     "Chỉ có phản lực của bóng tác dụng lên chân",
                     "Lực hút của Trái Đất lên bóng triệt tiêu phản lực"], 0,
                    stem_en="According to Newton's third law, when a foot kicks a ball, which forces act?"))
    # 10) Đổi nhiệt độ
    for c in [0, 100, 37, 25, 80, 45]:
        f_ = c * 9 / 5 + 32
        f_ = int(f_) if f_ == int(f_) else round(f_, 1)
        stem = "Nhiệt độ %d °C tương đương với bao nhiêu °F?" % c
        cand = sorted({f_, f_ + 10, f_ - 20, f_ + 32})
        opts = ["%g °F" % x for x in cand]
        items.append(mk(A, "Nhiệt học", 3,
                        "°F = °C × 9/5 + 32 = %d × 9/5 + 32 = %g °F." % (c, f_),
                        stem, opts, opts.index("%g °F" % f_),
                        stem_en="What is %d °C in Fahrenheit?" % c))
    return items

# ======================================================================
# CHEM
# ======================================================================
ATOMIC_NUMBERS = [("H", 1), ("He", 2), ("Li", 3), ("C", 6), ("N", 7), ("O", 8), ("F", 9),
                  ("Na", 11), ("Mg", 12), ("Al", 13), ("Si", 14), ("P", 15), ("S", 16),
                  ("Cl", 17), ("K", 19), ("Ca", 20), ("Fe", 26), ("Cu", 29), ("Zn", 30),
                  ("Ag", 47), ("I", 53)]

def pool_chem():
    items = []
    A = "CHEM"
    # 1) Số hiệu nguyên tử
    for sym, z in ATOMIC_NUMBERS[:12]:
        stem = "Số hiệu nguyên tử (số proton trong hạt nhân) của nguyên tố %s là bao nhiêu?" % sym
        cand = sorted({z, z + 1, z - 1 if z > 1 else z + 2, z + 3})
        opts = [str(v) for v in cand]
        items.append(mk(A, "Bảng tuần hoàn & Cấu hình electron", 1,
                        "%s có Z = %d." % (sym, z),
                        stem, opts, opts.index(str(z)),
                        stem_en="What is the atomic number of %s?" % sym))
    # 2) Số electron hóa trị nhóm A
    for sym, z, val in [("Li", 3, 1), ("Na", 11, 1), ("K", 19, 1), ("Mg", 12, 2), ("Ca", 20, 2),
                        ("Al", 13, 3), ("C", 6, 4), ("O", 8, 6), ("Cl", 17, 7), ("He", 2, 2)]:
        stem = "Nguyên tử %s (Z = %d) có bao nhiêu electron ở lớp ngoài cùng?" % (sym, z)
        cand = [val, val + 1, val + 2, val - 1 if val > 1 else 10]
        opts = [str(v) for v in cand]
        items.append(mk(A, "Bảng tuần hoàn & Cấu hình electron", 2,
                        "Cấu hình lớp ngoài cùng của %s có %d electron." % (sym, val),
                        stem, opts, opts.index(str(val)),
                        stem_en="How many valence electrons does a %s atom (Z = %d) have?" % (sym, z)))
    # 3) Mol: m = n × M
    for n, m in [(0.5, 18), (2, 44), (3, 32), (1.5, 28), (0.25, 98), (2, 18)]:
        mass = n * m
        mass = int(mass) if mass == int(mass) else mass
        stem = "Tính khối lượng của %g mol chất có khối lượng mol phân tử %d g/mol." % (n, m)
        cand = sorted({mass, mass + 10, mass - 5, mass + 20})
        opts = ["%g g" % x for x in cand]
        items.append(mk(A, "Mol & Dung dịch", 2,
                        "m = n × M = %g × %d = %g g." % (n, m, mass),
                        stem, opts, opts.index("%g g" % mass),
                        stem_en="Calculate the mass of %g mol of a substance whose molar mass is %d g/mol." % (n, m)))
    # 4) Thể tích khí ở ĐKTC: V = n × 22,4
    for n in [0.5, 1, 2, 0.25, 1.5, 3]:
        v = round(n * 22.4, 1)
        stem = "Ở điều kiện tiêu chuẩn (0 °C, 1 atm), thể tích của %g mol khí là bao nhiêu?" % n
        cand = sorted({v, round(v + 2.24, 1), round(v - 2.24, 1), round(v * 2, 1)})
        opts = ["%g L" % x for x in cand]
        items.append(mk(A, "Mol & Dung dịch", 2,
                        "V = n × 22,4 = %g × 22,4 = %g L." % (n, v),
                        stem, opts, opts.index("%g L" % v),
                        stem_en="At STP, what is the volume of %g mol of gas?" % n))
    # 5) pH của axit mạnh / bazơ mạnh loãng
    for conc, ph in [(1e-1, 1), (1e-2, 2), (1e-3, 3), (1e-4, 4)]:
        stem = "Dung dịch HCl có nồng độ %.0e mol/L. pH của dung dịch là bao nhiêu?" % conc
        cand = sorted({ph, ph + 1, ph - 1, 7})
        opts = [str(v) for v in cand]
        items.append(mk(A, "Mol & Dung dịch", 3,
                        "HCl mạnh phân li hoàn toàn: [H+] = %.0e ⇒ pH = %d." % (conc, ph),
                        stem, opts, opts.index(str(ph)),
                        stem_en="An HCl solution has a concentration of %.0e mol/L. What is its pH?" % conc))
    # 6) Hóa trị / số oxi hóa
    curated = [
        ("Trong phân tử HNO₃, số oxi hóa của nguyên tố N là bao nhiêu?", "+5",
         ["+5", "+3", "+1", "−3"], "H(+1)×3 + N + O(−2)×3 = 0 ⇒ N = +5."),
        ("Trong phân tử KMnO₄, số oxi hóa của nguyên tố Mn là bao nhiêu?", "+7",
         ["+7", "+4", "+2", "+6"], "K(+1) + Mn + O(−2)×4 = 0 ⇒ Mn = +7."),
        ("Trong phân tử H₂SO₄, số oxi hóa của nguyên tố S là bao nhiêu?", "+6",
         ["+6", "+4", "+2", "−2"], "H(+1)×2 + S + O(−2)×4 = 0 ⇒ S = +6."),
        ("Trong phân tử NH₃, số oxi hóa của nguyên tố N là bao nhiêu?", "−3",
         ["−3", "+3", "+5", "0"], "H(+1)×3 + N = 0 ⇒ N = −3."),
        ("Trong ion CrO₄²⁻, số oxi hóa của nguyên tố Cr là bao nhiêu?", "+6",
         ["+6", "+3", "+2", "+7"], "O(−2)×4 = −8; ion mang điện −2 ⇒ Cr = +6."),
    ]
    for stem_vi, correct, opts, expl in curated:
        items.append(mk(A, "Liên kết & Phản ứng", 3, expl, stem_vi, opts, opts.index(correct)))
    # 7) Liên kết hóa học
    for stem_vi, correct, opts, expl in [
        ("Liên kết giữa nguyên tử Na và nguyên tử Cl trong phân tử NaCl chủ yếu thuộc loại liên kết nào?",
         "Liên kết ion", ["Liên kết ion", "Liên kết cộng hóa trị không cực",
                          "Liên kết kim loại", "Liên kết cho - nhận"],
         "Na nhường electron cho Cl (hiệu độ âm điện lớn) ⇒ liên kết ion."),
        ("Liên kết giữa hai nguyên tử N trong phân tử N₂ thuộc loại liên kết nào?",
         "Liên kết cộng hóa trị không cực", ["Liên kết cộng hóa trị không cực",
                                              "Liên kết ion", "Liên kết kim loại",
                                              "Liên kết van der Waals"],
         "Hai nguyên tử giống nhau ⇒ hiệu độ âm điện = 0 ⇒ CHT không cực."),
        ("Liên kết cộng hóa trị phân cực tồn tại trong phân tử nào sau đây?",
         "HCl", ["HCl", "NaCl", "Cl₂", "MgO"], "H–Cl có hiệu độ âm điện khác 0 nhưng nhỏ ⇒ CHT phân cực."),
    ]:
        items.append(mk(A, "Liên kết & Phản ứng", 3, expl, stem_vi, opts, opts.index(correct)))
    # 8) Xu hướng tuần hoàn
    for stem_vi, correct, opts, expl in [
        ("Trong cùng một chu kỳ, theo chiều từ trái sang phải, tính phi kim của các nguyên tố thay đổi thế nào?",
         "Tăng dần", ["Tăng dần", "Giảm dần", "Không đổi", "Lúc tăng lúc giảm không theo quy luật"],
         "Điện tích hạt nhân tăng, bán kính giảm ⇒ hút electron mạnh hơn ⇒ tính phi kim tăng."),
        ("Trong cùng một nhóm A, theo chiều từ trên xuống dưới, bán kính nguyên tử thay đổi thế nào?",
         "Tăng dần", ["Tăng dần", "Giảm dần", "Không đổi", "Giảm rồi tăng"],
         "Số lớp electron tăng ⇒ bán kính tăng."),
        ("Nguyên tố nào có độ âm điện lớn nhất trong bảng tuần hoàn?",
         "Flo (F)", ["Flo (F)", "Oxi (O)", "Clo (Cl)", "Nitơ (N)"],
         "Flo có độ âm điện lớn nhất (~3,98)."),
    ]:
        items.append(mk(A, "Bảng tuần hoàn & Cấu hình electron", 3, expl, stem_vi, opts, opts.index(correct)))
    return items

# ======================================================================
# HIST (Việt Nam, có kiểm chứng)
# ======================================================================
HIST_FACTS = [
    # (câu hỏi năm, sự kiện, năm đúng, [năm nhiễu])
    ("Năm nào Khúc Thừa Dụ xưng Tiết độ sứ, mở đầu thời kỳ tự chủ của dân tộc ta?", "905", ["938", "1010", "968"]),
    ("Trận Bạch Đằng năm 938 gắn với nhân vật lịch sử nào?",
     "Ngô Quyền", ["Quang Trung", "Lê Lợi", "Trần Hưng Đạo"],
     "938"),
    ("Năm nào Đinh Bộ Lĩnh dẹp loạn 12 sứ quân, thống nhất đất nước?", "968", ["905", "981", "1010"]),
    ("Năm nào Lý Thái Tổ dời đô từ Hoa Lư ra Thăng Long?", "1010", ["1009", "968", "1075"]),
    ("Trận Như Nguyệt đánh Tống (1075–1077) do ai chỉ huy?", "Lý Thường Kiệt", ["Trần Hưng Đạo", "Ngô Quyền", "Lê Hoàn"], None),
    ("Trận Bạch Đằng năm 1288 đánh tan quân nào?", "Nguyên Mông", ["Nam Hán", "Tống", "Minh"], None),
    ("Khởi nghĩa Lam Sơn do ai lãnh đạo?", "Lê Lợi", ["Trần Hưng Đạo", "Quang Trung", "Ngô Quyền"], "1418"),
    ("Chiến thắng Chi Lăng – Xương Giang năm 1427 kết thúc cuộc kháng chiến chống triều nào?", "Minh", ["Tống", "Nguyên", "Thanh"], None),
    ("Năm nào Lê Lợi lên ngôi hoàng đế, lập ra nhà Hậu Lê?", "1428", ["1418", "1407", "1789"]),
    ("Chiến thắng Ngọc Hồi – Đống Đa năm 1789 do ai chỉ huy đánh tan quân Thanh?", "Quang Trung", ["Lê Lợi", "Lý Thường Kiệt", "Trần Hưng Đạo"], None),
    ("Năm 1858, thực dân Pháp nổ súng mở màn xâm lược Việt Nam tại đâu?", "Đà Nẵng", ["Huế", "Gia Định", "Sài Gòn"], None),
    ("Phong trào Cần Vương (1885–1896) gắn với vị vua nào?", "Hàm Nghi", ["Duy Tân", "Thành Thái", "Khải Định"], None),
    ("Ngày 5 tháng 6 năm 1911, người thanh niên Nguyễn Tất Thành ra đi tìm đường cứu nước tại đâu?",
     "Bến cảng Nhà Rồng", ["Cảng Hải Phòng", "Bến Tre", "Cảng Cam Ranh"], None),
    ("Ngày 3 tháng 2 năm 1930, sự kiện lịch sử nào diễn ra?",
     "Đảng Cộng sản Việt Nam được thành lập", ["Cách mạng Tháng Tám thành công", "Toàn quốc kháng chiến", "Ký Hiệp định Giơnevơ"], None),
    ("Ngày 19 tháng 8 năm 1945 gắn với sự kiện nào?",
     "Khởi nghĩa Tháng Tám thắng lợi ở Hà Nội", ["Tuyên ngôn độc lập được đọc", "Toàn quốc kháng chiến bùng nổ", "Điện Biên Phủ"], None),
    ("Ngày 2 tháng 9 năm 1945, tại đâu Chủ tịch Hồ Chí Minh đọc Tuyên ngôn độc lập?",
     "Quảng trường Ba Đình (Hà Nội)", ["Quảng trường Nhà hát lớn (Hà Nội)", "Dinh Độc Lập (Sài Gòn)", "Bến Nhà Rồng"], None),
    ("Ngày 19 tháng 12 năm 1946, sự kiện gì diễn ra?",
     "Cuộc kháng chiến toàn quốc chống thực dân Pháp bùng nổ", ["Chiến dịch Điện Biên Phủ bắt đầu",
                                                                  "Hiệp định Giơnevơ được ký",
                                                                  "Cách mạng Tháng Tám thành công"], None),
    ("Chiến dịch Điện Biên Phủ (1954) do ai làm tổng tư lệnh?", "Võ Nguyên Giáp", ["Trường Chinh", "Lê Duẩn", "Phạm Văn Đồng"], None),
    ("Trận Điện Biên Phủ toàn thắng vào ngày tháng năm nào?", "7 tháng 5 năm 1954",
     ["30 tháng 4 năm 1975", "19 tháng 12 năm 1946", "2 tháng 9 năm 1945"], None),
    ("Hiệp định nào ký ngày 27 tháng 1 năm 1973 chấm dứt chiến tranh, lập lại hòa bình ở Việt Nam?",
     "Hiệp định Paris", ["Hiệp định Giơnevơ", "Hiệp định Sơ bộ", "Hiệp định Fontainebleau"], None),
    ("Chiến dịch Hồ Chí Minh toàn thắng vào ngày tháng năm nào?", "30 tháng 4 năm 1975",
     ["7 tháng 5 năm 1954", "19 tháng 8 năm 1945", "27 tháng 1 năm 1973"], None),
    ("Ngày 2 tháng 7 năm 1976, sự kiện trọng đại nào diễn ra?",
     "Nước Cộng hòa Xã hội Chủ nghĩa Việt Nam được thành lập", ["Giải phóng miền Nam", "Hiệp định Paris được ký",
                                                                "Đảng Cộng sản Việt Nam ra đời"], None),
    ("Đại hội nào của Đảng (1986) khởi xướng công cuộc Đổi mới?",
     "Đại hội VI", ["Đại hội IV", "Đại hội V", "Đại hội VII"], None),
]

def pool_hist():
    items = []
    A = "HIST"
    for fact in HIST_FACTS:
        q, correct, distractors = fact[0], fact[1], fact[2]
        year = fact[3] if len(fact) > 3 else None
        # câu hỏi chính
        opts = [correct] + list(distractors)
        # giữ nguyên thứ tự rồi xáo trộn có seed
        order = list(range(4))
        rng.shuffle(order)
        opts4 = [opts[i] for i in order]
        correct_idx = opts4.index(correct)
        expl = "Đáp án: %s." % correct
        topic = "Kháng chiến chống Pháp" if any(k in q for k in ["1945", "1946", "1954", "1973", "1975", "Pháp"]) \
            else ("Hiện đại 1945-1975" if any(k in q for k in ["1976", "1986"]) else "Thời kỳ phong kiến & Kháng Pháp Mông")
        items.append(mk(A, topic, 2, expl, q, opts4, correct_idx,
                        stem_en="History: %s" % q))
        # biến thể hỏi năm (nếu có năm gắn với sự kiện)
        if year:
            ev = correct if correct.isdigit() else None
            if ev is None:
                # tìm năm trong câu hỏi nếu câu hỏi chứa năm
                m = re.search(r"(1[0-9]{3})", q)
                ev = m.group(1) if m else None
            if ev:
                qs = "Sự kiện gắn với năm %s là gì?" % ev
                o2 = [correct, "Khởi nghĩa Yên Bái", "Phong trào Đông Du", "Xô Viết Nghệ Tĩnh"]
                # chỉ tạo biến thể nếu correct là tên sự kiện/người hợp lý
                if not correct.isdigit():
                    random.shuffle(o2)
                    items.append(mk(A, topic, 2, expl, qs, o2, o2.index(correct)))
    # thêm các mục tự luận gắn mốc thời gian phổ quát
    extra = [
        ("Sự kiện nào mở đầu cuộc kháng chiến toàn quốc chống thực dân Pháp (1946)?",
         "Lời kêu gọi toàn quốc kháng chiến của Chủ tịch Hồ Chí Minh",
         ["Chiến dịch Việt Bắc", "Hiệp định Sơ bộ", "Tuyên ngôn độc lập"]),
        ("Chiến dịch Biên giới năm 1950 có ý nghĩa gì?",
         "Khai thông biên giới Việt – Trung, phá thế bao vây của địch",
         ["Giải phóng hoàn toàn miền Bắc", "Kết thúc chiến tranh", "Thành lập Chính phủ lâm thời"]),
        ("Phong trào Xô Viết Nghệ Tĩnh (1930–1931) là cao trào đấu tranh của giai cấp nào?",
         "Công nhân và nông dân", ["Tư sản và địa chủ", "Tiểu tư sản và trí thức", "Quân đội và cảnh sát"]),
        ("Chiến thắng “Điện Biên Phủ trên không” (1972) diễn ra ở đâu?",
         "Hà Nội và Hải Phòng", ["Sài Gòn", "Huế", "Đà Nẵng"]),
        ("Ai soạn thảo và đọc Tuyên ngôn độc lập năm 1945?",
         "Chủ tịch Hồ Chí Minh", ["Võ Nguyên Giáp", "Trường Chinh", "Phạm Văn Đồng"]),
        ("Lê Hoàn (Lê Đại Hành) đã đánh bại quân xâm lược nào năm 981?",
         "Tống", ["Nam Hán", "Nguyên Mông", "Minh"]),
        ("Nhà Lý được thành lập năm nào?", "1009", ["1010", "968", "1225"],
         "1009"),
        ("Năm 1042, vua Lý Thánh Tông cho ban hành bộ luật nào?",
         "Hình thư", ["Hồng Đức", "Gia Long", "Quốc triều hình luật"]),
        ("Bộ luật Hồng Đức được ban hành dưới triều đại nào?",
         "Nhà Hậu Lê", ["Nhà Lý", "Nhà Trần", "Nhà Nguyễn"]),
        ("Năm 1788, ai lên ngôi hoàng đế tại Phú Xuân (Huế)?",
         "Quang Trung (Nguyễn Huệ)", ["Gia Long", "Lê Chiêu Thống", "Minh Mạng"]),
    ]
    for row in extra:
        q, correct, distractors = row[0], row[1], row[2]
        year = row[3] if len(row) > 3 else None
        opts = [correct] + list(distractors)
        rng.shuffle(opts)
        items.append(mk(A, "Thời kỳ phong kiến & Kháng Pháp Mông" if any(k in q for k in ["Lê", "Lý", "Trần", "981", "1042", "1788"]) else "Kháng chiến chống Pháp",
                        2, "Đáp án: %s." % correct, q, opts, opts.index(correct),
                        stem_en="History: %s" % q))
    return items

# ======================================================================
# BIO
# ======================================================================
def pool_bio():
    items = []
    A = "BIO"
    curated = [
        ("Bào quan nào là “nhà máy năng lượng” của tế bào, nơi diễn ra hô hấp tế bào?", "Ti thể",
         ["Ti thể", "Lục lạp", "Nhân", "Ribosome"], "Hô hấp tế bào tạo ATP chủ yếu diễn ra ở ti thể.",
         "Tế bào & Di truyền"),
        ("Bào quan nào thực hiện quang hợp ở tế bào thực vật?", "Lục lạp",
         ["Lục lạp", "Ti thể", "Không bào", "Bộ máy Golgi"], "Quang hợp diễn ra tại lục lạp nhờ diệp lục.",
         "Tế bào & Di truyền"),
        ("Bào quan nào tổng hợp protein?", "Ribosome",
         ["Ribosome", "Lysosome", "Lưới nội chất hạt", "Ti thể"],
         "Ribosome là nơi dịch mã mRNA để tạo chuỗi polypeptide.", "Tế bào & Di truyền"),
        ("Bộ phận nào của tế bào chứa nhiễm sắc thể và điều khiển hoạt động tế bào?", "Nhân",
         ["Nhân", "Ti thể", "Màng tế bào", "Tế bào chất"], "Nhân chứa DNA tổ chức thành NST.", "Tế bào & Di truyền"),
        ("Màng tế bào có cấu tạo chủ yếu từ thành phần nào?", "Lớp kép phospholipid và protein",
         ["Lớp kép phospholipid và protein", "Cellulose và chitin", "DNA và RNA", "Chỉ protein"],
         "Mô hình khảm lỏng: lớp kép phospholipid + protein nhúng.", "Tế bào & Di truyền"),
        ("DNA được cấu tạo từ các đơn phân nào?", "Nucleotide",
         ["Nucleotide", "Axit amin", "Monosaccharide", "Axit béo"],
         "DNA gồm 4 loại nucleotide: A, T, G, X.", "Tế bào & Di truyền"),
        ("Trong phân tử DNA, bazơ nitơ nào bắt cặp bổ sung với ađenin (A)?", "Timin (T)",
         ["Timin (T)", "Guanin (G)", "Xitozin (X)", "Ađenin (A)"],
         "A liên kết với T bằng 2 liên kết hydro.", "Tế bào & Di truyền"),
        ("Trong phân tử DNA, ađenin (A) liên kết với timin (T) bằng mấy liên kết hydro?", "2",
         ["2", "1", "3", "4"], "A–T có 2 liên kết hydro; G–X có 3 liên kết hydro.", "Tế bào & Di truyền"),
        ("Kết quả của quá trình nguyên phân là gì?", "1 tế bào mẹ tạo ra 2 tế bào con giống hệt nhau về bộ NST",
         ["1 tế bào mẹ tạo ra 2 tế bào con giống hệt nhau về bộ NST",
          "1 tế bào mẹ tạo ra 4 tế bào con có bộ NST giảm một nửa",
          "1 tế bào mẹ tạo ra 2 tế bào con có bộ NST giảm một nửa",
          "1 tế bào mẹ tạo ra vô số tế bào con"], "Nguyên phân giữ nguyên bộ NST lưỡng bội 2n.", "Tế bào & Di truyền"),
        ("Kết quả của quá trình giảm phân từ 1 tế bào sinh dục chín là gì?",
         "4 tế bào con có bộ NST đơn bội (n)", ["4 tế bào con có bộ NST đơn bội (n)",
                                               "2 tế bào con có bộ NST lưỡng bội (2n)",
                                               "2 tế bào con có bộ NST đơn bội (n)",
                                               "4 tế bào con có bộ NST lưỡng bội (2n)"],
         "Giảm phân: 2 lần phân chia liên tiếp, bộ NST giảm đi một nửa.", "Tế bào & Di truyền"),
        ("Theo lai một cặp tính trạng của Mendel, tỉ lệ kiểu hình ở F2 là bao nhiêu?", "3 trội : 1 lặn",
         ["3 trội : 1 lặn", "1 trội : 1 lặn", "9 : 3 : 3 : 1", "1 : 1 : 1 : 1"],
         "F1 dị hợp Aa × Aa ⇒ F2 kiểu hình 3A- : 1aa.", "Tế bào & Di truyền"),
        ("Theo định luật phân ly độc lập, phép lai AaBb × AaBb cho tỉ lệ kiểu hình F2 là bao nhiêu?",
         "9 : 3 : 3 : 1", ["9 : 3 : 3 : 1", "3 : 3 : 1 : 1", "1 : 1 : 1 : 1", "3 : 1"],
         "Hai cặp gen phân ly độc lập ⇒ tỉ lệ 9:3:3:1.", "Tế bào & Di truyền"),
        ("Bộ ba kết thúc (STOP) trong mã di truyền gồm những bộ ba nào?", "UAA, UAG, UGA",
         ["UAA, UAG, UGA", "AUG, UAA, UGA", "UAA, AUG, UAG", "GUG, UAG, UAA"],
         "Ba bộ ba STOP không mã hóa axit amin nào.", "Tế bào & Di truyền"),
        ("Quá trình truyền thông tin di truyền từ DNA sang mRNA được gọi là gì?", "Phiên mã",
         ["Phiên mã", "Dịch mã", "Tái bản", "Đột biến"], "Phiên mã tổng hợp mRNA từ mạch khuôn DNA.",
         "Tế bào & Di truyền"),
        ("Quá trình tổng hợp protein trên ribosome dựa trên mRNA được gọi là gì?", "Dịch mã",
         ["Dịch mã", "Phiên mã", "Tái bản DNA", "Hoán vị gen"], "Dịch mã: ribosome đọc codon trên mRNA.",
         "Tế bào & Di truyền"),
        ("Sản phẩm của quang hợp ở thực vật là gì?", "Glucose và O₂",
         ["Glucose và O₂", "CO₂ và H₂O", "Tinh bột và CO₂", "O₂ và CO₂"],
         "6CO₂ + 6H₂O → C₆H₁₂O₆ + 6O₂ (ánh sáng, diệp lục).", "Cơ thể thực vật"),
        ("Sản phẩm cuối cùng của hô hấp tế bào gồm những chất nào?",
         "CO₂, H₂O và năng lượng (ATP)", ["CO₂, H₂O và năng lượng (ATP)", "O₂ và glucose",
                                          "Glucose và H₂O", "CO₂ và O₂"],
         "C₆H₁₂O₆ + 6O₂ → 6CO₂ + 6H₂O + ATP.", "Tế bào & Di truyền"),
        ("Tim người có mấy ngăn?", "4", ["4", "2", "3", "5"],
         "Tim người gồm 2 tâm nhĩ và 2 tâm thất.", "Cơ thể người"),
        ("Tế bào máu nào có chức năng vận chuyển oxy?", "Hồng cầu",
         ["Hồng cầu", "Bạch cầu", "Tiểu cầu", "Tế bào biểu mô"],
         "Huyết sắc tố (hemoglobin) trong hồng cầu bắt O₂.", "Cơ thể người"),
        ("Chức năng chính của bạch cầu là gì?", "Bảo vệ cơ thể chống nhiễm trùng",
         ["Bảo vệ cơ thể chống nhiễm trùng", "Vận chuyển oxy", "Đông máu", "Vận chuyển CO₂"],
         "Bạch cầu thực bào và tiết kháng thể.", "Cơ thể người"),
        ("Bộ NST của người bình thường có bao nhiêu nhiễm sắc thể?", "46",
         ["46", "44", "48", "23"], "Người có 23 cặp NST, tổng 46 NST.", "Tế bào & Di truyền"),
        ("Chọn lọc tự nhiên là cơ chế nào được Darwin đề xuất?",
         "Các biến dị thích nghi được tích lũy qua các thế hệ",
         ["Các biến dị thích nghi được tích lũy qua các thế hệ",
          "Các đặc điểm tập nhiễm được truyền cho đời sau",
          "Sinh vật tự biến đổi theo ý muốn",
          "Đột biến luôn có lợi cho cơ thể"],
         "Chọn lọc tự nhiên: cá thể thích nghi tốt sinh sản nhiều hơn.", "Sinh thái & Tiến hóa"),
        ("Sinh vật nào là sinh vật sản xuất trong chuỗi thức ăn?", "Thực vật",
         ["Thực vật", "Hổ", "Nấm phân hủy", "Thỏ"],
         "Sinh vật sản xuất tự tổng hợp chất hữu cơ từ ánh sáng.", "Sinh thái & Tiến hóa"),
        ("Trong chuỗi thức ăn, sinh vật phân hủy chủ yếu là những loài nào?", "Vi khuẩn và nấm",
         ["Vi khuẩn và nấm", "Sư tử và hổ", "Cỏ và lúa", "Chim và cá"],
         "Phân hủy chất hữu cơ trả lại môi trường.", "Sinh thái & Tiến hóa"),
        ("Tuyến nào tiết insulin để điều hòa đường huyết?", "Tụy",
         ["Tụy", "Gan", "Thận", "Tuyến giáp"], "Tế bào beta của đảo Langerhans tiết insulin.", "Cơ thể người"),
        ("Thiếu vitamin D gây bệnh gì ở trẻ em?", "Còi xương",
         ["Còi xương", "Scobut", "Beri-beri", "Thiếu máu"], "Vitamin D cần cho hấp thu canxi, tạo xương.",
         "Cơ thể người"),
        ("Thiếu máu thường do thiếu chất nào?", "Sắt",
         ["Sắt", "Canxi", "Iod", "Vitamin C"], "Sắt là thành phần của hemoglobin.", "Cơ thể người"),
    ]
    for stem_vi, correct, opts, expl, topic in curated:
        items.append(mk(A, topic, 2, expl, stem_vi, opts, opts.index(correct),
                        stem_en="Biology: %s" % stem_vi))
    # parameterized: nguyên phân k lần
    for k in [2, 3, 4, 5, 6, 7]:
        con = 2 ** k
        stem = "Một tế bào nguyên phân %d lần liên tiếp. Số tế bào con được tạo ra là bao nhiêu?" % k
        cand = sorted({con, con - 2, con + 2, con * 2})
        opts = [str(v) for v in cand]
        items.append(mk(A, "Tế bào & Di truyền", 3,
                        "Số tế bào con = 2^%d = %d." % (k, con),
                        stem, opts, opts.index(str(con)),
                        stem_en="A cell undergoes mitosis %d consecutive times. How many daughter cells are produced?" % k))
    return items

# ======================================================================
# INT3202 Cơ sở dữ liệu
# ======================================================================
def pool_int3202():
    items = []
    A = "INT3202"
    # (stem_vi, [4 options], correct_index, topic, difficulty, expl, stem_en)
    curated = [
        ("Khóa chính (Primary Key) của một bảng có đặc điểm nào sau đây?",
         ["Duy nhất và không được NULL", "Có thể trùng và được NULL",
          "Chỉ cần duy nhất, được phép NULL", "Không được trùng nhưng được phép NULL"],
         0, "Mô hình quan hệ & Khóa", 1,
         "PK: định danh duy nhất mỗi bộ, không nhận NULL.",
         "In a relational database, which constraint uniquely identifies each row of a table?"),
        ("Khóa ngoại (Foreign Key) dùng để làm gì?",
         ["Tham chiếu tới khóa chính của bảng khác, đảm bảo toàn vẹn tham chiếu",
          "Định danh duy nhất mỗi dòng trong bảng",
          "Tăng tốc truy vấn toàn văn (full-text)",
          "Mã hóa dữ liệu nhạy cảm"],
         0, "Mô hình quan hệ & Khóa", 1,
         "FK đảm bảo giá trị tham chiếu tồn tại ở bảng cha.",
         "What is the purpose of a foreign key in a relational schema?"),
        ("Trong bảng quan hệ, một bảng có thể có bao nhiêu khóa chính?",
         ["Tối đa 1 (có thể gồm nhiều cột)", "Tối đa 2", "Không giới hạn", "Đúng 1 cột duy nhất"],
         0, "Mô hình quan hệ & Khóa", 2,
         "Mỗi bảng có đúng một khóa chính (có thể là khóa ghép).",
         "How many primary keys can a table have?"),
        ("Dạng chuẩn 1NF yêu cầu điều kiện nào?",
         ["Mỗi thuộc tính chứa giá trị nguyên tử (không lặp, không đa trị)",
          "Không có phụ thuộc bắc cầu",
          "Mọi phụ thuộc hàm đều xuất phát từ khóa",
          "Không có phụ thuộc riêng phần"],
         0, "Chuẩn hóa dữ liệu", 3,
         "1NF: miền giá trị nguyên tử, mỗi ô chỉ chứa một giá trị.",
         "Which condition does First Normal Form require?"),
        ("Dạng chuẩn 2NF khắc phục vấn đề gì của 1NF?",
         ["Loại bỏ phụ thuộc riêng phần vào một phần của khóa ghép",
          "Loại bỏ phụ thuộc bắc cầu",
          "Loại bỏ dư thừa do lặp nhóm",
          "Loại bỏ đa trị"],
         0, "Chuẩn hóa dữ liệu", 3,
         "2NF: thuộc tính không khóa phải phụ thuộc đầy đủ vào TOÀN BỘ khóa.",
         "What problem does Second Normal Form eliminate?"),
        ("Dạng chuẩn 3NF yêu cầu điều kiện nào?",
         ["Không có phụ thuộc bắc cầu từ khóa tới thuộc tính không khóa",
          "Mọi determinant phải là superkey",
          "Không có phụ thuộc đa trị",
          "Thuộc tính phải nguyên tử"],
         0, "Chuẩn hóa dữ liệu", 4,
         "3NF: loại bắc cầu khóa → A → B.",
         "Which condition does Third Normal Form require?"),
        ("Dạng chuẩn BCNF mạnh hơn 3NF ở điểm nào?",
         ["Mọi determinant đều phải là superkey",
          "Cho phép phụ thuộc bắc cầu",
          "Bỏ yêu cầu nguyên tử",
          "Chỉ áp dụng cho bảng có 1 khóa"],
         0, "Chuẩn hóa dữ liệu", 5,
         "BCNF: với mọi phụ thuộc X → Y, X phải là superkey.",
         "How is BCNF stronger than Third Normal Form?"),
        ("Ràng buộc nào đảm bảo giá trị ở cột này phải tồn tại trong bảng khác?",
         ["Khóa ngoại (Foreign Key)", "Khóa chính (Primary Key)", "UNIQUE", "CHECK"],
         0, "Mô hình quan hệ & Khóa", 1,
         "FK → tham chiếu tới PK/UNIQUE của bảng được tham chiếu.",
         "Which constraint guarantees that a value exists in another table?"),
        ("Chỉ thị SQL nào dùng để lọc các nhóm sau GROUP BY?",
         ["HAVING", "WHERE", "ORDER BY", "LIMIT"],
         0, "Ngôn ngữ truy vấn SQL", 2,
         "WHERE lọc dòng trước khi nhóm; HAVING lọc nhóm sau tổng hợp.",
         "Which SQL clause filters groups after GROUP BY?"),
        ("Phép JOIN nào giữ lại mọi dòng của bảng bên trái kể cả khi không khớp?",
         ["LEFT JOIN", "INNER JOIN", "CROSS JOIN", "RIGHT JOIN"],
         0, "Ngôn ngữ truy vấn SQL", 2,
         "LEFT JOIN: giữ toàn bộ bảng trái, phần thiếu điền NULL.",
         "Which JOIN keeps every row of the left table?"),
        ("Phép INNER JOIN trả về kết quả nào?",
         ["Chỉ các dòng khớp điều kiện ở cả hai bảng",
          "Mọi dòng của bảng trái",
          "Mọi dòng của bảng phải",
          "Tích Descartes của hai bảng"],
         0, "Ngôn ngữ truy vấn SQL", 1,
         "INNER JOIN: giao hai quan hệ theo điều kiện khớp.",
         "What does an INNER JOIN return?"),
        ("Phép UNION khác UNION ALL ở điểm nào?",
         ["UNION loại bỏ dòng trùng, UNION ALL giữ nguyên",
          "UNION ALL loại bỏ dòng trùng",
          "UNION chậm hơn UNION ALL trong mọi trường hợp và trả về cùng kết quả",
          "Không có gì khác biệt"],
         0, "Ngôn ngữ truy vấn SQL", 2,
         "UNION = hợp + khử trùng; UNION ALL không khử trùng.",
         "How does UNION differ from UNION ALL?"),
        ("ACID gồm bốn tính chất nào của giao dịch?",
         ["Atomicity, Consistency, Isolation, Durability",
          "Atomicity, Concurrency, Isolation, Durability",
          "Availability, Consistency, Isolation, Durability",
          "Atomicity, Consistency, Integrity, Durability"],
         0, "Chuẩn hóa dữ liệu", 2,
         "Atomicity, Consistency, Isolation, Durability.",
         "Which properties make up ACID?"),
        ("Mức cô lập (isolation level) nào ngăn hiện tượng dirty read?",
         ["Read Committed trở lên", "Read Uncommitted", "Serializable only", "Repeatable Read only"],
         0, "Chuẩn hóa dữ liệu", 4,
         "Read Uncommitted cho phép dirty read; Read Committed chặn nó.",
         "Which isolation level prevents dirty reads?"),
        ("Hiện tượng non-repeatable read là gì?",
         ["Cùng một dòng được đọc hai lần trong một giao dịch cho kết quả khác nhau",
          "Đọc được dữ liệu chưa commit của giao dịch khác",
          "Xuất hiện dòng mới giữa hai lần đọc cùng điều kiện",
          "Giao dịch bị khóa chết"],
         0, "Chuẩn hóa dữ liệu", 4,
         "Non-repeatable read: dòng bị UPDATE giữa 2 lần đọc.",
         "What is a non-repeatable read?"),
        ("Chỉ mục (index) B-Tree chủ yếu giúp ích gì?",
         ["Tăng tốc truy vấn tìm kiếm/sắp xếp theo khóa",
          "Tăng tốc ghi dữ liệu",
          "Giảm dung lượng lưu trữ",
          "Mã hóa dữ liệu"],
         0, "Ngôn ngữ truy vấn SQL", 3,
         "B-Tree index: O(log n) khi tra cứu theo tiền tố khóa.",
         "What is the main benefit of a B-Tree index?"),
        ("Theo định lý CAP, một hệ phân tán chỉ có thể đảm bảo tối đa mấy trong ba tính chất?",
         ["2", "1", "3", "0"],
         0, "NoSQL & Lưu trữ phân tán", 4,
         "CAP: Consistency, Availability, Partition tolerance — chỉ chọn 2.",
         "According to the CAP theorem, how many of the three properties can a distributed system guarantee at once?"),
        ("MongoDB lưu dữ liệu theo mô hình nào?",
         ["Document (BSON)", "Key-Value", "Column-family", "Graph"],
         0, "NoSQL & Lưu trữ phân tán", 2,
         "MongoDB lưu tài liệu BSON (giống JSON).",
         "What data model does MongoDB use?"),
        ("Redis thuộc loại cơ sở dữ liệu nào?",
         ["Key-Value (in-memory)", "Document", "Column-family", "Quan hệ"],
         0, "NoSQL & Lưu trữ phân tán", 2,
         "Redis: kho key-value trên RAM, có TTL và cấu trúc dữ liệu phong phú.",
         "Which type of database is Redis?"),
        ("Cassandra thuộc loại cơ sở dữ liệu nào?",
         ["Column-family (wide-column)", "Document", "Graph", "Key-Value thuần"],
         0, "NoSQL & Lưu trữ phân tán", 3,
         "Cassandra: lưu theo họ cột, tối ưu ghi lớn phân tán.",
         "Which type of database is Cassandra?"),
        ("Công cụ SQL nào dùng để trả về một số dòng đầu tiên?",
         ["LIMIT", "TOP trong mọi DBMS đều giống nhau", "FIRST", "HEAD"],
         0, "Ngôn ngữ truy vấn SQL", 1,
         "MariaDB/MySQL: LIMIT n (SQL Server: TOP n).",
         "Which SQL clause returns only the first N rows (MySQL/MariaDB)?"),
        ("Ràng buộc UNIQUE khác gì với PRIMARY KEY?",
         ["UNIQUE cho phép một giá trị NULL (tùy DBMS), PK không cho phép NULL",
          "UNIQUE luôn không cho phép NULL, PK thì có",
          "Không có gì khác",
          "UNIQUE tự động là khóa chính"],
         0, "Mô hình quan hệ & Khóa", 3,
         "MySQL/MariaDB: UNIQUE cho phép nhiều NULL; PK không.",
         "How does UNIQUE differ from PRIMARY KEY?"),
        ("Câu lệnh SQL nào loại bỏ các dòng trùng hoàn toàn trong kết quả?",
         ["SELECT DISTINCT", "SELECT UNIQUE", "SELECT DIFFERENT", "SELECT ONLY"],
         0, "Ngôn ngữ truy vấn SQL", 2,
         "DISTINCT khử dòng trùng trong tập kết quả.",
         "Which SQL keyword removes duplicate rows from the result?"),
        ("Hàm tổng hợp nào đếm số dòng (kể cả NULL)?",
         ["COUNT(*)", "COUNT(column)", "SUM(*)", "TOTAL(*)"],
         0, "Ngôn ngữ truy vấn SQL", 2,
         "COUNT(*) đếm mọi dòng; COUNT(col) bỏ qua NULL.",
         "Which aggregate function counts all rows including NULLs?"),
        ("VIEW trong SQL là gì?",
         ["Một bảng ảo được định nghĩa từ một câu SELECT",
          "Một bản sao vật lý của bảng",
          "Một chỉ mục đặc biệt",
          "Một loại trigger"],
         0, "Ngôn ngữ truy vấn SQL", 2,
         "VIEW = bảng logic dựa trên truy vấn, không lưu dữ liệu.",
         "What is a VIEW in SQL?"),
        ("Trigger là gì?",
         ["Một đoạn mã tự động thực thi khi có sự kiện DML (INSERT/UPDATE/DELETE)",
          "Một loại chỉ mục",
          "Một ràng buộc khóa chính",
          "Một thủ tục được gọi thủ công"],
         0, "Ngôn ngữ truy vấn SQL", 3,
         "Trigger gắn với sự kiện bảng, tự chạy khi sự kiện xảy ra.",
         "What is a trigger?"),
        ("Ký tự đại diện nào trong LIKE khớp một chuỗi bất kỳ (kể cả rỗng)?",
         ["%", "_", "*", "#"],
         0, "Ngôn ngữ truy vấn SQL", 1,
         "% khớp 0..n ký tự; _ khớp đúng 1 ký tự.",
         "Which wildcard in LIKE matches any sequence of characters?"),
        ("Trong FULLTEXT index, toán tử nào nối các từ khóa với AND?",
         ["+", "-", "*", "@"],
         0, "Ngôn ngữ truy vấn SQL", 4,
         "MariaDB FULLTEXT: +bắt buộc, -loại trừ.",
         "In a FULLTEXT index, which operator requires a term to be present?"),
        ("JOIN nào tạo tích Đề-các (mọi tổ hợp dòng của hai bảng)?",
         ["CROSS JOIN", "INNER JOIN", "LEFT JOIN", "NATURAL JOIN"],
         0, "Ngôn ngữ truy vấn SQL", 3,
         "CROSS JOIN: n × m dòng.",
         "Which JOIN produces the Cartesian product?"),
        ("SET TRANSACTION ISOLATION LEVEL SERIALIZABLE đảm bảo điều gì?",
         ["Không có hiện tượng đọc sai, đọc lặp và đọc ảo",
          "Chỉ chặn dirty read",
          "Cho phép phantom read",
          "Không khóa gì cả"],
         0, "Chuẩn hóa dữ liệu", 5,
         "Serializable = cô lập tuyệt đối như chạy tuần tự.",
         "What does the SERIALIZABLE isolation level guarantee?"),
    ]
    for stem_vi, opts, correct, topic, diff, expl, en in curated:
        opts = list(opts)
        items.append(mk(A, topic, diff, expl, stem_vi, opts, correct, stem_en=en))
    # bổ sung câu hỏi lý thuyết đếm được
    more = [
        ("Một bảng có khóa chính ghép (A, B). Thuộc tính phụ thuộc vào {A} suy ra được thuộc tính \'x\'. Bảng này vi phạm dạng chuẩn nào?",
         "1NF", ["1NF", "2NF", "3NF", "BCNF"], "Phụ thuộc riêng phần vào một phần khóa ghép ⇒ vi phạm 2NF."),
        ("Cho phụ thuộc hàm A → B và B → C, với A là khóa. Thuộc tính C vi phạm dạng chuẩn nào?",
         "3NF", ["3NF", "2NF", "1NF", "BCNF"], "A → B → C là phụ thuộc bắc cầu ⇒ vi phạm 3NF."),
        ("Trong quan hệ R(A, B, C) với A là khóa chính và B → C, dạng chuẩn cao nhất có thể đạt được là gì?",
         "2NF", ["2NF", "1NF", "3NF", "BCNF"], "Có bắc cầu qua B (B không phải superkey) ⇒ 3NF vi phạm ⇒ chỉ đạt 2NF."),
    ]
    for stem_vi, correct, opts, expl in more:
        items.append(mk(A, "Chuẩn hóa dữ liệu", 4, expl, stem_vi.replace("{A}", "A"), opts, opts.index(correct)))
    return items

# ======================================================================
# INT3304 Mạng máy tính
# ======================================================================
def pool_int3304():
    items = []
    A = "INT3304"
    curated = [
        ("Mô hình OSI gồm bao nhiêu tầng?", ["7", "5", "4", "6"], 0,
         "Kiến trúc phân tầng OSI/TCP-IP", 1, "OSI: Physical, Data Link, Network, Transport, Session, Presentation, Application.",
         "How many layers does the OSI model have?"),
        ("Tầng nào trong mô hình OSI chịu trách nhiệm định tuyến gói tin qua các mạng?",
         ["Tầng Network (tầng 3)", "Tầng Transport (tầng 4)", "Tầng Data Link (tầng 2)", "Tầng Application (tầng 7)"],
         0, "Kiến trúc phân tầng OSI/TCP-IP", 2,
         "Network layer: định tuyến, địa chỉ IP, gói tin.",
         "Which OSI layer is responsible for routing packets across networks?"),
        ("Tầng Transport (TCP/UDP) trong mô hình OSI có chức năng chính nào?",
         ["Cung cấp truyền thông đầu cuối (end-to-end) giữa các tiến trình",
          "Định tuyến giữa các mạng",
          "Mã hóa dữ liệu",
          "Truy cập môi trường truyền dẫn"],
         0, "Kiến trúc phân tầng OSI/TCP-IP", 3,
         "Transport: segment, cổng (port), tin cậy (TCP) hoặc best-effort (UDP).",
         "What is the main function of the Transport layer?"),
        ("TCP bắt tay ba bước (3-way handshake) gồm các thông điệp nào?",
         ["SYN, SYN-ACK, ACK", "ACK, SYN, FIN", "SYN, ACK, FIN", "HELLO, SYN, ACK"],
         0, "Kiến trúc phân tầng OSI/TCP-IP", 2,
         "Client SYN → Server SYN-ACK → Client ACK.",
         "Which messages make up the TCP three-way handshake?"),
        ("UDP khác TCP chủ yếu ở điểm nào?",
         ["UDP không đảm bảo tin cậy/thứ tự, không bắt tay, độ trễ thấp",
          "UDP có bắt tay 3 bước",
          "UDP đảm bảo thứ tự gói tin",
          "UDP chậm hơn TCP"],
         0, "Kiến trúc phân tầng OSI/TCP-IP", 2,
         "UDP: không kết nối, không retransmit, dùng cho streaming/DNS.",
         "How does UDP mainly differ from TCP?"),
        ("Cổng mặc định của giao thức HTTPS là bao nhiêu?", ["443", "80", "22", "8080"], 0,
         "Giao thức ứng dụng HTTP/REST", 1, "HTTPS = HTTP + TLS, cổng 443.",
         "What is the default port for HTTPS?"),
        ("Cổng mặc định của HTTP là bao nhiêu?", ["80", "443", "8080", "21"], 0,
         "Giao thức ứng dụng HTTP/REST", 1, "HTTP mặc định cổng 80.",
         "What is the default port for HTTP?"),
        ("Cổng mặc định của SSH là bao nhiêu?", ["22", "23", "21", "25"], 0,
         "Bảo mật mạng & Xác thực", 1, "SSH cổng 22 (Telnet 23 không mã hóa).",
         "What is the default port for SSH?"),
        ("Cổng mặc định của DNS là bao nhiêu?", ["53", "25", "110", "143"], 0,
         "Giao thức ứng dụng HTTP/REST", 2, "DNS dùng cổng 53 (UDP/TCP).",
         "What is the default port for DNS?"),
        ("Mã trạng thái HTTP nào nghĩa là “Not Found”?", ["404", "403", "500", "301"], 0,
         "Giao thức ứng dụng HTTP/REST", 1, "404: tài nguyên không tồn tại.",
         "Which HTTP status code means Not Found?"),
        ("Mã trạng thái HTTP nào nghĩa là “Unauthorized” (chưa xác thực)?", ["401", "403", "400", "407"], 0,
         "Bảo mật mạng & Xác thực", 2, "401: thiếu/sai thông tin xác thực; 403: đã xác thực nhưng không đủ quyền.",
         "Which HTTP status code means Unauthorized?"),
        ("Mã trạng thái HTTP nào nghĩa là “Forbidden” (không đủ quyền)?", ["403", "401", "404", "500"], 0,
         "Bảo mật mạng & Xác thực", 2, "403: server từ chối vì không có quyền.",
         "Which HTTP status code means Forbidden?"),
        ("Mã trạng thái HTTP nào nghĩa là “Internal Server Error”?", ["500", "404", "502", "503"], 0,
         "Giao thức ứng dụng HTTP/REST", 1, "500: lỗi phía server.",
         "Which HTTP status code means Internal Server Error?"),
        ("Phương thức HTTP nào thường dùng để lấy dữ liệu?", ["GET", "POST", "PUT", "DELETE"], 0,
         "Giao thức ứng dụng HTTP/REST", 1, "GET: an toàn, idempotent, lấy tài nguyên.",
         "Which HTTP method is typically used to retrieve data?"),
        ("Phương thức HTTP nào thường dùng để tạo mới tài nguyên trên server?",
         ["POST", "GET", "HEAD", "OPTIONS"], 0,
         "Giao thức ứng dụng HTTP/REST", 1,
         "POST tạo tài nguyên; PUT/PATCH cập nhật.",
         "Which HTTP method is typically used to create a new resource?"),
        ("IPv4 có độ dài bao nhiêu bit?", ["32", "64", "128", "16"], 0,
         "Kiến trúc phân tầng OSI/TCP-IP", 1,
         "IPv4 = 32 bit, 4 octet; IPv6 = 128 bit.",
         "How many bits long is an IPv4 address?"),
        ("IPv6 có độ dài bao nhiêu bit?", ["128", "32", "64", "256"], 0,
         "Kiến trúc phân tầng OSI/TCP-IP", 1, "IPv6 = 128 bit.",
         "How many bits long is an IPv6 address?"),
        ("Subnet mask 255.255.255.0 tương ứng tiền tố CIDR nào?", ["/24", "/16", "/8", "/28"], 0,
         "Kiến trúc phân tầng OSI/TCP-IP", 3, "24 bit mạng ⇒ /24.",
         "Which CIDR prefix corresponds to the subnet mask 255.255.255.0?"),
        ("Địa chỉ IP nào sau đây thuộc dải private?",
         ["192.168.1.10", "8.8.8.8", "1.1.1.1", "203.0.113.5"], 0,
         "Kiến trúc phân tầng OSI/TCP-IP", 3,
         "192.168.0.0/16 là dải private (RFC 1918).",
         "Which of the following IPv4 addresses is private?"),
        ("Thiết bị nào hoạt động ở tầng 2 (Data Link) và chuyển tiếp khung theo địa chỉ MAC?",
         ["Switch", "Router", "Hub", "Modem"], 0,
         "Kiến trúc phân tầng OSI/TCP-IP", 2,
         "Switch: bảng MAC, chuyển frame trong LAN.",
         "Which device operates at layer 2 and forwards frames by MAC address?"),
        ("Thiết bị nào định tuyến gói tin giữa các mạng khác nhau?",
         ["Router", "Switch", "Hub", "Repeater"], 0,
         "Kiến trúc phân tầng OSI/TCP-IP", 1,
         "Router: định tuyến ở tầng Network bằng IP.",
         "Which device routes packets between different networks?"),
        ("Giao thức nào ánh xạ địa chỉ IP sang địa chỉ MAC trong mạng LAN?",
         ["ARP", "DHCP", "DNS", "ICMP"], 0,
         "Kiến trúc phân tầng OSI/TCP-IP", 3,
         "ARP phân giải IP → MAC (broadcast trong LAN).",
         "Which protocol maps an IP address to a MAC address on a LAN?"),
        ("Giao thức nào cấp phát địa chỉ IP tự động cho máy trạm?",
         ["DHCP", "DNS", "ARP", "NAT"], 0,
         "Kiến trúc phân tầng OSI/TCP-IP", 2,
         "DHCP: cấp IP, mask, gateway, DNS tự động.",
         "Which protocol automatically assigns IP addresses to hosts?"),
        ("Giao thức nào phân giải tên miền (ví dụ google.com) thành địa chỉ IP?",
         ["DNS", "DHCP", "ARP", "SMTP"], 0,
         "Giao thức ứng dụng HTTP/REST", 1,
         "DNS: domain name → IP.",
         "Which protocol resolves domain names to IP addresses?"),
        ("Lệnh ping sử dụng giao thức nào?", ["ICMP", "TCP", "UDP", "ARP"], 0,
         "Kiến trúc phân tầng OSI/TCP-IP", 2,
         "ping gửi ICMP Echo Request.",
         "Which protocol does the ping command use?"),
        ("TLS trong HTTPS chủ yếu đảm bảo điều gì?",
         ["Mã hóa và toàn vẹn dữ liệu giữa client và server",
          "Định tuyến gói tin",
          "Cấp phát IP",
          "Phân giải tên miền"],
         0, "Bảo mật mạng & Xác thực", 2,
         "TLS: mã hóa, xác thực, toàn vẹn.",
         "What does TLS in HTTPS primarily provide?"),
        ("JWT (JSON Web Token) thường được dùng để làm gì?",
         ["Mang thông tin xác thực/ủy quyền giữa các bên",
          "Mã hóa ổ cứng",
          "Cấp phát IP",
          "Nén dữ liệu"],
         0, "Bảo mật mạng & Xác thực", 3,
         "JWT: token tự chứa claims, ký số, dùng cho stateless auth.",
         "What is a JWT commonly used for?"),
        ("Tấn công XSS (Cross-Site Scripting) nhằm vào thành phần nào?",
         ["Trình duyệt của người dùng (chèn mã độc vào trang web)",
          "Máy chủ CSDL",
          "Bộ định tuyến",
          "Tường lửa"],
         0, "Bảo mật mạng & Xác thực", 3,
         "XSS: script chạy trong ngữ cảnh trang của nạn nhân.",
         "What does a cross-site scripting attack target?"),
        ("Tấn công SQL Injection lợi dụng điểm yếu nào?",
         ["Dữ liệu người dùng được nối trực tiếp vào câu truy vấn SQL",
          "Mật khẩu yếu",
          "Cổng mở thừa",
          "Cert TLS hết hạn"],
         0, "Bảo mật mạng & Xác thực", 3,
         "SQLi: nhập dữ liệu phá vỡ cấu trúc truy vấn.",
         "What weakness does SQL injection exploit?"),
        ("CSRF là loại tấn công nào?",
         ["Lợi dụng phiên đăng nhập của nạn nhân để thực hiện hành động trái ý muốn",
          "Quét cổng mở trên máy chủ",
          "Nghe lén lưu lượng mạng",
          "Từ chối dịch vụ"],
         0, "Bảo mật mạng & Xác thực", 4,
         "CSRF: trình duyệt tự gửi request kèm cookie phiên của nạn nhân.",
         "What kind of attack is CSRF?"),
    ]
    for stem_vi, opts, correct, topic, diff, expl, en in curated:
        items.append(mk(A, topic, diff, expl, stem_vi, list(opts), correct, stem_en=en))
    # tham số hóa CIDR
    for prefix, hosts in [(28, 14), (29, 6), (30, 2), (26, 62), (25, 126), (27, 30)]:
        stem = "Mạng con có tiền tố /%d (mặt nạ mặc định) cho phép tối đa bao nhiêu địa chỉ host khả dụng?" % prefix
        cand = [hosts, hosts + 2, hosts + 6, hosts * 2 + 2, hosts + 8]
        cand = list(dict.fromkeys(cand))[:4]
        opts = [str(v) for v in cand]
        items.append(mk(A, "Kiến trúc phân tầng OSI/TCP-IP", 4,
                        "Host khả dụng = 2^(32-%d) - 2 = %d." % (prefix, hosts),
                        stem, opts, opts.index(str(hosts)),
                        stem_en="How many usable host addresses does a /%d subnet allow?" % prefix))
    # đổi nhị phân sang thập phân (octet)
    for oct_bin in ["11000000", "10101010", "11111111", "10000001", "01010101", "11110000"]:
        val = int(oct_bin, 2)
        stem = "Octet nhị phân %s tương ứng giá trị thập phân nào?" % oct_bin
        cand = sorted({val, val + 1, val - 1, val + 16})
        opts = [str(v) for v in cand]
        items.append(mk(A, "Kiến trúc phân tầng OSI/TCP-IP", 3,
                        "%s₂ = %d." % (oct_bin, val),
                        stem, opts, opts.index(str(val)),
                        stem_en="What decimal value does the binary octet %s represent?" % oct_bin))
    return items

# ======================================================================
# INT3306 Lập trình Java
# ======================================================================
def pool_int3306():
    items = []
    A = "INT3306"
    curated = [
        ("String trong Java có đặc điểm nào sau đây?",
         ["Bất biến (immutable)", "Có thể thay đổi nội dung tại chỗ",
          "Không thể so sánh bằng equals()", "Luôn nằm trên stack"],
         0, "Cú pháp & OOP cơ bản", 2,
         "Mọi thao tác trên String tạo đối tượng mới.",
         "Which of the following is true about String in Java?"),
        ("So sánh nội dung hai chuỗi trong Java nên dùng phương thức nào?",
         ["equals()", "==", "compareTo() chỉ", "contentEquals() chỉ"],
         0, "Cú pháp & OOP cơ bản", 2,
         "== so sánh tham chiếu; equals() so sánh nội dung.",
         "Which method should be used to compare the contents of two Java strings?"),
        ("Kết quả của Integer.valueOf(127) == Integer.valueOf(127) là gì?",
         ["true (do Integer cache -128..127)", "false", "Lỗi biên dịch", "Tùy JVM"],
         0, "Cú pháp & OOP cơ bản", 4,
         "Integer cache các giá trị -128..127, cùng tham chiếu.",
         "What does Integer.valueOf(127) == Integer.valueOf(127) evaluate to?"),
        ("HashMap trong Java có độ phức tạp tra cứu trung bình là bao nhiêu?",
         ["O(1)", "O(log n)", "O(n)", "O(n log n)"], 0,
         "Ngoại lệ & Collections", 2,
         "Hash: bảng băm + bucket; trung bình O(1).",
         "What is the average lookup complexity of a HashMap?"),
        ("TreeMap lưu các khóa theo thứ tự nào?",
         ["Sắp xếp tăng dần theo tự nhiên/so sánh được (đỏ-đen)",
          "Ngẫu nhiên", "Theo thứ tự chèn", "Theo độ dài khóa"],
         0, "Ngoại lệ & Collections", 3,
         "TreeMap: cây đỏ-đen, O(log n), có thứ tự.",
         "In what order does a TreeMap store its keys?"),
        ("Cấu trúc nào không cho phép phần tử trùng lặp?",
         ["HashSet", "ArrayList", "LinkedList", "Vector"], 0,
         "Ngoại lệ & Collections", 1,
         "Set: không trùng phần tử.",
         "Which collection does not allow duplicate elements?"),
        ("ArrayList truy cập phần tử theo chỉ số có độ phức tạp bao nhiêu?",
         ["O(1)", "O(n)", "O(log n)", "O(n²)"], 0,
         "Ngoại lệ & Collections", 2,
         "Mảng động: truy cập theo index O(1).",
         "What is the time complexity of accessing an element by index in an ArrayList?"),
        ("LinkedList phù hợp nhất cho thao tác nào?",
         ["Thêm/xóa ở đầu hoặc cuối danh sách thường xuyên",
          "Truy cập ngẫu nhiên theo chỉ số",
          "Tìm kiếm nhị phân",
          "Sắp xếp tại chỗ"],
         0, "Ngoại lệ & Collections", 2,
         "LinkedList: O(1) chèn/xóa đầu-cuối, O(n) truy cập index.",
         "For which operations is LinkedList best suited?"),
        ("checked exception và unchecked exception khác nhau ở điểm nào?",
         ["Checked phải xử lý/khai báo (kế thừa Exception); unchecked kế thừa RuntimeException",
          "Unchecked phải xử lý, checked thì không",
          "Cả hai đều bắt buộc try-catch",
          "Checked chỉ xảy ra lúc biên dịch"],
         0, "Ngoại lệ & Collections", 3,
         "Checked: Exception trực tiếp; unchecked: RuntimeException.",
         "How do checked and unchecked exceptions differ?"),
        ("Khối finally chạy khi nào?",
         ["Luôn chạy sau try/catch dù có ngoại lệ hay không (trừ khi JVM tắt)",
          "Chỉ chạy khi không có ngoại lệ",
          "Chỉ chạy khi có ngoại lệ",
          "Không bao giờ chạy nếu có return"],
         0, "Ngoại lệ & Collections", 2,
         "finally để giải phóng tài nguyên; return trong try vẫn vào finally.",
         "When does a finally block execute?"),
        ("try-with-resources dùng để làm gì?",
         ["Tự động đóng tài nguyên implement AutoCloseable",
          "Bắt lỗi compile-time",
          "Tối ưu GC",
          "Kiểm tra null tự động"],
         0, "Ngoại lệ & Collections", 3,
         "Tài nguyên được đóng tự động kể cả khi có ngoại lệ.",
         "What is try-with-resources used for?"),
        ("Annotation @Override có ý nghĩa gì?",
         ["Báo cho trình biên dịch kiểm tra phương thức có thực sự ghi đè lớp cha/interface không",
          "Buộc chạy nhanh hơn",
          "Đánh dấu phương thức thread-safe",
          "Tạo bean Spring"],
         0, "Cú pháp & OOP cơ bản", 2,
         "@Override bắt lỗi gõ sai chữ ký ghi đè.",
         "What does the @Override annotation do?"),
        ("Phương thức interface có thân (default method) từ Java mấy?",
         ["Java 8", "Java 11", "Java 5", "Java 17"], 0,
         "Cú pháp & OOP cơ bản", 3,
         "Java 8 cho phép default/static method trong interface.",
         "Since which Java version can interface methods have a body (default methods)?"),
        ("Optional<T> dùng để làm gì?",
         ["Tránh null và biểu đạt rõ ràng “có thể không có giá trị”",
          "Tăng tốc độ xử lý",
          "Khai báo hằng số",
          "Đồng bộ luồng"],
         0, "Cú pháp & OOP cơ bản", 3,
         "Optional: container 0/1 phần tử, buộc xử lý trường hợp vắng.",
         "What is Optional<T> used for?"),
        ("Stream API trong Java 8 hỗ trợ thao tác nào là terminal operation?",
         ["collect", "map", "filter", "sorted"], 0,
         "Cú pháp & OOP cơ bản", 4,
         "collect/forEach/reduce là terminal; map/filter/sorted là intermediate.",
         "Which of the following is a terminal operation in the Java Stream API?"),
        ("@RestController trong Spring là tổ hợp của những annotation nào?",
         ["@Controller + @ResponseBody", "@Component + @Service",
          "@Controller + @RequestMapping", "@RestController không phải tổ hợp"],
         0, "Spring Boot & REST API", 3,
         "@RestController = @Controller + @ResponseBody (trả JSON).",
         "What is @RestController a combination of?"),
        ("@RequestBody dùng để làm gì trong Spring MVC?",
         ["Gắn thân (body) của HTTP request (thường là JSON) vào tham số phương thức",
          "Đọc tham số query string",
          "Đọc biến path",
          "Gắn response header"],
         0, "Spring Boot & REST API", 2,
         "@RequestBody: deserialization JSON → object.",
         "What is @RequestBody used for in Spring MVC?"),
        ("Trong Spring Boot, annotation nào đánh dấu lớp là bean và tự quét?",
         ["@Component", "@Bean", "@Configuration", "@Autowired"], 0,
         "Spring Boot & REST API", 2,
         "@Component: stereotype chung, được component-scan.",
         "Which annotation marks a class as a Spring-managed bean discovered by component scanning?"),
        ("@Autowired làm gì?",
         ["Yêu cầu Spring tiêm phụ thuộc (dependency injection) tự động",
          "Đánh dấu endpoint",
          "Bật giao dịch",
          "Validate dữ liệu"],
         0, "Spring Boot & REST API", 2,
         "DI theo kiểu/khớp constructor/field/setter.",
         "What does @Autowired do?"),
        ("ResponseEntity trong Spring dùng để làm gì?",
         ["Tùy biến toàn bộ response: status, headers, body",
          "Chỉ chứa body",
          "Đọc request",
          "Tạo view HTML"],
         0, "Spring Boot & REST API", 3,
         "ResponseEntity<T>: kiểm soát status + headers + body.",
         "What is ResponseEntity used for?"),
        ("@Transactional mặc định tạo loại giao dịch nào?",
         ["Rollback khi gặp RuntimeException/Error",
          "Luôn rollback mọi exception",
          "Không bao giờ rollback",
          "Rollback khi gặp checked exception"],
         0, "Spring Boot & REST API", 4,
         "Mặc định rollback với unchecked exception (RuntimeException).",
         "What kind of transaction does @Transactional create by default?"),
        ("Bean mặc định trong Spring có scope nào?",
         ["singleton", "prototype", "request", "session"], 0,
         "Spring Boot & REST API", 3,
         "Mặc định singleton: 1 instance mỗi container.",
         "What is the default scope of a Spring bean?"),
        ("new String(\"abc\") khác gì với \"abc\"?",
         ["new String tạo đối tượng mới ngoài pool, \"abc\" có thể dùng chung pool",
          "Không khác",
          "new String nhanh hơn",
          "\"abc\" luôn tạo đối tượng mới"],
         0, "Cú pháp & OOP cơ bản", 4,
         "String literal vào pool; new luôn tạo mới trên heap.",
         "How does new String(\"abc\") differ from \"abc\"?"),
        ("Từ khóa final đặt trước một lớp có ý nghĩa gì?",
         ["Lớp không thể bị kế thừa",
          "Lớp không thể bị khởi tạo",
          "Lớp chỉ có phương thức static",
          "Lớp là immutable record"],
         0, "Cú pháp & OOP cơ bản", 2,
         "final class: chặn extends. String là final.",
         "What does the final modifier on a class mean?"),
        ("hashCode() và equals() trong Java có hợp đồng nào?",
         ["Hai object equals() == true phải có cùng hashCode()",
          "Hai object có cùng hashCode() phải equals()",
          "Không có ràng buộc nào",
          "equals() không được dùng trong HashMap"],
         0, "Cú pháp & OOP cơ bản", 4,
         "Hợp đồng: equals ⇒ hashCode bằng nhau (ngược lại không bắt buộc).",
         "What is the contract between hashCode() and equals() in Java?"),
        ("Exception là lớp cha của những loại ngoại lệ nào?",
         ["Checked và unchecked (qua RuntimeException)",
          "Chỉ checked",
          "Chỉ unchecked",
          "Error"],
         0, "Ngoại lệ & Collections", 3,
         "Throwable → Error | Exception → (RuntimeException).",
         "What kinds of exceptions does Exception parent?"),
        ("Interface có thể chứa trường nào sau đây?",
         ["public static final constants", "instance fields", "private instance fields không hằng", "không chứa trường nào"],
         0, "Cú pháp & OOP cơ bản", 3,
         "Trường interface ngầm định public static final.",
         "What kinds of fields can an interface contain?"),
    ]
    for stem_vi, opts, correct, topic, diff, expl, en in curated:
        items.append(mk(A, topic, diff, expl, stem_vi, list(opts), correct, stem_en=en))
    return items

# ======================================================================
# INT3401 Trí tuệ nhân tạo
# ======================================================================
def pool_int3401():
    items = []
    A = "INT3401"
    curated = [
        ("Thuật toán BFS (tìm kiếm theo chiều rộng) duyệt các đỉnh theo thứ tự nào?",
         ["Tăng dần theo khoảng cách từ đỉnh nguồn (theo từng lớp)",
          "Luôn đi sâu hết một nhánh trước",
          "Ngẫu nhiên",
          "Theo thứ tự alphabet của đỉnh"],
         0, "Tìm kiếm & Biểu diễn tri thức", 2,
         "BFS dùng hàng đợi (queue), mở rộng theo lớp.",
         "In what order does BFS visit vertices?"),
        ("Thuật toán DFS (tìm kiếm theo chiều sâu) dùng cấu trúc dữ liệu nào?",
         ["Ngăn xếp (stack) hoặc đệ quy", "Hàng đợi (queue)", "Hàng đợi ưu tiên (priority queue)", "Bảng băm"],
         0, "Tìm kiếm & Biểu diễn tri thức", 2,
         "DFS: LIFO — đi sâu tới khi kẹt rồi quay lui.",
         "Which data structure does DFS use?"),
        ("Hàm heuristic trong thuật toán A* được gọi là “admissible” khi nào?",
         ["Không bao giờ đánh giá quá cao chi phí thật còn lại",
          "Luôn đánh giá đúng chi phí thật",
          "Luôn đánh giá quá cao chi phí thật",
          "Bằng 0 ở mọi nút"],
         0, "Tìm kiếm & Biểu diễn tri thức", 4,
         "Admissible heuristic: h(n) ≤ h*(n) ⇒ A* tối ưu.",
         "When is a heuristic in A* called admissible?"),
        ("Khi heuristic admissible, A* đảm bảo tính chất gì?",
         ["Tìm đường đi ngắn nhất (optimality)",
          "Chỉ đảm bảo đầy đủ (completeness)",
          "Chạy trong O(1)",
          "Không dùng bộ nhớ"],
         0, "Tìm kiếm & Biểu diễn tri thức", 4,
         "A* với h admissible ⇒ tối ưu trên đồ thị chi phí không âm.",
         "What does A* guarantee when the heuristic is admissible?"),
        ("Thuật toán Dijkstra giải bài toán gì?",
         ["Đường đi ngắn nhất từ một nguồn tới các đỉnh khác (trọng số không âm)",
          "Cây khung nhỏ nhất",
          "Luồng cực đại",
          "Sắp xếp mảng"],
         0, "Tìm kiếm & Biểu diễn tri thức", 3,
         "Dijkstra: tham lam trên hàng đợi ưu tiên.",
         "What problem does Dijkstra's algorithm solve?"),
        ("Overfitting (quá khớp) là hiện tượng gì?",
         ["Mô hình khớp dữ liệu train tốt nhưng tổng quát hóa kém trên dữ liệu mới",
          "Mô hình học quá ít dữ liệu",
          "Mô hình luôn dự đoán một lớp duy nhất",
          "Mô hình có quá ít tham số"],
         0, "Học máy & Mạng nơ-ron", 2,
         "Overfitting: variance cao, học nhiễu của train.",
         "What is overfitting?"),
        ("Trade-off bias-variance nói về điều gì?",
         ["Độ lệch và phương sai của mô hình luôn đối nghịch nhau khi điều chỉnh độ phức tạp",
          "Hai tham số không liên quan",
          "Chỉ áp dụng cho mạng nơ-ron",
          "Tốc độ học và độ chính xác"],
         0, "Học máy & Mạng nơ-ron", 4,
         "Bias cao → underfit; variance cao → overfit.",
         "What does the bias-variance trade-off describe?"),
        ("Tập validation (tập kiểm định) dùng để làm gì?",
         ["Điều chỉnh siêu tham số và chọn mô hình, tránh “nhìn trộm” test",
          "Train mô hình cuối cùng",
          "Đánh giá báo cáo cuối cùng duy nhất",
          "Làm sạch dữ liệu"],
         0, "Học máy & Mạng nơ-ron", 3,
         "Validation: model selection; test: đánh giá cuối.",
         "What is the validation set used for?"),
        ("Precision (độ chính xác tích cực) được tính bằng công thức nào?",
         ["TP / (TP + FP)", "TP / (TP + FN)", "TN / (TN + FP)", "TP / tổng số mẫu"],
         0, "Học máy & Mạng nơ-ron", 2,
         "Precision: trong các dự đoán dương, bao nhiêu đúng.",
         "How is precision calculated?"),
        ("Recall (độ phủ) được tính bằng công thức nào?",
         ["TP / (TP + FN)", "TP / (TP + FP)", "TN / (TN + FN)", "FN / (TP + FN)"],
         0, "Học máy & Mạng nơ-ron", 2,
         "Recall: trong các nhãn dương thật, tìm được bao nhiêu.",
         "How is recall calculated?"),
        ("F1-score là gì?",
         ["Trung bình điều hòa của precision và recall",
          "Trung bình cộng của precision và recall",
          "Độ chính xác trên toàn bộ dữ liệu",
          "Tỉ lệ dự đoán đúng lớp đa số"],
         0, "Học máy & Mạng nơ-ron", 2,
         "F1 = 2·P·R / (P + R).",
         "What is the F1 score?"),
        ("Trong ma trận nhầm lẫn, FN (false negative) nghĩa là gì?",
         ["Thực tế dương nhưng mô hình dự đoán âm",
          "Thực tế âm nhưng mô hình dự đoán dương",
          "Thực tế âm và dự đoán âm",
          "Thực tế dương và dự đoán dương"],
         0, "Học máy & Mạng nơ-ron", 2,
         "FN: bỏ sót mẫu dương.",
         "In a confusion matrix, what does a false negative mean?"),
        ("Thuật toán k-NN phân loại một điểm mới dựa trên nguyên lý nào?",
         ["Bỏ phiếu đa số của k hàng xóm gần nhất",
          "Siêu phẳng phân tách",
          "Chia để trị theo cây",
          "Cụm centroid"],
         0, "Học máy & Mạng nơ-ron", 2,
         "k-NN: lazy learning, khoảng cách (Euclid...).",
         "On what principle does k-NN classify a new point?"),
        ("SVM tìm cái gì để phân tách hai lớp?",
         ["Siêu phẳng có lề (margin) lớn nhất",
          "Đường thẳng chia đôi dữ liệu",
          "Cây quyết định sâu nhất",
          "Trọng tâm cụm"],
         0, "Học máy & Mạng nơ-ron", 3,
         "SVM: maximizing margin giữa các lớp.",
         "What does SVM find to separate two classes?"),
        ("Hàm kích hoạt ReLU có công thức nào?",
         ["max(0, x)", "1 / (1 + e^(-x))", "tanh(x)", "e^x / Σe^xj"],
         0, "Học máy & Mạng nơ-ron", 2,
         "ReLU: tuyến tính từng đoạn, chống vanishing gradient.",
         "What is the formula of the ReLU activation function?"),
        ("Backpropagation (lan truyền ngược) dùng để làm gì?",
         ["Tính gradient của hàm mất mát theo từng trọng số để cập nhật",
          "Khởi tạo trọng số",
          "Tăng tốc độ suy luận",
          "Chuẩn hóa dữ liệu đầu vào"],
         0, "Học máy & Mạng nơ-ron", 3,
         "Backprop: chain rule từ output về input.",
         "What is backpropagation used for?"),
        ("Gradient descent cập nhật trọng số theo hướng nào?",
         ["Ngược hướng gradient của hàm mất mát",
          "Theo hướng gradient",
          "Ngẫu nhiên",
          "Theo chiều tăng dần của loss"],
         0, "Học máy & Mạng nơ-ron", 3,
         "w := w − η·∇L.",
         "In which direction does gradient descent update the weights?"),
        ("Learning rate (tốc độ học) quá lớn gây hiện tượng gì?",
         ["Hội tụ dao động/phân kỳ, không học được",
          "Học rất chậm nhưng ổn định",
          "Không ảnh hưởng",
          "Overfitting tuyệt đối"],
         0, "Học máy & Mạng nơ-ron", 3,
         "η quá lớn: bước nhảy vượt qua cực tiểu.",
         "What happens if the learning rate is too large?"),
        ("Tokenization trong xử lý ngôn ngữ là gì?",
         ["Chia văn bản thành các đơn vị nhỏ (token) để mô hình xử lý",
          "Dịch văn bản sang ngôn ngữ khác",
          "Mã hóa văn bản bằng AES",
          "Tóm tắt văn bản"],
         0, "Mô hình ngôn ngữ lớn (LLM)", 2,
         "Token: từ/mảnh từ/ký tự tùy bộ mã hóa (BPE...).",
         "What is tokenization in NLP?"),
        ("Mô hình LLM được huấn luyện theo mục tiêu tự giám sát nào?",
         ["Dự đoán token tiếp theo (next-token prediction)",
          "Phân loại ảnh",
          "Regresion giá",
          "Cụm từ không nhãn"],
         0, "Mô hình ngôn ngữ lớn (LLM)", 3,
         "Causal LM: học phân phối p(token kế tiếp | ngữ cảnh).",
         "What self-supervised objective are LLMs trained with?"),
        ("Nhiệt độ (temperature) khi suy luận LLM điều khiển điều gì?",
         ["Độ ngẫu nhiên khi chọn token tiếp theo",
          "Độ dài câu trả lời",
          "Tốc độ sinh token",
          "Kích thước context window"],
         0, "Mô hình ngôn ngữ lớn (LLM)", 3,
         "Temperature cao → phân bố phẳng → ngẫu nhiên hơn.",
         "What does the temperature parameter control during LLM inference?"),
        ("Hiện tượng “ảo giác” (hallucination) của LLM là gì?",
         ["Mô hình sinh nội dung sai nhưng trình bày tự tin, trôi chảy",
          "Mô hình từ chối trả lời",
          "Mô hình lặp lại câu hỏi",
          "Mô hình sinh quá chậm"],
         0, "Mô hình ngôn ngữ lớn (LLM)", 2,
         "Hallucination: bịa thông tin không có trong dữ liệu/kiểm chứng được.",
         "What is hallucination in LLMs?"),
        ("RAG (Retrieval-Augmented Generation) giải quyết vấn đề gì chủ yếu?",
         ["Bổ sung kiến thức ngoài/cập nhật vào câu trả lời, giảm ảo giác",
          "Tăng tốc suy luận",
          "Giảm kích thước mô hình",
          "Dịch ngôn ngữ"],
         0, "Mô hình ngôn ngữ lớn (LLM)", 3,
         "RAG: truy hồi tài liệu liên quan rồi đưa vào prompt.",
         "What problem does RAG primarily solve?"),
        ("Few-shot prompting nghĩa là gì?",
         ["Cung cấp vài ví dụ mẫu trong prompt để định hướng đầu ra",
          "Fine-tuning lại toàn bộ mô hình",
          "Huấn luyện mô hình mới từ đầu",
          "Chỉ dùng một từ làm prompt"],
         0, "Mô hình ngôn ngữ lớn (LLM)", 2,
         "In-context learning: ví dụ trong prompt định hướng format/hành vi.",
         "What does few-shot prompting mean?"),
        ("Chain-of-Thought (CoT) prompting yêu cầu mô hình làm gì?",
         ["Trình bày các bước suy luận trung gian trước khi trả lời",
          "Trả lời bằng một từ duy nhất",
          "Liệt kê tài liệu tham khảo",
          "Từ chối câu hỏi khó"],
         0, "Mô hình ngôn ngữ lớn (LLM)", 2,
         "CoT: “step by step” — cải thiện suy luận đa bước.",
         "What does Chain-of-Thought prompting ask the model to do?"),
        ("Quantization (lượng tử hóa) mô hình nhằm mục đích gì?",
         ["Giảm dung lượng và chi phí suy luận (ví dụ 4-bit/8-bit) đổi lấy chút độ chính xác",
          "Tăng độ chính xác tuyệt đối",
          "Tăng kích thước context",
          "Thay đổi kiến trúc mô hình"],
         0, "Mô hình ngôn ngữ lớn (LLM)", 3,
         "Q4_K_M: trọng số 4-bit, chạy được trên GPU T4.",
         "What is the purpose of model quantization?"),
        ("Khi đặt temperature = 0, mô hình LLM có xu hướng gì?",
         ["Sinh đầu ra gần như tất định (greedy), ít ngẫu nhiên",
          "Sinh đầu ra ngẫu nhiên tối đa",
          "Từ chối trả lời",
          "Sinh vô hạn token"],
         0, "Mô hình ngôn ngữ lớn (LLM)", 2,
         "T=0: luôn chọn token xác suất cao nhất.",
         "What does setting temperature = 0 make an LLM tend to do?"),
        ("Mục đích của RLHF trong huấn luyện trợ lý LLM là gì?",
         ["Tinh chỉnh mô hình theo sở thích/phản hồi con người (hữu ích, an toàn, đúng ý)",
          "Giảm kích thước mô hình",
          "Tăng tốc suy luận",
          "Mở rộng context window"],
         0, "Mô hình ngôn ngữ lớn (LLM)", 3,
         "RLHF: reward model học từ so sánh của con người.",
         "What is the purpose of RLHF when training LLM assistants?"),
        ("Prompt-injection là loại rủi ro nào?",
         ["Đầu vào độc hại điều khiển hành vi mô hình ngoài ý muốn nhà phát triển",
          "Đánh cắp trọng số mô hình",
          "Làm tràn bộ nhớ GPU",
          "Tấn công từ chối dịch vụ"],
         0, "Mô hình ngôn ngữ lớn (LLM)", 4,
         "Prompt injection: “bỏ qua chỉ thị trước đó và ...”.",
         "What kind of risk is prompt injection?"),
    ]
    for stem_vi, opts, correct, topic, diff, expl, en in curated:
        items.append(mk(A, topic, diff, expl, stem_vi, list(opts), correct, stem_en=en))
    return items

# ======================================================================
# TOEIC Tiếng Anh (Part 5 - hoàn thành câu)
# ======================================================================
TOEIC_FRAMES = [
    # (stem, [A,B,C,D], correct_idx, grammar_point, explanation)
    ("The manager ____ the quarterly report yesterday.", ["review", "reviews", "reviewed", "reviewing"], 2,
     "Thì quá khứ đơn", "yesterday → quá khứ đơn: reviewed."),
    ("She ____ to the office every day by bus.", ["go", "goes", "going", "gone"], 1,
     "Thì hiện tại đơn (ngôi 3 số ít)", "Chủ ngữ ngôi 3 số ít → goes."),
    ("The train ____ at 7:30 tomorrow morning.", ["leaves", "left", "leave", "leaving"], 0,
     "Hiện tại đơn mang nghĩa tương lai (lịch trình)", "Lịch trình cố định dùng hiện tại đơn: leaves."),
    ("We ____ in this company since 2019.", ["work", "worked", "have worked", "are working"], 2,
     "Hiện tại hoàn thành với since", "since + mốc thời gian → hiện tại hoàn thành."),
    ("By the time he arrived, the meeting ____ started.", ["has", "had", "did", "was"], 1,
     "Quá khứ hoàn thành", "By the time + quá khứ đơn → quá khứ hoàn thành: had."),
    ("If I ____ you, I would call her right away.", ["am", "was", "were", "be"], 2,
     "Câu điều kiện loại 2", "If + were cho mọi ngôi trong điều kiện loại 2."),
    ("Each of the employees ____ required to attend the training.", ["is", "are", "were", "be"], 0,
     "Sự hòa hợp chủ vị với Each of", "Each of + plural noun → động từ số ít: is."),
    ("The number of applicants ____ increasing every year.", ["is", "are", "were", "be"], 0,
     "Sự hòa hợp chủ vị với The number of", "The number of → số ít: is."),
    ("A number of candidates ____ selected for the final interview.", ["was", "were", "is", "be"], 1,
     "Sự hòa hợp chủ vị với A number of", "A number of → số nhiều: were."),
    ("Neither the director nor the staff ____ informed about the change.", ["was", "were", "are", "be"], 1,
     "Neither...nor... (gần nhất quyết định)", "Quy tắc gần nhất: staff (số nhiều) → were."),
    ("The report ____ by the assistant before the deadline.", ["completed", "was completed", "completes", "completing"], 1,
     "Câu bị động quá khứ", "Chủ ngữ là vật chịu tác động → bị động: was completed."),
    ("English ____ widely spoken in international business.", ["is", "are", "be", "been"], 0,
     "Câu bị động hiện tại", "English (số ít, vật) → is spoken."),
    ("She enjoys ____ to classical music while working.", ["listen", "listens", "listening", "listened"], 2,
     "Động từ + V-ing", "enjoy + V-ing: listening."),
    ("He decided ____ the position because of the salary.", ["accept", "accepting", "to accept", "accepted"], 2,
     "Động từ + to V", "decide + to V: to accept."),
    ("The consultant suggested ____ the meeting until Monday.", ["postpone", "postponing", "to postpone", "postponed"], 1,
     "Động từ + V-ing", "suggest + V-ing: postponing."),
    ("We have to avoid ____ unnecessary expenses this quarter.", ["make", "making", "to make", "made"], 1,
     "Động từ + V-ing", "avoid + V-ing: making."),
    ("The CEO is ____ the investors tomorrow morning.", ["meet", "meets", "meeting", "met"], 2,
     "Hiện tại tiếp diễn mang nghĩa tương lai", "Lịch cá nhân gần → hiện tại tiếp diễn: meeting."),
    ("This laptop is ____ than the one I bought last year.", ["expensive", "more expensive", "most expensive", "expensiver"], 1,
     "So sánh hơn với tính từ dài", "Tính từ dài → more + adj + than."),
    ("Her proposal was ____ convincing than his.", ["much", "more", "most", "very"], 1,
     "So sánh hơn", "more + adj-dài + than."),
    ("The task was not as ____ as we expected.", ["difficult", "more difficult", "most difficult", "difficulty"], 0,
     "So sánh bằng (as...as)", "as + adj nguyên mẫu + as."),
    ("____ people attended the conference this year than last year.", ["Fewer", "Less", "Little", "Few"], 0,
     "Fewer với danh từ đếm được", "people đếm được → fewer."),
    ("There is ____ furniture left in the old office.", ["few", "many", "little", "several"], 2,
     "Little với danh từ không đếm được", "furniture không đếm được → little."),
    ("Many ____ were submitted before the deadline.", ["application", "applications", "applicant", "applied"], 1,
     "Số nhiều với many", "many + danh từ số nhiều: applications."),
    ("The proposal was approved ____ the board of directors.", ["by", "with", "from", "of"], 0,
     "Giới từ chỉ tác nhân trong câu bị động", "Bị động → by + tác nhân."),
    ("The meeting will take place ____ Monday afternoon.", ["in", "on", "at", "by"], 1,
     "Giới từ theo ngày", "on + ngày/thứ: on Monday."),
    ("Our flight departs ____ 6:45 in the morning.", ["on", "in", "at", "by"], 2,
     "Giới từ theo giờ", "at + giờ: at 6:45."),
    ("She has been living in Ho Chi Minh City ____ 2021.", ["for", "since", "from", "during"], 1,
     "Since + mốc thời gian", "since + mốc bắt đầu."),
    ("He has worked here ____ ten years.", ["since", "for", "from", "during"], 1,
     "For + khoảng thời gian", "for + khoảng thời gian."),
    ("____ it was raining heavily, the event continued as planned.", ["Although", "Because", "So", "Therefore"], 0,
     "Liên từ chỉ sự nhượng bộ", "Nhượng bộ → although."),
    ("Sales dropped ____ the economic downturn.", ["because", "because of", "although", "so that"], 1,
     "Because of + cụm danh từ", "because of + danh từ; because + mệnh đề."),
    ("The CEO, ____ founded the company, gave a speech.", ["who", "which", "whose", "whom"], 0,
     "Mệnh đề quan hệ cho người (chủ ngữ)", "Người + làm chủ ngữ → who."),
    ("The report, _____ was released yesterday, shows strong growth.", ["who", "which", "whose", "whom"], 1,
     "Mệnh đề quan hệ cho vật", "Vật → which."),
    ("We need someone ____ can handle multiple clients.", ["who", "which", "whose", "whom"], 0,
     "Mệnh đề quan hệ cho người", "someone (người) + chủ ngữ → who."),
    ("She works ____ a senior accountant in Hanoi.", ["like", "as", "alike", "such"], 1,
     "As + nghề nghiệp", "as = với vai trò; like = giống như (so sánh)."),
    ("The new software works exactly ____ the old one did.", ["like", "as", "alike", "such"], 1,
     "As + mệnh đề", "as + mệnh đề; like + danh từ."),
    ("All employees must arrive ____ time for the ceremony.", ["in", "on", "at", "by"], 1,
     "Cụm từ on time", "on time = đúng giờ."),
    ("Could you please fill ____ the application form?", ["in", "on", "up", "at"], 0,
     "Cụm động từ fill in", "fill in = điền vào (form)."),
    ("The meeting has been put ____ until next Friday.", ["on", "off", "up", "away"], 1,
     "Cụm động từ put off", "put off = hoãn lại."),
    ("We are looking forward ____ your reply.", ["to receive", "to receiving", "receive", "receiving"], 1,
     "Cụm từ look forward to + V-ing", "look forward to là giới từ to + V-ing."),
    ("The shipment was delayed ____ a customs inspection.", ["due to", "because", "although", "so"], 0,
     "Due to + cụm danh từ", "due to + danh từ = do."),
    ("The contract is valid ____ the end of December.", ["until", "by", "in", "at"], 0,
     "Until + mốc kết thúc", "until = cho đến khi."),
    ("Applicants must submit their CVs ____ Friday at the latest.", ["until", "by", "in", "at"], 1,
     "By + hạn chót", "by = trước hạn chót."),
    ("The director asked the team to ____ the proposal again.", ["look at", "look up", "look into", "look after"], 2,
     "Cụm động từ look into", "look into = xem xét, điều tra."),
    ("Please ____ your dictionaries during the test.", ["put away", "put on", "put out", "put up"], 0,
     "Cụm động từ put away", "put away = cất đi."),
    ("____ the rain, the outdoor concert was canceled.", ["Because of", "Although", "Despite", "So"], 0,
     "Because of + cụm danh từ", "because of + danh từ."),
    ("_____ the rain, the outdoor concert proceeded as planned.", ["Because of", "Despite", "Although", "So that"], 1,
     "Despite + cụm danh từ", "despite + danh từ/V-ing."),
    ("_____ it was raining, the outdoor concert proceeded as planned.", ["Despite", "Although", "Because of", "So that"], 1,
     "Although + mệnh đề", "although + S + V."),
    ("The marketing team is responsible ____ the product launch.", ["of", "for", "to", "with"], 1,
     "Cụm tính từ responsible for", "responsible for = chịu trách nhiệm về."),
    ("The results were consistent ____ our expectations.", ["with", "to", "for", "of"], 0,
     "Cụm tính từ consistent with", "consistent with = nhất quán với."),
    ("Employees are eligible ____ a bonus after one year.", ["for", "to", "of", "with"], 0,
     "Cụm tính từ eligible for", "eligible for = đủ điều kiện nhận."),
    ("The new policy will come ____ effect next month.", ["in", "into", "on", "at"], 1,
     "Cụm từ come into effect", "come into effect = có hiệu lực."),
    ("Sales have increased significantly ____ the last quarter.", ["over", "since", "during", "while"], 0,
     "Over + khoảng thời gian", "over the last quarter = trong quý vừa qua."),
    ("The hotel offers ____ rooms as well as standard rooms.", ["luxury", "luxurious", "luxuriously", "luxuriate"], 1,
     "Tính từ bổ nghĩa danh từ", "Cần tính từ: luxurious rooms."),
    ("The instructions were written very ____.", ["clear", "clearly", "clearer", "clarity"], 1,
     "Trạng từ bổ nghĩa động từ", "Bổ nghĩa “were written” → trạng từ: clearly."),
    ("We need to ____ the contract before signing.", ["review", "reviewer", "reviewing", "reviewed"], 0,
     "Động từ nguyên mẫu sau need to", "need to + V: review."),
    ("The manager gave a brief ____ of the new system.", ["introduce", "introduction", "introduced", "introductory"], 1,
     "Danh từ sau mạo từ", "a brief + danh từ: introduction."),
    ("_____ is required for all applicants.", ["Experience", "Experienced", "Experiencing", "Experiencedly"], 0,
     "Danh từ làm chủ ngữ", "Vị trí chủ ngữ → danh từ: Experience."),
    ("The software allows users to ____ their settings easily.", ["custom", "customize", "customary", "customer"], 1,
     "Động từ nguyên mẫu sau to", "allows users to + V: customize."),
    ("All ____ must be submitted by Friday.", ["report", "reports", "reporting", "reported"], 1,
     "Danh từ số nhiều làm chủ ngữ", "All + danh từ số nhiều: reports."),
    ("The committee ____ its decision next week.", ["announce", "will announce", "announced", "announcing"], 1,
     "Tương lai đơn", "next week → will announce."),
]

def pool_toeic():
    items = []
    A = "TOEIC"
    for i, (stem, opts, correct, point, expl) in enumerate(TOEIC_FRAMES):
        topic = "Part 5 - Ngữ pháp & Từ loại"
        items.append(mk(A, topic, 2 if i % 5 else 3, expl, stem, list(opts), correct, stem_en=stem))
    return items

def pool_toeic_spelling():
    """Câu TÌM LỖI CHÍNH TẢ tiếng Anh (tất định, đáp án duy nhất)."""
    items = []
    A = "TOEIC"
    FILLERS = ["company", "project", "meeting", "report", "office"]
    for right, wrong in EN_MISSPELL.items():
        rng.shuffle(FILLERS)
        opts = [wrong, FILLERS[0], FILLERS[1], FILLERS[2]]
        rng.shuffle(opts)
        stem = "Chọn từ viết SAI chính tả trong câu sau: “I %s your email yesterday and will reply soon.”" % wrong
        items.append(mk(A, "Part 5 - Ngữ pháp & Từ loại", 2,
                        "“%s” viết sai; đúng là “%s”." % (wrong, right),
                        stem, opts, opts.index(wrong),
                        stem_en="Choose the misspelled word: 'I %s your email yesterday.'" % wrong))
    return items

# ======================================================================
# EN ASSERTION FACTS — nguồn tất định cho nhãn grammar.
# Mỗi fact: (câu khẳng định TA đúng, subject, topic, difficulty,
#            từ sai -> viết đúng, từ đúng -> viết sai, explanation)
# Câu hỏi dạng: "Trong câu tiếng Anh sau, từ nào bị SAI ngữ pháp?"
# ======================================================================
EN_FACTS = [
    # INT3202
    ("The primary key uniquely identifies each row in a relational table.", "INT3202", "Mô hình quan hệ & Khóa", 3,
     "identifies", "identify", "Chủ ngữ ngôi 3 số ít → identifies."),
    ("A foreign key references the primary key of another table.", "INT3202", "Mô hình quan hệ & Khóa", 2,
     "references", "reference", "Chủ ngữ ngôi 3 số ít → references."),
    ("A relational table stores data in rows and columns.", "INT3202", "Mô hình quan hệ & Khóa", 2,
     "stores", "store", "Chủ ngữ ngôi 3 số ít → stores."),
    ("The ACID properties guarantee reliable transactions.", "INT3202", "Chuẩn hóa dữ liệu", 3,
     "guarantee", "guarantees", "Chủ ngữ số nhiều 'ACID properties' → guarantee."),
    ("An index improves the speed of data retrieval.", "INT3202", "Ngôn ngữ truy vấn SQL", 2,
     "improves", "improve", "Chủ ngữ ngôi 3 số ít → improves."),
    ("Each table in a relational schema has a unique name.", "INT3202", "Mô hình quan hệ & Khóa", 2,
     "has", "have", "Chủ ngữ ngôi 3 số ít → has."),
    # INT3304
    ("TCP uses a three-way handshake to establish a connection.", "INT3304", "Kiến trúc phân tầng OSI/TCP-IP", 3,
     "uses", "use", "Chủ ngữ ngôi 3 số ít → uses."),
    ("HTTPS encrypts communication between the client and the server.", "INT3304", "Bảo mật mạng & Xác thực", 3,
     "encrypts", "encrypt", "Chủ ngữ ngôi 3 số ít → encrypts."),
    ("The Domain Name System resolves domain names into IP addresses.", "INT3304", "Giao thức ứng dụng HTTP/REST", 2,
     "resolves", "resolve", "Chủ ngữ ngôi 3 số ít → resolves."),
    ("A packet travels through multiple routers before reaching its destination.", "INT3304", "Kiến trúc phân tầng OSI/TCP-IP", 3,
     "travels", "travel", "Chủ ngữ ngôi 3 số ít → travels."),
    ("The firewall filters incoming and outgoing network traffic.", "INT3304", "Bảo mật mạng & Xác thực", 3,
     "filters", "filter", "Chủ ngữ ngôi 3 số ít → filters."),
    ("Switches forward frames based on MAC addresses.", "INT3304", "Kiến trúc phân tầng OSI/TCP-IP", 2,
     "forward", "forwards", "Chủ ngữ số nhiều → forward."),
    # INT3306
    ("A String object is immutable in Java.", "INT3306", "Cú pháp & OOP cơ bản", 2,
     "is", "are", "Chủ ngữ số ít → is."),
    ("The HashMap class stores key-value pairs.", "INT3306", "Ngoại lệ & Collections", 2,
     "stores", "store", "Chủ ngữ ngôi 3 số ít → stores."),
    ("Java compiles source code into bytecode.", "INT3306", "Cú pháp & OOP cơ bản", 2,
     "compiles", "compile", "Chủ ngữ ngôi 3 số ít → compiles."),
    ("The finally block executes after the try block.", "INT3306", "Ngoại lệ & Collections", 3,
     "executes", "execute", "Chủ ngữ ngôi 3 số ít → executes."),
    ("A checked exception must be handled or declared.", "INT3306", "Ngoại lệ & Collections", 3,
     "must", "musts", "must là động từ khuyết thiếu, không chia -s."),
    ("The Spring container manages bean lifecycles.", "INT3306", "Spring Boot & REST API", 3,
     "manages", "manage", "Chủ ngữ ngôi 3 số ít → manages."),
    # INT3401
    ("A heuristic guides the search toward the goal.", "INT3401", "Tìm kiếm & Biểu diễn tri thức", 3,
     "guides", "guide", "Chủ ngữ ngôi 3 số ít → guides."),
    ("Backpropagation computes gradients through the network.", "INT3401", "Học máy & Mạng nơ-ron", 3,
     "computes", "compute", "Chủ ngữ ngôi 3 số ít → computes."),
    ("The test set evaluates the final model performance.", "INT3401", "Học máy & Mạng nơ-ron", 2,
     "evaluates", "evaluate", "Chủ ngữ ngôi 3 số ít → evaluates."),
    ("Overfitting reduces the ability to generalize.", "INT3401", "Học máy & Mạng nơ-ron", 2,
     "reduces", "reduce", "Chủ ngữ ngôi 3 số ít → reduces."),
    ("The gradient points in the direction of steepest ascent.", "INT3401", "Học máy & Mạng nơ-ron", 4,
     "points", "point", "Chủ ngữ ngôi 3 số ít → points."),
    ("Tokenization splits text into smaller units.", "INT3401", "Mô hình ngôn ngữ lớn (LLM)", 2,
     "splits", "split", "Chủ ngữ ngôi 3 số ít → splits."),
    # TOEIC (ngữ pháp nghiệp vụ chung)
    ("The sales department submits its report every Monday.", "TOEIC", "Part 5 - Ngữ pháp & Từ loại", 2,
     "submits", "submit", "Chủ ngữ ngôi 3 số ít → submits."),
    ("Our supplier delivers the materials on time.", "TOEIC", "Part 5 - Ngữ pháp & Từ loại", 2,
     "delivers", "deliver", "Chủ ngữ ngôi 3 số ít → delivers."),
    ("The meeting starts at nine o'clock sharp.", "TOEIC", "Part 5 - Ngữ pháp & Từ loại", 2,
     "starts", "start", "Chủ ngữ ngôi 3 số ít → starts."),
    ("Every employee attends the safety training once a year.", "TOEIC", "Part 5 - Ngữ pháp & Từ loại", 2,
     "attends", "attend", "Every + số ít → attends."),
    ("The accountant reconciles the invoices every month.", "TOEIC", "Part 5 - Ngữ pháp & Từ loại", 3,
     "reconciles", "reconcile", "Chủ ngữ ngôi 3 số ít → reconciles."),
    ("The company provides health insurance for all staff.", "TOEIC", "Part 5 - Ngữ pháp & Từ loại", 2,
     "provides", "provide", "Chủ ngữ ngôi 3 số ít → provides."),
    # MATH
    ("The sum of two even numbers is always an even number.", "MATH", "Số học & Tổ hợp", 3,
     "is", "are", "Chủ ngữ số ít (the sum) → is."),
    ("Every prime number greater than 2 is odd.", "MATH", "Số học & Tổ hợp", 3,
     "is", "are", "Every + số ít → is."),
    ("The product of two consecutive integers is even.", "MATH", "Số học & Tổ hợp", 3,
     "is", "are", "Chủ ngữ số ít → is."),
    ("A triangle has exactly three sides.", "MATH", "Hình học & Vectơ", 1,
     "has", "have", "Chủ ngữ ngôi 3 số ít → has."),
    ("The square root of 144 is 12.", "MATH", "Đại số & Giải phương trình", 1,
     "is", "are", "Chủ ngữ số ít → is."),
    ("Zero is the additive identity in arithmetic.", "MATH", "Đại số & Giải phương trình", 2,
     "is", "are", "Chủ ngữ số ít → is."),
    # PHYS
    ("Newton's first law states that an object in motion remains in motion.", "PHYS", "Cơ học & Chuyển động", 2,
     "states", "state", "Chủ ngữ ngôi 3 số ít → states."),
    ("The unit of electric current is the ampere.", "PHYS", "Điện học & Ohm", 1,
     "is", "are", "Chủ ngữ số ít → is."),
    ("Sound travels faster in solids than in air.", "PHYS", "Sóng & Quang", 2,
     "travels", "travel", "Chủ ngữ ngôi 3 số ít → travels."),
    ("The speed of light in a vacuum is constant.", "PHYS", "Sóng & Quang", 3,
     "is", "are", "Chủ ngữ số ít → is."),
    ("An object at rest remains at rest unless acted on by a force.", "PHYS", "Cơ học & Chuyển động", 3,
     "remains", "remain", "Chủ ngữ ngôi 3 số ít → remains."),
    ("The total energy of an isolated system is conserved.", "PHYS", "Cơ học & Chuyển động", 4,
     "is", "are", "Chủ ngữ số ít → is."),
    # CHEM
    ("An atom contains a nucleus with protons and neutrons.", "CHEM", "Bảng tuần hoàn & Cấu hình electron", 2,
     "contains", "contain", "Chủ ngữ ngôi 3 số ít → contains."),
    ("Water is composed of hydrogen and oxygen.", "CHEM", "Liên kết & Phản ứng", 1,
     "is", "are", "Chủ ngữ số ít → is."),
    ("The periodic table organizes elements by atomic number.", "CHEM", "Bảng tuần hoàn & Cấu hình electron", 2,
     "organizes", "organize", "Chủ ngữ ngôi 3 số ít → organizes."),
    ("A catalyst increases the rate of a chemical reaction.", "CHEM", "Liên kết & Phản ứng", 3,
     "increases", "increase", "Chủ ngữ ngôi 3 số ít → increases."),
    ("Sodium reacts violently with cold water.", "CHEM", "Liên kết & Phản ứng", 3,
     "reacts", "react", "Chủ ngữ ngôi 3 số ít → reacts."),
    ("Every atom of a given element has the same atomic number.", "CHEM", "Bảng tuần hoàn & Cấu hình electron", 3,
     "has", "have", "Every + số ít → has."),
    # HIST
    ("Ngo Quyen defeated the Southern Han at Bach Dang in 938.", "HIST", "Thời kỳ phong kiến & Kháng Pháp Mông", 3,
     "defeated", "defeat", "Sự kiện năm 938 → quá khứ đơn: defeated."),
    ("The August Revolution succeeded in August 1945.", "HIST", "Hiện đại 1945-1975", 2,
     "succeeded", "succeed", "Sự kiện quá khứ → succeeded."),
    ("Ho Chi Minh declared independence on September 2, 1945.", "HIST", "Hiện đại 1945-1975", 2,
     "declared", "declare", "Sự kiện quá khứ → declared."),
    ("The Battle of Dien Bien Phu ended on May 7, 1954.", "HIST", "Kháng chiến chống Pháp", 3,
     "ended", "end", "Sự kiện quá khứ → ended."),
    ("Le Loi founded the Later Le dynasty in 1428.", "HIST", "Thời kỳ phong kiến & Kháng Pháp Mông", 3,
     "founded", "found", "Sự kiện quá khứ → founded."),
    ("Ly Thai To moved the capital to Thang Long in 1010.", "HIST", "Thời kỳ phong kiến & Kháng Pháp Mông", 3,
     "moved", "move", "Sự kiện quá khứ → moved."),
    # BIO
    ("The mitochondria produce most of the cell's ATP.", "BIO", "Tế bào & Di truyền", 2,
     "produce", "produces", "'mitochondria' là số nhiều → produce."),
    ("DNA stores genetic information in the nucleus.", "BIO", "Tế bào & Di truyền", 2,
     "stores", "store", "Chủ ngữ ngôi 3 số ít → stores."),
    ("Photosynthesis occurs in the chloroplasts of plant cells.", "BIO", "Cơ thể thực vật", 2,
     "occurs", "occur", "Chủ ngữ ngôi 3 số ít → occurs."),
    ("Hemoglobin carries oxygen in red blood cells.", "BIO", "Cơ thể người", 2,
     "carries", "carry", "Chủ ngữ ngôi 3 số ít → carries."),
    ("Every human somatic cell has 46 chromosomes.", "BIO", "Tế bào & Di truyền", 2,
     "has", "have", "Every + số ít → has."),
    ("Insulin lowers blood glucose levels after a meal.", "BIO", "Cơ thể người", 3,
     "lowers", "lower", "Chủ ngữ ngôi 3 số ít → lowers."),
]

def build_grammar_items(facts=None):
    """Mỗi EN fact → 1 item nhãn grammar (lỗi ngữ pháp TA tất định trong câu dẫn)."""
    items = []
    for sentence, subject, topic, diff, right, wrong, expl in (facts if facts is not None else EN_FACTS):
        corrupted = sentence.replace(right, wrong, 1)
        assert corrupted != sentence, sentence
        content_words = [w.strip(".,\"'“”") for w in sentence.split()]
        pool = list(dict.fromkeys(w for w in content_words
                                  if w.lower() != right.lower() and len(w) > 3))
        FILLER = ["analyse", "compute", "validate", "optimize", "review", "design",
                  "implement", "inspect", "monitor", "schedule"]
        for f in FILLER:
            if len(pool) >= 3:
                break
            if f not in pool:
                pool.append(f)
        distract = rng.sample(pool, 3)
        options = distract + [wrong]
        rng.shuffle(options)
        label_stem = ("Trong câu tiếng Anh sau, từ nào bị SAI ngữ pháp? “%s”" % corrupted)
        items.append(mk(subject, topic, diff,
                        "%s (Đáp án đúng: “%s”.)" % (expl, right),
                        label_stem, options, options.index(wrong),
                        explanation="Lỗi ngữ pháp: “%s” → đúng phải là “%s”. %s" % (wrong, right, expl)))
        items[-1]["_grammar_note"] = "Lỗi ngữ pháp tiếng Anh: “%s” → “%s”" % (wrong, right)
    return items

# ======================================================================
# BỔ SUNG NỘI DUNG (đảm bảo >= 1000 câu không trùng)
# ======================================================================
def pool_math_extra():
    from math import gcd, comb, perm, log2
    items = []
    A = "MATH"
    # Phương trình bậc nhất thêm 66 bộ
    for a in [3, 4, 5, 6, 7, 8, 2, 9, 11, 13]:
        for x in [3, 5, 7, 11, 13, 17]:
            b = (a * x) % 29 + 1
            c = a * x + b
            stem = "Giải phương trình %dx + %d = %d." % (a, b, c)
            opts = [str(v) for v in sorted({x, x + 1, x - 1, x + 2})]
            items.append(mk(A, "Đại số & Giải phương trình", 2,
                            "x = (%d - %d)/%d = %d." % (c, b, a, x),
                            stem, opts, opts.index(str(x)),
                            stem_en="Solve the equation %dx + %d = %d." % (a, b, c)))
    # Phần trăm thêm 22 bộ
    for p, n in [(6, 150), (8, 250), (14, 350), (18, 300), (22, 200), (32, 125),
                 (38, 200), (42, 300), (48, 175), (52, 250), (58, 300), (62, 150),
                 (64, 125), (66, 200), (68, 175), (72, 125), (78, 200), (84, 250),
                 (85, 40), (92, 75), (4, 350), (28, 400)]:
        val = p * n / 100
        val = int(val) if val == int(val) else round(val, 1)
        stem = "Tính %d%% của %d." % (p, n)
        cand = sorted({val, round(val + 3, 1), round(val - 3, 1), round(val + 7, 1)})
        opts = [("%g" % v) for v in cand]
        items.append(mk(A, "Đại số & Giải phương trình", 2,
                        "%d%% của %d = %g." % (p, n, val),
                        stem, opts, opts.index("%g" % val),
                        stem_en="What is %d%% of %d?" % (p, n)))
    # ƯCLN/BCNN thêm 20 cặp
    for a, b in [(21, 28), (33, 44), (36, 48), (40, 60), (54, 72), (63, 84), (75, 100),
                 (80, 120), (90, 126), (108, 144), (16, 20), (26, 39), (35, 42),
                 (50, 75), (65, 91), (77, 99), (105, 126), (117, 156), (44, 55), (28, 70)]:
        g = gcd(a, b)
        l = a * b // g
        stem = "ƯCLN của %d và %d là bao nhiêu?" % (a, b)
        cand = sorted({g, g * 2, g + 1, g + 3})
        opts = [str(v) for v in cand]
        items.append(mk(A, "Số học & Tổ hợp", 3,
                        "ƯCLN(%d, %d) = %d." % (a, b, g),
                        stem, opts, opts.index(str(g)),
                        stem_en="What is the greatest common divisor of %d and %d?" % (a, b)))
        stem = "BCNN của %d và %d là bao nhiêu?" % (a, b)
        cand = sorted({l, l + a, max(a, b), l + b})
        opts = [str(v) for v in cand]
        items.append(mk(A, "Số học & Tổ hợp", 3,
                        "BCNN(%d, %d) = %d." % (a, b, l),
                        stem, opts, opts.index(str(l)),
                        stem_en="What is the least common multiple of %d and %d?" % (a, b)))
    # Tổ hợp thêm 8 cặp
    for n, k in [(14, 2), (16, 2), (18, 2), (20, 2), (12, 4), (15, 3)]:
        v = comb(n, k)
        stem = "Có bao nhiêu cách chọn %d phần tử từ %d phần tử phân biệt?" % (k, n)
        cand = []
        for c in [v, v + n, v + 20, v + k, v + 100, v + 7]:
            if c not in cand:
                cand.append(c)
            if len(cand) == 4:
                break
        opts = [str(x) for x in cand]
        items.append(mk(A, "Số học & Tổ hợp", 4,
                        "C(%d, %d) = %d." % (n, k, v),
                        stem, opts, opts.index(str(v)),
                        stem_en="How many ways are there to choose %d elements from %d?" % (k, n)))
    # Modulo thêm 8
    for a, m in [(31, 7), (47, 9), (63, 8), (129, 10), (200, 11), (333, 12), (999, 13), (1234, 7)]:
        r = a % m
        stem = "Số dư khi chia %d cho %d là bao nhiêu?" % (a, m)
        cand = sorted({r, r + 1, r + 2, r + 4})
        opts = [str(v) for v in cand]
        items.append(mk(A, "Số học & Tổ hợp", 3,
                        "%d mod %d = %d." % (a, m, r),
                        stem, opts, opts.index(str(r)),
                        stem_en="What is the remainder when %d is divided by %d?" % (a, m)))
    # Log2 thêm 4
    for n in [4096, 8192, 16384, 32768]:
        k = int(log2(n))
        stem = "Giá trị của log₂(%d) là bao nhiêu?" % n
        cand = sorted({k, k + 1, k - 1, k + 2})
        opts = [str(v) for v in cand]
        items.append(mk(A, "Đại số & Giải phương trình", 3,
                        "log₂(%d) = %d." % (n, k),
                        stem, opts, opts.index(str(k)),
                        stem_en="What is the base-2 logarithm of %d?" % n))
    return items

def pool_phys_extra():
    items = []
    A = "PHYS"
    for s, t in [(60, 4), (75, 5), (100, 10), (140, 7), (210, 6), (320, 8),
                 (380, 10), (450, 9), (480, 12), (630, 9)]:
        v = s // t
        stem = "Một vật chuyển động thẳng đều đi được %d m trong %d s. Vận tốc là bao nhiêu?" % (s, t)
        cand = sorted({v, v + 5, max(1, v - 4), v + 8})
        opts = ["%d m/s" % x for x in cand]
        items.append(mk(A, "Cơ học & Chuyển động", 2,
                        "v = s/t = %d/%d = %d." % (s, t, v),
                        stem, opts, opts.index("%d m/s" % v),
                        stem_en="An object travels %d m in %d s at constant speed. What is its speed?" % (s, t)))
    for t in [7, 8, 9, 10]:
        v = 10 * t
        stem = "Vật rơi tự do (g = 10 m/s²). Sau %d s, vận tốc là bao nhiêu?" % t
        cand = sorted({v, v + 10, v - 10, v + 20})
        opts = ["%d m/s" % x for x in cand]
        items.append(mk(A, "Cơ học & Chuyển động", 3,
                        "v = g·t = %d." % v,
                        stem, opts, opts.index("%d m/s" % v),
                        stem_en="An object falls freely for %d s. What is its speed?" % t))
    for t in [1, 2, 3, 4]:
        h = 5 * t * t
        stem = "Vật rơi tự do từ trạng thái nghỉ (g = 10 m/s²). Sau %d s, vật rơi được quãng đường bao nhiêu?" % t
        cand = sorted({h, h + 10, h - 5, h + 20})
        opts = ["%d m" % x for x in cand]
        items.append(mk(A, "Cơ học & Chuyển động", 4,
                        "h = ½gt² = 5×%d² = %d." % (t, h),
                        stem, opts, opts.index("%d m" % h),
                        stem_en="An object falls from rest for %d s. How far does it fall?" % t))
    return items

def pool_chem_extra():
    items = []
    A = "CHEM"
    for sym, z, val in [("S", 16, 6), ("P", 15, 5), ("Si", 14, 4), ("Al", 13, 3), ("Cl", 17, 7)]:
        stem = "Nguyên tử %s (Z = %d) có bao nhiêu electron ở lớp ngoài cùng?" % (sym, z)
        cand = [val, val + 1, val + 2, val - 1 if val > 1 else 10]
        opts = [str(v) for v in cand]
        items.append(mk(A, "Bảng tuần hoàn & Cấu hình electron", 2,
                        "Lớp ngoài cùng của %s có %d electron." % (sym, val),
                        stem, opts, opts.index(str(val)),
                        stem_en="How many valence electrons does %s (Z = %d) have?" % (sym, z)))
    # Bazơ mạnh → pH
    for conc, ph in [(1e-1, 13), (1e-2, 12), (1e-3, 11), (1e-4, 10)]:
        stem = "Dung dịch NaOH có nồng độ %.0e mol/L. pH của dung dịch là bao nhiêu?" % conc
        cand = sorted({ph, ph - 1, ph + 1, 7})
        opts = [str(v) for v in cand]
        items.append(mk(A, "Mol & Dung dịch", 3,
                        "NaOH mạnh: [OH-] = %.0e ⇒ pOH = %d ⇒ pH = %d." % (conc, 14 - ph, ph),
                        stem, opts, opts.index(str(ph)),
                        stem_en="A NaOH solution has a concentration of %.0e mol/L. What is its pH?" % conc))
    # Số oxi hóa thêm 6 trường hợp
    for stem_vi, correct, opts, expl in [
        ("Trong phân tử Fe₂O₃, số oxi hóa của Fe là bao nhiêu?", "+3", ["+3", "+2", "+6", "−3"],
         "O(−2)×3 = −6 ⇒ Fe×2 = +6 ⇒ Fe = +3."),
        ("Trong phân tử NO₂, số oxi hóa của N là bao nhiêu?", "+4", ["+4", "+2", "+1", "+5"],
         "O(−2)×2 = −4 ⇒ N = +4."),
        ("Trong phân tử HClO, số oxi hóa của Cl là bao nhiêu?", "+1", ["+1", "+3", "−1", "+5"],
         "H(+1) + Cl + O(−2) = 0 ⇒ Cl = +1."),
        ("Trong phân tử P₂O₅, số oxi hóa của P là bao nhiêu?", "+5", ["+5", "+3", "−3", "+6"],
         "O(−2)×5 = −10 ⇒ P×2 = +10 ⇒ P = +5."),
        ("Trong phân tử CO₂, số oxi hóa của C là bao nhiêu?", "+4", ["+4", "+2", "−4", "0"],
         "O(−2)×2 = −4 ⇒ C = +4."),
        ("Trong ion SO₄²⁻, số oxi hóa của S là bao nhiêu?", "+6", ["+6", "+4", "+2", "−2"],
         "O(−2)×4 = −8; ion −2 ⇒ S = +6."),
    ]:
        items.append(mk(A, "Liên kết & Phản ứng", 3, expl, stem_vi, opts, opts.index(correct)))
    # Mol: tính số mol
    for mass, m in [(18, 18), (88, 44), (64, 32), (196, 98), (56, 28), (80, 40)]:
        n = mass / m
        n = int(n) if n == int(n) else n
        stem = "Có %g g chất có M = %d g/mol. Số mol là bao nhiêu?" % (mass, m)
        cand = [n, n + 1, n + 2, n + 4]
        opts = ["%g mol" % x for x in cand]
        items.append(mk(A, "Mol & Dung dịch", 2,
                        "n = m/M = %g/%d = %g." % (mass, m, n),
                        stem, opts, opts.index("%g mol" % n),
                        stem_en="How many moles are in %g g of a substance with M = %d g/mol?" % (mass, m)))
    return items

def pool_hist_extra():
    items = []
    A = "HIST"
    facts = [
        ("Khởi nghĩa Yên Bái (1930) do ai lãnh đạo?", "Nguyễn Thái Học", ["Phan Bội Châu", "Phan Châu Trinh", "Nguyễn An Ninh"],
         "Thời kỳ phong kiến & Kháng Pháp Mông"),
        ("Phong trào Đông Du (1905–1909) do ai khởi xướng?", "Phan Bội Châu", ["Phan Châu Trinh", "Nguyễn Thái Học", "Huỳnh Thúc Kháng"],
         "Thời kỳ phong kiến & Kháng Pháp Mông"),
        ("Phan Châu Trinh chủ trương cải cách theo con đường nào?",
         "Cải cách dân chủ (dựa vào Pháp để tự cường)", ["Khởi nghĩa vũ trang", "Bất bạo động", "Cải cách tôn giáo"],
         "Thời kỳ phong kiến & Kháng Pháp Mông"),
        ("Năm nào Nguyễn Ái Quốc đọc “Sơ thảo lần thứ nhất những luận cương về vấn đề dân tộc và vấn đề thuộc địa” của Lênin?",
         "1920", ["1911", "1917", "1925"], "Thời kỳ phong kiến & Kháng Pháp Mông"),
        ("“Đường Kách mệnh” (1927) là tác phẩm của ai?",
         "Nguyễn Ái Quốc", ["Phan Bội Châu", "Hồ Tùng Mậu", "Lê Hồng Phong"],
         "Thời kỳ phong kiến & Kháng Pháp Mông"),
        ("Tổ chức nào được thành lập ngày 27 tháng 6 năm 1929 tại Hà Nội?",
         "Đông Dương Cộng sản Đảng", ["An Nam Cộng sản Đảng", "Tân Việt Cách mạng Đảng", "Việt Nam Quốc dân Đảng"],
         "Thời kỳ phong kiến & Kháng Pháp Mông"),
        ("Trận Đông Bộ Đầu (1258) quân ta đánh thắng đạo quân nào của Nguyên Mông?",
         "Quân Mông Cổ", ["Quân Nam Hán", "Quân Minh", "Quân Thanh"],
         "Thời kỳ phong kiến & Kháng Pháp Mông"),
        ("Nhà Trần được thành lập năm nào?", "1225", ["1226", "1009", "1400"],
         "Thời kỳ phong kiến & Kháng Pháp Mông"),
        ("Hồ Quý Ly phế truất nhà Trần, lập ra nhà Hồ năm nào?", "1400", ["1225", "1407", "1428"],
         "Thời kỳ phong kiến & Kháng Pháp Mông"),
        ("Trận Chi Lăng – Xương Giang (1427) nghĩa quân Lam Sơn đánh bại đạo quân nào?",
         "Quân Minh", ["Quân Thanh", "Quân Nam Hán", "Quân Nguyên"],
         "Thời kỳ phong kiến & Kháng Pháp Mông"),
        ("Nguyễn Huệ lên ngôi hoàng đế lấy hiệu Quang Trung năm nào?", "1788", ["1771", "1789", "1802"],
         "Thời kỳ phong kiến & Kháng Pháp Mông"),
        ("Năm 1802, ai lên ngôi hoàng đế lập ra nhà Nguyễn?", "Gia Long (Nguyễn Ánh)",
         ["Minh Mạng", "Tự Đức", "Khải Định"], "Thời kỳ phong kiến & Kháng Pháp Mông"),
        ("Kinh đô của nhà Nguyễn (1802–1945) đặt ở đâu?", "Huế", ["Hà Nội", "Thăng Long", "Sài Gòn"],
         "Thời kỳ phong kiến & Kháng Pháp Mông"),
        ("Năm 1858, Pháp mở đầu xâm lược Việt Nam bằng cuộc tấn công vào đâu?",
         "Đà Nẵng", ["Gia Định", "Huế", "Hải Phòng"], "Thời kỳ phong kiến & Kháng Pháp Mông"),
        ("Hiệp ước nào (1862) triều đình nhà Nguyễn nhượng 3 tỉnh miền Đông Nam Kỳ cho Pháp?",
         "Hiệp ước Nhâm Tuất", ["Hiệp ước Giáp Tuất", "Hiệp ước Hác-măng", "Hiệp ước Patơnốt"],
         "Thời kỳ phong kiến & Kháng Pháp Mông"),
        ("Ai là lãnh tụ khởi nghĩa Hương Khê (1885–1896)?",
         "Phan Đình Phùng", ["Đinh Công Tráng", "Nguyễn Thiện Thuật", "Đề Nắm"],
         "Thời kỳ phong kiến & Kháng Pháp Mông"),
        ("Trận Vạn Tường (1965) quân ta đã đánh bại đối phương nào?",
         "Quân viễn chinh Mỹ", ["Quân Pháp", "Quân Nhật", "Quân Anh"],
         "Hiện đại 1945-1975"),
        ("Tổng tiến công và nổi dậy Tết Mậu Thân diễn ra vào năm nào?",
         "1968", ["1965", "1972", "1975"], "Hiện đại 1945-1975"),
        ("Cuộc tập kích chiến lược bằng B-52 của Mỹ vào Hà Nội, Hải Phòng (12 ngày đêm) diễn ra cuối năm nào?",
         "1972", ["1968", "1973", "1975"], "Hiện đại 1945-1975"),
        ("Ai là Tổng Bí thư đầu tiên của Đảng Cộng sản Việt Nam?",
         "Trần Phú", ["Nguyễn Ái Quốc", "Trường Chinh", "Lê Hồng Phong"],
         "Hiện đại 1945-1975"),
    ]
    for q, correct, distractors, topic in facts:
        opts = [correct] + list(distractors)
        rng.shuffle(opts)
        items.append(mk(A, topic, 3, "Đáp án: %s." % correct, q, opts, opts.index(correct),
                        stem_en="History: %s" % q))
    # Biến thể hỏi năm cho các năm nổi bật
    year_facts = [
        ("968", "Đinh Bộ Lĩnh thống nhất đất nước, lập nhà Đinh"),
        ("1010", "Lý Thái Tổ dời đô ra Thăng Long"),
        ("1258", "Chiến thắng Đông Bộ Đầu lần 1 chống Nguyên Mông"),
        ("1285", "Chiến thắng Chương Dương – Hàm Tử – Tây Kết chống Nguyên Mông lần 2"),
        ("1400", "Hồ Quý Ly lập nhà Hồ"),
        ("1427", "Chiến thắng Chi Lăng – Xương Giang"),
        ("1771", "Khởi nghĩa Tây Sơn bùng nổ"),
        ("1789", "Chiến thắng Ngọc Hồi – Đống Đa"),
        ("1858", "Pháp nổ súng xâm lược Việt Nam tại Đà Nẵng"),
        ("1885", "Phong trào Cần Vương bùng nổ"),
    ]
    for year, event in year_facts:
        q = "Sự kiện nào sau đây diễn ra vào năm %s?" % year
        opts = [event, "Khởi nghĩa Yên Bái", "Đông Du", "Xô Viết Nghệ Tĩnh"]
        rng.shuffle(opts)
        items.append(mk(A, "Thời kỳ phong kiến & Kháng Pháp Mông", 3,
                        "Năm %s: %s." % (year, event), q, opts, opts.index(event),
                        stem_en="Which event took place in %s?" % year))
    return items

def pool_bio_extra():
    items = []
    A = "BIO"
    curated = [
        ("Enzim (enzyme) có bản chất hóa học chủ yếu là chất nào?", "Protein",
         ["Protein", "Lipid", "Carbohydrate", "Vitamin"],
         "Hầu hết enzyme là protein (một số ít là RNA).", "Tế bào & Di truyền"),
        ("Enzim có tính chất nào sau đây?", "Có tính đặc hiệu (mỗi enzyme xúc tác chủ yếu cho một phản ứng)",
         ["Có tính đặc hiệu (mỗi enzyme xúc tác chủ yếu cho một phản ứng)",
          "Bị tiêu hao hoàn toàn sau phản ứng",
          "Không bị ảnh hưởng bởi nhiệt độ",
          "Hoạt động tốt nhất ở pH rất cao"],
         "Enzyme đặc hiệu cho cơ chất và phản ứng nhất định.", "Tế bào & Di truyền"),
        ("Quá trình hô hấp tế bào chủ yếu diễn ra ở bào quan nào?", "Ti thể",
         ["Ti thể", "Lục lạp", "Nhân", "Bộ máy Golgi"],
         "Ti thể là nơi chu trình Krebs và chuỗi hô hấp xảy ra.", "Tế bào & Di truyền"),
        ("Phương trình tổng quát của hô hấp tế bào là gì?",
         "C₆H₁₂O₆ + 6O₂ → 6CO₂ + 6H₂O + năng lượng",
         ["C₆H₁₂O₆ + 6O₂ → 6CO₂ + 6H₂O + năng lượng",
          "6CO₂ + 6H₂O → C₆H₁₂O₆ + 6O₂",
          "C₆H₁₂O₆ → 2C₂H₅OH + 2CO₂",
          "C₆H₁₂O₆ + O₂ → C₆H₁₂O₆"],
         "Hô hấp hiếu khí: đường bị oxi hóa hoàn toàn.", "Tế bào & Di truyền"),
        ("Nguyên liệu chính của quang hợp là gì?", "CO₂ và H₂O",
         ["CO₂ và H₂O", "O₂ và glucose", "N₂ và H₂O", "CO và O₂"],
         "6CO₂ + 6H₂O (ánh sáng) → C₆H₁₂O₆ + 6O₂.", "Cơ thể thực vật"),
        ("Quang hợp diễn ra ở bộ phận nào của tế bào thực vật?", "Lục lạp",
         ["Lục lạp", "Ti thể", "Không bào", "Vách tế bào"],
         "Diệp lục trong lục lạp hấp thụ ánh sáng.", "Cơ thể thực vật"),
        ("Người có nhóm máu O có thể cho máu cho nhóm nào?", "Mọi nhóm máu",
         ["Mọi nhóm máu", "Chỉ nhóm O", "Nhóm O và A", "Nhóm AB và B"],
         "Nhóm O không có kháng nguyên A/B → người cho phổ thông.", "Cơ thể người"),
        ("Nhóm máu AB có thể nhận máu từ nhóm nào?", "Mọi nhóm máu",
         ["Mọi nhóm máu", "Chỉ nhóm AB", "Chỉ nhóm O và A", "Chỉ nhóm O và B"],
         "AB không có kháng thể anti-A/anti-B → nhận phổ thông.", "Cơ thể người"),
        ("Gen lặn gây bệnh mù màu nằm trên nhiễm sắc thể nào?", "NST giới tính X",
         ["NST giới tính X", "NST thường số 21", "NST giới tính Y", "NST thường số 1"],
         "Bệnh mù màu/máu khó đông: gen lặn trên X.", "Tế bào & Di truyền"),
        ("Bộ ba mở đầu (START codon) trên mRNA là gì?", "AUG",
         ["AUG", "UAA", "UAG", "UGA"], "AUG mã hóa Met và khởi đầu dịch mã.", "Tế bào & Di truyền"),
        ("Nơi dịch mã tổng hợp protein là bào quan nào?", "Ribosome",
         ["Ribosome", "Nhân", "Ti thể", "Lục lạp"],
         "Ribosome đọc mRNA và gắn các axit amin.", "Tế bào & Di truyền"),
        ("Trong hệ sinh thái, sinh vật tiêu thụ là những loài nào?",
         "Động vật (ăn thực vật/động vật khác)", ["Động vật (ăn thực vật/động vật khác)",
                                               "Thực vật quang hợp",
                                               "Vi khuẩn và nấm phân hủy",
                                               "Khoáng chất vô cơ"],
         "Sinh vật tiêu thụ lấy chất hữu cơ sẵn có.", "Sinh thái & Tiến hóa"),
        ("Mũi tên trong chuỗi thức ăn biểu thị điều gì?",
         "Chiều dòng năng lượng và vật chất (từ thức ăn sang kẻ ăn)",
         ["Chiều dòng năng lượng và vật chất (từ thức ăn sang kẻ ăn)",
          "Chiều di chuyển của sinh vật",
          "Chiều gió",
          "Chiều phát tán hạt giống"],
         "Năng lượng truyền từ sinh vật bị ăn sang sinh vật ăn.", "Sinh thái & Tiến hóa"),
        ("Vaccine hoạt động dựa trên nguyên lý nào?",
         "Đưa kháng nguyên suy yếu/bất hoạt để cơ thể tạo kháng thể và trí nhớ miễn dịch",
         ["Đưa kháng nguyên suy yếu/bất hoạt để cơ thể tạo kháng thể và trí nhớ miễn dịch",
          "Giết trực tiếp vi khuẩn bằng độc tố",
          "Làm đông máu tại vết thương",
          "Giảm nhiệt độ cơ thể"],
         "Miễn dịch chủ động nhân tạo.", "Cơ thể người"),
        ("Kháng thể (antibody) là loại phân tử nào?", "Protein",
         ["Protein", "Lipid", "Carbohydrate", "DNA"],
         "Kháng thể là globulin miễn dịch (protein).", "Cơ thể người"),
        ("Thiếu vitamin C gây bệnh gì?", "Scobut (chảy máu chân răng)",
         ["Scobut (chảy máu chân răng)", "Còi xương", "Beri-beri", "Quáng gà"],
         "Vitamin C cần cho tổng hợp collagen.", "Cơ thể người"),
    ]
    for stem_vi, correct, opts, expl, topic in curated:
        items.append(mk(A, topic, 2, expl, stem_vi, opts, opts.index(correct),
                        stem_en="Biology: %s" % stem_vi))
    # parameterized: gen
    for total, a in [(3000, 600), (2400, 480), (4200, 900), (1800, 300)]:
        g = (total - 2 * a) // 2
        stem = "Một gen có tổng %d nucleotit, trong đó A = %d. Số nucleotit loại G là bao nhiêu?" % (total, a)
        cand = sorted({g, g + 100, g - 100, a})
        opts = [str(v) for v in cand]
        items.append(mk(A, "Tế bào & Di truyền", 4,
                        "A + T = %d ⇒ G + X = %d ⇒ G = %d." % (2 * a, total - 2 * a, g),
                        stem, opts, opts.index(str(g)),
                        stem_en="A gene has %d nucleotides in total, of which A = %d. How many G nucleotides does it have?" % (total, a)))
    return items

def pool_int3202_extra():
    items = []
    A = "INT3202"
    curated = [
        ("Trong SQL, toán tử EXISTS trả về TRUE khi nào?",
         ["Khi truy vấn con trả về ít nhất một dòng",
          "Khi truy vấn con trả về NULL",
          "Khi truy vấn con trả về đúng một dòng",
          "Khi truy vấn con trả về mọi dòng"],
         0, "Ngôn ngữ truy vấn SQL", 3,
         "EXISTS: ngữ nghĩa boolean theo sự tồn tại dòng.",
         "When does the EXISTS operator return TRUE in SQL?"),
        ("Trong SQL, toán tử IN tương đương với gì?",
         ["= ANY của một danh sách/tập giá trị",
          "= ALL của một danh sách giá trị",
          "LIKE '%...%'",
          "BETWEEN"],
         0, "Ngôn ngữ truy vấn SQL", 3,
         "x IN (a,b,c) ⇔ x = a OR x = b OR x = c.",
         "What is the IN operator equivalent to in SQL?"),
        ("Câu SQL nào sau đây tính số lượng đơn hàng theo từng khách hàng?",
         ["SELECT customer_id, COUNT(*) FROM orders GROUP BY customer_id",
          "SELECT customer_id, COUNT(*) FROM orders",
          "SELECT COUNT(*) FROM orders WHERE customer_id",
          "SELECT customer_id FROM orders ORDER BY customer_id"],
         0, "Ngôn ngữ truy vấn SQL", 2,
         "GROUP BY gom theo khóa, COUNT(*) đếm mỗi nhóm.",
         "Which SQL query counts orders per customer?"),
        ("Hàm COALESCE(a, b, c) trả về giá trị nào?",
         ["Giá trị đầu tiên khác NULL trong danh sách",
          "Tổng của a, b, c",
          "Giá trị lớn nhất",
          "NULL nếu mọi tham số đều khác NULL"],
         0, "Ngôn ngữ truy vấn SQL", 3,
         "COALESCE: thay NULL bằng giá trị mặc định theo thứ tự.",
         "What does COALESCE return?"),
        ("CASE WHEN trong SQL dùng để làm gì?",
         ["Tạo biểu thức điều kiện trả về các giá trị khác nhau",
          "Đổi tên bảng",
          "Tạo index",
          "Khóa dòng"],
         0, "Ngôn ngữ truy vấn SQL", 2,
         "CASE WHEN ... THEN ... ELSE ... END: rẽ nhánh trong SELECT.",
         "What is CASE WHEN used for in SQL?"),
        ("Anomaly nào xảy ra khi xóa một dòng kéo theo mất thông tin không liên quan?",
         ["Delete anomaly (dị thường xóa)",
          "Insert anomaly (dị thường chèn)",
          "Update anomaly (dị thường sửa)",
          "Phantom read"],
         0, "Chuẩn hóa dữ liệu", 3,
         "Dữ liệu không chuẩn hóa: xóa kéo theo mất mát thông tin.",
         "Which anomaly occurs when deleting a row also loses unrelated information?"),
        ("Khóa hiển nhiên (surrogate key) là gì?",
         ["Khóa nhân tạo do hệ thống sinh (không mang ý nghĩa nghiệp vụ)",
          "Khóa chính gồm nhiều cột nghiệp vụ",
          "Khóa ngoại",
          "Khóa được mã hóa"],
         0, "Mô hình quan hệ & Khóa", 2,
         "Surrogate key: ID kỹ thuật tự tăng, ổn định theo thời gian.",
         "What is a surrogate key?"),
        ("Mối quan hệ nhiều-nhiều (M:N) trong mô hình ER được cài đặt thế nào?",
         ["Tách thành bảng trung gian chứa khóa ngoại của hai bên",
          "Thêm khóa ngoại vào một trong hai bảng",
          "Không thể cài đặt",
          "Dùng khóa chính ghép 4 cột"],
         0, "Mô hình quan hệ & Khóa", 3,
         "M:N → junction table (bảng liên kết) với FK tới cả hai.",
         "How is a many-to-many relationship implemented?"),
        ("Một thực thể yếu (weak entity) trong mô hình ER có đặc điểm gì?",
         ["Không có khóa riêng, được định danh qua thực thể chủ sở hữu",
          "Không có thuộc tính",
          "Luôn có đúng 1 thuộc tính",
          "Là thực thể trừu tượng"],
         0, "Mô hình quan hệ & Khóa", 4,
         "Weak entity: định danh một phần qua owner (partial key).",
         "What characterizes a weak entity in an ER model?"),
        ("Trong MariaDB/MySQL, kiểu dữ liệu nào phù hợp để lưu chuỗi UTF-8 4 byte (emoji)?",
         ["utf8mb4", "utf8", "latin1", "ascii"], 0,
         "Ngôn ngữ truy vấn SQL", 2,
         "utf8mb4 hỗ trợ toàn bộ Unicode kể cả emoji (4 byte).",
         "Which character set stores 4-byte UTF-8 (emoji) in MySQL/MariaDB?"),
        ("Giao dịch bị deadlock nghĩa là gì?",
         ["Hai hoặc nhiều giao dịch chờ khóa lẫn nhau vô hạn",
          "Giao dịch chạy quá lâu",
          "Giao dịch đọc dữ liệu bẩn",
          "Giao dịch bị hủy do hết bộ nhớ"],
         0, "Chuẩn hóa dữ liệu", 4,
         "Deadlock: vòng chờ khóa — DBMS phát hiện và hủy một nạn nhân.",
         "What is a deadlock between transactions?"),
        ("Mức cô lập READ COMMITTED cho phép hiện tượng nào?",
         ["Non-repeatable read và phantom read",
          "Dirty read",
          "Không cho phép hiện tượng nào",
          "Chỉ dirty read"],
         0, "Chuẩn hóa dữ liệu", 4,
         "READ COMMITTED chặn dirty read, còn non-repeatable/phantom.",
         "Which phenomena does READ COMMITTED allow?"),
        ("Phương án nào mô tả đúng về subquery tương quan (correlated subquery)?",
         ["Truy vấn con tham chiếu tới cột của truy vấn ngoài",
          "Truy vấn con độc lập hoàn toàn",
          "Truy vấn con luôn trả về 1 dòng",
          "Truy vấn con không được trong WHERE"],
         0, "Ngôn ngữ truy vấn SQL", 4,
         "Correlated: thực thi lại cho mỗi dòng ngoài.",
         "What characterizes a correlated subquery?"),
        ("Trong ER, quan hệ 1:N giữa phòng ban và nhân viên được cài đặt thế nào?",
         ["Thêm khóa ngoại phòng ban vào bảng nhân viên",
          "Thêm khóa ngoại nhân viên vào bảng phòng ban",
          "Tạo bảng trung gian",
          "Gộp hai bảng"],
         0, "Mô hình quan hệ & Khóa", 2,
         "1:N → FK nằm ở phía N.",
         "How is a 1:N relationship between department and employee implemented?"),
        ("CHECK constraint dùng để làm gì?",
         ["Ràng buộc điều kiện trên giá trị cột (ví dụ difficulty BETWEEN 1 AND 5)",
          "Tạo index",
          "Đảm bảo duy nhất",
          "Khóa ngoại"],
         0, "Mô hình quan hệ & Khóa", 2,
         "CHECK: biểu thức boolean phải đúng với mọi dòng.",
         "What is a CHECK constraint used for?"),
    ]
    for stem_vi, opts, correct, topic, diff, expl, en in curated:
        items.append(mk(A, topic, diff, expl, stem_vi, list(opts), correct, stem_en=en))
    return items

def pool_int3304_extra():
    items = []
    A = "INT3304"
    for port, svc in [(23, "Telnet"), (25, "SMTP"), (110, "POP3"), (143, "IMAP"), (3389, "RDP"),
                      (67, "DHCP server"), (179, "BGP"), (3306, "MySQL/MariaDB")]:
        stem = "Cổng mặc định của giao thức %s là bao nhiêu?" % svc
        cand = sorted({port, port + 1, max(1, port - 1), port + 10})
        opts = [str(v) for v in cand]
        items.append(mk(A, "Giao thức ứng dụng HTTP/REST", 2,
                        "%s dùng cổng %d." % (svc, port),
                        stem, opts, opts.index(str(port)),
                        stem_en="What is the default port for %s?" % svc))
    for stem_vi, opts, correct, expl, en in [
        ("MTU (Maximum Transmission Unit) là gì?",
         ["Kích thước gói dữ liệu lớn nhất có thể truyền qua một liên kết",
          "Tốc độ tối đa của liên kết",
          "Số lượng host tối đa trong mạng",
          "Độ dài tối đa của tên miền"],
         0, "MTU Ethernet thường 1500 byte.", "What is the Maximum Transmission Unit (MTU)?"),
        ("Độ trễ (latency) và băng thông (bandwidth) khác nhau thế nào?",
         ["Latency là thời gian một gói đi từ A tới B; bandwidth là lượng dữ liệu truyền được trong một đơn vị thời gian",
          "Là hai tên gọi của cùng một khái niệm",
          "Latency đo bằng Hz, bandwidth đo bằng giây",
          "Bandwidth luôn nhỏ hơn latency"],
         0, "Latency: thời gian; bandwidth: thông lượng.",
         "How do latency and bandwidth differ?"),
        ("Switch học địa chỉ MAC của các máy trong LAN như thế nào?",
         ["Đọc địa chỉ nguồn của khung đến và ghi vào bảng MAC",
          "Hỏi DNS",
          "Quét toàn bộ mạng theo chu kỳ",
          "Từ cấu hình tĩnh do quản trị viên nhập"],
         0, "Switch xây dựng bảng MAC động từ nguồn của frame.",
         "How does a switch learn MAC addresses?"),
        ("Broadcast storm là hiện tượng gì?",
         ["Bão khung quảng bá làm nghẽn mạng LAN do vòng lặp",
          "Tấn công DDoS từ Internet",
          "Nhiễu sóng vô tuyến",
          "Mất kết nối định kỳ"],
         0, "Spanning Tree Protocol (STP) ngăn vòng lặp L2.",
         "What is a broadcast storm?"),
        ("NAT (Network Address Translation) chủ yếu dùng để làm gì?",
         ["Chuyển đổi giữa địa chỉ riêng (private) và địa chỉ công cộng (public)",
          "Mã hóa dữ liệu",
          "Phân giải tên miền",
          "Cấp phát IP tự động"],
         0, "NAT: tiết kiệm IPv4, chia sẻ 1 IP public cho nhiều máy.",
         "What is NAT primarily used for?"),
        ("Mô hình TCP/IP gồm mấy tầng?",
         ["4 (hoặc 5) tầng", "7 tầng", "3 tầng", "6 tầng"],
         0, "TCP/IP: Application, Transport, Internet, Network Access.",
         "How many layers does the TCP/IP model have?"),
        ("Cáp xoắn đôi UTP Cat6 hỗ trợ tốc độ tối đa danh nghĩa nào theo chuẩn 10GBASE-T?",
         ["10 Gbps (trong khoảng cách ngắn)", "10 Mbps", "100 Mbps", "1 Gbps only"],
         0, "Cat6: 10GBASE-T tới ~55m; Cat6a cho 100m.",
         "What is the nominal maximum speed of Cat6 UTP at short distances?"),
    ]:
        items.append(mk(A, "Kiến trúc phân tầng OSI/TCP-IP", 3, expl, stem_vi, list(opts), correct, stem_en=en))
    return items

def pool_int3306_extra():
    items = []
    A = "INT3306"
    curated = [
        ("Generics trong Java được xóa bỏ (type erasure) tại thời điểm nào?",
         ["Lúc biên dịch", "Lúc chạy", "Lúc đóng gói JAR", "Không bao giờ bị xóa"],
         0, "Cú pháp & OOP cơ bản", 3,
         "Erasure: thông tin kiểu generic biến mất ở bytecode.",
         "When are generics erased in Java?"),
        ("Functional interface trong Java 8 là gì?",
         ["Interface có đúng một abstract method (dùng cho lambda)",
          "Interface chỉ có static methods",
          "Interface không thể implement",
          "Interface có default methods"],
         0, "Cú pháp & OOP cơ bản", 3,
         "@FunctionalInterface: 1 abstract method duy nhất.",
         "What is a functional interface in Java 8?"),
        ("Biểu thức lambda trong Java 8 dùng để làm gì?",
         ["Viết gọn implementation của functional interface",
          "Tạo class vô danh với nhiều method",
          "Khai báo biến hằng",
          "Định nghĩa annotation"],
         0, "Cú pháp & OOP cơ bản", 2,
         "Lambda: (args) -> body rút gọn anonymous class.",
         "What are lambda expressions used for?"),
        ("Từ khóa synchronized trong Java đảm bảo điều gì?",
         ["Chỉ một luồng được vào vùng tới hạn tại một thời điểm",
          "Tăng tốc độ xử lý",
          "Tự động giải phóng bộ nhớ",
          "Biên dịch nhanh hơn"],
         0, "Ngoại lệ & Collections", 3,
         "synchronized: mutex trên object/class monitor.",
         "What does the synchronized keyword guarantee?"),
        ("Từ khóa volatile đảm bảo điều gì?",
         ["Mọi luồng thấy được giá trị mới nhất của biến (không cache trên CPU)",
          "Biến không thể thay đổi",
          "Biến lưu trên stack",
          "Tự động atomic cho mọi thao tác"],
         0, "Ngoại lệ & Collections", 4,
         "volatile: visibility, không gộp với atomicity.",
         "What does the volatile keyword guarantee?"),
        ("LocalDate trong java.time khác Date như thế nào?",
         ["Immutable, không chứa múi giờ, API rõ ràng",
          "Có thể thay đổi (mutable)",
          "Chỉ lưu giờ",
          "Nhanh hơn 10 lần"],
         0, "Cú pháp & OOP cơ bản", 3,
         "java.time: immutable, thread-safe, tách LocalDate/ZonedDateTime.",
         "How does LocalDate differ from Date?"),
        ("StringBuilder khác String ở điểm nào khi nối chuỗi trong vòng lặp?",
         ["StringBuilder mutable, tránh tạo nhiều đối tượng tạm",
          "String nhanh hơn",
          "Không khác nhau",
          "StringBuilder không thread-safe nên luôn nhanh hơn"],
         0, "Cú pháp & OOP cơ bản", 3,
         "Nối String trong loop: O(n²) sao chép; StringBuilder O(n).",
         "How does StringBuilder differ from String in a loop?"),
        ("equalsIgnoreCase() dùng khi nào?",
         ["So sánh chuỗi không phân biệt hoa thường",
          "So sánh tham chiếu",
          "So sánh theo thứ tự alphabet có dấu",
          "So sánh số"],
         0, "Cú pháp & OOP cơ bản", 1,
         "So sánh nội dung bỏ qua case.",
         "When do you use equalsIgnoreCase()?"),
        ("List<? extends Number> cho phép thao tác nào?",
         ["Đọc phần tử ra Number (producer) nhưng không thêm phần tử",
          "Thêm Integer vào list",
          "Thêm Double vào list",
          "Không đọc được gì"],
         0, "Cú pháp & OOP cơ bản", 4,
         "PECS: extends = producer (đọc); super = consumer (ghi).",
         "What does List<? extends Number> allow?"),
        ("Phương thức threads.join() dùng để làm gì?",
         ["Chờ luồng khác kết thúc trước khi tiếp tục",
          "Bắt đầu một luồng",
          "Dừng luồng hiện tại",
          "Đặt độ ưu tiên luồng"],
         0, "Ngoại lệ & Collections", 3,
         "join(): luồng gọi chờ luồng đích chết.",
         "What is the purpose of Thread.join()?"),
    ]
    for stem_vi, opts, correct, topic, diff, expl, en in curated:
        items.append(mk(A, topic, diff, expl, stem_vi, list(opts), correct, stem_en=en))
    return items

def pool_int3401_extra():
    items = []
    A = "INT3401"
    curated = [
        ("Alpha-beta pruning trong minimax có tác dụng gì?",
         ["Cắt tỉa các nhánh không thể ảnh hưởng tới quyết định cuối",
          "Tăng độ sâu tìm kiếm",
          "Loại bỏ nước đi hợp lệ",
          "Chuyển sang tìm kiếm ngẫu nhiên"],
         0, "Tìm kiếm & Biểu diễn tri thức", 4,
         "Cắt nhánh α/β giữ nguyên kết quả minimax.",
         "What is the effect of alpha-beta pruning in minimax?"),
        ("K-means dùng để làm gì?",
         ["Phân cụm dữ liệu không nhãn thành k cụm theo centroid",
          "Phân loại có nhãn",
          "Giảm chiều dữ liệu",
          "Hồi quy"],
         0, "Học máy & Mạng nơ-ron", 2,
         "k-means: lặp gán cụm – cập nhật centroid.",
         "What is K-means used for?"),
        ("Trong mạng nơ-ron, hàm softmax ở lớp cuối dùng để làm gì?",
         ["Chuyển logits thành phân phối xác suất (tổng = 1)",
          "Chuẩn hóa đầu vào",
          "Kích hoạt tuyến tính",
          "Tăng tốc train"],
         0, "Học máy & Mạng nơ-ron", 3,
         "Softmax: e^zi / Σe^zj — xác suất cho đa lớp.",
         "What is the softmax function used for in the output layer?"),
        ("Một epoch trong huấn luyện là gì?",
         ["Một lượt duyệt hết toàn bộ tập huấn luyện",
          "Một batch nhỏ",
          "Một lần cập nhật trọng số",
          "Toàn bộ quá trình train"],
         0, "Học máy & Mạng nơ-ron", 2,
         "Epoch = 1 pass qua toàn dataset.",
         "What is one epoch in training?"),
        ("Batch size ảnh hưởng đến huấn luyện như thế nào?",
         ["Batch lớn: gradient ổn định hơn nhưng tốn bộ nhớ; batch nhỏ: nhiễu hơn nhưng tổng quát tốt hơn đôi khi",
          "Batch càng lớn càng nhanh hội tụ tuyệt đối",
          "Batch size không ảnh hưởng",
          "Batch nhỏ luôn tốt hơn"],
         0, "Học máy & Mạng nơ-ron", 4,
         "Trade-off giữa độ ổn định gradient và tổng quát hóa.",
         "How does batch size affect training?"),
        ("Cosine similarity giữa hai vector đo điều gì?",
         ["Góc giữa hai vector (độ tương đồng hướng)",
          "Khoảng cách Euclid",
          "Độ lớn vector",
          "Tích có hướng"],
         0, "Mô hình ngôn ngữ lớn (LLM)", 3,
         "cosine = (A·B)/(|A||B|) — phổ biến cho embeddings.",
         "What does cosine similarity measure?"),
        ("Self-attention trong Transformer cho phép mô hình làm gì?",
         ["Mỗi token nhìn trực tiếp mọi token khác bất kể khoảng cách",
          "Chỉ nhìn token lân cận",
          "Chỉ xử lý từng từ độc lập",
          "Tăng kích thước từ vựng"],
         0, "Mô hình ngôn ngữ lớn (LLM)", 4,
         "Attention: trọng số tương quan giữa mọi cặp token.",
         "What does self-attention allow a Transformer to do?"),
        ("Data leakage trong ML nghĩa là gì?",
         ["Thông tin từ tập test/tương lai rò rỉ vào quá trình train làm đánh giá lạc quan giả",
          "Dữ liệu bị mất do lỗi ổ cứng",
          "Mô hình bị lộ mã nguồn",
          "Dữ liệu bị mã hóa"],
         0, "Học máy & Mạng nơ-ron", 3,
         "Leakage: ví dụ chuẩn hóa bằng thống kê toàn bộ dữ liệu trước khi chia.",
         "What is data leakage in machine learning?"),
        ("Với dữ liệu mất cân bằng nghiêm trọng, chỉ số nào đáng tin cậy hơn accuracy?",
         ["F1-score (hoặc PR-AUC)", "Accuracy",
          "Số lượng tham số",
          "Thời gian train"],
         0, "Học máy & Mạng nơ-ron", 2,
         "Accuracy bị đánh lừa bởi lớp đa số.",
         "Which metric is more reliable than accuracy on severely imbalanced data?"),
        ("Context window của LLM là gì?",
         ["Số token tối đa mô hình xử lý được trong một lần (prompt + output)",
          "Kích thước từ vựng",
          "Số lớp Transformer",
          "Số tham số của mô hình"],
         0, "Mô hình ngôn ngữ lớn (LLM)", 2,
         "Context window: giới hạn token input+output.",
         "What is the context window of an LLM?"),
        ("Mục đích của kỹ thuật “prompt engineering” là gì?",
         ["Thiết kế chỉ dẫn đầu vào để mô hình trả lời đúng định dạng và đúng nội dung mong muốn",
          "Huấn luyện lại mô hình",
          "Nén mô hình",
          "Tăng tốc suy luận"],
         0, "Mô hình ngôn ngữ lớn (LLM)", 2,
         "Prompt tốt: vai trò, ngữ cảnh, định dạng đầu ra, ràng buộc.",
         "What is the purpose of prompt engineering?"),
        ("Khi LLM sinh câu trả lời tự tin nhưng sai thực tế, ta gọi đó là hiện tượng gì?",
         ["Hallucination (ảo giác)",
          "Overfitting",
          "Underfitting",
          "Catastrophic forgetting"],
         0, "Mô hình ngôn ngữ lớn (LLM)", 2,
         "Hallucination: nội dung bịa đặt có vẻ hợp lý.",
         "What is it called when an LLM confidently produces false facts?"),
        ("Tỷ lệ learning rate quá nhỏ gây hiện tượng gì?",
         ["Hội tụ rất chậm, có thể kẹt ở điểm cực trị kém",
          "Phân kỳ",
          "Overfitting tức thì",
          "Không ảnh hưởng"],
         0, "Học máy & Mạng nơ-ron", 3,
         "η nhỏ: bước nhảy li ti.",
         "What does a too-small learning rate cause?"),
        ("Decision tree dự đoán một mẫu mới bằng cách nào?",
         ["Theo một đường đi từ gốc tới lá theo các điều kiện rẽ nhánh",
          "Bằng trung bình các lá",
          "Bằng láng giềng gần nhất",
          "Bằng siêu phẳng"],
         0, "Học máy & Mạng nơ-ron", 2,
         "Cây: rẽ nhánh if-else trên thuộc tính.",
         "How does a decision tree classify a new sample?"),
    ]
    for stem_vi, opts, correct, topic, diff, expl, en in curated:
        items.append(mk(A, topic, diff, expl, stem_vi, list(opts), correct, stem_en=en))
    return items


# ======================================================================
# MỞ RỘNG QUY MÔ (parameterized sweeps để mỗi domain >= 85 clean items)
# ======================================================================
EN_ABSURD_OPTIONS = [
    "The color of a sunset over the sea",
    "The taste of freshly brewed coffee",
    "The sound of a dove's wings",
    "The price of a movie ticket in 1995",
    "The smell of jasmine at dawn",
    "The lyrics of a famous folk song",
    "The pattern of a traditional ao dai",
    "The flavor of sweet and sour soup",
    "The rhythm of a marching band",
    "The aroma of beef pho simmering for hours",
]

def pool_phys_extra2():
    items = []
    A = "PHYS"
    # Định luật Ohm I = U/R
    for u, r, cur in [(12, 4, 3), (24, 6, 4), (36, 6, 6), (48, 8, 6), (110, 10, 11),
                      (220, 20, 11), (60, 12, 5), (9, 3, 3)]:
        cand = sorted({cur, cur + 1, cur - 1, cur + 2})
        opts = ["%d A" % v for v in cand]
        stem = "Một mạch điện có hiệu điện thế %d V và điện trở %d Ω. Cường độ dòng điện là bao nhiêu?" % (u, r)
        items.append(mk(A, "Điện học & Ohm", 2,
                        "I = U/R = %d/%d = %d." % (u, r, cur),
                        stem, opts, opts.index("%d A" % cur),
                        stem_en="A circuit has voltage %d V and resistance %d ohms. What is the current?" % (u, r)))
    # Công suất P = UI
    for u, i, p in [(12, 2, 24), (24, 3, 72), (110, 2, 220), (220, 1, 220),
                    (9, 0.5, 4.5), (5, 2, 10), (48, 0.25, 12), (36, 2, 72)]:
        cand = sorted({p, p + 10, max(1, p - 10), p + 30})
        opts = ["%g W" % v for v in cand]
        stem = "Thiết bị dùng hiệu điện thế %g V, dòng %g A. Công suất tiêu thụ là bao nhiêu?" % (u, i)
        items.append(mk(A, "Điện học & Ohm", 2,
                        "P = U·I = %g." % p,
                        stem, opts, opts.index("%g W" % p),
                        stem_en="A device runs at %g V and %g A. What is its power consumption?" % (u, i)))
    # Lực F = ma
    for m, a, f in [(2, 5, 10), (4, 3, 12), (5, 10, 50), (8, 4, 32), (10, 10, 100),
                    (6, 7, 42), (3, 8, 24), (15, 4, 60)]:
        cand = sorted({f, f + 10, max(1, f - 10), f + 20})
        opts = ["%d N" % v for v in cand]
        stem = "Vật có khối lượng %d kg, tăng tốc %d m/s². Lực tác dụng là bao nhiêu?" % (m, a)
        items.append(mk(A, "Cơ học & Chuyển động", 2,
                        "F = m·a = %d×%d = %d." % (m, a, f),
                        stem, opts, opts.index("%d N" % f),
                        stem_en="An object of mass %d kg accelerates at %d m/s^2. What force acts on it?" % (m, a)))
    # Sóng v = fλ
    for lam, f, v in [(2, 50, 100), (0.5, 340, 170), (0.25, 1500, 375), (1, 20, 20),
                      (0.1, 1000, 100), (4, 80, 320), (2, 60, 120), (0.5, 200, 100)]:
        cand = sorted({v, v + 50, v + 100, max(5, v - 50)})
        opts = ["%d m/s" % x for x in cand]
        stem = "Một sóng có bước sóng %g m và tần số %g Hz. Vận tốc truyền sóng là bao nhiêu?" % (lam, f)
        items.append(mk(A, "Sóng & Quang", 3,
                        "v = λ·f = %g." % v,
                        stem, opts, opts.index("%d m/s" % v),
                        stem_en="A wave has wavelength %g m and frequency %g Hz. What is its speed?" % (lam, f)))
    # Lò xo F = kx
    for k, x, f in [(100, 0.1, 10), (200, 0.05, 10.0), (150, 0.2, 30), (500, 0.02, 10),
                    (250, 0.1, 25), (300, 0.05, 15), (40, 0.5, 20), (800, 0.01, 8)]:
        cand = sorted({f, f + 10, max(1, f - 5), f + 20})
        opts = ["%g N" % x_ for x_ in cand]
        stem = "Lò xo có độ cứng %d N/m giãn %g m. Lực đàn hồi là bao nhiêu?" % (k, x)
        items.append(mk(A, "Cơ học & Chuyển động", 3,
                        "F = k·x = %d×%g = %g." % (k, x, f),
                        stem, opts, opts.index("%g N" % f),
                        stem_en="A spring with stiffness %d N/m stretches %g m. What is the elastic force?" % (k, x)))
    # Khối lượng riêng ρ = m/V
    for m, v, r in [(100, 100, 1), (270, 100, 2.7), (780, 100, 7.8), (890, 100, 8.9),
                    (750, 100, 7.5), (11300, 1000, 11.3)]:
        cand = sorted({r, round(r + 1, 1), max(0.5, round(r - 1, 1)), round(r + 2, 1)})
        opts = ["%g g/cm³" % x for x in cand]
        stem = "Một vật có khối lượng %g g, thể tích %g cm³. Khối lượng riêng là bao nhiêu?" % (m, v)
        items.append(mk(A, "Cơ học & Chuyển động", 3,
                        "ρ = m/V = %g/%g = %g." % (m, v, r),
                        stem, opts, opts.index("%g g/cm³" % r),
                        stem_en="An object has mass %g g and volume %g cm^3. What is its density?" % (m, v)))
    # Công A = F·s
    for f, s, a in [(20, 5, 100), (50, 10, 500), (100, 2, 200), (15, 4, 60), (200, 3, 600),
                    (80, 5, 400), (30, 8, 240), (60, 7, 420)]:
        cand = sorted({a, a + 50, max(10, a - 50), a + 100})
        opts = ["%d J" % v for v in cand]
        stem = "Lực %d N đẩy vật dịch chuyển %d m theo phương của lực. Công thực hiện là bao nhiêu?" % (f, s)
        items.append(mk(A, "Cơ học & Chuyển động", 2,
                        "A = F·s = %d×%d = %d." % (f, s, a),
                        stem, opts, opts.index("%d J" % a),
                        stem_en="A force of %d N moves an object %d m along its direction. How much work is done?" % (f, s)))
    return items

def pool_chem_extra2():
    items = []
    A = "CHEM"
    # Tính M từ m, n
    for m, n, M in [(18, 1, 18), (88, 2, 44), (64, 2, 32), (196, 2, 98), (56, 2, 28),
                    (160, 2, 80), (142, 1, 142), (100, 1, 100)]:
        cand = sorted({M, M + 2, M - 2, M + 10})
        opts = ["%d g/mol" % v for v in cand]
        stem = "Có %g g chất với số mol là %g mol. Khối lượng mol là bao nhiêu?" % (m, n)
        items.append(mk(A, "Mol & Dung dịch", 2,
                        "M = m/n = %g/%g = %d." % (m, n, M),
                        stem, opts, opts.index("%d g/mol" % M),
                        stem_en="%g g of a substance is %g mol. What is its molar mass?" % (m, n)))
    # Tính m từ n, M
    for n, M, m in [(2, 18, 36), (3, 98, 294), (0.5, 32, 16), (1, 58.5, 58.5),
                    (4, 16, 64), (2, 44, 88), (0.5, 28, 14), (3, 26, 78)]:
        cand = sorted({m, m + 16, max(1, m - 8), m + 32})
        opts = ["%g g" % v for v in cand]
        stem = "Có %g mol chất có M = %g g/mol. Khối lượng là bao nhiêu?" % (n, M)
        items.append(mk(A, "Mol & Dung dịch", 2,
                        "m = n·M = %g×%g = %g." % (n, M, m),
                        stem, opts, opts.index("%g g" % m),
                        stem_en="What is the mass of %g mol of a substance with M = %g g/mol?" % (n, M)))
    # Số phân tử
    for n, label in [(1, "6.022×10²³"), (2, "1.2044×10²⁴"), (0.5, "3.011×10²³"), (3, "1.8066×10²⁴")]:
        cand = sorted({label, "3.011×10²³" if label != "3.011×10²³" else "6.022×10²³",
                       "1.2044×10²⁴" if label != "1.2044×10²⁴" else "6.022×10²³",
                       "9.033×10²³"})
        opts = cand
        stem = "Một lượng chất %g mol chứa bao nhiêu phân tử? (NA = 6.022×10²³)" % n
        items.append(mk(A, "Mol & Dung dịch", 3,
                        "N = n·N_A.", stem, opts, opts.index(label),
                        stem_en="How many molecules are in %g mol? (NA = 6.022e23)" % n))
    # Electron lớp ngoài cùng thêm nguyên tố
    for sym, z, val in [("B", 5, 3), ("C", 6, 4), ("N", 7, 5), ("O", 8, 6), ("F", 9, 7),
                        ("Na", 11, 1), ("Mg", 12, 2), ("K", 19, 1), ("Ca", 20, 2)]:
        cand = [val, val + 1, val + 2, val - 1 if val > 1 else 10]
        opts = [str(v) for v in cand]
        stem = "Nguyên tử %s (Z = %d) có bao nhiêu electron ở lớp ngoài cùng?" % (sym, z)
        items.append(mk(A, "Bảng tuần hoàn & Cấu hình electron", 2,
                        "Lớp ngoài cùng của %s có %d electron." % (sym, val),
                        stem, opts, opts.index(str(val)),
                        stem_en="How many valence electrons does %s (Z = %d) have?" % (sym, z)))
    # pH của axit mạnh
    for exp, ph in [(1, 1), (2, 2), (3, 3), (4, 4), (5, 5)]:
        stem = "Dung dịch HCl nồng độ 10⁻%d mol/L. pH của dung dịch là bao nhiêu?" % exp
        cand = sorted({ph, ph + 1, max(0, ph - 1), 7})
        opts = [str(v) for v in cand]
        items.append(mk(A, "Mol & Dung dịch", 3,
                        "HCl mạnh: [H+] = 10⁻%d ⇒ pH = %d." % (exp, ph),
                        stem, opts, opts.index(str(ph)),
                        stem_en="A solution of HCl has a concentration of 10^-%d mol/L. What is its pH?" % exp))
    # Hệ số cân bằng (combustion/acid)
    for stem_vi, correct, opts, expl in [
        ("Cân bằng H₂ + O₂ → H₂O. Hệ số của O₂ là bao nhiêu?", "1", ["1", "2", "3", "4"],
         "2H₂ + O₂ → 2H₂O."),
        ("Cân bằng CH₄ + O₂ → CO₂ + H₂O. Hệ số của O₂ là bao nhiêu?", "2", ["1", "2", "3", "4"],
         "CH₄ + 2O₂ → CO₂ + 2H₂O."),
        ("Cân bằng C₃H₈ + O₂ → CO₂ + H₂O. Hệ số của O₂ là bao nhiêu?", "5", ["4", "5", "6", "7"],
         "C₃H₈ + 5O₂ → 3CO₂ + 4H₂O."),
        ("Cân bằng Al + HCl → AlCl₃ + H₂. Hệ số của HCl là bao nhiêu?", "6", ["3", "4", "6", "8"],
         "2Al + 6HCl → 2AlCl₃ + 3H₂."),
        ("Cân bằng Fe + HCl → FeCl₂ + H₂. Hệ số của HCl là bao nhiêu?", "2", ["1", "2", "3", "4"],
         "Fe + 2HCl → FeCl₂ + H₂."),
        ("Cân bằng C + O₂ → CO₂. Hệ số của O₂ là bao nhiêu?", "1", ["1", "2", "3", "4"],
         "C + O₂ → CO₂."),
        ("Cân bằng CuO + H₂ → Cu + H₂O. Hệ số của H₂ là bao nhiêu?", "1", ["1", "2", "3", "4"],
         "CuO + H₂ → Cu + H₂O."),
        ("Cân bằng 2KClO₃ → 2KCl + O₂. Hệ số của O₂ là bao nhiêu?", "3", ["2", "3", "4", "6"],
         "2KClO₃ → 2KCl + 3O₂."),
        ("Cân bằng N₂ + H₂ → NH₃. Hệ số của H₂ là bao nhiêu?", "3", ["2", "3", "4", "6"],
         "N₂ + 3H₂ → 2NH₃."),
    ]:
        items.append(mk(A, "Liên kết & Phản ứng", 3, expl, stem_vi, opts, opts.index(correct)))
    return items

def pool_bio_extra2():
    items = []
    A = "BIO"
    # Punnett square
    for stem, opts, correct, expl in [
        ("Ở đậu Hà Lan, P: Aa × Aa thì tỷ lệ kiểu hình ở F₁ là bao nhiêu? (A trội hoàn toàn so với a)",
         ["3 trội : 1 lặn", "1 trội : 1 lặn", "100% trội", "100% lặn"], 0,
         "Aa × Aa → 1AA : 2Aa : 1aa → 3 trội : 1 lặn."),
        ("Ở đậu Hà Lan, P: AA × aa thì tỷ lệ kiểu hình ở F₁ là bao nhiêu?",
         ["100% trội", "3 : 1", "1 : 1", "100% lặn"], 0,
         "AA × aa → 100% Aa (trội)."),
        ("Ở đậu Hà Lan, P: Aa × aa thì tỷ lệ kiểu hình ở F₁ là bao nhiêu?",
         ["1 trội : 1 lặn", "3 : 1", "100% trội", "100% lặn"], 0,
         "Aa × aa → 1Aa : 1aa."),
        ("Ở đậu Hà Lan, P: Aa × AA thì tỷ lệ kiểu hình ở F₁ là bao nhiêu?",
         ["100% trội", "1 : 1", "3 : 1", "100% lặn"], 0,
         "Aa × AA → 1AA : 1Aa — đều trội."),
    ]:
        items.append(mk(A, "Tế bào & Di truyền", 3, expl, stem, opts, correct,
                        stem_en="Genetics problem"))
    # Số loại giao tử 2^n
    for n in range(2, 8):
        v = 2 ** n
        stem = "Một cá thể có %d cặp gen dị hợp nằm trên các NST thường khác nhau. Cho tối đa bao nhiêu loại giao tử?" % n
        cand = sorted({v, v + 2, max(1, v - 1), v * 2})
        opts = [str(x) for x in cand]
        items.append(mk(A, "Tế bào & Di truyền", 4,
                        "2^n = 2^%d = %d." % (n, v),
                        stem, opts, opts.index(str(v)),
                        stem_en="An individual has %d heterozygous gene pairs on different chromosomes. How many gamete types at most?" % n))
    curated = [
        ("Enzyme DNA polymerase có vai trò gì?", "Tổng hợp mạch DNA mới bổ sung trong quá trình nhân đôi",
         ["Tổng hợp mạch DNA mới bổ sung trong quá trình nhân đôi",
          "Tổng hợp chuỗi polypeptide",
          "Sửa lỗi sao chép RNA",
          "Cắt ADN tại điểm xác định"],
         "DNA polymerase tổng hợp mạch mới 5'→3'.", "Tế bào & Di truyền"),
        ("Tổng số mã di truyền trong bộ mã chuẩn là bao nhiêu?", "64",
         ["64", "61", "20", "3"], "4×4×4 = 64 codon.", "Tế bào & Di truyền"),
        ("Mã di truyền nào sau đây là mã kết thúc (stop codon)?", "UAA",
         ["UAA", "AUG", "UUA", "AAA"], "UAA, UAG, UGA là các stop codon.", "Tế bào & Di truyền"),
        ("F₁ dị hợp tử (Aa) cho tỉ lệ kiểu hình nào ở F₂ (trội hoàn toàn)?", "3 : 1",
         ["3 : 1", "1 : 1", "1 : 2 : 1", "9 : 3 : 3 : 1"],
         "Aa × Aa → 3 trội : 1 lặn.", "Tế bào & Di truyền"),
        ("Hiện tượng các gen nằm trên cùng một NST được gọi là gì?", "Liên kết gen",
         ["Liên kết gen", "Hoán vị gen", "Phân li độc lập", "Di truyền qua tế bào chất"],
         "Gene linkage: các gen cùng NST di truyền cùng nhau.", "Tế bào & Di truyền"),
        ("Thể 3 nhiễm (trisomy) là dạng đột biến gì?",
         "Đột biến lệch bội — thừa 1 NST trong cặp tương đồng",
         ["Đột biến lệch bội — thừa 1 NST trong cặp tương đồng",
          "Đột biến đa bội lẻ",
          "Đột biến gen",
          "Đột biến cấu trúc NST"],
         "Trisomy: 2n+1 (ví dụ Down: 3 NST số 21).", "Tế bào & Di truyền"),
        ("Số NST trong tế bào sinh dưỡng người mắc hội chứng Turner (XO) là bao nhiêu?", "45",
         ["45", "44", "46", "47"],
         "Turner: chỉ có 1 NST giới tính X → 44 + 1 = 45.", "Tế bào & Di truyền"),
        ("Giai đoạn quang hợp nào diễn ra trong pha sáng?", "Quang phân li nước và giải phóng O₂, tạo ATP và NADPH",
         ["Quang phân li nước và giải phóng O₂, tạo ATP và NADPH",
          "Cố định CO₂ tạo glucose",
          "Thực hiện chu trình Calvin",
          "Hô hấp tế bào"],
         "Pha sáng: cần ánh sáng, diễn ra tại màng thylakoid.", "Cơ thể thực vật"),
        ("Sản phẩm cuối cùng của chu trình Calvin (pha tối) là gì?", "Glucose (C₆H₁₂O₆)",
         ["Glucose (C₆H₁₂O₆)", "O₂", "ATP", "CO₂"],
         "Calvin cố định CO₂ thành đường.", "Cơ thể thực vật"),
        ("Trong tiêu hóa ở người, phần lớn chất dinh dưỡng được hấp thụ ở đâu?", "Ruột non",
         ["Ruột non", "Dạ dày", "Đại tràng", "Miệng"],
         "Vi nhung mao ruột non hấp thụ chất dinh dưỡng.", "Cơ thể người"),
        ("Tim người có mấy ngăn?", "4",
         ["4", "2", "3", "1"],
         "2 tâm nhĩ + 2 tâm thất.", "Cơ thể người"),
        ("Vòng tuần hoàn nhỏ dẫn máu từ tim tới đâu?", "Phổi",
         ["Phổi", "Gan", "Thận", "Toàn thân"],
         "Tâm thất phải → động mạch phổi → phổi → tâm nhĩ trái.", "Cơ thể người"),
        ("Đơn vị lọc của thận là gì?", "Nephron",
         ["Nephron", "Tiểu cầu", "Bể thận", "Ống góp"],
         "Mỗi thận ~1 triệu nephron.", "Cơ thể người"),
        ("Hormone insulin do bộ phận nào tiết ra?", "Tế bào β của đảo Langerhans (tụy)",
         ["Tế bào β của đảo Langerhans (tụy)",
          "Vỏ thượng thận",
          "Tuyến giáp",
          "Tuyến yên"],
         "Insulin hạ đường huyết; glucagon làm tăng.", "Cơ thể người"),
        ("Hormone adrenalin được tiết ra từ đâu khi căng thẳng?", "Tủy thượng thận",
         ["Tủy thượng thận", "Tuyến yên", "Tuyến giáp", "Buồng trứng"],
         "Adrenalin: tăng nhịp tim, huyết áp.", "Cơ thể người"),
        ("Trong chuỗi thức ăn, sinh vật nào là bậc dinh dưỡng đầu tiên?", "Sinh vật sản xuất (thực vật)",
         ["Sinh vật sản xuất (thực vật)",
          "Sinh vật tiêu thụ bậc 1",
          "Sinh vật tiêu thụ bậc 2",
          "Sinh vật phân hủy"],
         "Bậc 1 là sinh vật tự dưỡng.", "Sinh thái & Tiến hóa"),
        ("Năng lượng truyền qua các bậc dinh dưỡng thường chỉ còn khoảng bao nhiêu %?", "10%",
         ["10%", "50%", "90%", "25%"],
         "Quy tắc 10%: ~90% mất đi dưới dạng nhiệt.", "Sinh thái & Tiến hóa"),
        ("Quần xã (community) gồm gì?",
         "Tập hợp các quần thể sinh vật khác loài cùng sống trong một sinh cảnh",
         ["Tập hợp các quần thể sinh vật khác loài cùng sống trong một sinh cảnh",
          "Một loài sinh vật duy nhất",
          "Các yếu tố vô sinh của môi trường",
          "Chỉ gồm động vật ăn thịt"],
         "Quần xã = nhiều loài; quần thể = một loài.", "Sinh thái & Tiến hóa"),
        ("Theo học thuyết Darwin, cơ chế tiến hóa chủ yếu là gì?",
         "Chọn lọc tự nhiên",
         ["Chọn lọc tự nhiên",
          "Sử dụng và không sử dụng bộ phận",
          "Biến dị tổ hợp do giao phối",
          "Đột biến nhân tạo"],
         "Darwin: chọn lọc tự nhiên giữ lại biến dị có lợi.", "Sinh thái & Tiến hóa"),
        ("Cách ly nào ngăn cản các loài khác nhau giao phối và sinh con hữu thụ?",
         "Cách ly sinh sản",
         ["Cách ly sinh sản",
          "Cách ly địa lý",
          "Cách ly mùa vụ",
          "Cách ly cơ học"],
         "Isolation sinh sản là bước cuối của hình thành loài.", "Sinh thái & Tiến hóa"),
    ]
    for stem_vi, correct, opts, expl, topic in curated:
        items.append(mk(A, topic, 2, expl, stem_vi, opts, opts.index(correct),
                        stem_en="Biology: %s" % stem_vi))
    return items

def pool_hist_extra2():
    items = []
    A = "HIST"
    # Năm -> thế kỷ (tất định)
    ROMANS = ["I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII",
              "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX", "XXI"]
    for year in [938, 1010, 1225, 1258, 1427, 1788, 1858, 1930, 1945, 1954,
                 1975, 1976, 1986, 1995, 2007]:
        century = (year + 99) // 100
        roman = ROMANS[century - 1]
        stem = "Sự kiện năm %d thuộc thế kỷ nào?" % year
        cand = [ROMANS[century - 1 + d] for d in (0, -1, 1, 2, -2, 3, -3, 4, -4, 5, -5)
                if 1 <= century + d <= 21][:4]
        opts = cand
        rng.shuffle(opts)
        items.append(mk(A, "Thời kỳ phong kiến & Kháng Pháp Mông" if year < 1900 else "Hiện đại 1945-1975", 2,
                        "Năm %d thuộc thế kỷ %s." % (year, roman),
                        stem, opts, opts.index(roman),
                        stem_en="Which century does the year %d belong to?" % year))
    # Sự kiện -> năm
    events = [
        ("Ngô Quyền đánh bại quân Nam Hán trên sông Bạch Đằng", "938"),
        ("Lê Hoàn lên ngôi, lập nhà Tiền Lê và đánh bại quân Tống", "981"),
        ("Lý Thường Kiệt đánh bại quân Tống trên sông Như Nguyệt", "1077"),
        ("Hồ Quý Ly phế truất nhà Trần, lập nhà Hồ", "1400"),
        ("Khởi nghĩa Lam Sơn chống quân Minh bùng nổ (Lê Lợi)", "1418"),
        ("Lê Lợi lên ngôi hoàng đế lập nhà Hậu Lê", "1428"),
        ("Khởi nghĩa Tây Sơn bùng nổ", "1771"),
        ("Quang Trung đại thắng quân Thanh trong Tết Kỷ Dậu", "1789"),
        ("Gia Long lên ngôi, lập nhà Nguyễn", "1802"),
        ("Thực dân Pháp mở đầu xâm lược Việt Nam tại Đà Nẵng", "1858"),
        ("Phong trào Cần Vương bùng nổ", "1885"),
        ("Đảng Cộng sản Việt Nam được thành lập", "1930"),
        ("Cách mạng tháng Tám thành công", "1945"),
        ("Chiến thắng Điện Biên Phủ", "1954"),
        ("Giải phóng miền Nam, thống nhất đất nước (30/4)", "1975"),
        ("Đại hội VI khởi đầu công cuộc Đổi mới", "1986"),
    ]
    for event, year in events:
        stem = "Sự kiện “%s” xảy ra vào năm nào?" % event
        correct = year
        y0 = int(year)
        cand = sorted({correct, str(y0 - 1), str(y0 + 1), str(y0 + 25)})
        rng.shuffle(cand)
        topic = "Thời kỳ phong kiến & Kháng Pháp Mông" if y0 < 1900 else ("Hiện đại 1945-1975" if y0 >= 1945 else "Kháng chiến chống Pháp")
        items.append(mk(A, topic, 3,
                        "Năm %s: %s." % (year, event),
                        stem, cand, cand.index(correct),
                        stem_en="In which year did this event happen: %s?" % event))
    return items

def pool_int3202_extra2():
    items = []
    A = "INT3202"
    # CROSS JOIN m×n
    for m, n in [(3, 5), (4, 6), (5, 7), (6, 8), (7, 9), (8, 10), (10, 12), (12, 15)]:
        v = m * n
        stem = "Bảng X có %d dòng, bảng Y có %d dòng. Truy vấn “SELECT * FROM x CROSS JOIN y” trả về bao nhiêu dòng?" % (m, n)
        cand = sorted({v, v + m, v * 2, m + n})
        opts = [str(x) for x in cand]
        items.append(mk(A, "Ngôn ngữ truy vấn SQL", 3,
                        "CROSS JOIN = tích Descartes: %d×%d = %d." % (m, n, v),
                        stem, opts, opts.index(str(v)),
                        stem_en="Table X has %d rows, table Y has %d rows. How many rows does SELECT * FROM x CROSS JOIN y return?" % (m, n)))
        stem2 = "Nếu không có dòng nào khớp khóa nối, “SELECT * FROM x INNER JOIN y ON x.id = y.id” trả về bao nhiêu dòng?"
        cand2 = ["0", str(m), str(n), str(m * n)]
        rng.shuffle(cand2)
        items.append(mk(A, "Ngôn ngữ truy vấn SQL", 3,
                        "INNER JOIN chỉ giữ dòng có khớp; không khớp → 0 dòng.",
                        stem2, cand2, cand2.index("0"),
                        stem_en="If no rows match on the join key, how many rows does the INNER JOIN return?"))
    curated = [
        ("HAVING khác WHERE ở điểm nào?", ["HAVING lọc theo nhóm sau GROUP BY; WHERE lọc dòng trước khi nhóm",
          "HAVING chỉ dùng cho chuỗi; WHERE cho số",
          "WHERE sau GROUP BY; HAVING trước",
          "Hai mệnh đề thay thế lẫn nhau"], 0,
         "HAVING + hàm gộp (COUNT, SUM...) lọc kết quả nhóm.",
         "How does HAVING differ from WHERE?"),
        ("UNION khác UNION ALL ở điểm nào?", ["UNION loại bỏ dòng trùng; UNION ALL giữ tất cả",
          "UNION ALL nhanh hơn nhưng mất dữ liệu",
          "UNION giữ trùng; UNION ALL loại trùng",
          "Không khác nhau"], 0,
         "UNION có bước deduplicate; UNION ALL nối thẳng.",
         "How does UNION differ from UNION ALL?"),
        ("Lệnh DELETE và TRUNCATE khác nhau thế nào?",
         ["TRUNCATE xóa toàn bộ bảng nhanh hơn và không ghi từng dòng vào log; DELETE xóa từng dòng, có thể kèm WHERE",
          "DELETE nhanh hơn TRUNCATE",
          "TRUNCATE chỉ xóa bảng tạm",
          "Cả hai giống hệt nhau"], 0,
         "TRUNCATE: không có WHERE, reset auto-increment, DDL-like.",
         "How do DELETE and TRUNCATE differ?"),
        ("LEFT JOIN trả về những dòng nào?",
         ["Mọi dòng của bảng trái, khớp hoặc không (NULL ở bên phải nếu không khớp)",
          "Chỉ dòng khớp ở cả hai bảng",
          "Mọi dòng bảng phải",
          "Chỉ dòng không khớp"], 0,
         "LEFT JOIN: giữ bảng trái làm chủ.",
         "Which rows does LEFT JOIN return?"),
        ("Index trong cơ sở dữ liệu dùng để làm gì?",
         ["Tăng tốc truy vấn tìm kiếm/nối, đổi lại ghi chậm hơn",
          "Nén dữ liệu",
          "Mã hóa cột",
          "Xóa trùng lặp"], 0,
         "Index: B+tree lookup O(log n).",
         "What is an index used for?"),
        ("Composite index (a, b) hỗ trợ tìm kiếm nào hiệu quả?",
         ["Lọc theo a, hoặc theo a và b cùng lúc",
          "Chỉ lọc theo b",
          "Chỉ lọc theo tên bảng",
          "Không hỗ trợ lọc"], 0,
         "Leftmost prefix rule: dùng cột đầu của bộ index.",
         "Which lookups does a composite index (a, b) support efficiently?"),
        ("MVCC trong PostgreSQL/MariaDB InnoDB dùng để làm gì?",
         ["Cho phép đọc không chặn ghi và ghi không chặn đọc nhờ nhiều phiên bản bản ghi",
          "Nén bảng",
          "Phân mảnh dữ liệu",
          "Tự động backup"], 0,
         "Multi-Version Concurrency Control: snapshot cho mỗi transaction.",
         "What is MVCC used for?"),
        ("Replication (master–replica) chủ yếu phục vụ mục đích nào?",
         ["Dự phòng và scale đọc",
          "Tăng tốc ghi trên master",
          "Mã hóa dữ liệu khi truyền",
          "Xóa dữ liệu trùng"], 0,
         "Replica phục vụ read-only traffic và failover.",
         "What is master–replica replication primarily for?"),
        ("Sharding một bảng nghĩa là gì?",
         ["Chia bảng theo chiều ngang ra nhiều node theo một khóa phân mảnh",
          "Chia bảng thành nhiều cột",
          "Nén bảng trên đĩa",
          "Tạo bản sao toàn bộ bảng"], 0,
         "Horizontal partitioning across nodes.",
         "What does sharding a table mean?"),
        ("WAL (Write-Ahead Log) đảm bảo tính chất nào?",
         ["Durability — ghi log trước khi sửa dữ liệu thật để phục hồi sau sự cố",
          "Atomicity của SELECT",
          "Tính nhất quán tạm thời",
          "Tối ưu câu lệnh JOIN"], 0,
         "WAL: log trước, data sau — khôi phục sau crash.",
         "Which property does the Write-Ahead Log guarantee?"),
        ("Khi hai transaction chờ khóa của nhau vô hạn, DBMS thường xử lý ra sao?",
         ["Phát hiện deadlock và hủy (rollback) một transaction làm nạn nhân",
          "Tự động xếp hàng vô thời hạn",
          "Tắt máy chủ",
          "Cho phép cả hai cùng ghi"], 0,
         "Deadlock detector chọn victim để phá vòng chờ.",
         "What does a DBMS do when it detects a deadlock?"),
        ("UNIQUE constraint khác PRIMARY KEY ở điểm nào?",
         ["UNIQUE cho phép NULL (tùy DBMS) và một bảng có nhiều UNIQUE; PRIMARY KEY không NULL và chỉ có một",
          "UNIQUE nhanh hơn",
          "PRIMARY KEY cho phép trùng",
          "Không khác nhau"], 0,
         "PK = unique + not null + duy nhất.",
         "How does a UNIQUE constraint differ from a PRIMARY KEY?"),
        ("ON DELETE CASCADE nghĩa là gì?",
         ["Khi dòng cha bị xóa, các dòng con tham chiếu cũng bị xóa theo",
          "Ngăn không cho xóa dòng cha",
          "Set khóa ngoại con thành NULL khi xóa cha",
          "Xóa toàn bộ cơ sở dữ liệu"], 0,
         "Cascade: lan truyền thao tác xóa theo FK.",
         "What does ON DELETE CASCADE mean?"),
        ("AUTO_INCREMENT trong MySQL/MariaDB dùng để làm gì?",
         ["Tự động sinh số nguyên tăng dần cho khóa chính",
          "Tăng bộ đếm truy vấn",
          "Tự động tạo index",
          "Tự động sao lưu"], 0,
         "Surrogate key generation.",
         "What is AUTO_INCREMENT used for in MySQL/MariaDB?"),
        ("View trong SQL mang lại lợi ích nào?",
         ["Đơn giản hóa truy vấn phức tạp và ẩn/che bớt cột nhạy cảm",
          "Tăng tốc ghi",
          "Tự động tối ưu JOIN",
          "Thay thế hoàn toàn bảng gốc"], 0,
         "View = truy vấn đặt tên, bảo mật theo cột.",
         "What benefit does a SQL view provide?"),
        ("Materialized view khác view thường ở điểm nào?",
         ["Materialized view lưu dữ liệu vật lý nên đọc nhanh, cần refresh; view thường tính lại mỗi lần đọc",
          "Materialized view không lưu dữ liệu",
          "View thường nhanh hơn",
          "Không khác nhau"], 0,
         "Snapshot stored physically, periodic refresh.",
         "How does a materialized view differ from a regular view?"),
        ("Trong SQL, NULL so sánh thế nào với mọi giá trị?",
         ["NULL không bằng bất kỳ giá trị nào, kể cả NULL — phải dùng IS NULL",
          "NULL bằng 0",
          "NULL bằng chuỗi rỗng",
          "NULL lớn hơn mọi giá trị"], 0,
         "Three-valued logic: NULL = NULL → UNKNOWN.",
         "How does NULL compare to other values in SQL?"),
        ("COALESCE(discount, 0) trả về gì khi discount là NULL?",
         ["0", "NULL", "discount", "Lỗi cú pháp"], 0,
         "COALESCE lấy giá trị đầu tiên khác NULL.",
         "What does COALESCE(discount, 0) return when discount is NULL?"),
    ]
    for stem_vi, opts, correct, expl, en in curated:
        items.append(mk(A, "Ngôn ngữ truy vấn SQL" if "JOIN" in stem_vi or "SQL" in stem_vi or
                        "COALESCE" in stem_vi or "UNION" in stem_vi or "NULL" in stem_vi else
                        "Chuẩn hóa dữ liệu", 3, expl, stem_vi, list(opts), correct, stem_en=en))
    return items

def pool_int3304_extra2():
    items = []
    A = "INT3304"
    # Số host usable theo prefix
    for prefix, usable in [(22, 1022), (23, 510), (24, 254), (25, 126), (26, 62),
                           (27, 30), (28, 14), (29, 6), (30, 2), (16, 65534)]:
        host_bits = 32 - prefix
        stem = "Mạng con có prefix /%d có bao nhiêu địa chỉ host sử dụng được (usable)?" % prefix
        cand = []
        for c in [usable, usable + 2, usable + 6, usable * 2 + 2, 2 ** host_bits]:
            if c not in cand:
                cand.append(c)
            if len(cand) == 4:
                break
        opts = [str(v) for v in cand]
        items.append(mk(A, "Kiến trúc phân tầng OSI/TCP-IP", 3,
                        "Usable = 2^(32-%d) - 2 = %d." % (prefix, usable),
                        stem, opts, opts.index(str(usable)),
                        stem_en="How many usable host addresses does a /%d subnet provide?" % prefix))
    curated = [
        ("Cổng mặc định của SSH là bao nhiêu?", ["22", "23", "80", "443"], 0,
         "SSH dùng cổng 22.", "What is the default port for SSH?"),
        ("Cổng mặc định của DNS là bao nhiêu?", ["53", "54", "25", "110"], 0,
         "DNS dùng cổng 53 (UDP/TCP).", "What is the default port for DNS?"),
        ("Giao thức nào dùng để đồng bộ thời gian trên mạng?", ["NTP", "SNMP", "SMTP", "FTP"], 0,
         "NTP (port 123) đồng bộ đồng hồ hệ thống.", "Which protocol synchronizes time on a network?"),
        ("SNMP được dùng trong quản trị mạng để làm gì?", ["Giám sát và quản lý thiết bị mạng",
          "Truyền file", "Gửi email", "Phân giải tên miền"], 0,
         "SNMP: tra cứu/ghi cấu hình thiết bị qua MIB.", "What is SNMP used for in network management?"),
        ("HTTPS chạy trên cổng mặc định nào?", ["443", "80", "8443", "8080"], 0,
         "HTTPS = HTTP over TLS, cổng 443.", "What is the default port for HTTPS?"),
        ("Default gateway là gì?", ["Thiết bị router mà máy trạm gửi gói tin tới các mạng khác",
          "DNS server chính", "Firewall", "Proxy cache"], 0,
         "Mọi gói tin ra ngoài subnet đều đi qua default gateway.", "What is the default gateway?"),
        ("Lệnh traceroute/tracert dùng để làm gì?", ["Liệt kê các router trung gian giữa nguồn và đích",
          "Kiểm tra tốc độ mạng", "Xóa bảng định tuyến", "Quét cổng"], 0,
         "Tận dụng TTL tăng dần để lộ đường đi.", "What is traceroute used for?"),
        ("ARP request được phát như thế nào trong LAN?", ["Broadcast tới toàn bộ mạng broadcast domain",
          "Unicast tới đích", "Multicast tới nhóm", "Anycast"], 0,
         "ARP hỏi 'ai sở hữu IP này?' bằng frame broadcast.", "How is an ARP request sent?"),
        ("Gói tin DHCP DISCOVER được gửi dưới dạng nào?", ["Broadcast",
          "Unicast", "Multicast", "Anycast"], 0,
         "Client chưa có IP → bắt buộc broadcast.", "How is a DHCP DISCOVER packet sent?"),
        ("DHCP lease là gì?", ["Khoảng thời gian một địa chỉ IP được cấp phát cho client",
          "Giá thuê server", "Thời gian thuê tên miền", "Thời gian sống của gói tin"], 0,
         "Hết hạn phải gia hạn hoặc xin cấp lại.", "What is a DHCP lease?"),
        ("TTL trong gói IP dùng để làm gì?", ["Giới hạn số hop để gói không lặp vô hạn",
          "Giới hạn kích thước gói",
          "Thời gian lưu cache DNS",
          "Độ ưu tiên gói tin"], 0,
         "TTL giảm 1 mỗi router; về 0 thì hủy gói.", "What is the TTL field in an IP packet used for?"),
        ("Ping sử dụng giao thức nào ở tầng mạng?", ["ICMP", "UDP", "ARP", "TCP"], 0,
         "Ping = ICMP Echo Request/Reply.", "Which network-layer protocol does ping use?"),
        ("Broadcast, unicast và multicast khác nhau thế nào?",
         ["Broadcast: gửi tới mọi node; unicast: 1→1; multicast: 1→nhóm",
          "Cả ba giống hệt nhau",
          "Multicast gửi tới toàn mạng",
          "Unicast gửi tới mọi node trong VLAN"], 0,
         "Phân loại theo số lượng bên nhận.", "How do broadcast, unicast and multicast differ?"),
        ("Full-duplex nghĩa là gì?", ["Truyền hai chiều đồng thời",
          "Truyền một chiều",
          "Truyền luân phiên",
          "Không truyền được"], 0,
         "Full-duplex: gửi và nhận cùng lúc (switch hiện đại).", "What does full-duplex mean?"),
        ("Mục đích chính của VLAN là gì?", ["Chia một mạng vật lý thành nhiều broadcast domain logic",
          "Tăng tốc CPU switch",
          "Mã hóa lưu lượng",
          "Cấp IP động"], 0,
         "VLAN = phân đoạn logic L2.", "What is the main purpose of a VLAN?"),
        ("Cáp quang single-mode khác multi-mode ở điểm nào?",
         ["Single-mode: lõi rất nhỏ, truyền xa (vài chục km); multi-mode: lõi lớn, truyền ngắn",
          "Multi-mode truyền xa hơn single-mode",
          "Hai loại giống hệt nhau",
          "Single-mode chỉ dùng trong nhà"], 0,
         "Single-mode ~9µm, multi-mode ~50/62.5µm.", "How does single-mode fiber differ from multi-mode?"),
        ("Mục đích của subnetting (chia mạng con) là gì?",
         ["Tối ưu sử dụng địa chỉ IP và giảm broadcast domain",
          "Tăng tốc độ đường truyền",
          "Mã hóa dữ liệu",
          "Tự động cấu hình IP"], 0,
         "Chia nhỏ để quản lý và bảo mật tốt hơn.", "What is the purpose of subnetting?"),
        ("VLSM cho phép điều gì?",
         ["Dùng prefix khác nhau cho các mạng con trong cùng một khối địa chỉ",
          "Dùng một prefix cho mọi mạng con",
          "Bỏ qua subnet mask",
          "Chỉ dùng cho IPv6"], 0,
         "Variable-Length Subnet Mask: mịn hóa phân bổ.", "What does VLSM allow?"),
        ("Một liên kết Ethernet chuẩn (không jumbo frame) có MTU mặc định là bao nhiêu?",
         ["1500 byte", "9000 byte", "576 byte", "64 byte"], 0,
         "Ethernet MTU = 1500 byte payload.", "What is the default MTU of a standard Ethernet link?"),
        ("Firewall ở mức mạng (packet filter) hoạt động dựa trên yếu tố nào?",
         ["Địa chỉ nguồn/đích, cổng, giao thức của gói tin",
          "Nội dung file tải về",
          "Mật khẩu người dùng",
          "Tên miền website"], 0,
         "Packet filter: 5-tuple.", "What does a network-level packet-filter firewall inspect?"),
    ]
    for stem_vi, opts, correct, expl, en in curated:
        items.append(mk(A, "Kiến trúc phân tầng OSI/TCP-IP", 3, expl, stem_vi, list(opts), correct, stem_en=en))
    return items

def pool_int3306_extra2():
    items = []
    A = "INT3306"
    # Output puzzles (tất định)
    puzzles = [
        ("System.out.println(17 / 5);", "3", ["3", "3.4", "4", "2"],
         "Chia nguyên int: bỏ phần thập phân → 3.", "What does System.out.println(17 / 5) print?"),
        ("System.out.println(17 % 5);", "2", ["2", "3", "1", "5"],
         "17 mod 5 = 2.", "What does System.out.println(17 % 5) print?"),
        ("System.out.println(100 / 7);", "14", ["14", "14.28", "15", "13"],
         "Chia nguyên: 100/7 = 14.", "What does System.out.println(100 / 7) print?"),
        ("System.out.println(-7 / 2);", "-3", ["-3", "-4", "-3.5", "3"],
         "Java cắt về 0: (int)(-3.5) = -3.", "What does System.out.println(-7 / 2) print in Java?"),
        ("System.out.println(Math.floor(-2.5));", "-3.0", ["-3.0", "-2.0", "-2.5", "3.0"],
         "floor đi xuống vô cực.", "What does Math.floor(-2.5) return?"),
        ("System.out.println(Math.ceil(2.1));", "3.0", ["3.0", "2.0", "2.1", "4.0"],
         "ceil lên số nguyên gần nhất phía trên.", "What does Math.ceil(2.1) return?"),
        ("System.out.println(\"abcde\".substring(1, 4));", "bcd", ["bcd", "abc", "cde", "abcd"],
         "substring(begin, end) — end exclusive: vị trí 1,2,3.", "What does \"abcde\".substring(1, 4) return?"),
        ("System.out.println(\"java\".charAt(2));", "v", ["v", "a", "j", "t"],
         "charAt(0)='j', 1='a', 2='v', 3='a'.", "What does \"java\".charAt(2) return?"),
        ("System.out.println(\"result: \" + 1 + 2);", "result: 12", ["result: 12", "result: 3", "3", "Lỗi biên dịch"],
         "+ trái → phải: string + 1 = \"result: 1\", + 2 = \"result: 12\".",
         "What does System.out.println(\"result: \" + 1 + 2) print?"),
        ("System.out.println(1 + 2 + \"a\");", "3a", ["3a", "12a", "a12", "Lỗi biên dịch"],
         "1 + 2 tính trước thành 3, rồi nối chuỗi.",
         "What does System.out.println(1 + 2 + \"a\") print?"),
        ("int[] a = new int[5]; System.out.println(a[3]);", "0", ["0", "null", "Lỗi biên dịch", "Ngẫu nhiên"],
         "Mảng int mặc định = 0.", "What does a fresh int[5] array contain at index 3?"),
        ("System.out.println(Boolean.parseBoolean(\"TRUE\"));", "true", ["true", "false", "Lỗi", "null"],
         "parseBoolean không phân biệt hoa thường, chỉ \"true\" mới true.",
         "What does Boolean.parseBoolean(\"TRUE\") return?"),
    ]
    for code, correct, opts, expl, en in puzzles:
        stem_vi = "Đoạn mã sau in ra (hoặc trả về) giá trị nào? “%s”" % code
        items.append(mk(A, "Cú pháp & OOP cơ bản", 3, expl, stem_vi, list(opts), opts.index(correct), stem_en=en))
    curated = [
        ("HashSet lưu trữ phần tử như thế nào?", ["Không trùng lặp, không đảm bảo thứ tự",
          "Có thứ tự chèn vào",
          "Tự sắp xếp tăng dần",
          "Cho phép phần tử trùng lặp"], 0,
         "Set: không trùng; HashSet: không thứ tự.", "How does a HashSet store elements?"),
        ("TreeSet sắp xếp phần tử theo tiêu chí nào?", ["Thứ tự tự nhiên hoặc Comparator được cung cấp",
          "Thứ tự chèn vào",
          "Thứ tự ngẫu nhiên",
          "Theo hashCode"], 0,
         "NavigableSet dựa trên so sánh.", "How does a TreeSet order elements?"),
        ("Comparable và Comparator khác nhau thế nào?",
         ["Comparable: so sánh tự nhiên bên trong lớp (compareTo); Comparator: so sánh bên ngoài, linh hoạt nhiều thứ tự",
          "Comparator chỉ so sánh số",
          "Comparable dùng cho Map; Comparator cho List",
          "Không khác nhau"], 0,
         "compareTo vs compare(a, b).", "How do Comparable and Comparator differ?"),
        ("Optional.ofNullable(x).orElse(0) trả về gì khi x là null?",
         ["0", "null", "Lỗi NullPointerException", "Optional.empty"], 0,
         "orElse trả về mặc định khi empty.", "What does Optional.ofNullable(x).orElse(0) return when x is null?"),
        ("Stream.map() là thao tác trung gian hay kết thúc?",
         ["Trung gian (intermediate) — lazy",
          "Kết thúc (terminal)",
          "Cả hai",
          "Không thuộc Stream API"], 0,
         "Intermediate ops trả về Stream mới, chưa đánh giá.", "Is Stream.map() an intermediate or terminal operation?"),
        ("Annotation nào là tổng hợp của @Controller và @ResponseBody?",
         ["@RestController", "@Service", "@Repository", "@Configuration"], 0,
         "@RestController trả body trực tiếp (JSON/XML).", "Which annotation combines @Controller and @ResponseBody?"),
        ("Trong Spring MVC, @PathVariable dùng để làm gì?",
         ["Lấy giá trị động từ URL path (ví dụ /users/{id})",
          "Lấy query parameter",
          "Đọc header",
          "Bind form data"], 0,
         "Path variable ánh xạ biến trong URI.", "What is @PathVariable used for in Spring MVC?"),
        ("Spring bean mặc định có scope gì?", ["singleton",
          "prototype",
          "request",
          "session"], 0,
         "Một instance duy nhất cho mỗi bean definition.", "What is the default scope of a Spring bean?"),
        ("final trên một class nghĩa là gì?", ["Class không thể bị kế thừa",
          "Class không thể khởi tạo",
          "Class là immutable",
          "Class là abstract"], 0,
         "final class: đóng cho kế thừa (ví dụ String).", "What does final on a class mean?"),
        ("Khối static trong Java chạy khi nào?",
         ["Một lần duy nhất khi class được nạp lần đầu",
          "Mỗi lần tạo object",
          "Mỗi lần gọi method",
          "Khi JVM tắt"], 0,
         "Static initializer: class loading time.", "When does a static block in Java run?"),
    ]
    for stem_vi, opts, correct, expl, en in curated:
        items.append(mk(A, "Spring Boot & REST API" if "Spring" in stem_vi or "@" in stem_vi else
                        "Ngoại lệ & Collections" if any(w in stem_vi for w in ["Set", "Optional", "Stream", "Comparable"])
                        else "Cú pháp & OOP cơ bản", 3, expl, stem_vi, list(opts), correct, stem_en=en))
    return items

def pool_int3401_extra2():
    items = []
    A = "INT3401"
    # Ma trận nhầm lẫn: tính accuracy & F1 (tất định)
    cases = [(80, 10, 10, 100), (50, 10, 20, 120), (30, 30, 10, 130), (90, 5, 5, 100)]
    for tp, fp, fn, tn in cases:
        total = tp + fp + fn + tn
        acc = (tp + tn) / total * 100
        p = tp / (tp + fp) * 100
        r = tp / (tp + fn) * 100
        f1 = 2 * p * r / (p + r)
        stem = ("Một bộ phân loại có TP=%d, FP=%d, FN=%d, TN=%d. " % (tp, fp, fn, tn))
        cand = sorted({round(acc, 1), round(acc - 5, 1), round(acc + 5, 1), round(f1, 1)})
        opts = ["%.1f%%" % v for v in cand]
        items.append(mk(A, "Học máy & Mạng nơ-ron", 3,
                        "Accuracy = (TP+TN)/tổng = %.1f%%." % acc,
                        stem + "Độ chính xác (accuracy) là bao nhiêu?",
                        opts, opts.index("%.1f%%" % round(acc, 1)),
                        stem_en="A classifier has TP=%d, FP=%d, FN=%d, TN=%d. What is its accuracy?" % (tp, fp, fn, tn)))
        cand = sorted({round(f1, 1), round(f1 + 5, 1), round(max(0, f1 - 5), 1), round(acc, 1)})
        opts = ["%.1f%%" % v for v in cand]
        items.append(mk(A, "Học máy & Mạng nơ-ron", 4,
                        "P=%.1f%%, R=%.1f%% → F1 = %.1f%%." % (p, r, f1),
                        stem + "F1-score là bao nhiêu?",
                        opts, opts.index("%.1f%%" % round(f1, 1)),
                        stem_en="A classifier has TP=%d, FP=%d, FN=%d, TN=%d. What is its F1-score?" % (tp, fp, fn, tn)))
        cand = sorted({round(p, 1), round(p - 5, 1), round(p + 5, 1), round(acc, 1)})
        opts = ["%.1f%%" % v for v in cand]
        items.append(mk(A, "Học máy & Mạng nơ-ron", 4,
                        "Precision = TP/(TP+FP) = %.1f%%." % p,
                        stem + "Precision là bao nhiêu?",
                        opts, opts.index("%.1f%%" % round(p, 1)),
                        stem_en="A classifier has TP=%d, FP=%d, FN=%d, TN=%d. What is its precision?" % (tp, fp, fn, tn)))
        cand = sorted({round(r, 1), round(r - 5, 1), round(r + 5, 1), 100.0})
        opts = ["%.1f%%" % v for v in cand]
        items.append(mk(A, "Học máy & Mạng nơ-ron", 4,
                        "Recall = TP/(TP+FN) = %.1f%%." % r,
                        stem + "Recall là bao nhiêu?",
                        opts, opts.index("%.1f%%" % round(r, 1)),
                        stem_en="A classifier has TP=%d, FP=%d, FN=%d, TN=%d. What is its recall?" % (tp, fp, fn, tn)))
    curated = [
        ("Trong thuật toán A*, hàm f(n) được tính như thế nào?",
         ["f(n) = g(n) + h(n) — chi phí đến n cộng heuristics từ n tới đích",
          "f(n) = g(n) − h(n)",
          "f(n) = h(n) × g(n)",
          "f(n) = max(g(n), h(n))"], 0,
         "g: chi phí thực đã đi; h: ước lượng còn lại.", "In A*, how is f(n) computed?"),
        ("Greedy best-first search chọn nút nào để mở rộng tiếp?",
         ["Nút có h(n) nhỏ nhất",
          "Nút có g(n) nhỏ nhất",
          "Nút có f(n) nhỏ nhất",
          "Nút ngẫu nhiên"], 0,
         "Greedy: chỉ nhìn heuristic, bỏ qua chi phí đã đi.", "Which node does greedy best-first search expand?"),
        ("Tính chất Markov trong mô hình Markov nghĩa là gì?",
         ["Trạng thái tương lai chỉ phụ thuộc trạng thái hiện tại, không phụ thuộc quá khứ",
          "Mọi trạng thái độc lập",
          "Xích luôn hội tụ",
          "Tất cả trạng thái đều absorbing"], 0,
         "Memorylessness: P(s_{t+1}|s_t).", "What does the Markov property mean?"),
        ("Naive Bayes giả định điều gì về các thuộc tính?",
         ["Các thuộc tính độc lập có điều kiện với nhãn lớp",
          "Các thuộc tính tương quan mạnh",
          "Thuộc tính liên tục phân phối chuẩn",
          "Không có giả định nào"], 0,
         "P(x1..xn|c) ≈ ΠP(xi|c).", "What does Naive Bayes assume about features?"),
        ("SVM tìm siêu phẳng phân chia hai lớp theo tiêu chí nào?",
         ["Tối đa hóa khoảng cách lề (margin) tới các điểm gần nhất (support vectors)",
          "Tối thiểu hóa số lỗi trên tập train",
          "Tối đa hóa độ chính xác train",
          "Ngẫu nhiên"], 0,
         "Maximum margin classifier.", "What criterion does SVM use to find the separating hyperplane?"),
        ("Hồi quy logistic dùng hàm nào để ép đầu ra về khoảng (0, 1)?",
         ["Sigmoid (logistic)",
          "ReLU",
          "Tanh",
          "Softmax"], 0,
         "σ(z) = 1/(1+e^{-z}).", "Which function does logistic regression use to squash outputs to (0, 1)?"),
        ("Cross-entropy loss thường dùng cho bài toán nào?",
         ["Phân loại (classification)",
          "Hồi quy tuyến tính",
          "Phân cụm",
          "Giảm chiều"], 0,
         "Cross-entropy đo chênh lệch phân phối dự đoán và nhãn one-hot.", "Cross-entropy loss is typically used for which task?"),
        ("Dropout trong mạng nơ-ron dùng để làm gì?",
         ["Ngẫu nhiên tắt một tỉ lệ neuron khi train để giảm overfitting",
          "Tăng tốc suy luận",
          "Giảm kích thước mô hình",
          "Tăng batch size"], 0,
         "Regularization bằng noise cấu trúc.", "What is dropout used for?"),
        ("Random Forest là ensemble dựa trên kỹ thuật nào?",
         ["Bagging — huấn luyện nhiều cây trên các mẫu bootstrap và trung bình/bầu chọn",
          "Boosting tuần tự sửa lỗi",
          "Stacking các model khác loại",
          "Dropout cho cây"], 0,
         "Bagging giảm variance của decision tree.", "Which ensemble technique does Random Forest use?"),
        ("AUC-ROC đo lường điều gì?",
         ["Khả năng phân biệt của mô hình trên mọi ngưỡng (diện tích dưới đường ROC)",
          "Độ chính xác tại ngưỡng 0.5",
          "Tốc độ suy luận",
          "Độ phức tạp mô hình"], 0,
         "AUC = 1: phân biệt hoàn hảo; 0.5: ngẫu nhiên.", "What does AUC-ROC measure?"),
        ("ROC curve vẽ hai đại lượng nào lên hai trục?",
         ["True Positive Rate (recall) và False Positive Rate",
          "Precision và Recall",
          "Accuracy và Loss",
          "TP và FP"], 0,
         "TPR = TP/(TP+FN); FPR = FP/(FP+TN).", "Which two quantities does the ROC curve plot?"),
        ("K-fold cross-validation đảm bảo điều gì?",
         ["Mỗi mẫu được dùng để validation đúng một lần, giảm phương sai của ước lượng",
          "Train nhanh hơn",
          "Loại bỏ hoàn toàn overfitting",
          "Không cần tập test"], 0,
         "K lần chia — trung bình các điểm số.", "What does k-fold cross-validation ensure?"),
        ("Trong LLM, “tokenize” nghĩa là gì?",
         ["Chia văn bản thành các đơn vị nhỏ (token) để đưa vào mô hình",
          "Mã hóa token thành số nguyên",
          "Loại bỏ dấu câu",
          "Dịch văn bản"], 0,
         "Token = mảnh từ/ký tự; sau đó map sang ID.", "What does “tokenize” mean in LLMs?"),
        ("BPE (Byte-Pair Encoding) là kỹ thuật gì?",
         ["Thuật toán tách token dựa trên cặp byte/ký tự phổ biến nhất, lặp lại",
          "Mã hóa toàn bộ từ điển",
          "Nén ảnh",
          "Băm mật khẩu"], 0,
         "BPE: merge pair tần suất cao dần thành subword.", "What is Byte-Pair Encoding?"),
        ("Zero-shot prompting nghĩa là gì?",
         ["Yêu cầu mô hình làm nhiệm vụ mà không đưa ví dụ mẫu",
          "Đưa đúng 1 ví dụ mẫu",
          "Đưa nhiều ví dụ mẫu",
          "Không đưa prompt nào"], 0,
         "Zero-shot: chỉ mô tả nhiệm vụ; few-shot: kèm ví dụ.", "What does zero-shot prompting mean?"),
        ("Chain-of-thought prompting giúp mô hình làm gì?",
         ["Suy luận từng bước, cải thiện độ chính xác bài toán nhiều bước",
          "Trả lời ngắn gọn hơn",
          "Tăng tốc sinh token",
          "Giảm hallucination tuyệt đối"], 0,
         "CoT: hiện hóa bước trung gian trước khi kết luận.", "What does chain-of-thought prompting help the model do?"),
        ("Top-p (nucleus sampling) chọn token theo cơ chế nào?",
         ["Chọn từ tập nhỏ nhất có tổng xác suất ≥ p",
          "Chọn token có xác suất cao nhất",
          "Chọn ngẫu nhiên đều",
          "Loại bỏ mọi token có p < 0.5"], 0,
         "Nucleus: cắt đuôi phân phối theo ngưỡng tích lũy.", "How does top-p (nucleus) sampling select tokens?"),
        ("RAG (Retrieval-Augmented Generation) hoạt động theo quy trình nào?",
         ["Truy xuất tài liệu liên quan → đưa vào prompt → mô hình sinh câu trả lời dựa trên tài liệu đó",
          "Sinh ngẫu nhiên rồi tra cứu",
          "Chỉ fine-tune lại mô hình",
          "Đọc toàn bộ internet mỗi lần hỏi"], 0,
         "Retrieve → augment → generate.", "How does RAG work?"),
        ("XOR có thể học được bởi mạng perceptron đơn lớp không?",
         ["Không — XOR không phân tách tuyến tính",
          "Có — với learning rate đủ lớn",
          "Có — với bias lớn",
          "Có — nếu dùng tanh"], 0,
         "Minsky & Papert 1969: XOR cần lớp ẩn.", "Can a single-layer perceptron learn XOR?"),
    ]
    for stem_vi, opts, correct, expl, en in curated:
        items.append(mk(A, "Mô hình ngôn ngữ lớn (LLM)" if any(w in stem_vi for w in
                        ["LLM", "token", "BPE", "prompt", "RAG", "Top-p", "Zero-shot", "Chain"]) else
                        "Học máy & Mạng nơ-ron", 3, expl, stem_vi, list(opts), correct, stem_en=en))
    return items

def pool_toeic_frames_extra():
    return [
        ("The ____ for the annual conference will be sent next Monday.", ["agenda", "agency", "agent", "agitate"], 0,
         "Danh từ chỉ chương trình nghị sự/lịch trình", "The agenda is the list of items to be discussed."),
        ("Please ____ the invoice by the end of this month.", ["settle", "settled", "settling", "settles"], 0,
         "Động từ nguyên mẫu sau please", "Imperative: please + V nguyên mẫu: settle."),
        ("The supplier ____ the order two days late.", ["deliver", "delivered", "delivering", "delivers"], 1,
         "Quá khứ đơn", "two days late + sự việc đã xảy ra → delivered."),
        ("All employees must complete the ____ form before Friday.", ["reimbursement", "reimburse", "reimbursed", "reimbursing"], 0,
         "Danh từ sau mạo từ the", "the + danh từ: reimbursement form."),
        ("The shipment is expected to ____ on Thursday.", ["arrive", "arrives", "arrived", "arriving"], 0,
         "Động từ nguyên mẫu sau to", "be expected to + V: arrive."),
        ("Our company offers a full ____ on all products.", ["warranty", "warrant", "warranted", "warranting"], 0,
         "Danh từ sau a", "a full warranty = bảo hành toàn diện."),
        ("The flight was canceled due to ____ weather conditions.", ["severe", "severely", "severity", "severeness"], 0,
         "Tính từ bổ nghĩa danh từ", "severe weather = thời tiết khắc nghiệt."),
        ("We need to ____ the budget by next quarter.", ["finalize", "final", "finally", "finality"], 0,
         "Động từ nguyên mẫu sau need to", "need to + V: finalize."),
        ("The meeting room has been ____ for tomorrow afternoon.", ["booked", "booking", "books", "book"], 0,
         "Bị động hoàn thành", "has been + V3: booked."),
        ("____ of the attendees received a copy of the minutes.", ["Each", "Every", "All", "Many"], 0,
         "Each of + danh từ số nhiều", "Each of the + plural → số ít."),
        ("The new policy applies to ____ staff members.", ["all", "every", "each", "both"], 0,
         "all với danh từ số nhiều", "all + số nhiều: all staff members."),
        ("Please submit the report ____ the deadline.", ["before", "during", "while", "since"], 0,
         "Giới từ before chỉ thời gian", "before + mốc thời gian."),
        ("The seminar will be held ____ the main conference hall.", ["in", "on", "at", "by"], 0,
         "Giới từ in với không gian khép kín", "in + hội trường/không gian."),
        ("He has ____ experience in international sales.", ["extensive", "extensively", "extent", "extend"], 0,
         "Tính từ bổ nghĩa danh từ", "extensive experience = kinh nghiệm sâu rộng."),
        ("The CEO made an ____ announcement this morning.", ["official", "officially", "office", "officer"], 0,
         "Tính từ bổ nghĩa danh từ", "official announcement = thông báo chính thức."),
    ]

# ======================================================================
# SINH ITEM NGỮ PHÁP TỪ MẪU (mở rộng EN_FACTS để đủ hạn ngạch 165)
# ======================================================================
_GRAMMAR_DOM = {
    "MATH": (["The equation", "The derivative", "The integral", "The matrix", "The theorem",
              "The fraction", "The polynomial", "The sequence", "The logarithm", "The coefficient"],
             ["These equations", "Those derivatives", "The fractions", "The matrices", "The theorems",
              "The polynomials", "The sequences", "The logarithms", "The coefficients", "The vectors"],
             ("converges", "converge")),
    "PHYS": (["The particle", "The force", "The wave", "The current", "The field",
              "The charge", "The orbit", "The circuit", "The lever", "The pendulum"],
             ["These particles", "The forces", "The waves", "The currents", "The fields",
              "The charges", "The orbits", "The circuits", "The levers", "The pendulums"],
             ("accelerates", "accelerate")),
    "CHEM": (["The atom", "The molecule", "The ion", "The electron", "The reaction",
              "The compound", "The orbital", "The element", "The solution", "The crystal"],
             ["These atoms", "The molecules", "The ions", "The electrons", "The reactions",
              "The compounds", "The orbitals", "The elements", "The solutions", "The crystals"],
             ("reacts", "react")),
    "HIST": (["The dynasty", "The empire", "The kingdom", "The reform", "The uprising",
              "The treaty", "The army", "The colony", "The revolution", "The reign"],
             ["These dynasties", "The empires", "The kingdoms", "The reforms", "The uprisings",
              "The treaties", "The armies", "The colonies", "The revolutions", "The rebellions"],
             ("expands", "expand")),
    "BIO": (["The cell", "The enzyme", "The gene", "The chromosome", "The organism",
             "The tissue", "The organ", "The species", "The ecosystem", "The protein"],
            ["These cells", "The enzymes", "The genes", "The chromosomes", "The organisms",
             "The tissues", "The organs", "The species", "The ecosystems", "The proteins"],
            ("divides", "divide")),
    "INT3202": (["The table", "The index", "The row", "The column", "The transaction",
                 "The constraint", "The query", "The schema", "The view", "The key"],
                ["These tables", "The indexes", "The rows", "The columns", "The transactions",
                 "The constraints", "The queries", "The schemas", "The views", "The keys"],
                ("locks", "lock")),
    "INT3304": (["The router", "The switch", "The packet", "The frame", "The port",
                 "The host", "The network", "The protocol", "The firewall", "The server"],
                ["These routers", "The switches", "The packets", "The frames", "The ports",
                 "The hosts", "The networks", "The protocols", "The firewalls", "The servers"],
                ("forwards", "forward")),
    "INT3306": (["The class", "The method", "The object", "The interface", "The exception",
                 "The package", "The thread", "The stream", "The bean", "The annotation"],
                ["These classes", "The methods", "The objects", "The interfaces", "The exceptions",
                 "The packages", "The threads", "The streams", "The beans", "The annotations"],
                ("compiles", "compile")),
    "INT3401": (["The model", "The neuron", "The layer", "The parameter", "The gradient",
                 "The dataset", "The feature", "The classifier", "The embedding", "The prediction"],
                ["These models", "The neurons", "The layers", "The parameters", "The gradients",
                 "The datasets", "The features", "The classifiers", "The embeddings", "The predictions"],
                ("learns", "learn")),
    "TOEIC": (["The report", "The invoice", "The contract", "The employee", "The manager",
               "The meeting", "The proposal", "The budget", "The supplier", "The client"],
              ["These reports", "The invoices", "The contracts", "The employees", "The managers",
               "The meetings", "The proposals", "The budgets", "The suppliers", "The clients"],
              ("submits", "submit")),
}

def gen_grammar_templates():
    facts = []
    for code, (sing, plur, (vs, vb)) in _GRAMMAR_DOM.items():
        topic = SUBJECTS[code][1][0]
        for s in sing:
            facts.append(("%s is correct." % s, code, topic, 2, "is", "are",
                          "Chủ ngữ ngôi 3 số ít → is."))
            facts.append(("%s has an important role." % s, code, topic, 2, "has", "have",
                          "Chủ ngữ ngôi 3 số ít → has."))
            facts.append(("%s requires careful analysis." % s, code, topic, 3, "requires", "require",
                          "Chủ ngữ ngôi 3 số ít → requires."))
            facts.append(("%s %s slowly." % (s, vs), code, topic, 3, vs, vb,
                          "Chủ ngữ ngôi 3 số ít → %s." % vs))
        for s in plur:
            facts.append(("%s are correct." % s, code, topic, 2, "are", "is",
                          "Chủ ngữ số nhiều → are."))
            facts.append(("%s have an important role." % s, code, topic, 2, "have", "has",
                          "Chủ ngữ số nhiều → have."))
            facts.append(("%s require careful analysis." % s, code, topic, 3, "require", "requires",
                          "Chủ ngữ số nhiều → require."))
    return facts

# ======================================================================
# CÂU DỰ PHÒNG cho spelling tiếng Việt (khi stem không chứa từ thay được)
# ======================================================================
VN_SPELL_FALLBACK = [
    "Mọi người đều được đi học đúng tuổi quy định.",
    "Không ai là hoàn hảo tuyệt đối trên đời này.",
    "Hạnh phúc đến từ những điều rất nhỏ bé.",
    "Chúng ta cần biết lắng nghe nhau nhiều hơn.",
    "Thời tiết hôm nay đẹp hơn hôm qua rất nhiều.",
    "Mỗi ngày trôi qua đều là một cơ hội mới.",
    "Sức khỏe là vốn quý nhất của con người.",
    "Hà Nội là trung tâm văn hóa của cả nước.",
    "Làm việc chăm chỉ sẽ mang lại thành công.",
    "Đọc sách giúp mở rộng kiến thức mỗi ngày.",
    "Bố mẹ luôn yêu thương con cái vô điều kiện.",
    "Thành phố Hồ Chí Minh là đô thị lớn nhất nước ta.",
    "Đường ngắn nhất không phải lúc nào cũng dễ dàng.",
    "Trong lớp học, chúng em được làm quen bạn mới.",
    "Đội bóng quê hương đã chiến thắng vẻ vang.",
    "Cây xanh giúp làm sạch không khí trong lành.",
    "Mẹ em hay mua mắm muối về dự trữ.",
    "Núi rừng Việt Nam rất hùng vĩ và tráng lệ.",
    "Học sinh cần rèn luyện thân thể thường xuyên.",
    "Vấn đề bảo vệ môi trường đang được quan tâm đặc biệt.",
    "Kỹ năng giao tiếp rất quan trọng trong công việc.",
    "Trách nhiệm của mỗi công dân là xây dựng đất nước.",
    "Lời nói không mất tiền mua, lựa lời mà nói cho vừa lòng nhau.",
    "Thời gian là vàng bạc, đừng đánh rơi vô nghĩa.",
]
for _city in ["Hà Nội", "Huế", "Đà Nẵng", "Cần Thơ", "Nha Trang",
              "Hạ Long", "Vinh", "Quy Nhơn", "Pleiku", "Buôn Ma Thuột"]:
    VN_SPELL_FALLBACK.append("Ở %s, người dân chủ yếu đi xe máy để đi làm hằng ngày." % _city)
    VN_SPELL_FALLBACK.append("%s có rất nhiều di tích lịch sử nổi tiếng lâu đời." % _city)
    VN_SPELL_FALLBACK.append("Đến %s, du khách được chiêm ngưỡng nhiều danh lam thắng cảnh." % _city)

# ======================================================================
# BƠM LỖI (tất định)
# ======================================================================
def _tag(it, gold):
    it = dict(it)
    it["true_correct_idx"] = it["correct_idx"]
    it["gold_issue_type"] = gold
    return it

def inject_spelling_vi(item):
    for table, key in ((item["stem"], "stem"),):
        for word in sorted(VN_MISSPELL, key=len, reverse=True):
            if word in table:
                new = dict(item)
                new[key] = table.replace(word, VN_MISSPELL[word], 1)
                new["explanation"] = item["explanation"] + " (Lỗi chính tả: “%s” → đúng “%s”.)" % (VN_MISSPELL[word], word)
                new["true_correct_idx"] = item["correct_idx"]
                new["gold_issue_type"] = "spelling"
                return new
    for i in range(4):
        opt = item["option_%c" % (ord("a") + i)]
        for word in sorted(VN_MISSPELL, key=len, reverse=True):
            if word in opt:
                new = dict(item)
                new["option_%c" % (ord("a") + i)] = opt.replace(word, VN_MISSPELL[word], 1)
                new["explanation"] = item["explanation"] + " (Lỗi chính tả trong phương án: “%s” → đúng “%s”.)" % (VN_MISSPELL[word], word)
                new["true_correct_idx"] = item["correct_idx"]
                new["gold_issue_type"] = "spelling"
                return new
    return None

def inject_logic(item):
    new = dict(item)
    wrong = [i for i in range(4) if i != item["correct_idx"]]
    new["true_correct_idx"] = item["correct_idx"]
    new["correct_idx"] = rng.choice(wrong)
    new["explanation"] = item["explanation"] + " [demo] Đáp án thực sự đúng là phương án %s." % ("ABCD"[item["correct_idx"]])
    new["gold_issue_type"] = "logic"
    return new

def inject_distractor(item):
    new = dict(item)
    wrong = [i for i in range(4) if i != item["correct_idx"]]
    target = rng.choice(wrong)
    correct_text = new["option_%c" % (ord("a") + item["correct_idx"])]
    if rng.random() < 0.5:
        new["option_%c" % (ord("a") + target)] = correct_text
        note = "một phương án sai bị thay bằng phương án TRÙNG LẶP đáp án đúng"
    else:
        ctx = EN_ABSURD_OPTIONS if (item.get("stem_en") or item["subject_code"] == "TOEIC") else ABSURD_OPTIONS
        new["option_%c" % (ord("a") + target)] = rng.choice(ctx)
        note = "một phương án sai bị thay bằng phương án LẠC phạm trù"
    new["explanation"] = item["explanation"] + " (%s.)" % note
    new["true_correct_idx"] = item["correct_idx"]
    new["gold_issue_type"] = "distractor"
    return new

def make_fallback_spelling(sentence, dom_idx, code, topic):
    matches = [w for w in VN_MISSPELL if w in sentence]
    if not matches:
        return None
    key = matches[(dom_idx + sum(ord(c) for c in sentence)) % len(matches)]
    wrong = VN_MISSPELL[key]
    corrupted = sentence.replace(key, wrong, 1)
    words = [w.strip(".,!?“”\"'") for w in sentence.split()]
    pool = list(dict.fromkeys(w for w in words
                              if w and w != key and w != wrong and len(w) > 2))
    for pad in ["tuy nhiên", "đồng thời", "cuối cùng", "trước hết"]:
        if len(pool) >= 3:
            break
        if pad not in pool:
            pool.append(pad)
    distract = rng.sample(pool, 3)
    options = distract + [wrong]
    rng.shuffle(options)
    stem = "Trong câu sau, từ nào bị viết SAI chính tả? “%s”" % corrupted
    return {
        "subject_code": code,
        "subject_name": SUBJECTS[code][0],
        "topic_name": topic,
        "stem": stem,
        "option_a": options[0], "option_b": options[1], "option_c": options[2], "option_d": options[3],
        "correct_idx": options.index(wrong),
        "true_correct_idx": options.index(wrong),
        "difficulty": 2,
        "explanation": "“%s” viết sai chính tả; đúng là “%s”." % (wrong, key),
        "stem_en": None,
        "gold_issue_type": "spelling",
    }

# ======================================================================
# ORCHESTRATOR
# ======================================================================
def dedup(items):
    seen, out = set(), []
    for it in items:
        k = it["stem"].strip().lower()
        if k in seen:
            continue
        seen.add(k)
        out.append(it)
    return out

def main():
    pools = OrderedDict([
        ("MATH", pool_math() + pool_math_extra()),
        ("PHYS", pool_phys() + pool_phys_extra() + pool_phys_extra2()),
        ("CHEM", pool_chem() + pool_chem_extra() + pool_chem_extra2()),
        ("HIST", pool_hist() + pool_hist_extra() + pool_hist_extra2()),
        ("BIO", pool_bio() + pool_bio_extra() + pool_bio_extra2() + pool_bio_extra3()),
        ("INT3202", pool_int3202() + pool_int3202_extra() + pool_int3202_extra2() + pool_int3202_extra3()),
        ("INT3304", pool_int3304() + pool_int3304_extra() + pool_int3304_extra2()),
        ("INT3306", pool_int3306() + pool_int3306_extra() + pool_int3306_extra2() + pool_int3306_extra3()),
        ("INT3401", pool_int3401() + pool_int3401_extra() + pool_int3401_extra2() + pool_int3401_extra3()),
        ("TOEIC", pool_toeic() + [
            mk("TOEIC", "Part 5 - Ngữ pháp & Từ loại", 2, expl, stem, list(opts), correct,
               stem_en=stem)
            for stem, opts, correct, _pt, expl in pool_toeic_frames_extra()]),
    ])

    toeic_spelling = pool_toeic_spelling()
    grammar_corpus = build_grammar_items(list(EN_FACTS) + gen_grammar_templates())
    for g in grammar_corpus:
        g.pop("_grammar_note", None)
        g["true_correct_idx"] = g["correct_idx"]
        g["gold_issue_type"] = "grammar"

    all_items, warnings = [], []
    for dom_idx, code in enumerate(SUBJECTS):
        topic = SUBJECTS[code][1][0]
        is_toeic = code == "TOEIC"
        quota = PER_DOMAIN_TOEIC if is_toeic else PER_DOMAIN_VN
        dom_rng = random.Random(SEED + dom_idx * 997)

        pool = dedup(pools[code])
        dom_rng.shuffle(pool)
        clean = list(pool)

        # --- grammar ---
        gram = [g for g in grammar_corpus if g["subject_code"] == code]
        dom_rng.shuffle(gram)
        if len(gram) < quota["grammar"]:
            warnings.append("GRAMMAR thiếu %s: có %d cần %d" % (code, len(gram), quota["grammar"]))
        dom_items = [dict(g) for g in gram[:quota["grammar"]]]

        # --- spelling ---
        if is_toeic:
            sp = list(toeic_spelling)
            dom_rng.shuffle(sp)
            spelling_items = [_tag(x, "spelling") for x in sp[:quota["spelling"]]]
            rest = clean
        else:
            spelling_items, rest = [], []
            for it in clean:
                if len(spelling_items) < quota["spelling"]:
                    inj = inject_spelling_vi(it)
                    if inj is not None:
                        spelling_items.append(inj)
                        continue
                rest.append(it)
            fb = list(VN_SPELL_FALLBACK)
            dom_rng.shuffle(fb)
            fi = 0
            while len(spelling_items) < quota["spelling"] and fi < len(fb):
                fb_item = make_fallback_spelling(fb[fi], dom_idx, code, topic)
                if fb_item is not None:
                    spelling_items.append(fb_item)
                fi += 1
        if len(spelling_items) < quota["spelling"]:
            warnings.append("SPELLING thiếu %s: %d/%d" % (code, len(spelling_items), quota["spelling"]))
        dom_items += spelling_items

        # --- none ---
        none_items = [_tag(x, "none") for x in rest[:quota["none"]]]
        rest = rest[quota["none"]:]

        # --- logic ---
        logic_items = [inject_logic(x) for x in rest[:quota["logic"]]]
        rest = rest[quota["logic"]:]

        # --- distractor ---
        distractor_items = [inject_distractor(x) for x in rest[:quota["distractor"]]]
        used = quota["none"] + quota["logic"] + quota["distractor"] + (0 if is_toeic else quota["spelling"])
        if len(clean) < used:
            warnings.append("CLEAN thiếu %s: có %d cần %d" % (code, len(clean), used))
        dom_items += none_items + logic_items + distractor_items

        if len(dom_items) != 100:
            warnings.append("DOMAIN %s có %d/100 câu" % (code, len(dom_items)))
        all_items += dom_items
        print("[%s] clean=%d -> grammar=%d spelling=%d none=%d logic=%d distractor=%d total=%d" % (
            code, len(clean), min(len([g for g in grammar_corpus if g['subject_code'] == code]), quota["grammar"]),
            len(spelling_items), len(none_items), len(logic_items), len(distractor_items), len(dom_items)))

    # --- global dedup theo stem ---
    seen, final_items = set(), []
    dropped = 0
    for it in all_items:
        k = it["stem"].strip().lower()
        if k in seen:
            dropped += 1
            continue
        seen.add(k)
        final_items.append(it)
    print("dropped duplicates:", dropped, "-> total:", len(final_items))

    rng.shuffle(final_items)
    for i, it in enumerate(final_items, 1):
        it["id"] = "DQ%04d" % i
        it["correct_label"] = "ABCD"[it["correct_idx"]]
        it["true_correct_label"] = "ABCD"[it["true_correct_idx"]]

    label_counts = Counter(it["gold_issue_type"] for it in final_items)
    domain_counts = Counter(it["subject_code"] for it in final_items)
    print("label counts:", dict(label_counts))
    print("domain counts:", dict(domain_counts))
    for lab, quota in LABEL_QUOTA.items():
        if label_counts.get(lab, 0) != quota:
            warnings.append("LABEL %s: có %d cần %d" % (lab, label_counts.get(lab, 0), quota))
    for code in SUBJECTS:
        if domain_counts.get(code, 0) != 100:
            warnings.append("DOMAIN %s: có %d cần 100" % (code, domain_counts.get(code, 0)))

    out = {
        "meta": {
            "tool": "python_demo_llm/gen_eval_questions.py",
            "seed": SEED,
            "generated_at": "2026-11-20",
            "description": "1000 câu hỏi trắc nghiệm AI-authored có gold label cho demo đánh giá AI (none/spelling/grammar/logic/distractor).",
            "label_quota": dict(LABEL_QUOTA),
            "subjects": {code: SUBJECTS[code][0] for code in SUBJECTS},
            "count": len(final_items),
        },
        "questions": final_items,
    }
    import os
    os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
    with open(OUT_PATH, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)

    if warnings:
        print("\nWARNINGS:")
        for w in warnings:
            print(" -", w)
        sys.exit(1 if any("có" in w or "thiếu" in w for w in warnings) else 0)
    print("\nOK: wrote %d questions to %s" % (len(final_items), OUT_PATH))


def pool_int3202_extra3():
    A = "INT3202"
    items = []
    curated = [
        ("Câu SQL nào lấy 10 nhân viên lương cao nhất?",
         ["SELECT * FROM employees ORDER BY salary DESC LIMIT 10",
          "SELECT * FROM employees ORDER BY salary ASC LIMIT 10",
          "SELECT * FROM employees LIMIT 10 ORDER BY salary DESC",
          "SELECT TOP 10 * FROM employees ORDER BY salary ASC"],
         0, "Ngôn ngữ truy vấn SQL", 2,
         "ORDER BY salary DESC rồi LIMIT 10.",
         "Which SQL query returns the 10 highest-paid employees?"),
        ("FOREIGN KEY đảm bảo điều gì?",
         ["Giá trị ở cột con phải tồn tại ở cột khóa cha (hoặc NULL nếu cho phép)",
          "Cột con luôn duy nhất",
          "Cột con được tự động đánh chỉ mục",
          "Cột con không được chứa NULL"],
         0, "Mô hình quan hệ & Khóa", 2,
         "Referential integrity: tham chiếu hợp lệ sang bảng cha.",
         "What does a FOREIGN KEY enforce?"),
        ("PRIMARY KEY khác UNIQUE ở điểm nào?",
         ["PRIMARY KEY không cho NULL và mỗi bảng chỉ có một; UNIQUE cho phép nhiều và thường cho NULL",
          "PRIMARY KEY cho phép trùng",
          "UNIQUE luôn nhanh hơn",
          "Không khác nhau"],
         0, "Mô hình quan hệ & Khóa", 2,
         "PK = unique + not null + duy nhất một bộ.",
         "How does a PRIMARY KEY differ from UNIQUE?"),
        ("Transaction đảm bảo tính chất nào?",
         ["All-or-nothing: hoặc mọi thao tác thành công, hoặc hoàn tác toàn bộ",
          "Chạy không cần khóa",
          "Tự động tối ưu chỉ mục",
          "Không bao giờ rollback"],
         0, "Chuẩn hóa dữ liệu", 2,
         "Tính nguyên tử (Atomicity) của ACID.",
         "Which property does a transaction guarantee?"),
        ("Isolation level nào thấp nhất trong SQL chuẩn?",
         ["READ UNCOMMITTED",
          "READ COMMITTED",
          "REPEATABLE READ",
          "SERIALIZABLE"],
         0, "Chuẩn hóa dữ liệu", 3,
         "READ UNCOMMITTED: cho phép dirty read.",
         "Which is the lowest isolation level in standard SQL?"),
        ("SAVEPOINT trong transaction dùng để làm gì?",
         ["Đánh dấu điểm để rollback từng phần về đúng vị trí đó",
          "Lưu transaction xuống đĩa",
          "Đặt tên cho transaction",
          "Khóa bảng tạm thời"],
         0, "Chuẩn hóa dữ liệu", 3,
         "Rollback từng phần, không phải toàn bộ transaction.",
         "What is a SAVEPOINT used for?"),
        ("FULL OUTER JOIN trả về những dòng nào?",
         ["Mọi dòng của cả hai bảng; phần không khớp được điền NULL",
          "Chỉ dòng khớp ở cả hai bên",
          "Chỉ dòng bảng trái",
          "Chỉ dòng không khớp"],
         0, "Ngôn ngữ truy vấn SQL", 3,
         "Union của LEFT và RIGHT JOIN.",
         "Which rows does a FULL OUTER JOIN return?"),
        ("COUNT(cột) khác COUNT(*) ở điểm nào?",
         ["COUNT(cột) bỏ qua giá trị NULL; COUNT(*) đếm mọi dòng",
          "COUNT(*) nhanh hơn và luôn lớn hơn",
          "COUNT(cột) đếm cả NULL",
          "Không khác nhau"],
         0, "Ngôn ngữ truy vấn SQL", 3,
         "COUNT(expr) chỉ đếm expr NOT NULL.",
         "How does COUNT(column) differ from COUNT(*)?"),
        ("Dạng chuẩn 1 (1NF) yêu cầu gì?",
         ["Mọi ô chứa giá trị nguyên tử (atom), không có nhóm lặp",
          "Không phụ thuộc bắc cầu",
          "Mọi determinant là superkey",
          "Không có khóa ngoại"],
         0, "Chuẩn hóa dữ liệu", 2,
         "1NF: giá trị nguyên tử, không lặp nhóm thuộc tính.",
         "What does first normal form (1NF) require?"),
        ("Dạng chuẩn BCNF khác 3NF ở điểm nào?",
         ["BCNF đòi hỏi mọi determinant đều là superkey; 3NF nới lỏng cho khóa chính",
          "BCNF nới lỏng hơn 3NF",
          "3NF đòi hỏi determinant là siêu khóa",
          "Hai dạng chuẩn hoàn toàn giống nhau"],
         0, "Chuẩn hóa dữ liệu", 4,
         "BCNF: mọi X → Y thì X là superkey.",
         "How does BCNF differ from 3NF?"),
        ("SQL Injection xảy ra khi nào?",
         ["Khi dữ liệu người dùng được nối trực tiếp vào câu lệnh SQL mà không tham số hóa",
          "Khi CSDL chạy quá tải",
          "Khi bảng thiếu khóa chính",
          "Khi index bị hỏng"],
         0, "Bảo mật dữ liệu & Quản trị", 2,
         "Prepared statement/parameterized query là cách phòng thủ chuẩn.",
         "When does SQL injection occur?"),
        ("Ràng buộc nào đảm bảo cột không được NULL?",
         ["NOT NULL",
          "UNIQUE",
          "CHECK",
          "FOREIGN KEY"],
         0, "Mô hình quan hệ & Khóa", 1,
         "NOT NULL: mọi dòng phải có giá trị.",
         "Which constraint ensures a column is never NULL?"),
        ("Chỉ mục UNIQUE INDEX trên email đảm bảo điều gì?",
         ["Không có hai dòng có cùng giá trị email",
          "Email luôn in hoa",
          "Email được mã hóa",
          "Email tự động sinh"],
         0, "Mô hình quan hệ & Khóa", 2,
         "Unique index: ngăn trùng lặp.",
         "What does a UNIQUE INDEX on email guarantee?"),
        ("EXPLAIN / EXPLAIN ANALYZE trong PostgreSQL/MariaDB dùng để làm gì?",
         ["Xem kế hoạch thực thi (plan) của câu truy vấn để tối ưu",
          "Chạy truy vấn trong sandbox",
          "Lịch sử truy vấn",
          "Mã hóa truy vấn"],
         0, "Ngôn ngữ truy vấn SQL", 3,
         "Execution plan: thứ tự join, index nào được dùng.",
         "What is EXPLAIN used for?"),
    ]
    for stem_vi, opts, correct, topic, diff, expl, en in curated:
        items.append(mk(A, topic, diff, expl, stem_vi, list(opts), correct, stem_en=en))
    return items

def pool_int3306_extra3():
    A = "INT3306"
    items = []
    puzzles = [
        ("System.out.println(10 / 3);", "3", ["3", "3.33", "4", "1"],
         "Chia nguyên int → 3.", "What does System.out.println(10 / 3) print?"),
        ("System.out.println(10 % 3);", "1", ["1", "3", "0", "10"],
         "10 mod 3 = 1.", "What does System.out.println(10 % 3) print?"),
        ('String s = "abc"; s.toUpperCase(); System.out.println(s);', "abc", ["abc", "ABC", "Abc", "Lỗi biên dịch"],
         "String immutable — kết quả bị bỏ.", "What does s.toUpperCase() without assignment print?"),
        ('int[] a = {1, 2, 3}; System.out.println(a.length);', "3", ["3", "2", "4", "Lỗi biên dịch"],
         "length của mảng là thuộc tính, kết quả 3.", "What is printed for a.length?"),
        ('System.out.println("Java".length());', "4", ["4", "5", "3", "0"],
         "String.length() = 4.", "What does \"Java\".length() return?"),
        ("System.out.println(2 + 3 * 4);", "14", ["14", "20", "9", "24"],
         "Nhân trước cộng sau: 2 + 12 = 14.", "What does System.out.println(2 + 3 * 4) print?"),
        ("System.out.println(true && false || true);", "true", ["true", "false", "Lỗi", "null"],
         "&& trước ||: (true && false) || true = true.", "What does true && false || true evaluate to?"),
        ("System.out.println(0.1 + 0.2 == 0.3);", "false", ["false", "true", "Lỗi biên dịch", "0.3"],
         "Sai số thập phân nhị phân.", "What does 0.1 + 0.2 == 0.3 print?"),
        ("Integer a = 128; Integer b = 128; System.out.println(a == b);", "false",
         ["false", "true", "Lỗi biên dịch", "null"],
         "128 ngoài vùng cache Integer (-128..127).", "What does Integer 128 == 128 print?"),
        ("Integer a = 127; Integer b = 127; System.out.println(a == b);", "true",
         ["true", "false", "Lỗi biên dịch", "null"],
         "127 trong cache Integer → cùng object.", "What does Integer 127 == 127 print?"),
        ('System.out.println("5" + 5);', "55", ["55", "10", "Lỗi biên dịch", "5"],
         "String + int → nối chuỗi.", "What does \"5\" + 5 print?"),
        ('StringBuilder sb = new StringBuilder("a"); sb.append("b"); System.out.println(sb);', "ab",
         ["ab", "a", "b", "Lỗi biên dịch"], "StringBuilder mutable.", "What does the StringBuilder print?"),
        ('System.out.println(10 > 5 ? "yes" : "no");', "yes", ["yes", "no", "true", "Lỗi"],
         "Toán tử ba ngôi.", "What does 10 > 5 ? \"yes\" : \"no\" evaluate to?"),
        ("System.out.println('A' + 1);", "66", ["66", "A1", "B", "Lỗi biên dịch"],
         "char + int → int (65 + 1).", "What does 'A' + 1 print?"),
        ("int x = 5; System.out.println(x++);", "5", ["5", "6", "0", "Lỗi biên dịch"],
         "Post-increment: in rồi mới tăng.", "What does x++ print?"),
        ("int x = 5; System.out.println(++x);", "6", ["6", "5", "7", "Lỗi biên dịch"],
         "Pre-increment: tăng rồi mới in.", "What does ++x print?"),
    ]
    for code, correct, opts, expl, en in puzzles:
        stem_vi = "Đoạn mã sau in ra (hoặc trả về) giá trị nào? “%s”" % code
        items.append(mk(A, "Cú pháp & OOP cơ bản", 3, expl, stem_vi, list(opts), opts.index(correct), stem_en=en))
    curated = [
        ("JVM là gì?", ["Máy ảo thực thi bytecode Java trên mọi nền tảng",
          "Trình biên dịch Java sang C++",
          "IDE của Java",
          "Bộ nhớ heap của Java"], 0,
         "Write once, run anywhere.", "What is the JVM?"),
        ("Trình biên dịch JIT trong JVM làm gì?", ["Biên dịch bytecode nóng sang mã máy ngay khi chạy để tăng tốc",
          "Biên dịch Java sang Python",
          "Kiểm tra lỗi cú pháp",
          "Nén bytecode"], 0,
         "Just-In-Time: hotspot profiling + native code.", "What does the JIT compiler in the JVM do?"),
        ("Garbage collector của JVM quản lý vùng nhớ nào?", ["Heap",
          "Stack",
          "Metaspace chứa hằng",
          "Vùng nhớ của hệ điều hành"], 0,
         "GC dọn object không còn tham chiếu trên heap.", "Which memory area does the JVM garbage collector manage?"),
        ("StackOverflowError thường xảy ra khi nào?", ["Đệ quy vô hạn hoặc không có điều kiện dừng",
          "Hết heap",
          "File không tồn tại",
          "Mạng bị ngắt"], 0,
         "Mỗi call frame chiếm stack; đệ quy vô hạn làm tràn.", "When does a StackOverflowError typically occur?"),
        ("try-with-resources tự động làm gì khi thoát khỏi khối lệnh?",
         ["Gọi close() trên mọi resource khai báo trong ngoặc try",
          "Ghi log vào file",
          "Rollback transaction",
          "Giải phóng mọi object trong heap"], 0,
         "AutoCloseable.close() được gọi tự động, kể cả khi có exception.", "What does try-with-resources do automatically?"),
        ("String pool (string interning) giúp gì?",
         ["Tái sử dụng các chuỗi literal giống nhau, tiết kiệm bộ nhớ",
          "Mã hóa chuỗi bí mật",
          "Nối chuỗi nhanh hơn StringBuilder",
          "Nén chuỗi UTF-8"], 0,
         "Hai literal giống nhau cùng tham chiếu một object.", "What is the purpose of the string constant pool?"),
        ("Trong Spring, @Transactional mặc định rollback khi nào?",
         ["Khi phương thức ném RuntimeException hoặc Error (unchecked)",
          "Khi ném mọi loại exception",
          "Chỉ khi gọi rollback() thủ công",
          "Không bao giờ rollback"], 0,
         "Checked exceptions mặc định KHÔNG rollback (trừ khi khai báo rollbackFor).", "When does Spring roll back a @Transactional method by default?"),
        ("@GetMapping là viết tắt của annotation nào?",
         ["@RequestMapping(method = RequestMethod.GET)",
          "@RequestMapping(method = RequestMethod.POST)",
          "@RequestMapping(method = RequestMethod.PUT)",
          "@RequestLine(\"GET\")"], 0,
         "Composed annotation theo HTTP method.", "@GetMapping is shorthand for which annotation?"),
        ("Kỷ lục (record) trong Java 16 tạo ra điều gì?",
         ["Lớp dữ liệu bất biến với constructor, accessor, equals/hashCode/toString sinh tự động",
          "Interface chỉ có getter",
          "Enum nâng cao",
          "Lớp trừu tượng"], 0,
         "record Point(int x, int y) {} — data carrier.", "What does a Java record generate?"),
        ("Từ khóa var trong Java 10+ suy kiểu tại thời điểm nào?",
         ["Lúc biên dịch (kiểu tĩnh)",
          "Lúc chạy (kiểu động)",
          "Không bao giờ — bắt buộc ghi rõ kiểu",
          "Lúc đóng gói JAR"], 0,
         "var: local-variable type inference, vẫn là static typing.", "When does var infer types in Java 10+?"),
        ("Trong Spring Boot, application.properties dùng để làm gì?",
         ["Cấu hình ứng dụng (cổng, datasource, profile...) tách khỏi mã nguồn",
          "Ghi log",
          "Khai báo dependency",
          "Đóng gói ứng dụng"], 0,
         "Externalised configuration; ưu tiên YAML/properties.", "What is application.properties used for in Spring Boot?"),
        ("NullPointerException thuộc loại exception nào?",
         ["Unchecked exception",
          "Checked exception",
          "Error",
          "Throwable duy nhất không bắt được"], 0,
         "RuntimeException là unchecked.", "Is NullPointerException checked or unchecked?"),
    ]
    for stem_vi, opts, correct, expl, en in curated:
        items.append(mk(A, "Spring Boot & REST API" if ("Spring" in stem_vi or "@" in stem_vi) else
                        "Cú pháp & OOP cơ bản", 3, expl, stem_vi, list(opts), correct, stem_en=en))
    return items

def pool_int3401_extra3():
    A = "INT3401"
    items = []
    curated = [
        ("Perceptron đơn lớp học được những hàm nào?",
         ["Các hàm phân tách tuyến tính (AND, OR)",
          "Mọi hàm logic kể cả XOR",
          "Chỉ hàm AND",
          "Không học được hàm nào"], 0,
         "Minsky & Papert: XOR không tuyến tính tách được.",
         "Which functions can a single-layer perceptron learn?"),
        ("Trong thuật toán ID3, thuộc tính nào được chọn để phân nhánh?",
         ["Thuộc tính cho information gain lớn nhất",
          "Thuộc tính có giá trị cao nhất",
          "Thuộc tính xuất hiện đầu tiên",
          "Thuộc tính ngẫu nhiên"], 0,
         "Information gain = entropy cha − entropy con có trọng số.",
         "Which feature does ID3 choose for splitting?"),
        ("Entropy của một tập có hai lớp cân bằng (50/50) là bao nhiêu?",
         ["1 bit",
          "0 bit",
          "2 bit",
          "0.5 bit"], 0,
         "H = −0.5·log2(0.5) − 0.5·log2(0.5) = 1.",
         "What is the entropy of a balanced two-class set?"),
        ("Gradient descent cập nhật tham số theo hướng nào?",
         ["Ngược hướng gradient của hàm mất mát",
          "Cùng hướng gradient",
          "Ngẫu nhiên",
          "Vuông góc với gradient"], 0,
         "w ← w − η·∇L(w).",
         "Which direction does gradient descent update parameters in?"),
        ("Learning rate quá lớn thường gây hiện tượng gì?",
         ["Phân kỳ hoặc dao động quanh cực tiểu",
          "Hội tụ siêu nhanh và ổn định",
          "Overfitting nặng",
          "Không ảnh hưởng gì"], 0,
         "Bước nhảy lớn vượt qua thung lũng loss.",
         "What does a too-large learning rate typically cause?"),
        ("Random forest dùng kỹ thuật ensemble nào?",
         ["Bagging — trung bình/bầu chọn trên nhiều cây huấn luyện trên mẫu bootstrap",
          "Boosting tuần tự",
          "Stacking với mạng nơ-ron",
          "Dropout cho cây"], 0,
         "Bagging giảm phương sai của cây đơn lẻ.",
         "Which ensemble technique does Random Forest use?"),
        ("Tokenizer ảnh hưởng đến chi phí và giới hạn context của LLM như thế nào?",
         ["Cùng văn bản nhưng chia thành nhiều/ít token khác nhau — token quyết định chi phí và độ dài tối đa",
          "Tokenizer không liên quan đến context window",
          "Tokenizer chỉ ảnh hưởng tốc độ, không ảnh hưởng độ dài",
          "Tokenizer làm giảm số token mọi văn bản"], 0,
         "1 token ≈ 3–4 ký tự tiếng Anh; context window tính theo token.",
         "How does the tokenizer affect LLM cost and context limits?"),
        ("LLM “hallucinate” (bịa) thường xuất phát từ đâu?",
         ["Mô hình dự đoán token tiếp theo theo thống kê, không có cam kết về sự thật",
          "Dữ liệu huấn luyện chứa virus",
          "GPU chạy quá nhiệt",
          "Người dùng nhập sai"], 0,
         "Không có nguồn sự thật — nội dung trôi chảy nhưng có thể sai.",
         "Where do LLM hallucinations typically come from?"),
    ]
    for stem_vi, opts, correct, expl, en in curated:
        items.append(mk(A, "Học máy & Mạng nơ-ron" if "Tokenizer" not in stem_vi or "LLM" not in stem_vi else "Mô hình ngôn ngữ lớn (LLM)",
                        3, expl, stem_vi, list(opts), correct, stem_en=en))
    return items

def pool_bio_extra3():
    A = "BIO"
    items = []
    curated = [
        ("Số nhiễm sắc thể trong tế bào sinh dưỡng của người là bao nhiêu?",
         ["46", "23", "44", "48"], 0,
         "2n = 46 (23 cặp).", "How many chromosomes are in a human somatic cell?"),
        ("Bộ nhiễm sắc thể đơn bội (n) của giao tử người là bao nhiêu?",
         ["23", "46", "22", "24"], 0,
         "n = 23.", "What is the haploid chromosome number in human gametes?"),
        ("Quá trình nhân đôi DNA diễn ra ở đâu trong tế bào nhân thực?",
         ["Nhân tế bào",
          "Tế bào chất",
          "Ti thể",
          "Màng sinh chất"], 0,
         "DNA replication diễn ra trong nhân (pha S).", "Where does DNA replication occur in eukaryotic cells?"),
        ("Phiên mã (transcription) tạo ra trực tiếp loại phân tử nào?",
         ["RNA (mRNA, tRNA, rRNA)",
          "DNA mới",
          "Protein",
          "Axit amin tự do"], 0,
         "Transcription: DNA → RNA.", "What molecule does transcription directly produce?"),
        ("Dịch mã (translation) sử dụng khuôn mẫu là phân tử nào?",
         ["mRNA",
          "tRNA",
          "DNA",
          "rRNA"], 0,
         "Ribosome đọc mRNA 5'→3'.", "Which molecule serves as the template for translation?"),
        ("Đột biến gen được định nghĩa là gì?",
         ["Sự thay đổi trình tự nucleotide của gen",
          "Sự thay đổi số lượng NST",
          "Sự thay đổi cấu trúc NST",
          "Sự tái tổ hợp gen khi giảm phân"], 0,
         "Gene mutation: thay đổi trình tự nu (thay thế, thêm, mất).", "How is a gene mutation defined?"),
    ]
    for stem_vi, opts, correct, expl, en in curated:
        items.append(mk(A, "Tế bào & Di truyền", 2, expl, stem_vi, list(opts), correct, stem_en=en))
    return items

if __name__ == "__main__":
    main()
