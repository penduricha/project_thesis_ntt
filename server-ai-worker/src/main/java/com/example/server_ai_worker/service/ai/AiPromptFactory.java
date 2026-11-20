package com.example.server_ai_worker.service.ai;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.example.server_ai_worker.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Tạo prompt + JSON Schema (Ollama Structured Outputs) cho 2 loại task:
 * 1) question_check: Chuẩn 5 nhãn (spelling, grammar, logic, distractor, key_doubt), hỗ trợ chế độ zero/few/few_cot (Blind-Solve CoT).
 * 2) nl_search: Trích xuất bộ lọc tìm kiếm theo danh mục môn học/chủ đề động từ ER v4.
 */
@Component
public class AiPromptFactory {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final String QUESTION_CHECK_SCHEMA = """
            {
              "type": "object",
              "additionalProperties": false,
              "required": ["analysis", "has_issue", "suggested_difficulty", "suggestions"],
              "properties": {
                "analysis": {
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["self_solve_reasoning", "predicted_answer", "matches_correct_label"],
                  "properties": {
                    "self_solve_reasoning": {"type": "string"},
                    "predicted_answer": {"type": ["string", "null"], "enum": ["A", "B", "C", "D", null]},
                    "matches_correct_label": {"type": "boolean"}
                  }
                },
                "has_issue": {"type": "boolean"},
                "suggested_difficulty": {"type": ["integer", "null"], "minimum": 1, "maximum": 5},
                "suggestions": {
                  "type": "array",
                  "maxItems": 8,
                  "items": {
                    "type": "object",
                    "additionalProperties": false,
                    "required": ["issue_type", "severity", "target_field", "original_text", "proposed_text", "explanation"],
                    "properties": {
                      "issue_type": {"type": "string", "enum": ["spelling", "grammar", "logic", "distractor", "key_doubt"]},
                      "severity": {"type": "string", "enum": ["low", "medium", "high"]},
                      "target_field": {"type": "string", "enum": ["question", "stem", "option_a", "option_b", "option_c", "option_d"]},
                      "original_text": {"type": ["string", "null"]},
                      "proposed_text": {"type": ["string", "null"]},
                      "explanation": {"type": "string"}
                    }
                  }
                }
              }
            }""";

    private static final String BASE_CHECK_SYSTEM = """
            Bạn là chuyên gia thẩm định chất lượng câu hỏi trắc nghiệm (tiếng Việt và tiếng Anh).
            Nhiệm vụ: phát hiện lỗi thực sự của câu hỏi trắc nghiệm và trả về DUY NHẤT JSON hợp lệ theo schema.

            Phân loại chuẩn 5 nhãn trong suggestions[].issue_type:
            - spelling: Lỗi chính tả, sai dấu thanh tiếng Việt, đánh máy thừa/thiếu ký tự, viết hoa/dấu câu sai rõ ràng.
            - grammar: Lỗi ngữ pháp, chia thì, hòa hợp chủ-vị, giới từ, từ loại hoặc cấu trúc câu sai.
            - logic: Câu dẫn mơ hồ, thiếu dữ kiện để giải, thông tin mâu thuẫn, không có đáp án đúng, hoặc có từ 2 phương án trở lên cùng đúng.
            - distractor: Phương án nhiễu kém chất lượng — trùng lặp/đồng nghĩa với phương án khác, sai lộ liễu vô lý, hoặc lệch hẳn phạm trù ngữ pháp/kiểu giá trị khiến người làm dễ đoán mò.
            - key_doubt: Nghi ngờ correct_label đang gán nhầm đáp án đúng. Chỉ cảnh báo cho giảng viên tự kiểm tra, KHÔNG tự ý sửa đáp án đúng.

            Quy tắc bắt buộc:
            1) Trích dẫn "original_text" PHẢI khớp nguyên văn từng ký tự từ trường "target_field" được chỉ định (không tự bịa trích dẫn). Nếu nhận xét chung cả câu ("question") hoặc không trích được đoạn cụ thể, để "original_text": null.
            2) "proposed_text" là đoạn thay thế đề xuất tương ứng với "original_text" (hoặc null nếu chỉ cảnh báo như key_doubt/logic tổng quát). Không lặp lại y hệt "original_text".
            3) Nếu câu hỏi sạch, rõ ràng, đáp án hợp lý và phương án nhiễu đạt yêu cầu: trả về "has_issue": false và "suggestions": []. KHÔNG bới móc lỗi vặt về văn phong khi câu đã chuẩn.
            4) Một câu hỏi có thể có nhiều lỗi thuộc các trường khác nhau; liệt kê tối đa các lỗi thật sự quan trọng trong mảng "suggestions".
            5) Mọi "explanation" và "self_solve_reasoning" viết bằng tiếng Việt ngắn gọn, rõ ràng.""";

