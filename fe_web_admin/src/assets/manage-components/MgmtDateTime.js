export default class MgmtDateTime {
    _date;

    getDate() {
        return this._date;
    }

    setDate(date) {
        if(date) {
            this._date = date;
        } else {
            this._date = new Date();
        }
    }

    // constructor() {
    //
    // }

    constructor(date) {
        this.setDate(date);
    }

    getFormatedDate() {
        const dateGet = new Date(this.getDate());
        let day = dateGet.getDay();
        let month = dateGet.getMonth() + 1;
        let year = dateGet.getFullYear();
    }

    // getFormatedDateByParamDate() {
    //     if (!this._date) return '';
    //     let dateReturn;
    //     if (typeof this._date === 'string') {
    //         const [y, m, d] = this._date.split('-').map(Number);
    //         this._date = new Date(y, m - 1, d);
    //     } else {
    //         dateReturn = this._date;
    //     }
    //     const day = dateReturn.getDate();
    //     const month = dateReturn.getMonth() + 1;
    //     const year = dateReturn.getFullYear();
    //     return `${day}/${month}/${year}`;
    // }

    getFormatedDateTime() {
        const dateGet = new Date(this.getDate());
        let day = dateGet.getDay();
        let month = dateGet.getMonth() + 1;
        let year = dateGet.getFullYear();
        let hour = dateGet.getHours();
        let minute = dateGet.getMinutes();
        let second = dateGet.getSeconds();
        return `${day}/${month}/${year} (${hour}:${minute}:${second})`;
    }

}