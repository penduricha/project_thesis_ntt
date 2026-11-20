package com.example.server_ai_worker.service.ai;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.example.server_ai_worker.config.AiProperties;
import com.example.server_ai_worker.model.jpa.IssueType;
import com.example.server_ai_worker.repository.jpa.IssueTypeRepository;

/**
 * Nạp danh mục 5 nhãn lỗi chuẩn + 'none' vào ntt_issue_types nếu bảng còn trống
 * (khi chạy độc lập trên ntt_demo_llm). Đặt ai.demo.seed-issue-types=false khi nối DB chính đã seed bằng SQL.
 */
@Component
public class DemoDataInit implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInit.class);

    private final AiProperties props;
    private final IssueTypeRepository repo;

    public DemoDataInit(AiProperties props, IssueTypeRepository repo) {
        this.props = props;
        this.repo = repo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.getDemo().isSeedIssueTypes()) {
            return;
        }
        if (repo.count() > 0) {
            return;
        }
        List<IssueType> seeds = List.of(
                new IssueType("spelling", "Lỗi chính tả", true, 10),
                new IssueType("grammar", "Lỗi ngữ pháp", true, 20),
                new IssueType("logic", "Lỗi logic / mơ hồ / thiếu dữ kiện", true, 30),
                new IssueType("distractor", "Phương án nhiễu kém chất lượng", true, 40),
                new IssueType("key_doubt", "Nghi ngờ sai đáp án", false, 50),
                new IssueType("none", "Không có lỗi", false, 900)
        );
        repo.saveAll(seeds);
        log.info("Đã nạp {} loại lỗi chuẩn hóa (5 nhãn + none) vào ntt_issue_types", seeds.size());
    }
}