    private static final String BLIND_SOLVE_INSTRUCTIONS = """

            Quy trình tự giải độc lập (Blind-Solve CoT):
            - Bước 1: Tạm bỏ qua correct_label của đề, tự phân tích ngữ cảnh/kiến thức và giải câu hỏi trong analysis.self_solve_reasoning (2-4 câu), chốt đáp án tự giải vào analysis.predicted_answer ("A"|"B"|"C"|"D"|null).
            - Bước 2: Đối chiếu analysis.predicted_answer với correct_label; ghi analysis.matches_correct_label = true/false. Nếu khác hoặc thấy phương án khác cũng đúng -> kiểm tra kỹ để gắn key_doubt hoặc logic.
            - Bước 3: Rà kỹ từng trường stem, option_a..option_d để bắt chính xác spelling, grammar, distractor.""";

    private static final List<String> FEW_SHOT_EXAMPLES = List.of(
            """
            [Ví dụ 1 - Câu hỏi đạt chuẩn]
            Input: stem="The quarterly financial report will be reviewed ___ the board of directors next Monday.", A="by", B="at", C="on", D="with", correct_label="A"
            Output:
            {"analysis":{"self_solve_reasoning":"Cấu trúc bị động 'be reviewed ___ the board' cần giới từ chỉ tác nhân là 'by'. Đáp án đúng là A.","predicted_answer":"A","matches_correct_label":true},"has_issue":false,"suggested_difficulty":null,"suggestions":[]}""",
            """
            [Ví dụ 2 - Nhiều lỗi: chính tả + phương án nhiễu kém]
            Input: stem="Trong mô hình quan hệ, khóa chính (Primary Key) dùng để lam gì?", A="Xác định duy nhất mỗi bộ trong quan hệ", B="Lưu trữ toàn bộ mật khẩu người dùng", C="Xác định duy nhất từng dòng dữ liệu", D="Tạo bản sao dự phòng tự động", correct_label="A"
            Output:
            {"analysis":{"self_solve_reasoning":"Khóa chính dùng để định danh duy nhất mỗi bộ (tuple/dòng) trong bảng quan hệ. Phương án A đúng, nhưng C cũng mang nghĩa tương đương A.","predicted_answer":"A","matches_correct_label":true},"has_issue":true,"suggested_difficulty":null,"suggestions":[{"issue_type":"spelling","severity":"low","target_field":"stem","original_text":"dùng để lam gì?","proposed_text":"dùng để làm gì?","explanation":"Từ 'làm' bị thiếu dấu huyền ('lam')."},{"issue_type":"distractor","severity":"high","target_field":"option_c","original_text":"Xác định duy nhất từng dòng dữ liệu","proposed_text":"Liên kết khóa ngoại tới bảng cha bất kỳ","explanation":"Phương án C đồng nghĩa với đáp án đúng A ('mỗi bộ' và 'từng dòng dữ liệu' đều chỉ bản ghi), gây trùng đáp án."}]}""",
            """
            [Ví dụ 3 - Nghi ngờ sai đáp án (key_doubt)]
            Input: stem="Giao thức HTTP mặc định sử dụng cổng (port) nào?", A="21", B="22", C="80", D="443", correct_label="D"
            Output:
            {"analysis":{"self_solve_reasoning":"HTTP không mã hóa dùng cổng mặc định 80 (C), còn 443 là cổng mặc định của HTTPS.","predicted_answer":"C","matches_correct_label":false},"has_issue":true,"suggested_difficulty":null,"suggestions":[{"issue_type":"key_doubt","severity":"high","target_field":"question","original_text":null,"proposed_text":null,"explanation":"Câu hỏi hỏi về giao thức HTTP mặc định (cổng 80 - phương án C), nhưng correct_label hiện tại là D (443 - cổng của HTTPS). Giảng viên cần kiểm tra lại đáp án đúng hoặc sửa câu dẫn thành HTTPS."}]}"""
    );

