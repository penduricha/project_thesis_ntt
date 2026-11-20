package com.example.be_web_admin.chat;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Ánh xạ khối "chat:" trong application.yaml. Mọi con số của module chat nằm ở đây, không viết cứng trong mã.
 * Giá trị mặc định bên dưới khớp với bảng cấu hình ở tài liệu thiết kế (mục 13 và 14).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "chat")
public class ChatProperties {

    private Ws ws = new Ws();
    private Message message = new Message();
    private Group group = new Group();
    private Page page = new Page();
    private Rate rate = new Rate();
    private Event event = new Event();

    @Getter
    @Setter
    public static class Ws {
        /**
         * Mẫu Origin được phép bắt tay WebSocket (hỗ trợ ký tự * ở cổng, ví dụ http://localhost:*). Ứng dụng mobile gốc
         * không gửi Origin nên luôn được qua. Flutter web khi chạy thử dùng cổng ngẫu nhiên nên cần mẫu, không liệt kê
         * từng cổng cố định. Môi trường thật: thêm đúng tên miền https của trang web admin vào đây.
         */
        private List<String> allowedOriginPatterns =
                new ArrayList<>(List.of("http://localhost:*", "http://127.0.0.1:*"));
        private int ticketTtlSeconds = 30;
        private int pingIntervalSeconds = 25;
        /** Đóng phiên nếu không thấy khung nào quá thời gian này (nhỏ hơn mốc 60 giây của Nginx). */
        private int idleTimeoutSeconds = 50;
        private int sweepIntervalMs = 10_000;
        private int maxConnectionsPerAdmin = 5;
        private int sendTimeLimitMs = 5_000;
        private int sendBufferBytes = 65_536;
        /** Khung client gửi lên (chỉ có ping) tối đa bấy nhiêu byte, quá thì đóng mã 4002. */
        private int maxClientFrameBytes = 1_024;
        /** Bộ đệm khung văn bản của container WebSocket (phải lớn hơn maxClientFrameBytes để khung quá khổ bị đóng mã 4002, không phải 1009). */
        private int containerTextBufferBytes = 4_096;
    }

    @Getter
    @Setter
    public static class Message {
        /** Độ dài tối đa của nội dung tin, đếm theo ký tự Unicode (code point). */
        private int maxLength = 2000;
    }

    @Getter
    @Setter
    public static class Group {
        /** Số thành viên tối thiểu của nhóm, gồm cả chủ nhóm. */
        private int minMembers = 3;
        /** Số thành viên tối đa của nhóm, gồm cả chủ nhóm. */
        private int maxMembers = 50;
        private int maxNameLength = 100;
    }

    @Getter
    @Setter
    public static class Page {
        private int defaultSize = 30;
        private int maxSize = 100;
    }

    @Getter
    @Setter
    public static class Rate {
        /** Gửi tin: tối đa "messages" tin trong mỗi "windowSeconds" giây. */
        private int messages = 20;
        private int windowSeconds = 10;
        /** Xin vé WebSocket: số lần mỗi phút. */
        private int ticketsPerMinute = 10;
        /** Tìm admin: số lần mỗi 10 giây. */
        private int searchesPer10Seconds = 20;
        /** Mở chat 1-1 hoặc tạo nhóm: số lần mỗi phút. */
        private int newConversationsPerMinute = 10;
    }

    @Getter
    @Setter
    public static class Event {
        /** Số luồng phát sự kiện WebSocket. Để 1 cho sự kiện của cùng một cuộc trò chuyện giữ đúng thứ tự commit. */
        private int threads = 1;
        private int queueCapacity = 2_000;
    }
}
