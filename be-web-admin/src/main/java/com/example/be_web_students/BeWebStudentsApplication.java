package com.example.be_web_students;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.example.be_web_students.repository.jpa")
@EnableRedisRepositories(basePackages = "com.example.be_web_students.repository.cached")
@EntityScan(basePackages = "com.example.be_web_students.model.jpa")
public class BeWebStudentsApplication {

	public static void main(String[] args) throws RuntimeException {
		Dotenv dotenv = Dotenv.load();
		dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));
		SpringApplication.run(BeWebStudentsApplication.class, args);
	}
}