    private static final String NL_SEARCH_SYSTEM_TEMPLATE = """
            Bạn là trợ lý chuyển câu tìm kiếm tiếng Việt tự nhiên thành bộ lọc cấu trúc JSON cho ngân hàng câu hỏi trắc nghiệm.
            Hôm nay là ngày %s (múi giờ Việt Nam, UTC+7).

            Quy tắc ánh xạ:
            - subject_code: CHỈ chọn đúng mã có trong danh mục môn học bên dưới; nếu không nhắc tới hoặc không khớp rõ ràng thì để null.
            - topic_name: CHỈ chọn đúng tên chủ đề có trong danh mục bên dưới (ưu tiên chủ đề thuộc môn đã chọn); nếu không khớp thì để null.
            - difficulty: số nguyên 1 (rất dễ/nhận biết) đến 5 (rất khó/vận dụng cao), hoặc null nếu không đề cập. Map gần đúng: dễ -> 1 hoặc 2, trung bình -> 3, khó -> 4 hoặc 5.
            - validation_status: "draft" (nháp/chờ duyệt), "approved" (đã duyệt), "rejected" (từ chối), hoặc null.
            - keywords: danh sách từ khóa chuyên môn quan trọng để tìm toàn văn (bỏ các từ nhiễu như "tìm câu hỏi", "cho tôi", "mức độ", "trạng thái").
            - search_in: phạm vi tìm từ khóa ("stem", "options", "explanation", hoặc mặc định "all").
            - created_from, created_to: định dạng YYYY-MM-DD tính theo hôm nay nếu có nhắc mốc thời gian ("hôm nay", "tuần này", "tháng trước"...), ngược lại để null.

            Danh mục môn học và chủ đề hợp lệ:
            %s""";

    private final AiProperties props;
    private final ObjectMapper json;
    private final SubjectCatalogResolver catalogResolver;

    public AiPromptFactory(AiProperties props, ObjectMapper json, SubjectCatalogResolver catalogResolver) {
        this.props = props;
        this.json = json;
        this.catalogResolver = catalogResolver;
    }

    public ChatSpec buildQuestionCheck(JsonNode input) {
        String mode = props.getLlm().getQuestionCheckMode();
        String normMode = mode == null ? "few_cot" : mode.trim().toLowerCase(Locale.ROOT);
        boolean useCot = !"few".equals(normMode) && !"zero".equals(normMode);
        boolean useFewShot = !"zero".equals(normMode);

        StringBuilder sys = new StringBuilder(BASE_CHECK_SYSTEM);
        if (useCot) {
            sys.append(BLIND_SOLVE_INSTRUCTIONS);
        } else {
            sys.append("\n\nHãy điền analysis.self_solve_reasoning ngắn gọn rồi liệt kê suggestions nếu có lỗi.");
        }
        if (props.getLlm().isSuggestDifficulty()) {
            sys.append("\n- Hãy ước lượng độ khó từ 1 (rất dễ) đến 5 (rất khó) vào suggested_difficulty.");
        } else {
            sys.append("\n- Để suggested_difficulty = null trừ khi cần nhấn mạnh.");
        }

        if (useFewShot) {
            int count = Math.max(1, Math.min(props.getLlm().getFewShotCount(), FEW_SHOT_EXAMPLES.size()));
            sys.append("\n");
            for (int i = 0; i < count; i++) {
                sys.append("\n").append(FEW_SHOT_EXAMPLES.get(i));
            }
        }

        StringBuilder user = new StringBuilder("Hãy kiểm tra câu hỏi trắc nghiệm sau:\n");
        appendOptionalMeta(user, input);
        user.append("stem: ").append(safeText(input, "stem")).append("\n");
        user.append("option_a: ").append(safeText(input, "option_a")).append("\n");
        user.append("option_b: ").append(safeText(input, "option_b")).append("\n");
        user.append("option_c: ").append(safeText(input, "option_c")).append("\n");
        user.append("option_d: ").append(safeText(input, "option_d")).append("\n");
        user.append("correct_label: ").append(safeText(input, "correct_label")).append("\n");
        String authorExp = safeTextOrNull(input, "explanation");
        if (authorExp != null) {
            user.append("explanation_cua_tac_gia: ").append(authorExp).append("\n");
        }

        return new ChatSpec(sys.toString(), user.toString(), QUESTION_CHECK_SCHEMA, props.getLlm().getQuestionCheck());
    }

    public ChatSpec buildNlSearch(JsonNode input) {
        SubjectCatalogResolver.Catalog catalog = catalogResolver.resolve(input);
        String catalogText = formatCatalog(catalog);
        String today = LocalDate.now(VN_ZONE).toString();
        String sys = NL_SEARCH_SYSTEM_TEMPLATE.formatted(today, catalogText);
        String user = "Câu tìm kiếm: " + safeText(input, "query");
        String dynamicSchema = buildNlSearchSchema(catalog);
        return new ChatSpec(sys, user, dynamicSchema, props.getLlm().getNlSearch());
    }

