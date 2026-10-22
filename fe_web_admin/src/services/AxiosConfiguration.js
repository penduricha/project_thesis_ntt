

export default class AxiosConfiguration {
    constructor() {}

    // Call api from spring boot
    requestPathFromSpringBoot() {
        const ip_address = 'localhost';
        const port = '8080';
        const protocol = 'http';
        return `${protocol}://${ip_address}:${port}/api`;
    }

    // requestConfigFromSpringBoot(){
    //     const ip_address= 'localhost';
    //     const port = '8080';
    //     // return axios.create({
    //     //     baseURL: `http://${ip_address}:${port}/api`,
    //     //     headers: {
    //     //         'Content-Type': 'application/json',
    //     //         // Set header Authorization if there are securities
    //     //     }
    //     // });
    //     return ip_address, port;
    // }

    // Call api from nodeJS
    requestPathFromNodeJS() {

    }
}