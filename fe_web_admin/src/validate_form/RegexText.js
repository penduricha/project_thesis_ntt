/**
 * RegexText
 * -----------------------------------------------------------------------------
 * Bộ kiểm tra dữ liệu nhập (<input>, <textarea>, <p>) theo chuẩn Việt Nam.
 *
 *  - isEmpty(value)            : chuỗi rỗng / chỉ toàn khoảng trắng
 *  - isEmail(value)            : email, tên miền tối thiểu 2 cấp (gmail.com, edu.com.vn, ...)
 *  - isPhone(value, opts)      : số điện thoại Việt Nam (di động; tuỳ chọn thêm số bàn)
 *  - isIdCard(value)             : căn cước công dân 12 số
 *  - parseIdCard(value)          : tách mã tỉnh / giới tính / năm sinh từ IdCard
 *  - isFullName(value, opts)   : họ và tên Việt Nam
 *  - checkSpelling(textOrEl)   : kiểm tra chính tả tiếng Việt (nspell + dictionary-vi)
 *  - validate(type, input)     : kiểm tra nhanh, trả về { valid, message } để hiển thị lỗi
 *
 * Yêu cầu: ES2022 (private fields). Chính tả cần: npm i nspell dictionary-vi
 */

// ---- Bảng chữ cái tiếng Việt (29 chữ cái, gồm cả dấu thanh) -----------------
// Không có f, j, w, z theo chuẩn chữ quốc ngữ. Muốn cho phép thì thêm vào đây.
const VN_LOWER = (
    'aàáảãạăằắẳẵặâầấẩẫậ' +
    'bcdđ' +
    'eèéẻẽẹêềếểễệ' +
    'ghiìíỉĩị' +
    'klmn' +
    'oòóỏõọôồốổỗộơờớởỡợ' +
    'pqrst' +
    'uùúủũụưừứửữự' +
    'vxyỳýỷỹỵ'
).normalize('NFC');
const VN_UPPER = VN_LOWER.toUpperCase();
const VN_LETTERS = VN_LOWER + VN_UPPER;

/** Chỉ nhận chuỗi, cắt khoảng trắng hai đầu. Giá trị khác chuỗi -> ''. */
const clean = (v) => (typeof v === 'string' ? v.trim() : '');

export class RegexText {
    // ===========================================================================
    // Regex (biên dịch một lần)
    // ===========================================================================

    /**
     * Email:
     *  - phần local ≤ 64 ký tự, không bắt đầu/kết thúc/lặp liền các ký tự . _ % + -
     *  - tên miền BẮT BUỘC ≥ 2 cấp: "gmail.com", "edu.com.vn" ✔ | "gmail" ✘
     *  - mỗi nhãn tên miền ≤ 63 ký tự, không bắt đầu/kết thúc bằng "-"; TLD ≥ 2 chữ cái
     */
    static #EMAIL =
        /^(?=.{1,254}$)(?=[^@]{1,64}@)[A-Za-z0-9]+(?:[._%+-][A-Za-z0-9]+)*@(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,}$/;

    /**
     * Di động VN (10 số sau khi bỏ khoảng trắng/dấu chấm/gạch):
     *  03x (32-39) | 05x (52,56,58,59) | 07x (70,76-79) | 08x (81-89) | 09x (90-99)
     *  Tiền tố: 0 | 84 | +84
     */
    static #PHONE_MOBILE = /^(?:\+84|84|0)(?:3[2-9]|5[2689]|7[06-9]|8[1-9]|9\d)\d{7}$/;

    /** Điện thoại bàn: mã vùng 02x / 02xx + số thuê bao (tổng 11 số khi viết với 0). */
    static #PHONE_LANDLINE = /^(?:\+84|84|0)2\d{9}$/;

    /**
     * IdCard 12 số: [3 số mã tỉnh 001-096][1 số mã thế kỷ & giới tính][2 số năm sinh][6 số ngẫu nhiên]
     */
    static #IdCard = /^0(?:0[1-9]|[1-8]\d|9[0-6])\d{9}$/;

    /** Họ tên: chỉ chữ cái tiếng Việt, các từ cách nhau đúng 1 khoảng trắng. */
    static #NAME = new RegExp(`^[${VN_LETTERS}]+(?: [${VN_LETTERS}]+)*$`, 'u');

