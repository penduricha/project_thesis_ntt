package com.example.server_ai_worker;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Điểm khởi động server-ai-worker (Server 2). Đọc file .env ở thư mục chạy vào thuộc tính hệ thống
 * để application.yml dùng được ${IP_ADDRESS}, ${REDIS_PORT}, ${MARIADB_DBS}...
 *
 * <p>Redis cache/state dùng StringRedisTemplate trực tiếp (tránh tạo Set keyspace thừa từ @RedisHash).
 */
@SpringBootApplication
@EnableJpaRepositories(basePackages = {
        "com.example.server_ai_worker.repository.jpa",
        "com.example.server_ai_worker.demo"
})
@EntityScan(basePackages = {
        "com.example.server_ai_worker.model.jpa",
        "com.example.server_ai_worker.demo"
})
public class ServerAiWorkerApplication {
    //http://localhost:8082/api/node-colab.html
    //http://localhost:8082/api/demo-eval.html
    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));
        SpringApplication.run(ServerAiWorkerApplication.class, args);
    }
}
