// Giữ nguyên giá trị key cũ, nếu đổi thì dữ liệu đã lưu trong trình duyệt người dùng sẽ bị bỏ rơi
const STORAGE_KEYS = Object.freeze({
    SESSION: 'routerPathSessionStorage',
    LOCAL: 'routerPathLocalStorage',
});

/**
 * Lưu, đọc, xóa một chuỗi path trong một Storage (localStorage hoặc sessionStorage).
 */
class PathStorage {
    #storage;
    #key;

    constructor(storage, key) {
        this.#storage = storage;
        this.#key = key;
    }

    save(path) {
        if (path) {
            this.#storage.setItem(this.#key, path);
        }
    }

    get() {
        return this.#storage.getItem(this.#key) || null;
    }

    remove() {
        this.#storage.removeItem(this.#key);
    }
}

export default class RouterMgmt {
    #session;
    #local;

    constructor(
        sessionStorageArea = globalThis.sessionStorage,
        localStorageArea = globalThis.localStorage,
    ) {
        this.#session = new PathStorage(sessionStorageArea, STORAGE_KEYS.SESSION);
        this.#local = new PathStorage(localStorageArea, STORAGE_KEYS.LOCAL);
    }

    // ---------- Session storage ----------

    savePathToSessionStorage(routerPath) {
        this.#session.save(routerPath);
    }

    getPathFromSessionStorage() {
        return this.#session.get();
    }

    removePathFromSessionStorage() {
        this.#session.remove();
    }

    // ---------- Local storage ----------

    savePathToLocalStorage(routerPath) {
        this.#local.save(routerPath);
    }

    getPathFromLocalStorage() {
        return this.#local.get();
    }

    removePathFromLocalStorage() {
        this.#local.remove();
    }

    // ---------- Both ----------

    removeAllPaths() {
        this.#session.remove();
        this.#local.remove();
    }
}