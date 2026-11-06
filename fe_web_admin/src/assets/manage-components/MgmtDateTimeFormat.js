
export default class MgmtDateTimeFormat {
    getFormatedDateByParamDate(dateGet) {
        // 1. Chuẩn hóa input thành Date object
        let dateReturn;

        if (dateGet instanceof Date) {
            dateReturn = dateGet;
        } else if (typeof dateGet === 'string') {
            // Parse "YYYY-MM-DD" theo local time (tránh lỗi timezone)
            const parts = dateGet.split('-').map(Number);
            if (parts.length === 3 && parts.every(n => !isNaN(n))) {
                dateReturn = new Date(parts[0], parts[1] - 1, parts[2]);
            }
        }

        // 2. Kiểm tra hợp lệ
        if (!dateReturn || isNaN(dateReturn.getTime())) {
            console.warn('Invalid date input:', dateGet);
            return '';
        }

        // 3. Format
        const day   = dateReturn.getDate();
        const month = dateReturn.getMonth() + 1;
        const year  = dateReturn.getFullYear();

        return `${day}/${month}/${year}`;
    }
}