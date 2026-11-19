package com.example.be_web_admin.chat.ws;

import com.example.be_web_admin.chat.ChatProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Bản đồ adminId -> các kết nối đang mở (web và điện thoại cùng lúc). Nằm trong bộ nhớ nên chỉ đúng khi chạy MỘT
 * instance Spring; từ hai instance trở lên phải thêm Redis Pub/Sub (mục 16 của thiết kế).
 */
@Slf4j
@Component
public class ChatSessionRegistry {

    /** Phiên bị thu hồi (đăng xuất, đăng nhập nơi khác, tài khoản bị khóa): client không nối lại. */
    public static final CloseStatus SESSION_REVOKED = new CloseStatus(4001, "session revoked");
    /** Khung client gửi sai định dạng hoặc quá lớn. */
    public static final CloseStatus BAD_FRAME = new CloseStatus(4002, "bad frame");
    /** Server gửi không kịp cho client này. */
    public static final CloseStatus SLOW_CONSUMER = new CloseStatus(4003, "slow consumer");
    /** Không thấy khung nào quá hạn. */
    public static final CloseStatus IDLE_TIMEOUT = new CloseStatus(4000, "idle timeout");

    private final Map<Long, CopyOnWriteArrayList<ChatConnection>> byAdmin = new ConcurrentHashMap<>();
    private final ChatProperties properties;

    public ChatSessionRegistry(ChatProperties properties) {
        this.properties = properties;
    }

    /** Số kết nối đang mở của một admin (dùng để từ chối sớm ở bước bắt tay). */
    public int count(Long adminId) {
        List<ChatConnection> list = byAdmin.get(adminId);
        return list == null ? 0 : list.size();
    }

    /**
     * Đăng ký kết nối. Trả false nếu admin đã đủ số kết nối tối đa (kiểm tra nguyên tử, vì hai lần bắt tay có thể
     * cùng qua bước kiểm tra sớm).
     */
    public boolean register(ChatConnection connection) {
        int max = properties.getWs().getMaxConnectionsPerAdmin();
        boolean[] added = {false};
        byAdmin.compute(connection.adminId(), (id, list) -> {
            CopyOnWriteArrayList<ChatConnection> current = list != null ? list : new CopyOnWriteArrayList<>();
            if (current.size() < max) {
                current.add(connection);
                added[0] = true;
            }
            return current.isEmpty() ? null : current;
        });
        return added[0];
    }

    public void unregister(ChatConnection connection) {
        byAdmin.computeIfPresent(connection.adminId(), (id, list) -> {
            list.remove(connection);
            return list.isEmpty() ? null : list;
        });
    }

    /** Gửi cùng một chuỗi JSON tới mọi kết nối của các admin trong danh sách (chuyển JSON một lần, dùng cho mọi phiên). */
    public void sendToAdmins(Collection<Long> adminIds, String json) {
        Set<Long> distinct = new LinkedHashSet<>(adminIds);
        for (Long adminId : distinct) {
            List<ChatConnection> connections = byAdmin.get(adminId);
            if (connections == null) {
                continue;
            }
            for (ChatConnection connection : connections) {
                sendTo(connection, json);
            }
        }
    }

    /**
     * Gửi tới một kết nối. Lỗi gửi (đầy bộ đệm, quá thời gian, đứt) chỉ làm đóng chính kết nối đó: dữ liệu đã nằm trong
     * CSDL và client tự kéo bù khi nối lại, nên không bao giờ để lỗi lan sang người khác hay request gốc.
     */
    public void sendTo(ChatConnection connection, String json) {
        if (!connection.isOpen()) {
            unregister(connection);
            return;
        }
        try {
            connection.send(json);
        } catch (Exception e) {
            log.debug("Chat: gửi tới adminId={} lỗi, đóng kết nối: {}", connection.adminId(), e.toString());
            unregister(connection);
            connection.close(SLOW_CONSUMER);
        }
    }

    /** Đóng mọi kết nối của một admin (đăng xuất, thu hồi phiên). Chưa được nối vào luồng đăng xuất hiện có. */
    public void closeAll(Long adminId, CloseStatus status) {
        List<ChatConnection> connections = byAdmin.remove(adminId);
        if (connections != null) {
            connections.forEach(c -> c.close(status));
        }
    }

    /** Ảnh chụp mọi kết nối (cho bộ dọn kết nối im lặng). */
    public List<ChatConnection> snapshot() {
        List<ChatConnection> all = new ArrayList<>();
        byAdmin.values().forEach(all::addAll);
        return all;
    }

    @PreDestroy
    void closeEverything() {
        for (ChatConnection connection : snapshot()) {
            connection.close(CloseStatus.GOING_AWAY);
        }
        byAdmin.clear();
    }
}
