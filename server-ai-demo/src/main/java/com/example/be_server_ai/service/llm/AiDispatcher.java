package com.example.be_server_ai.service.llm;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import com.example.be_server_ai.config.AiProperties;
import com.example.be_server_ai.service.jpa.AiTaskService;
import com.example.be_server_ai.service.redis.AiQueueService;

/**
 * Vòng lặp lấy task khỏi ai:queue và giao cho node. Chạy theo vòng đời Spring (khởi động sau cùng, tắt đầu tiên).
 *
 * <p>Mỗi vòng: chờ đến khi có node nhận thêm được việc (chưa giữ chỗ) -> BRPOP ai:queue -> giữ chỗ trên node ->
 * giao cho một luồng xử lý. Vì chỉ lấy task khi còn node rảnh, khi cả 5 node đầy thì task chờ trong hàng đợi
 * (không bị lấy ra rồi bỏ đó). Tổng số request chạy cùng lúc không vượt tổng max-concurrency của các node.
 * Một luồng nền chạy định kỳ: làm mới trạng thái node trong Redis và quét task kẹt.
 *
 * <p>Tắt máy: dừng lấy việc mới, chờ các lượt gọi Colab đang chạy xong (tối đa read-timeout + 20 giây, phải nhỏ hơn
 * spring.lifecycle.timeout-per-shutdown-phase). Task còn dở thì sweeper đưa về hàng đợi ở lần chạy sau.
 */
@Component
public class AiDispatcher implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(AiDispatcher.class);

    private final NodePool pool;
    private final AiQueueService queue;
    private final AiTaskProcessor processor;
    private final AiTaskService tasks;
    private final AiProperties props;

    private final AtomicBoolean running = new AtomicBoolean();
    private Thread loop;
    private ExecutorService workers;
    private ScheduledExecutorService maintenance;

    public AiDispatcher(NodePool pool, AiQueueService queue, AiTaskProcessor processor, AiTaskService tasks,
                        AiProperties props) {
        this.pool = pool;
        this.queue = queue;
        this.processor = processor;
        this.tasks = tasks;
        this.props = props;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        workers = Executors.newCachedThreadPool(threadFactory("ai-worker-"));
        maintenance = Executors.newSingleThreadScheduledExecutor(threadFactory("ai-maint-"));
        long every = props.getOrchestrator().getSweeperInterval().toMillis();
        maintenance.scheduleWithFixedDelay(this::maintain, 10_000, every, TimeUnit.MILLISECONDS);
        loop = new Thread(this::dispatchLoop, "ai-dispatcher");
        loop.start();
        log.info("AiDispatcher đã chạy: chờ task trong ai:queue");
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        log.info("AiDispatcher dừng nhận việc mới, chờ các lượt gọi Colab đang chạy");
        maintenance.shutdownNow();
        try {
            loop.join(props.getOrchestrator().getQueueBlock().toMillis() + 3_000);
            workers.shutdown();
            long waitSec = props.getLlm().getReadTimeout().toSeconds() + 20;
            if (!workers.awaitTermination(waitSec, TimeUnit.SECONDS)) {
                log.warn("Còn lượt gọi Colab chưa xong sau {} giây, các task đó sẽ do sweeper xử lý", waitSec);
                workers.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            workers.shutdownNow();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    private void dispatchLoop() {
        Duration block = props.getOrchestrator().getQueueBlock();
        while (running.get()) {
            try {
                if (!pool.awaitCapacity(Duration.ofSeconds(1))) {
                    continue;
                }
                String taskId = queue.pop(block);
                if (taskId == null) {
                    continue;
                }
                NodePool.Lease lease = pool.acquire(Duration.ofSeconds(2), Set.of());
                if (lease == null) {
                    queue.push(taskId);   // node vừa đầy do một lượt thử lại khác: trả task về cuối hàng đợi
                    Thread.sleep(500);
                    continue;
                }
                try {
                    workers.execute(() -> processor.process(taskId, lease));
                } catch (RejectedExecutionException e) {
                    lease.release();
                    queue.push(taskId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable e) {
                log.warn("Vòng lấy task gặp lỗi (Redis hoặc CSDL?): {}", e.toString());
                sleepQuietly(2_000);
            }
        }
    }

    private void maintain() {
        try {
            pool.heartbeat();
            List<String> back = tasks.sweepStuck();
            back.forEach(queue::push);
        } catch (Throwable e) {
            log.warn("Job nền gặp lỗi: {}", e.toString());
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static ThreadFactory threadFactory(String prefix) {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
