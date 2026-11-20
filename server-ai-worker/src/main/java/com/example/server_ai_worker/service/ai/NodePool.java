package com.example.server_ai_worker.service.ai;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.server_ai_worker.config.AiProperties;
import com.example.server_ai_worker.dto.ai.NodeView;
import com.example.server_ai_worker.enums.AiErrorCode;
import com.example.server_ai_worker.enums.AiNodeStatus;
import com.example.server_ai_worker.model.redis.AiNodeState;
import com.example.server_ai_worker.service.redis.AiNodeStateService;

/**
 * Bộ chọn node trong bộ nhớ (tối đa 5 node colab-1..colab-5): chia tải theo tỷ lệ inFlight/maxConcurrency,
 * ngắt mạch khi lỗi liên tiếp (COOLING), bảo vệ SSRF chặt chẽ, không lưu URL/token vào MariaDB hay Redis.
 */
@Service
public class NodePool {

    private static final Logger log = LoggerFactory.getLogger(NodePool.class);
    private static final Pattern TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9_\\-]{16,128}");
    private static final int NODE_SLOTS = 5;

    private final AiProperties props;
    private final AiNodeStateService stateService;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private int rr;

    public NodePool(AiProperties props, AiNodeStateService stateService) {
        this.props = props;
        this.stateService = stateService;
        for (int i = 1; i <= NODE_SLOTS; i++) {
            nodes.put("colab-" + i, new Node("colab-" + i));
        }
        for (AiProperties.Node cfg : props.getNodes()) {
            if (cfg.getName() == null || cfg.getName().isBlank()) {
                continue;
            }
            Node n = nodes.computeIfAbsent(cfg.getName().trim(), Node::new);
            n.url = clean(cfg.getUrl());
            n.token = cfg.getToken() == null ? "" : cfg.getToken().trim();
            n.maxConcurrency = clampConcurrency(cfg.getMaxConcurrency());
            n.enabled = cfg.isEnabled();
        }
        heartbeat();
        log.info("NodePool: {} node, {} node đã có URL và token từ cấu hình",
                nodes.size(), nodes.values().stream().filter(Node::configured).count());
    }

    public int totalCapacity() {
        lock.lock();
        try {
            return nodes.values().stream().mapToInt(n -> n.maxConcurrency).sum();
        } finally {
            lock.unlock();
        }
    }

    public boolean hasEnabledNode() {
        lock.lock();
        try {
            return nodes.values().stream().anyMatch(n -> n.enabled && n.configured());
        } finally {
            lock.unlock();
        }
    }

    /** Chờ tối đa wait để lấy một lượt chạy trên node rảnh, bỏ qua các node trong exclude. */
    public Lease acquire(Duration wait, Set<String> exclude) throws InterruptedException {
        long deadline = System.nanoTime() + wait.toNanos();
        Lease lease = null;
        AiNodeState snap = null;
        lock.lockInterruptibly();
        try {
            while (true) {
                long now = System.currentTimeMillis();
                Node n = pick(exclude, now);
                if (n != null) {
                    rr = (new ArrayList<>(nodes.values()).indexOf(n) + 1) % nodes.size();
                    n.inFlight++;
                    lease = new Lease(n.name, n.url, n.token);
                    snap = toState(n, now);
                    break;
                }
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    break;
                }
                changed.awaitNanos(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(500)));
            }
        } finally {
            lock.unlock();
        }
        if (snap != null) {
            stateService.save(snap);
        }
        return lease;
    }

    private Node pick(Set<String> exclude, long now) {
        List<Node> list = new ArrayList<>(nodes.values());
        Node best = null;
        double bestLoad = Double.MAX_VALUE;
        for (int k = 0; k < list.size(); k++) {
            Node n = list.get((rr + k) % list.size());
            if (exclude.contains(n.name) || !eligible(n, now)) {
                continue;
            }
            double load = (double) n.inFlight / n.maxConcurrency;
            if (load < bestLoad) {
                best = n;
                bestLoad = load;
            }
        }
        return best;
    }

    private static boolean eligible(Node n, long now) {
        return n.enabled && n.configured() && n.cooldownUntilMs <= now && n.inFlight < n.maxConcurrency;
    }

    private void finish(String name, AiErrorCode failure, String message, boolean success,
                        long latencyMs, double tps, String digest, String runtimeVersion) {
        AiNodeState snap;
        lock.lock();
        try {
            Node n = nodes.get(name);
            if (n == null) {
                return;
            }
            long now = System.currentTimeMillis();
            n.inFlight = Math.max(0, n.inFlight - 1);
            if (failure != null) {
                AiProperties.Orchestrator o = props.getOrchestrator();
                n.failCount++;
                n.lastFailureMs = now;
                n.lastError = shorten(failure.name() + ": " + message);
                long cooldownMs = 0;
                if (failure == AiErrorCode.LLM_UNAUTHORIZED) {
                    cooldownMs = o.getCooldownMax().toMillis();
                } else if (n.failCount >= o.getFailThreshold()) {
                    int shift = Math.min(n.failCount - o.getFailThreshold(), 10);
                    cooldownMs = Math.min(o.getCooldown().toMillis() << shift, o.getCooldownMax().toMillis());
                }
                if (cooldownMs > 0) {
                    n.cooldownUntilMs = now + cooldownMs;
                    log.warn("Node {} nghỉ {} giây sau {} lỗi liên tiếp ({})",
                            name, cooldownMs / 1000, n.failCount, failure);
                }
            } else if (success) {
                n.failCount = 0;
                n.cooldownUntilMs = 0;
                n.lastError = null;
                n.lastSuccessMs = now;
                n.lastLatencyMs = latencyMs;
                if (tps > 0) {
                    n.tokensPerSec = Math.round(tps * 10.0) / 10.0;
                }
                if (digest != null) {
                    n.modelDigest = digest;
                }
                if (runtimeVersion != null) {
                    n.runtimeVersion = runtimeVersion;
                }
            }
            changed.signalAll();
            snap = toState(n, now);
        } finally {
            lock.unlock();
        }
        stateService.save(snap);
    }

    public final class Lease implements AutoCloseable {
        private final String name;
        private final String url;
        private final String token;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Lease(String name, String url, String token) {
            this.name = name;
            this.url = url;
            this.token = token;
        }

        public String name() {
            return name;
        }

        public String url() {
            return url;
        }

        public String token() {
            return token;
        }

        public void success(long latencyMs, double tokensPerSec, String digest, String runtimeVersion) {
            if (closed.compareAndSet(false, true)) {
                finish(name, null, null, true, latencyMs, tokensPerSec, digest, runtimeVersion);
            }
        }

        public void failure(AiErrorCode code, String message) {
            if (closed.compareAndSet(false, true)) {
                finish(name, code, message, false, 0, 0, null, null);
            }
        }

        public void release() {
            if (closed.compareAndSet(false, true)) {
                finish(name, null, null, false, 0, 0, null, null);
            }
        }

        @Override
        public void close() {
            release();
        }
    }

    public List<NodeView> views() {
        lock.lock();
        try {
            long now = System.currentTimeMillis();
            return nodes.values().stream().map(n -> toView(n, now)).toList();
        } finally {
            lock.unlock();
        }
    }

    public NodeView upsert(String name, String url, String token, Boolean enabled, Integer maxConcurrency) {
        String newUrl = (url == null || url.isBlank()) ? null : normalizeAndValidateUrl(url);
        String newToken = (token == null || token.isBlank()) ? null : validateToken(token);
        if (maxConcurrency != null && (maxConcurrency < 1 || maxConcurrency > 8)) {
            throw new IllegalArgumentException("maxConcurrency phải từ 1 đến 8.");
        }
        NodeView view;
        AiNodeState snap;
        lock.lock();
        try {
            Node n = nodes.get(name);
            if (n == null) {
                throw new IllegalArgumentException("Không có node '" + name + "'. Dùng colab-1 đến colab-" + NODE_SLOTS + ".");
            }
            boolean credentialsChanged = false;
            if (newUrl != null && !newUrl.equals(n.url)) {
                n.url = newUrl;
                credentialsChanged = true;
            }
            if (newToken != null && !newToken.equals(n.token)) {
                n.token = newToken;
                credentialsChanged = true;
            }
            if (enabled != null) {
                n.enabled = enabled;
            }
            if (maxConcurrency != null) {
                n.maxConcurrency = maxConcurrency;
            }
            if (credentialsChanged) {
                n.failCount = 0;
                n.cooldownUntilMs = 0;
                n.lastError = null;
                n.modelDigest = null;
                n.runtimeVersion = null;
            }
            changed.signalAll();
            long now = System.currentTimeMillis();
            view = toView(n, now);
            snap = toState(n, now);
        } finally {
            lock.unlock();
        }
        stateService.save(snap);
        log.info("Node {} cập nhật: bật={}, có URL và token={}", name, view.enabled(), view.hasToken());
        return view;
    }

    public NodeView remove(String name) {
        NodeView view;
        AiNodeState snap;
        lock.lock();
        try {
            Node n = nodes.get(name);
            if (n == null) {
                throw new IllegalArgumentException("Không có node '" + name + "'.");
            }
            n.url = "";
            n.token = "";
            n.enabled = false;
            n.failCount = 0;
            n.cooldownUntilMs = 0;
            n.lastError = null;
            n.modelDigest = null;
            n.runtimeVersion = null;
            changed.signalAll();
            long now = System.currentTimeMillis();
            view = toView(n, now);
            snap = toState(n, now);
        } finally {
            lock.unlock();
        }
        stateService.save(snap);
        return view;
    }

    public void heartbeat() {
        List<AiNodeState> states;
        lock.lock();
        try {
            long now = System.currentTimeMillis();
            states = nodes.values().stream().map(n -> toState(n, now)).toList();
        } finally {
            lock.unlock();
        }
        states.forEach(stateService::save);
    }

    public String normalizeAndValidateUrl(String raw) {
        String u = clean(raw);
        URI uri;
        try {
            uri = new URI(u);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("URL không đúng dạng. Ví dụ: https://ten-ngau-nhien.trycloudflare.com");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("URL phải bắt đầu bằng https://");
        }
        String host = uri.getHost();
        String path = uri.getRawPath();
        if (host == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                || (path != null && !path.isEmpty() && !"/".equals(path))
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new IllegalArgumentException("URL chỉ gồm tên miền của node, không có đường dẫn, tham số hay cổng.");
        }
        String h = host.toLowerCase(Locale.ROOT);
        List<String> suffixes = props.getLlm().getAllowedHostSuffixes();
        boolean allowed = suffixes.stream().anyMatch(s -> h.endsWith(s.toLowerCase(Locale.ROOT)));
        if (!allowed) {
            throw new IllegalArgumentException("Chỉ chấp nhận địa chỉ có đuôi: " + String.join(", ", suffixes));
        }
        return "https://" + h;
    }

    public String validateToken(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (!TOKEN_FORMAT.matcher(t).matches()) {
            throw new IllegalArgumentException(
                    "Token không đúng dạng (16 đến 128 ký tự gồm chữ, số, - và _).");
        }
        return t;
    }

    private static String clean(String url) {
        if (url == null) {
            return "";
        }
        return url.trim().replaceAll("(?i)(/api/chat|/health)?/*$", "");
    }

    private static int clampConcurrency(int v) {
        return Math.max(1, Math.min(8, v <= 0 ? 3 : v));
    }

    private static String shorten(String s) {
        if (s == null) {
            return null;
        }
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() > 200 ? one.substring(0, 200) + "…" : one;
    }

    private static AiNodeStatus statusOf(Node n, long now) {
        if (!n.enabled || !n.configured()) {
            return AiNodeStatus.DISABLED;
        }
        if (n.cooldownUntilMs > now) {
            return AiNodeStatus.COOLING;
        }
        if (n.inFlight >= n.maxConcurrency) {
            return AiNodeStatus.FULL;
        }
        return AiNodeStatus.IDLE;
    }

    private static NodeView toView(Node n, long now) {
        return new NodeView(
                n.name,
                n.url,
                n.enabled,
                !n.token.isEmpty(),
                statusOf(n, now),
                n.inFlight,
                n.maxConcurrency,
                n.failCount,
                n.cooldownUntilMs,
                n.lastLatencyMs,
                n.tokensPerSec,
                n.modelDigest,
                n.runtimeVersion,
                n.lastError);
    }

    private static AiNodeState toState(Node n, long now) {
        AiNodeState s = new AiNodeState(n.name, statusOf(n, now), n.maxConcurrency);
        s.setInFlight(n.inFlight);
        s.setFailCount(n.failCount);
        s.setCooldownUntilMs(n.cooldownUntilMs);
        s.setLastSuccessMs(n.lastSuccessMs == 0 ? null : n.lastSuccessMs);
        s.setLastFailureMs(n.lastFailureMs == 0 ? null : n.lastFailureMs);
        s.setTokensPerSec(n.tokensPerSec == 0 ? null : n.tokensPerSec);
        s.setUpdatedAtMs(now);
        return s;
    }

    private static final class Node {
        final String name;
        String url = "";
        String token = "";
        boolean enabled;
        int maxConcurrency = 3;
        int inFlight;
        int failCount;
        long cooldownUntilMs;
        long lastSuccessMs;
        long lastFailureMs;
        long lastLatencyMs;
        double tokensPerSec;
        String modelDigest;
        String runtimeVersion;
        String lastError;

        Node(String name) {
            this.name = name;
        }

        boolean configured() {
            return !url.isEmpty() && !token.isEmpty();
        }
    }
}
