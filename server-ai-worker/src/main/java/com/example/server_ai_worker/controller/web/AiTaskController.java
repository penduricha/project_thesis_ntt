package com.example.server_ai_worker.controller.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.server_ai_worker.dto.ai.BurstRequest;
import com.example.server_ai_worker.dto.ai.OverviewView;
import com.example.server_ai_worker.dto.ai.TaskCreateRequest;
import com.example.server_ai_worker.dto.ai.TaskView;
import com.example.server_ai_worker.model.jpa.IssueType;
import com.example.server_ai_worker.model.redis.AiTaskStatusCache;
import com.example.server_ai_worker.service.ai.AiTaskService;

/**
 * Endpoint tạo & theo dõi task AI cho chạy thử độc lập (node-colab.html) và quan sát hệ thống.
 */
@RestController
@RequestMapping("/web/ai")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AiTaskController {

    private final AiTaskService service;

    public AiTaskController(AiTaskService service) {
        this.service = service;
    }

    @PostMapping("/tasks")
    public ResponseEntity<TaskView> create(@RequestBody TaskCreateRequest req) {
        return ResponseEntity.accepted().body(service.submit(req));
    }

    @PostMapping("/tasks/burst")
    public ResponseEntity<Map<String, Object>> burst(@RequestBody(required = false) BurstRequest req) {
        int count = req == null || req.count() == null ? 10 : req.count();
        String type = req == null ? null : req.taskType();
        List<String> ids = service.submitBurst(count, type);
        return ResponseEntity.accepted().body(Map.of("count", ids.size(), "taskIds", ids));
    }

    @GetMapping("/tasks")
    public List<TaskView> recent() {
        return service.recentTasks();
    }

    @GetMapping("/tasks/{id}")
    public ResponseEntity<TaskView> one(@PathVariable("id") String id) {
        return service.getTask(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/tasks/{id}/status")
    public ResponseEntity<AiTaskStatusCache> fastStatus(@PathVariable("id") String id) {
        return service.getTaskStatusFast(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/issue-types")
    public List<IssueType> issueTypes() {
        return service.listIssueTypes();
    }

    @GetMapping("/overview")
    public OverviewView overview() {
        return service.overview();
    }
}
