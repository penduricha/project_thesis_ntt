export default class AdminLocalStorage {
    // Định nghĩa các key cố định dưới dạng static readonly (hoặc hằng số)
    static STORAGE_KEYS = {
        ADMIN_ID: 'adminIdLocalStorage',
        REMEMBER_LOGIN: 'adminRememberLogin'
    };

    /**
     * Lưu thông tin admin vào localStorage
     * @param {Object} admin
     */
    // setAdminLocalStorage(admin) {
    //     if (!admin) {
    //         console.error('Admin object is empty.');
    //         return;
    //     }
    //     localStorage.setItem(AdminLocalStorage.STORAGE_KEYS.ADMIN, JSON.stringify(admin));
    // }
    /**
     * Lưu thông tin Remember Me (adminId & password)
     * @param {number} adminId
     * @param {string} password
     */
    saveLocalStorageRememberMe(adminId, password) {
        if (!adminId || !password) {
            console.error('Admin ID or password is null.');
            return;
        }
        const credentials = { adminId, password };
        localStorage.setItem(
            AdminLocalStorage.STORAGE_KEYS.REMEMBER_LOGIN,
            JSON.stringify(credentials)
        );
    }

    /**
     * Lấy thông tin Remember Me
     * @returns {Object}
     */
    getLocalStorageRememberMe() {
        const data = localStorage.getItem(AdminLocalStorage.STORAGE_KEYS.REMEMBER_LOGIN);
        return data ? JSON.parse(data) : {};
    }

    /**
     * Xóa thông tin Remember Me
     */
    removeLocalStorageRememberMe() {
        const key = AdminLocalStorage.STORAGE_KEYS.REMEMBER_LOGIN;
        if (localStorage.getItem(key)) {
            localStorage.removeItem(key);
        }
    }

    /**
     * Lưu riêng adminId vào localStorage
     *
     */
    setAdminIdToLocalStorage(adminId) {
        if (!adminId) {
            console.error('Admin ID is empty.');
            return;
        }
        localStorage.setItem(AdminLocalStorage.STORAGE_KEYS.ADMIN_ID, adminId);
    }

    getAdminIdToLocalStorage() {
        return localStorage.getItem(AdminLocalStorage.STORAGE_KEYS.ADMIN_ID);
    }

    /**
     * Xóa riêng adminId khỏi localStorage
     */
    removeAdminIdFromLocalStorage() {
        const key = AdminLocalStorage.STORAGE_KEYS.ADMIN_ID;
        if (localStorage.getItem(key)) {
            localStorage.removeItem(key);
        } else {
            console.warn('Admin ID local storage is already empty.');
        }
    }
}