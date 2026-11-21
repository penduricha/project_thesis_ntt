package com.example.server_ai_worker.demo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DEMO ONLY — REST cho trang demo-eval.html (đánh giá chất lượng AI trên bộ 1000 câu demo).
 * Không ghi vào bảng ER v4 thật; toàn bộ dữ liệu nằm ở ntt_demo_questions / ntt_demo_eval_*.
 */
@RestController
@RequestMapping("/web/ai/demo")
public class DemoEvalController {

    private final DemoQuestionRepository questions;
    private final DemoEvalRunRepository runs;
    private final DemoEvalItemRepository items;
    private final DemoQuestionSeeder seeder;
    private final DemoEvalService service;

    public DemoEvalController(DemoQuestionRepository questions,
                              DemoEvalRunRepository runs,
                              DemoEvalItemRepository items,
                              DemoQuestionSeeder seeder,
                              DemoEvalService service) {
        this.questions = questions;
        this.runs = runs;
        this.items = items;
        this.seeder = seeder;
        this.service = service;
    }

    // ------------------------------------------------------------------
    // Câu hỏi demo
    // ------------------------------------------------------------------
    public record QuestionView(Long id, String extId, String subjectCode, String subjectName,
                               String topicName, String stem,
                               String optionA, String optionB, String optionC, String optionD,
                               Integer correctIdx, Integer trueCorrectIdx, String correctLabel,
                               Integer difficulty, String explanation, String goldIssueType) {
        static QuestionView of(DemoQuestion q) {
            return new QuestionView(q.getId(), q.getExtId(), q.getSubjectCode(), q.getSubjectName(),
                    q.getTopicName(), q.getStem(), q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getOptionD(),
                    q.getCorrectIdx(), q.getTrueCorrectIdx(), q.getCorrectLabel(),
                    q.getDifficulty(), q.getExplanation(), q.getGoldIssueType());
        }
    }

