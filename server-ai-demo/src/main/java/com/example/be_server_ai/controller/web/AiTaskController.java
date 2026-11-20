package com.example.be_server_ai.controller.web;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.be_server_ai.dto.BurstRequest;
import com.example.be_server_ai.dto.OverviewView;
import com.example.be_server_ai.dto.TaskCreateRequest;
import com.example.be_server_ai.dto.TaskView;
import com.example.be_server_ai.model.jpa.AiTask;
import com.example.be_server_ai.service.jpa.AiTaskService;
import com.example.be_server_ai.service.llm.NodePool;
import com.example.be_server_ai.service.redis.AiQueueService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Các endpoint demo gọi LLM. Khi chạy thật, Server 1 INSERT task rồi LPUSH ai:queue; ở đây Server AI đóng vai
 * Server 1 để trang HTML tạo task và xem kết quả. Luồng sau đó giống hệt thật: worker BRPOP, giao cho node
 * Colab rảnh, ghi kết quả vào MariaDB, PUBLISH ai:done.
 */
@RestController
@RequestMapping("/web")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AiTaskController {

    private final AiTaskService tasks;
    private final NodePool pool;
    private final AiQueueService queue;
    private final ObjectMapper om;

    public AiTaskController(AiTaskService tasks, NodePool pool, AiQueueService queue, ObjectMapper om) {
        this.tasks = tasks;
        this.pool = pool;
        this.queue = queue;
        this.om = om;
    }

    /** Tạo một task (nl_search hoặc question_check). Trả 202: task chạy nền, hỏi kết quả bằng GET /ai/tasks/{id}. */
    @PostMapping("/ai/tasks")
    public ResponseEntity<TaskView> create(@RequestBody TaskCreateRequest req) {
        String type = req.taskType() == null ? "" : req.taskType().trim().toLowerCase(Locale.ROOT);
        AiTask t = switch (type) {
            case "nl_search" -> tasks.createNlSearch(req.query(), req.createdBy());
            case "question_check" -> tasks.createQuestionCheck(req.questionId(), req.questionRevisionId(), req.stem(),
                    req.optionA(), req.optionB(), req.optionC(), req.optionD(), req.correctLabel(), req.createdBy());
            default -> throw new IllegalArgumentException("taskType phải là nl_search hoặc question_check.");
        };
        return ResponseEntity.accepted().body(TaskView.of(t, om, List.of()));
    }

    /** Tạo nhanh count (1 đến 60, mặc định 10) task nl_search khác nhau để xem chia tải giữa các node. */
    @PostMapping("/ai/tasks/burst")
    public ResponseEntity<Map<String, Object>> burst(@RequestBody(required = false) BurstRequest req) {
        int count = req == null || req.count() == null ? 10 : req.count();
        List<String> ids = tasks.createBurst(count, null).stream().map(AiTask::getTaskId).toList();
        return ResponseEntity.accepted().body(Map.of("count", ids.size(), "taskIds", ids));
    }

    /** 30 task mới nhất (không kèm đề xuất). */
    @GetMapping("/ai/tasks")
    public List<TaskView> recent() {
        return tasks.recent().stream().map(t -> TaskView.of(t, om, List.of())).toList();
    }

    /** Một task kèm các đề xuất sửa (question_check). */
    @GetMapping("/ai/tasks/{id}")
    public ResponseEntity<TaskView> one(@PathVariable("id") String id) {
        return tasks.find(id)
                .map(t -> ResponseEntity.ok(TaskView.of(t, om, tasks.suggestionsOf(id))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Độ dài hàng đợi và trạng thái 5 node. */
    @GetMapping("/ai/overview")
    public OverviewView overview() {
        return new OverviewView(queue.length(), pool.views(), System.currentTimeMillis());
    }
}
