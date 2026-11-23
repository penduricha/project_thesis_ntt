const CHANNEL_NAME = 'auth-sync';
const channel = typeof BroadcastChannel !== 'undefined'
    ? new BroadcastChannel(CHANNEL_NAME)
    : null;

// Tab đăng xuất gọi hàm này để báo cho các tab khác
export function broadcastLogout() {
    channel?.postMessage({ type: 'LOGOUT', at: Date.now() });
}

// Tab nhận tin: đăng ký callback, trả về hàm huỷ đăng ký
export function onRemoteLogout(callback) {
    if (!channel) return () => {};
    const handler = (event) => {
        if (event.data?.type === 'LOGOUT') callback();
    };
    channel.addEventListener('message', handler);
    return () => channel.removeEventListener('message', handler);
}