    public record PageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        static <T> PageView<T> of(Page<T> p) {
            return new PageView<>(p.getContent(), p.getNumber(), p.getSize(),
                    p.getTotalElements(), p.getTotalPages());
        }
    }

    @GetMapping("/questions")
    public PageView<QuestionView> list(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "10") int size,
                                       @RequestParam(required = false) String subject,
                                       @RequestParam(required = false) String label) {
        int safeSize = Math.max(1, Math.min(size, 50));
        Pageable pageable = PageRequest.of(Math.max(0, page), safeSize);
        boolean hasSubject = subject != null && !subject.isBlank();
        boolean hasLabel = label != null && !label.isBlank();
        Page<DemoQuestion> result;
        if (hasSubject && hasLabel) {
            result = questions.findBySubjectCodeAndGoldIssueType(subject.trim(), label.trim(), pageable);
        } else if (hasSubject) {
            result = questions.findBySubjectCode(subject.trim(), pageable);
        } else if (hasLabel) {
            result = questions.findByGoldIssueType(label.trim(), pageable);
        } else {
            result = questions.findAll(pageable);
        }
        return PageView.of(result.map(QuestionView::of));
    }

    @GetMapping("/questions/{extId}")
    public ResponseEntity<QuestionView> one(@PathVariable("extId") String extId) {
        return questions.findByExtId(extId)
                .map(q -> ResponseEntity.ok(QuestionView.of(q)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/questions/summary")
    public Map<String, Object> summary() {
        Map<String, Long> byLabel = new java.util.LinkedHashMap<>();
        for (Object[] row : questions.countByGoldLabel()) {
            byLabel.put((String) row[0], (Long) row[1]);
        }
        Map<String, Long> byDomain = new java.util.LinkedHashMap<>();
        for (Object[] row : questions.countBySubjectCode()) {
            byDomain.put((String) row[0], (Long) row[1]);
        }
        List<Map<String, Object>> domainLabel = new ArrayList<>();
        for (Object[] row : questions.countBySubjectAndLabel()) {
            domainLabel.add(Map.of(
                    "subjectCode", row[0],
                    "label", row[1],
                    "count", row[2]));
        }
        return Map.of(
                "total", questions.count(),
                "byLabel", byLabel,
                "byDomain", byDomain,
                "byDomainLabel", domainLabel);
    }

    // ------------------------------------------------------------------
    // Seed / reseed
    // ------------------------------------------------------------------
    @PostMapping("/seed")
    public ResponseEntity<Map<String, Object>> seed(@RequestParam(defaultValue = "false") boolean force) {
        if (force) {
            if (runs.countRunning() > 0) {
                Map<String, Object> body = new java.util.HashMap<>();
                body.put("error", "Đang có run chạy — không thể nạp lại câu hỏi.");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
            }
            items.deleteAllInBatch();
            runs.deleteAllInBatch();
            int loaded = seeder.reseed();
            return ResponseEntity.ok(Map.of("loaded", loaded, "forced", true));
        }
        long existing = questions.count();
        if (existing > 0) {
            return ResponseEntity.ok(Map.of("loaded", 0, "already", existing));
        }
        int loaded = seeder.reseed();
        return ResponseEntity.ok(Map.of("loaded", loaded, "forced", false));
    }

    // ------------------------------------------------------------------
    // Runs
    // ------------------------------------------------------------------
    public record StartRunRequest(String mode, Integer limit) {
    }

    public record RunView(long runId, String mode, String status, int totalQuestions,
                          int processedCount, int correctCount, int errorCount,
                          Double accuracy, Double macroF1, Double microF1,
                          java.time.Instant startedAt, java.time.Instant finishedAt, String notes) {
        static RunView of(DemoEvalRun r) {
            return new RunView(r.getId(), r.getMode(), r.getStatus(), r.getTotalQuestions(),
                    r.getProcessedCount(), r.getCorrectCount(), r.getErrorCount(),
                    r.getAccuracy(), r.getMacroF1(), r.getMicroF1(),
                    r.getStartedAt(), r.getFinishedAt(), r.getNotes());
        }
    }

    @PostMapping("/runs")
    public ResponseEntity<?> start(@RequestBody(required = false) StartRunRequest req) {
        try {
            DemoEvalRun run = service.start(req == null ? null : req.mode(), req == null ? null : req.limit());
            return ResponseEntity.accepted().body(RunView.of(run));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/runs")
    public List<RunView> recentRuns() {
        return runs.findTop10ByOrderByStartedAtDesc().stream().map(RunView::of).toList();
    }

    @GetMapping("/runs/{id}")
    public ResponseEntity<RunView> oneRun(@PathVariable("id") long id) {
        return runs.findById(id)
                .map(r -> ResponseEntity.ok(RunView.of(r)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/runs/{id}/stop")
    public Map<String, Object> stop(@PathVariable("id") long id) {
        return Map.of("stopped", service.stop(id));
    }

    @GetMapping("/runs/{id}/metrics")
    public ResponseEntity<DemoEvalService.RunMetricsView> metrics(@PathVariable("id") long id) {
        DemoEvalService.RunMetricsView m = service.metrics(id);
        return m == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(m);
    }

    // ------------------------------------------------------------------
    // Chi tiết dự đoán từng câu
    // ------------------------------------------------------------------
    public record ItemView(long itemId, String extId, String subjectCode,
                           String goldLabel, String predictedLabel, String rawPredictedIssue,
                           int suggestionCount, String topSeverity,
                           Long latencyMs, String modelName, String nodeName, String errorCode,
                           boolean predictionCorrect,
                           String stem, String correctLabel, String explanation) {
    }

    @GetMapping("/runs/{id}/items")
    public ResponseEntity<PageView<ItemView>> runItems(@PathVariable("id") long id,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "10") int size,
                                                       @RequestParam(defaultValue = "false") boolean onlyWrong) {
        if (!runs.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        int safeSize = Math.max(1, Math.min(size, 50));
        Pageable pageable = PageRequest.of(Math.max(0, page), safeSize);
        Page<DemoEvalItem> result = onlyWrong
                ? items.findByRunIdAndPredictionCorrectFalse(id, pageable)
                : items.findByRunId(id, pageable);

        Map<Long, DemoQuestion> byId = new java.util.HashMap<>();
        List<Long> ids = result.getContent().stream().map(DemoEvalItem::getDemoQuestionId).toList();
        if (!ids.isEmpty()) {
            questions.findAllById(ids).forEach(q -> byId.put(q.getId(), q));
        }
        Page<ItemView> mapped = result.map(it -> {
            DemoQuestion q = byId.get(it.getDemoQuestionId());
            return new ItemView(it.getId(), it.getExtId(), it.getSubjectCode(),
                    it.getGoldLabel(), it.getPredictedLabel(), it.getRawPredictedIssue(),
                    it.getSuggestionCount() == null ? 0 : it.getSuggestionCount(), it.getTopSeverity(),
                    it.getLatencyMs(), it.getModelName(), it.getNodeName(), it.getErrorCode(),
                    Boolean.TRUE.equals(it.getPredictionCorrect()),
                    q != null ? q.getStem() : null,
                    q != null ? q.getCorrectLabel() : null,
                    q != null ? q.getExplanation() : null);
        });
        return ResponseEntity.ok(PageView.of(mapped));
    }

    @DeleteMapping("/runs")
    public ResponseEntity<Map<String, Object>> deleteRuns() {
        if (runs.countRunning() > 0) {
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("error", "Đang có run chạy — không thể xóa.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        }
        items.deleteAllInBatch();
        runs.deleteAllInBatch();
        return ResponseEntity.ok(Map.of("deleted", true));
    }
}
