//package com.example.be_web_admin.controller.rabbitMQ;
//
//import org.springframework.amqp.core.Queue;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class RabbitMQConfig {
//
//    // Tên của hàng đợi
//    public static final String ADMIN_QUEUE = "admin_insert_queue";
//
//    @Bean
//    public Queue adminQueue() {
//        // Tạo queue bền vững (durable = true). Tin nhắn sẽ không mất nếu RabbitMQ bị restart.
//        return new Queue(ADMIN_QUEUE, true);
//    }
//}