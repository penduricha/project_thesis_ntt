package com.example.be_server_ai.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gắn khối "ai:" của application.yml. Tên thuộc tính khớp file yml đã gửi (relaxed binding:
 * question-check -> questionCheck, num-ctx -> numCtx).
 */
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private final Llm llm = new Llm();
    private List<Node> nodes = new ArrayList<>();
    private final Orchestrator orchestrator = new Orchestrator();
    private final Demo demo = new Demo();

    public Llm getLlm() {
        return llm;
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public void setNodes(List<Node> nodes) {
        this.nodes = nodes;
    }

    public Orchestrator getOrchestrator() {
        return orchestrator;
    }

    public Demo getDemo() {
        return demo;
    }

    public static class Llm {
        private String model = "qwen2.5:14b-instruct-q4_K_M";
        private String promptVersion = "v1";
        private Duration connectTimeout = Duration.ofSeconds(5);
        // Dưới 125 giây (Proxy Read Timeout của Cloudflare).
        private Duration readTimeout = Duration.ofSeconds(120);
        private String keepAlive = "30m";
        // Chỉ chấp nhận URL node có đuôi miền này (chống bị lợi dụng gọi địa chỉ nội bộ).
        private List<String> allowedHostSuffixes = new ArrayList<>(List.of(".trycloudflare.com"));
        // num_ctx của hai loại task PHẢI bằng nhau: Ollama nạp lại mô hình khi num_ctx đổi.
        private final Options questionCheck = new Options(8192, 1024, 0.0);
        private final Options nlSearch = new Options(8192, 512, 0.0);

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getPromptVersion() {
            return promptVersion;
        }

        public void setPromptVersion(String promptVersion) {
            this.promptVersion = promptVersion;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }

        public String getKeepAlive() {
            return keepAlive;
        }

        public void setKeepAlive(String keepAlive) {
            this.keepAlive = keepAlive;
        }

        public List<String> getAllowedHostSuffixes() {
            return allowedHostSuffixes;
        }

        public void setAllowedHostSuffixes(List<String> allowedHostSuffixes) {
            this.allowedHostSuffixes = allowedHostSuffixes;
        }

        public Options getQuestionCheck() {
            return questionCheck;
        }

        public Options getNlSearch() {
            return nlSearch;
        }
    }

    public static class Options {
        private int numCtx;
        private int numPredict;
        private double temperature;

        public Options() {
        }

        public Options(int numCtx, int numPredict, double temperature) {
            this.numCtx = numCtx;
            this.numPredict = numPredict;
            this.temperature = temperature;
        }

        public int getNumCtx() {
            return numCtx;
        }

        public void setNumCtx(int numCtx) {
            this.numCtx = numCtx;
        }

        public int getNumPredict() {
            return numPredict;
        }

        public void setNumPredict(int numPredict) {
            this.numPredict = numPredict;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }
    }

    /** Một node = một cặp URL + token (một tài khoản Colab). URL/token có thể trống: nhập sau qua trang HTML. */
    public static class Node {
        private String name;
        private String url = "";
        private String token = "";
        private int maxConcurrency = 3;
        private boolean enabled;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public int getMaxConcurrency() {
            return maxConcurrency;
        }

        public void setMaxConcurrency(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Orchestrator {
        private int maxAttempts = 5;
        private Duration maxWait = Duration.ofMinutes(10);
        private int failThreshold = 3;
        private Duration cooldown = Duration.ofSeconds(60);
        private Duration cooldownMax = Duration.ofMinutes(5);
        private Duration queueBlock = Duration.ofSeconds(5);
        private Duration sweeperInterval = Duration.ofSeconds(60);
        private Duration stuckAfter = Duration.ofMinutes(5);

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getMaxWait() {
            return maxWait;
        }

        public void setMaxWait(Duration maxWait) {
            this.maxWait = maxWait;
        }

        public int getFailThreshold() {
            return failThreshold;
        }

        public void setFailThreshold(int failThreshold) {
            this.failThreshold = failThreshold;
        }

        public Duration getCooldown() {
            return cooldown;
        }

        public void setCooldown(Duration cooldown) {
            this.cooldown = cooldown;
        }

        public Duration getCooldownMax() {
            return cooldownMax;
        }

        public void setCooldownMax(Duration cooldownMax) {
            this.cooldownMax = cooldownMax;
        }

        public Duration getQueueBlock() {
            return queueBlock;
        }

        public void setQueueBlock(Duration queueBlock) {
            this.queueBlock = queueBlock;
        }

        public Duration getSweeperInterval() {
            return sweeperInterval;
        }

        public void setSweeperInterval(Duration sweeperInterval) {
            this.sweeperInterval = sweeperInterval;
        }

        public Duration getStuckAfter() {
            return stuckAfter;
        }

        public void setStuckAfter(Duration stuckAfter) {
            this.stuckAfter = stuckAfter;
        }
    }

    /** Chỉ dùng cho demo: Server AI đóng vai Server 1 để tạo task. */
    public static class Demo {
        // admin_id có thật trong ntt_admins (khóa ngoại created_by).
        private long createdBy = 1L;
        // Bảng ntt_issue_types rỗng (CSDL demo do ddl-auto: update tạo) thì nạp sẵn vài loại lỗi. Tắt khi dùng CSDL của Server 1.
        private boolean seedIssueTypes = true;

        public boolean isSeedIssueTypes() {
            return seedIssueTypes;
        }

        public void setSeedIssueTypes(boolean seedIssueTypes) {
            this.seedIssueTypes = seedIssueTypes;
        }

        public long getCreatedBy() {
            return createdBy;
        }

        public void setCreatedBy(long createdBy) {
            this.createdBy = createdBy;
        }
    }
}
