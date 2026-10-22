export default class RouterManagement {
    _variableRouterPathSession;
    _variableRouterPathLocalStorage;

    getVariableRouterPathSession() {
        return this._variableRouterPathSession;
    }

    setVariableRouterPathSession(value) {
        this._variableRouterPathSession = value;
    }

    getVariableRouterPathLocalStorage() {
        return this._variableRouterPathLocalStorage;
    }

    setVariableRouterPathLocalStorage(value) {
        this._variableRouterPathLocalStorage = value;
    }

    constructor(){
        this.setVariableRouterPathSession('routerPathSessionStorage');
        this.setVariableRouterPathLocalStorage('routerPathLocalStorage');
    }

    savePathToSessionStorage(routerPath){
        //Khi chuyển trang khác, path được save vào session.
        if(routerPath){
            sessionStorage.setItem(this.getVariableRouterPathSession(), routerPath);
        }
    }

    getPathFromSessionStorage(){
        const routerPath = sessionStorage.getItem(this.getVariableRouterPathSession());
        return routerPath || null;
    }

    //other functions local_storage,
    //session storages
    //save path to local_storage
    removePathFromLocalStorage(){
        //Khi chuyển trang khác, path được save vào session.
        if(this.getPathFromLocalStorage()) {
            localStorage.removeItem(this.getVariableRouterPathLocalStorage());
        } else {
            console.error('Local storage not found.');
        }
    }

    removePathFromSessionStorage(){
        //Khi chuyển trang khác, path được save vào session.
        if(this.getPathFromSessionStorage()) {
            sessionStorage.removeItem(this.getVariableRouterPathSession());
        } else {
            console.error('Session storage not found.');
        }
    }

    savePathToLocalStorage(routerPath){
        //Khi chuyển trang khác, path được save vào session.
        localStorage.setItem(this.getVariableRouterPathLocalStorage(), routerPath);
    }

    getPathFromLocalStorage(){
        const routerPath = localStorage
            .getItem(this.getVariableRouterPathLocalStorage());
        return routerPath || null;
    }
}