package com.example.be_server_ai.controller.web;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.be_server_ai.dto.NodeCheckRequest;
import com.example.be_server_ai.dto.NodeCheckResponse;
import com.example.be_server_ai.dto.NodeUpsertRequest;
import com.example.be_server_ai.dto.NodeView;
import com.example.be_server_ai.service.llm.NodePool;
import com.example.be_server_ai.service.llm.OllamaClient;

/**
 * Quản lý 5 cặp URL + token của Colab (trang node-colab.html gọi vào đây).
 * URL và token chỉ nằm trong bộ nhớ NodePool; không trả token về trình duyệt, không ghi MariaDB/Redis.
 */
@RestController
@RequestMapping("/web")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AiNodeController {

    private final NodePool pool;
    private final OllamaClient ollama;

    public AiNodeController(NodePool pool, OllamaClient ollama) {
        this.pool = pool;
        this.ollama = ollama;
    }

    /** Danh sách node (colab-1 đến colab-5) kèm trạng thái. Không có token. */
    @GetMapping("/ai/nodes")
    public List<NodeView> list() {
        return pool.views();
    }

    /** Đặt URL, token, bật/tắt cho một node. Bỏ token (hoặc để trống) nghĩa là giữ token cũ. */
    @PutMapping("/ai/nodes/{name}")
    public NodeView upsert(@PathVariable("name") String name, @RequestBody NodeUpsertRequest req) {
        return pool.upsert(name, req.url(), req.token(), req.enabled(), req.maxConcurrency());
    }

    /** Xóa URL và token của node, tắt node. */
    @DeleteMapping("/ai/nodes/{name}")
    public NodeView remove(@PathVariable("name") String name) {
        return pool.remove(name);
    }

    /**
     * Thử một cặp URL + token trước khi lưu: GET /health rồi POST /api/chat với thân rỗng (không nạp mô hình).
     * Chỉ nhận địa chỉ *.trycloudflare.com để Server AI không bị lợi dụng gọi vào địa chỉ nội bộ.
     */
    @PostMapping("/ai/nodes/check")
    public NodeCheckResponse check(@RequestBody NodeCheckRequest req) {
        String url = pool.normalizeAndValidateUrl(req.url());
        String token = pool.validateToken(req.token());
        return ollama.check(url, token);
    }
}