    private String buildNlSearchSchema(SubjectCatalogResolver.Catalog catalog) {
        ObjectNode root = json.createObjectNode();
        root.put("type", "object");
        root.put("additionalProperties", false);
        ArrayNode req = root.putArray("required");
        for (String f : List.of("subject_code", "topic_name", "difficulty", "validation_status",
                "keywords", "search_in", "created_from", "created_to")) {
            req.add(f);
        }

        ObjectNode propsNode = root.putObject("properties");

        ObjectNode subj = propsNode.putObject("subject_code");
        ArrayNode subjType = subj.putArray("type").add("string").add("null");
        if (!catalog.allowedCodes().isEmpty()) {
            ArrayNode subjEnum = subj.putArray("enum");
            catalog.allowedCodes().forEach(subjEnum::add);
            subjEnum.addNull();
        }

        ObjectNode topic = propsNode.putObject("topic_name");
        ArrayNode topicType = topic.putArray("type").add("string").add("null");
        if (!catalog.allowedTopics().isEmpty()) {
            ArrayNode topicEnum = topic.putArray("enum");
            catalog.allowedTopics().forEach(topicEnum::add);
            topicEnum.addNull();
        }

        ObjectNode diff = propsNode.putObject("difficulty");
        diff.putArray("type").add("integer").add("null");
        diff.put("minimum", 1);
        diff.put("maximum", 5);

        ObjectNode valStatus = propsNode.putObject("validation_status");
        valStatus.putArray("type").add("string").add("null");
        valStatus.putArray("enum").add("draft").add("approved").add("rejected").addNull();

        ObjectNode keywords = propsNode.putObject("keywords");
        keywords.put("type", "array");
        keywords.put("maxItems", 8);
        keywords.putObject("items").put("type", "string");

        ObjectNode searchIn = propsNode.putObject("search_in");
        searchIn.put("type", "string");
        searchIn.putArray("enum").add("stem").add("options").add("explanation").add("all");

        ObjectNode createdFrom = propsNode.putObject("created_from");
        createdFrom.putArray("type").add("string").add("null");

        ObjectNode createdTo = propsNode.putObject("created_to");
        createdTo.putArray("type").add("string").add("null");

        try {
            return json.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Không dựng được schema JSON cho nl_search", e);
        }
    }

    private static void appendOptionalMeta(StringBuilder user, JsonNode input) {
        String subject = firstNonBlank(input, "subject_name", "subject_code");
        String topic = safeTextOrNull(input, "topic_name");
        String diff = safeTextOrNull(input, "difficulty");
        if (subject != null || topic != null || diff != null) {
            user.append("[Ngữ cảnh đề thi:");
            if (subject != null) {
                user.append(" Môn=").append(subject);
            }
            if (topic != null) {
                user.append(" | Chủ đề=").append(topic);
            }
            if (diff != null) {
                user.append(" | Độ khó hiện tại=").append(diff);
            }
            user.append("]\n");
        }
    }

    private static String formatCatalog(SubjectCatalogResolver.Catalog catalog) {
        if (catalog.subjects().isEmpty()) {
            return "(Chưa cấu hình danh mục môn học; để subject_code và topic_name là null)";
        }
        StringBuilder sb = new StringBuilder();
        for (SubjectCatalogResolver.SubjectEntry s : catalog.subjects()) {
            sb.append("- ").append(s.code()).append(" (").append(s.name()).append(")");
            if (!s.topics().isEmpty()) {
                sb.append(": ").append(String.join(", ", s.topics()));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String safeText(JsonNode node, String key) {
        if (node == null) {
            return "";
        }
        JsonNode child = node.get(key);
        return child == null || child.isNull() ? "" : child.asText("");
    }

    private static String safeTextOrNull(JsonNode node, String key) {
        if (node == null) {
            return null;
        }
        JsonNode child = node.get(key);
        if (child == null || child.isNull()) {
            return null;
        }
        String s = child.asText("").trim();
        return s.isEmpty() ? null : s;
    }

    private static String firstNonBlank(JsonNode node, String k1, String k2) {
        String a = safeTextOrNull(node, k1);
        return a != null ? a : safeTextOrNull(node, k2);
    }
}
