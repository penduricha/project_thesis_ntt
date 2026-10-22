export default class ManageDateTime {
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

    constructor(date) {
        this.setDate(date);
    }

    getFormatedDate() {
        const dateGet = new Date(this.getDate());
        let day = dateGet.getDay();
        let month = dateGet.getMonth() + 1;
        let year = dateGet.getFullYear();
    }

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