    /** Họ tên viết hoa chữ cái đầu mỗi từ: "Nguyễn Văn A". */
    static #NAME_CAPITALIZED = new RegExp(
        `^[${VN_UPPER}][${VN_LOWER}]*(?: [${VN_UPPER}][${VN_LOWER}]*)*$`,
        'u'
    );

    static #MESSAGES = {
        required: 'Vui lòng không để trống.',
        email: 'Email không hợp lệ (ví dụ: ten@gmail.com).',
        phone: 'Số điện thoại Việt Nam không hợp lệ.',
        idCard: 'Số IdCard không hợp lệ (gồm đúng 12 chữ số).',
        fullName: 'Họ và tên không hợp lệ (chỉ gồm chữ cái tiếng Việt, tối thiểu 2 từ).',
    };

    static #VALIDATORS = {
        email: 'isEmail',
        phone: 'isPhone',
        idCard: 'isIdCard',
        fullName: 'isFullName',
    };

    // ===========================================================================
    // Trạng thái của instance (dùng cho kiểm tra chính tả)
    // ===========================================================================
    #affUrl;
    #dicUrl;
    #ignoreWords;
    #spell = null;
    #loading = null;

    /**
     * @param {object}   [options]
     * @param {string}   [options.affUrl='/dictionaries/vi.aff']  URL file affix của từ điển
     * @param {string}   [options.dicUrl='/dictionaries/vi.dic']  URL file từ điển
     * @param {string[]} [options.ignoreWords=[]]  Từ bỏ qua khi soát chính tả (tên riêng, từ mượn...)
     */
    constructor({
                    affUrl = '/dictionaries/vi.aff',
                    dicUrl = '/dictionaries/vi.dic',
                    ignoreWords = [],
                } = {}) {
        this.#affUrl = affUrl;
        this.#dicUrl = dicUrl;
        this.#ignoreWords = ignoreWords;
    }

    // ===========================================================================
    // Tiện ích đọc dữ liệu từ phần tử HTML
    // ===========================================================================

    /** Lấy nội dung chữ: <input>/<textarea> -> .value ; <p>, <div>... -> .textContent */
    getText(el) {
        if (!el) return '';
        return typeof el.value === 'string' ? el.value : el.textContent ?? '';
    }

    #toText(input) {
        if (typeof input === 'string') return input;
        return input && typeof input === 'object' ? this.getText(input) : '';
    }

    // ===========================================================================
    // 1. Chuỗi rỗng
    // ===========================================================================

    /** true nếu null/undefined/''/chỉ có khoảng trắng (kể cả NBSP, zero-width). */
    isEmpty(value) {
        if (value === null || value === undefined) return true;
        return /^[\s\u200B-\u200D\uFEFF]*$/.test(String(value));
    }

    // ===========================================================================
    // 2. Email
    // ===========================================================================

    isEmail(value) {
        return RegexText.#EMAIL.test(clean(value));
    }

    // ===========================================================================
    // 3. Số điện thoại Việt Nam
    // ===========================================================================

    /**
     * Chấp nhận: 0912345678 | 0912 345 678 | 0912.345.678 | +84912345678 | (+84) 912-345-678
     * @param {object}  [opts]
     * @param value
     * @param {boolean} [opts.allowLandline=false] cho phép cả số điện thoại bàn (02x...)
     */
    isPhone(value, { allowLandline = false } = {}) {
        const phone = clean(value).replace(/[\s.\-()]/g, '');
        return (
            RegexText.#PHONE_MOBILE.test(phone) ||
            (allowLandline && RegexText.#PHONE_LANDLINE.test(phone))
        );
    }

    // ===========================================================================
    // 4. IdCard
    // ===========================================================================

    /**
     * Phân tích IdCard. Trả về null nếu không hợp lệ.
     * Mã thế kỷ/giới tính: 0=Nam 1900-1999, 1=Nữ 1900-1999, 2=Nam 2000-2099, 3=Nữ 2000-2099...
     * @returns {{provinceCode:string, gender:'Nam'|'Nữ', birthYear:number} | null}
     */
    parseIdCard(value) {
        const id = clean(value);
        if (!RegexText.#IdCard.test(id)) return null;

        const g = Number(id[3]);
        const birthYear = 1900 + Math.floor(g / 2) * 100 + Number(id.slice(4, 6));
        if (birthYear > new Date().getFullYear()) return null; // năm sinh ở tương lai

        return { provinceCode: id.slice(0, 3), gender: g % 2 === 0 ? 'Nam' : 'Nữ', birthYear };
    }

    isIdCard(value) {
        return this.parseIdCard(value) !== null;
    }

    // ===========================================================================
    // 5. Họ và tên
    // ===========================================================================

    /** Chuẩn hoá tên: NFC, bỏ khoảng trắng thừa; tuỳ chọn viết hoa chữ cái đầu mỗi từ. */
    normalizeName(value, { capitalize = false } = {}) {
        const name = clean(value).normalize('NFC').replace(/\s+/g, ' ');
        if (!capitalize) return name;
        return name
            .split(' ')
            .map((w) => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
            .join(' ');
    }

    /**
     * @param {object}  [opts]
     * @param value
     * @param {number}  [opts.minWords=2]
     * @param {number}  [opts.maxWords=7]
     * @param {number}  [opts.maxLength=60]
     * @param {boolean} [opts.requireCapitalized=false] bắt buộc viết hoa chữ đầu mỗi từ
     */
    isFullName(
        value,
        { minWords = 2, maxWords = 7, maxLength = 60, requireCapitalized = false } = {}
    ) {
        const name = this.normalizeName(value);
        if (!name || name.length > maxLength) return false;

        const words = name.split(' ').length;
        if (words < minWords || words > maxWords) return false;

        const regex = requireCapitalized ? RegexText.#NAME_CAPITALIZED : RegexText.#NAME;
        return regex.test(name);
    }

    // ===========================================================================
    // 6. Chính tả tiếng Việt (nspell + dictionary-vi) – dùng cho <p>, <textarea>
    // ===========================================================================

    /** Tải từ điển (lười – chỉ tải lần đầu, các lần sau dùng lại). */
    async loadSpellChecker() {
        if (this.#spell) return this.#spell;

        if (!this.#loading) {
            this.#loading = (async () => {
                const mod = await import('nspell');
                const nspell = mod.default ?? mod;

                const [aff, dic] = await Promise.all(
                    [this.#affUrl, this.#dicUrl].map(async (url) => {
                        const res = await fetch(url);
                        if (!res.ok) throw new Error(`Không tải được từ điển: ${url} (${res.status})`);
                        return res.text();
                    })
                );

                this.#spell = nspell(aff, dic);
                return this.#spell;
            })();

            // Nếu lỗi thì cho phép gọi lại lần sau
            this.#loading.catch(() => {
                this.#loading = null;
            });
        }
        return this.#loading;
    }

    /**
     * Soát chính tả. Nhận chuỗi hoặc phần tử HTML (<textarea>, <input>, <p>...).
     * @param {string|HTMLElement} input
     * @param {object}   [opts]
     * @param {string[]} [opts.ignoreWords=[]]   thêm từ bỏ qua cho lần gọi này
     * @param {number}   [opts.maxSuggestions=5] số gợi ý tối đa cho mỗi từ sai (0 = không gợi ý)
     * @returns {Promise<{valid:boolean, errors:Array<{word:string,index:number,length:number,suggestions:string[]}>}>}
     *          index/length tính theo chuỗi gốc -> dùng để tô màu từ sai.
     */
    async checkSpelling(input, { ignoreWords = [], maxSuggestions = 5 } = {}) {
        const spell = await this.loadSpellChecker();
        const text = this.#toText(input);

        const ignore = new Set(
            [...this.#ignoreWords, ...ignoreWords].map((w) => w.normalize('NFC').toLowerCase())
        );

        const errors = [];
        for (const m of text.matchAll(/[\p{L}\p{M}]+/gu)) {
            const word = m[0].normalize('NFC');
            const lower = word.toLowerCase();

            if (ignore.has(lower) || spell.correct(lower)) continue;

            errors.push({
                word,
                index: m.index,
                length: m[0].length,
                suggestions: maxSuggestions > 0 ? spell.suggest(lower).slice(0, maxSuggestions) : [],
            });
        }
        return { valid: errors.length === 0, errors };
    }

    // ===========================================================================
    // Kiểm tra nhanh + thông báo lỗi (đồng bộ)
    // ===========================================================================

    /**
     * @param {'email'|'phone'|'idCard'|'fullName'} type
     * @param {string|HTMLElement} input  chuỗi hoặc phần tử HTML
     * @param required
     * @param {object} [options]  { required = true, ...tuỳ chọn của từng hàm }
     * @returns {{valid:boolean, message:string}}
     */
    validate(type, input, { required = true, ...options } = {}) {
        const value = this.#toText(input);

        if (this.isEmpty(value)) {
            return required
                ? { valid: false, message: RegexText.#MESSAGES.required }
                : { valid: true, message: '' };
        }

        if (!Object.hasOwn(RegexText.#VALIDATORS, type)) {
            throw new Error(`Loại kiểm tra không được hỗ trợ: "${type}"`);
        }

        const valid = this[RegexText.#VALIDATORS[type]](value, options);
        return { valid, message: valid ? '' : RegexText.#MESSAGES[type] };
    }
}