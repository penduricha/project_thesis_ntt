package com.example.server_ai_worker.controller.web;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.server_ai_worker.dto.ai.NodeCheckRequest;
import com.example.server_ai_worker.dto.ai.NodeCheckResponse;
import com.example.server_ai_worker.dto.ai.NodeUpsertRequest;
import com.example.server_ai_worker.dto.ai.NodeView;
import com.example.server_ai_worker.service.ai.AiTaskService;

/**
 * Quản lý 5 cặp URL + token của Colab trong bộ nhớ NodePool (không trả token về trình duyệt).
 */
@RestController
@RequestMapping("/web/ai/nodes")
public class AiNodeController {

    private final AiTaskService service;

    public AiNodeController(AiTaskService service) {
        this.service = service;
    }

    @GetMapping
    public List<NodeView> list() {
        return service.listNodes();
    }

    @PutMapping("/{name}")
    public NodeView upsert(@PathVariable("name") String name, @RequestBody NodeUpsertRequest req) {
        return service.upsertNode(name, req.url(), req.token(), req.enabled(), req.maxConcurrency());
    }

    @DeleteMapping("/{name}")
    public NodeView remove(@PathVariable("name") String name) {
        return service.removeNode(name);
    }

    @PostMapping("/check")
    public NodeCheckResponse check(@RequestBody NodeCheckRequest req) {
        return service.checkNode(req.url(), req.token());
    }
}
