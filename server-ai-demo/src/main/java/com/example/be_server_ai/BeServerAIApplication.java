package com.example.be_server_ai;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

/**
 * Điểm khởi động Server AI. Đọc file .env ở thư mục chạy (thư mục gốc dự án) vào thuộc tính hệ thống để
 * application.yml dùng được ${IP_ADDRESS}, ${REDIS_PORT}, ${MARIADB_DBS}... Nếu không có .env thì dùng biến môi trường.
 *
 * <p>JPA (MariaDB) và Redis dùng hai gói repository riêng; entity JPA nằm ở model.jpa, còn model.redis
 * (@RedisHash) do gói repository.redis quét.
 */
@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.example.be_server_ai.repository.jpa")
@EnableRedisRepositories(basePackages = "com.example.be_server_ai.repository.redis")
@EntityScan(basePackages = "com.example.be_server_ai.model.jpa")
public class BeServerAIApplication {

    public static void main(String[] args) throws RuntimeException {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));
        SpringApplication.run(BeServerAIApplication.class, args);
    }
}
