package com.example.be_server_ai.data_init;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.example.be_server_ai.config.AiProperties;
import com.example.be_server_ai.model.jpa.IssueType;
import com.example.be_server_ai.repository.jpa.IssueTypeRepository;

/**
 * CHỈ DÙNG KHI DEMO. Khi CSDL trống và ddl-auto là update, Hibernate tạo bảng ntt_issue_types nhưng không có dữ liệu,
 * nên task question_check không có danh sách loại lỗi hợp lệ. Lớp này nạp sẵn các loại lỗi mà notebook test dùng
 * (spelling, logic, presentation) và none, chỉ khi bảng đang rỗng. Bản đầy đủ 6 dòng do Server 1 quản lý (script ER v4).
 * Tắt bằng ai.demo.seed-issue-types=false. Không có quyền INSERT thì chỉ ghi cảnh báo, không làm app dừng.
 */
@Component
public class DemoDataInit implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInit.class);

    private final IssueTypeRepository issueTypes;
    private final AiProperties props;

    public DemoDataInit(IssueTypeRepository issueTypes, AiProperties props) {
        this.issueTypes = issueTypes;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.getDemo().isSeedIssueTypes()) {
            return;
        }
        try {
            if (issueTypes.count() > 0) {
                return;
            }
            List<IssueType> rows = List.of(
                    new IssueType("spelling", "Chính tả", true),
                    new IssueType("logic", "Logic", true),
                    new IssueType("presentation", "Trình bày / Ngữ pháp", true),
                    new IssueType("none", "Không lỗi", false));
            for (IssueType row : rows) {
                issueTypes.save(row);
            }
            log.info("ntt_issue_types đang rỗng: đã nạp {} loại lỗi cho demo", rows.size());
        } catch (Exception e) {
            log.warn("Không nạp được ntt_issue_types (thiếu bảng hoặc quyền INSERT?): {}", e.toString());
        }
    }
}
