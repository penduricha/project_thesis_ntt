package com.example.server_ai_worker.service.ai;

import java.util.List;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.example.server_ai_worker.config.AiProperties;
import com.example.server_ai_worker.enums.AiErrorCode;
import com.example.server_ai_worker.model.jpa.AiTask;
import com.example.server_ai_worker.repository.jpa.AiTaskRepository;
import com.example.server_ai_worker.service.redis.AiQueueService;

/**
 * Vòng lặp điều phối trung tâm: giữ Semaphore đúng bằng sức chứa worker trước khi rút task từ ai:queue,
 * tránh rút cạn hàng đợi Redis vào hàng đợi bộ nhớ khi toàn bộ worker đang bận.
 * Định kỳ quét các task bị kẹt ở running quá lâu và làm mới heartbeat node trên Redis.
 */
@Component
public class AiDispatcher implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(AiDispatcher.class);

    private final AiQueueService queue;
    private final AiTaskProcessor processor;
    private final AiTaskRepository tasks;
    private final NodePool nodePool;
    private final AiProperties props;
    private final StringRedisTemplate redis;
    private String pendingCursor = "";

    private volatile boolean running;
    private ExecutorService workerPool;
    private Semaphore workerSlots;
    private ScheduledExecutorService sweeper;
    private Thread loopThread;

    public AiDispatcher(AiQueueService queue,
                        AiTaskProcessor processor,
                        AiTaskRepository tasks,
                        NodePool nodePool,
                        AiProperties props,
                        StringRedisTemplate redis) {
        this.queue = queue;
        this.processor = processor;
        this.tasks = tasks;
        this.nodePool = nodePool;
        this.props = props;
        this.redis = redis;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        int capacity = Math.max(4, nodePool.totalCapacity());
        AtomicInteger n = new AtomicInteger();
        this.workerSlots = new Semaphore(capacity);
        this.workerPool = Executors.newFixedThreadPool(capacity,
                r -> new Thread(r, "ai-worker-" + n.incrementAndGet()));
        this.sweeper = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "ai-sweeper"));
        this.running = true;

        this.loopThread = new Thread(this::runLoop, "ai-dispatcher");
        this.loopThread.start();

        long sweepSec = props.getOrchestrator().getSweeperInterval().toSeconds();
        this.sweeper.scheduleWithFixedDelay(this::sweepStuck, sweepSec, sweepSec, TimeUnit.SECONDS);
        log.info("AiDispatcher đã bật với sức chứa {} worker song song", capacity);
    }

    private void runLoop() {
        while (running) {
            try {
                if (!nodePool.hasEnabledNode()) {
                    Thread.sleep(1000);
                    continue;
                }
                workerSlots.acquire();
                boolean handedOff = false;
                try {
                    if (!running) {
                        break;
                    }
                    String taskId = queue.pop(props.getOrchestrator().getQueueBlock());
                    if (taskId == null) {
                        continue;
                    }
                    if (!running) {
                        queue.push(taskId);
                        break;
                    }
                    try {
                        workerPool.execute(() -> {
                            try {
                                processor.process(taskId);
                            } finally {
                                workerSlots.release();
                            }
                        });
                        handedOff = true;
                    } catch (RejectedExecutionException rejected) {
                        queue.push(taskId);
                    }
                } finally {
                    if (!handedOff) {
                        workerSlots.release();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("Lỗi ở vòng lặp AiDispatcher (sẽ chạy tiếp sau 2s): {}", e.getMessage());
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    void sweepStuck() {
        try {
            nodePool.heartbeat();
            long stuckSec = props.getOrchestrator().getStuckAfter().toSeconds();
            int maxAttempts = Math.min(5, Math.max(1, props.getOrchestrator().getMaxAttempts()));
            List<String> stuckIds = tasks.findStuckRunningIds(stuckSec);
            for (String id : stuckIds) {
                AiTask t = tasks.findById(id).orElse(null);
                if (t == null) {
                    continue;
                }
                if (t.getAttemptCount() < maxAttempts) {
                    if (tasks.requeue(id, AiErrorCode.LLM_TIMEOUT.name(),
                            "Tác vụ kẹt ở running quá " + stuckSec + "s, quét trả lại hàng đợi",
                            maxAttempts) > 0) {
                        queue.push(id);
                        log.warn("Quét task kẹt {} (lần {}), đẩy lại hàng đợi", id, t.getAttemptCount());
                    }
                } else {
                    if (tasks.markFailed(id, AiErrorCode.MAX_ATTEMPTS.name(),
                            "Tác vụ kẹt sau khi hết " + maxAttempts + " lần thử", null, null) > 0) {
                        queue.publishDone(id, "failed");
                        log.warn("Quét task kẹt {} hết lượt thử -> failed", id);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Quét tác vụ kẹt lỗi: {}", e.getMessage());
        }
        try {
            recoverPending();
        } catch (Exception e) {
            log.warn("Đối soát tác vụ đang chờ lỗi: {}", e.getMessage());
        }
    }

    /**
     * The DB is authoritative. A task can be lost after DB commit or between Redis pop and DB
     * claim. Re-enqueue old pending tasks, rate-limited across instances. Duplicate queue entries
     * cannot be claimed concurrently while a task stays running: process() uses an atomic
     * pending->running claim. Entries may accumulate during a long backlog; the TTL bounds the
     * recovery enqueue rate and recovers a crash after acquiring the marker but before push().
     */
    void recoverPending() {
        List<String> ids = tasks.findPendingForRecovery(pendingCursor);
        if (ids.isEmpty()) {
            pendingCursor = "";
            return;
        }
        long seconds = Math.max(120L, props.getOrchestrator().getSweeperInterval().toSeconds() * 2);
        for (String id : ids) {
            Boolean claimed = redis.opsForValue().setIfAbsent("ai:pending-recovery:" + id, "1", Duration.ofSeconds(seconds));
            if (claimed == null) throw new IllegalStateException("Redis returned no recovery marker result");
            if (claimed) queue.push(id);
        }
        pendingCursor = ids.get(ids.size() - 1);
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (loopThread != null) {
            loopThread.interrupt();
        }
        if (sweeper != null) {
            sweeper.shutdownNow();
        }
        if (workerPool != null) {
            workerPool.shutdown();
            long waitSec = Math.max(10L, props.getLlm().getReadTimeout().toSeconds() + 5L);
            try {
                if (!workerPool.awaitTermination(waitSec, TimeUnit.SECONDS)) {
                    log.warn("Một số worker chưa xong sau {}s, tiến hành ngắt để trả task về hàng đợi", waitSec);
                    workerPool.shutdownNow();
                    workerPool.awaitTermination(5, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                workerPool.shutdownNow();
            }
        }
        log.info("AiDispatcher đã tắt");
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
