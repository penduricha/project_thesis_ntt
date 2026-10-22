export default class StudentLocalStorage {
    // Định nghĩa các key cố định dưới dạng static readonly (hoặc hằng số)
    static STORAGE_KEYS = {
        STUDENT_ID: 'studentIdLocalStorage',
        REMEMBER_LOGIN: 'studentRememberLogin'
    };

    /**
     * Lưu thông tin student vào localStorage
     * @param {Object} student
     */
    // setStudentLocalStorage(student) {
    //     if (!student) {
    //         console.error('Student object is empty.');
    //         return;
    //     }
    //     localStorage.setItem(StudentLocalStorage.STORAGE_KEYS.STUDENT, JSON.stringify(student));
    // }
    /**
     * Lưu thông tin Remember Me (studentId & password)
     * @param {number} studentId
     * @param {string} password
     */
    saveLocalStorageRememberMe(studentId, password) {
        if (!studentId || !password) {
            console.error('Student ID or password is null.');
            return;
        }
        const credentials = { studentId, password };
        localStorage.setItem(
            StudentLocalStorage.STORAGE_KEYS.REMEMBER_LOGIN,
            JSON.stringify(credentials)
        );
    }

    /**
     * Lấy thông tin Remember Me
     * @returns {Object}
     */
    getLocalStorageRememberMe() {
        const data = localStorage.getItem(StudentLocalStorage.STORAGE_KEYS.REMEMBER_LOGIN);
        return data ? JSON.parse(data) : {};
    }

    /**
     * Xóa thông tin Remember Me
     */
    removeLocalStorageRememberMe() {
        const key = StudentLocalStorage.STORAGE_KEYS.REMEMBER_LOGIN;
        if (localStorage.getItem(key)) {
            localStorage.removeItem(key);
        }
    }

    /**
     * Lưu riêng studentId vào localStorage
     *
     */
    setStudentIdToLocalStorage(studentId) {
        if (!studentId) {
            console.error('Student ID is empty.');
            return;
        }
        localStorage.setItem(StudentLocalStorage.STORAGE_KEYS.STUDENT_ID, studentId);
    }

    getStudentIdToLocalStorage() {
        return localStorage.getItem(StudentLocalStorage.STORAGE_KEYS.STUDENT_ID);
    }

    /**
     * Xóa riêng studentId khỏi localStorage
     */
    removeStudentIdFromLocalStorage() {
        const key = StudentLocalStorage.STORAGE_KEYS.STUDENT_ID;
        if (localStorage.getItem(key)) {
            localStorage.removeItem(key);
        } else {
            console.warn('Student ID local storage is already empty.');
        }
    }
}