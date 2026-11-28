package com.example.be_web_admin;

import java.nio.file.Files;
import java.nio.file.Path;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.rest.RepositoryRestMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

// This application authenticates through its own JWT/Redis filters, not Boot's default
// in-memory user. Keep security auto-configuration enabled; exclude only that user service.
@SpringBootApplication(exclude = {
        RepositoryRestMvcAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
})
@EnableJpaRepositories(basePackages = {
		"com.example.be_web_admin.repository.jpa",
		"com.example.be_web_admin.chat.repository"
})
@EnableRedisRepositories(basePackages = "com.example.be_web_admin.repository.redis")
@EntityScan(basePackages = {
		"com.example.be_web_admin.model.jpa",
		"com.example.be_web_admin.chat.model"
})
public class BeWebAdminApplication {

	public static void main(String[] args) throws RuntimeException {
		String envDir = Files.isRegularFile(Path.of(".env"))
				? "./"
				: Files.isRegularFile(Path.of("be-web-admin/.env"))
						? "be-web-admin"
						: "./";
		Dotenv dotenv = Dotenv.configure()
				.directory(envDir)
				.ignoreIfMissing()
				.load();
		dotenv.entries(Dotenv.Filter.DECLARED_IN_ENV_FILE).forEach(entry -> {
			String key = entry.getKey();
			String val = entry.getValue();
			if (System.getProperty(key) == null && (System.getenv(key) == null || !val.isEmpty())) {
				System.setProperty(key, val);
			}
		});
		SpringApplication.run(BeWebAdminApplication.class, args);
	}
}
