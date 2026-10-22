plugins {
	java
	id("org.springframework.boot") version "3.3.0" // Đã sửa từ 4.1.0 thành 3.3.0 (bản stable mới nhất)
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21) // Giữ nguyên cấu hình JDK 21
	}
}

repositories {
	mavenCentral()
}

dependencies {
	// Web & Security
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-security")
	//implementation("org.springframework.boot:spring-boot-persistence")

	// Database & JPA
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	runtimeOnly("org.mariadb.jdbc:mariadb-java-client") // Chỉ giữ lại runtimeOnly, xóa implementation bị trùng

	// Redis & Session
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
	implementation("org.springframework.boot:spring-boot-starter-data-rest")
	implementation("org.springframework.session:spring-session-data-redis") // Sửa tên dependency
	implementation("org.springframework.session:spring-session-jdbc")      // Sửa tên dependency

	// Cache & Hibernate
	implementation("org.hibernate.orm:hibernate-jcache")
	implementation("org.ehcache:ehcache:3.10.8:jakarta")

	// JWT
	implementation("io.jsonwebtoken:jjwt-api:0.12.5")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")

	// Utilities
	implementation("io.github.cdimascio:dotenv-java:3.1.0")
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")

	// Development
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")

	// Testing (Gộp chung và sửa các starter không hợp lệ)
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("io.projectreactor:reactor-test") // Cần cho reactive redis test
	testCompileOnly("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testAnnotationProcessor("org.projectlombok:lombok")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
