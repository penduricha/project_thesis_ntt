export default class AdminStorage {
    // Định nghĩa các key cố định dưới dạng static readonly (hoặc hằng số)
    static STORAGE_KEYS = {
        ADMIN_ID: 'adminIdLocalStorage',
        REMEMBER_LOGIN: 'adminRememberLogin'
    };

    /**
     * Lưu thông tin Remember Me (chỉ lưu adminId, tuyệt đối không lưu mật khẩu ở localStorage)
     * @param {number|string} adminId
     */
    saveLocalStorageRememberMe(adminId) {
        if (!adminId) {
            console.error('Admin ID is null.');
            return;
        }
        localStorage.setItem(
            AdminStorage.STORAGE_KEYS.REMEMBER_LOGIN,
            JSON.stringify({ adminId: String(adminId) })
        );
    }

    /**
     * Lấy thông tin Remember Me (tự xóa trường password cũ nếu tồn tại trong localStorage)
     * @returns {Object}
     */
    getLocalStorageRememberMe() {
        const raw = localStorage.getItem(AdminStorage.STORAGE_KEYS.REMEMBER_LOGIN);
        if (!raw) {
            return {};
        }
        try {
            const parsed = JSON.parse(raw);
            if (!parsed || typeof parsed !== 'object' || !parsed.adminId) {
                return {};
            }
            if ('password' in parsed) {
                this.saveLocalStorageRememberMe(parsed.adminId);
            }
            return { adminId: parsed.adminId };
        } catch {
            this.removeLocalStorageRememberMe();
            return {};
        }
    }

    /**
     * Xóa thông tin Remember Me
     */
    removeLocalStorageRememberMe() {
        const key = AdminStorage.STORAGE_KEYS.REMEMBER_LOGIN;
        if (localStorage.getItem(key)) {
            localStorage.removeItem(key);
        }
    }

    /**
     * Lưu riêng adminId vào localStorage
     */
    setAdminIdToLocalStorage(adminId) {
        if (!adminId) {
            console.error('Admin ID is empty.');
            return;
        }
        localStorage.setItem(AdminStorage.STORAGE_KEYS.ADMIN_ID, adminId);
    }

    getAdminIdToLocalStorage() {
        return localStorage.getItem(AdminStorage.STORAGE_KEYS.ADMIN_ID);
    }

    /**
     * Xóa riêng adminId khỏi localStorage
     */
    removeAdminIdFromLocalStorage() {
        const key = AdminStorage.STORAGE_KEYS.ADMIN_ID;
        if (localStorage.getItem(key)) {
            localStorage.removeItem(key);
        } else {
            console.warn('Admin ID local storage is already empty.');
        }
    }
}
