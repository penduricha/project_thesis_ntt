package com.example.server_ai_worker.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gắn khối "ai:" của application.yml.
 */
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private final Llm llm = new Llm();
    private List<Node> nodes = new ArrayList<>();
    private final Orchestrator orchestrator = new Orchestrator();
    private final NlSearch nlSearch = new NlSearch();
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

    public NlSearch getNlSearch() {
        return nlSearch;
    }

    public Demo getDemo() {
        return demo;
    }

    public static class Llm {
        private String model = "qwen2.5:14b-instruct-q4_K_M";
        private String promptVersion = "v2";
        private String questionCheckMode = "few_cot";
        private int fewShotCount = 3;
        private boolean rulePreflight = true;
        private boolean suggestDifficulty = false;
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(120);
        private String keepAlive = "30m";
        private List<String> allowedHostSuffixes = new ArrayList<>(List.of(".trycloudflare.com"));
        // num_ctx của hai loại task phải bằng nhau: Ollama nạp lại mô hình khi num_ctx đổi.
        private final Options questionCheck = new Options(8192, 700, 0.0, 42);
        private final Options nlSearch = new Options(8192, 350, 0.0, 42);

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

        public String getQuestionCheckMode() {
            return questionCheckMode;
        }

        public void setQuestionCheckMode(String questionCheckMode) {
            this.questionCheckMode = questionCheckMode;
        }

        public int getFewShotCount() {
            return fewShotCount;
        }

        public void setFewShotCount(int fewShotCount) {
            this.fewShotCount = fewShotCount;
        }

        public boolean isRulePreflight() {
            return rulePreflight;
        }

        public void setRulePreflight(boolean rulePreflight) {
            this.rulePreflight = rulePreflight;
        }

        public boolean isSuggestDifficulty() {
            return suggestDifficulty;
        }

        public void setSuggestDifficulty(boolean suggestDifficulty) {
            this.suggestDifficulty = suggestDifficulty;
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
        private Integer seed;

        public Options() {
        }

        public Options(int numCtx, int numPredict, double temperature, Integer seed) {
            this.numCtx = numCtx;
            this.numPredict = numPredict;
            this.temperature = temperature;
            this.seed = seed;
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

        public Integer getSeed() {
            return seed;
        }

        public void setSeed(Integer seed) {
            this.seed = seed;
        }
    }

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

    public static class NlSearch {
        private List<SubjectCatalog> defaultSubjects = new ArrayList<>();

        public List<SubjectCatalog> getDefaultSubjects() {
            return defaultSubjects;
        }

        public void setDefaultSubjects(List<SubjectCatalog> defaultSubjects) {
            this.defaultSubjects = defaultSubjects;
        }
    }

    public static class SubjectCatalog {
        private String subjectCode;
        private String subjectName;
        private List<String> topics = new ArrayList<>();

        public String getSubjectCode() {
            return subjectCode;
        }

        public void setSubjectCode(String subjectCode) {
            this.subjectCode = subjectCode;
        }

        public String getSubjectName() {
            return subjectName;
        }

        public void setSubjectName(String subjectName) {
            this.subjectName = subjectName;
        }

        public List<String> getTopics() {
            return topics;
        }

        public void setTopics(List<String> topics) {
            this.topics = topics;
        }
    }

    public static class Demo {
        private long createdBy = 1L;
        private boolean seedIssueTypes = true;
        private int evalConcurrency = 2;

        public boolean isSeedIssueTypes() {
            return seedIssueTypes;
        }

        public void setSeedIssueTypes(boolean seedIssueTypes) {
            this.seedIssueTypes = seedIssueTypes;
        }

        public int getEvalConcurrency() {
            return evalConcurrency;
        }

        public void setEvalConcurrency(int evalConcurrency) {
            this.evalConcurrency = evalConcurrency;
        }

        public long getCreatedBy() {
            return createdBy;
        }

        public void setCreatedBy(long createdBy) {
            this.createdBy = createdBy;
        }
    }
}
