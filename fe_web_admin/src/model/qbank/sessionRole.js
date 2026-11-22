import { TokenLogin } from '@/model/TokenLogin.js';

/**
 * Đọc claim của ACCESS token đang lưu để biết vai người dùng.
 *
 * Mục đích: ẩn/hiện mục menu theo vai ngay trên giao diện (giống `S.role` của bản demo).
 * Đây chỉ là lớp trang trí — quyền thật do backend quyết định bằng @PreAuthorize, nên sửa
 * token trong localStorage cũng không gọi được API của vai khác.
 *
 * Token là JWT 3 phần; phần payload là Base64URL. Không xác minh chữ ký ở đây (không có khoá),
 * chỉ giải mã để hiển thị.
 */
function decodePayload(token) {
    if (!token || typeof token !== 'string') {
        return null;
    }
    const parts = token.split('.');
    if (parts.length !== 3) {
        return null;
    }
    try {
        const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
        const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
        const json = decodeURIComponent(
            atob(padded)
                .split('')
                .map((c) => `%${`00${c.charCodeAt(0).toString(16)}`.slice(-2)}`)
                .join(''),
        );
        return JSON.parse(json);
    } catch {
        return null;
    }
}

/**
 * @param {TokenLogin} tokenLogin
 * @returns {{role: string, adminId: number|null}} role là 'ADMIN' | 'LECTURER' (mặc định LECTURER
 *          giống cách backend fail-closed trong RoleAuthorities), adminId là null nếu không đọc được.
 */
export function readSession(tokenLogin = new TokenLogin()) {
    const payload = decodePayload(tokenLogin.getAccessTokenFromStorage());
    const role = String(payload?.role || 'LECTURER').toUpperCase();
    return {
        role: role === 'ADMIN' ? 'ADMIN' : 'LECTURER',
        adminId: payload?.adminId ?? null,
    };
}

export function isAdmin(tokenLogin = new TokenLogin()) {
    return readSession(tokenLogin).role === 'ADMIN';
}
