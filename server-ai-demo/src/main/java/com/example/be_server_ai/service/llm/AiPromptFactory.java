package com.example.be_server_ai.service.llm;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.be_server_ai.config.AiProperties;
import com.example.be_server_ai.enums.AiTaskType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Dựng yêu cầu gửi LLM từ input_json của task. Prompt và schema lấy từ hai notebook đã thử trên Colab:
 * question_check theo fix_error_quiz.ipynb (một câu mỗi task, có sẵn các trường original/fixed),
 * nl_search theo test_llm.ipynb. Danh sách issue_type hợp lệ lấy từ ntt_issue_types.
 *
 * <p>input_json của question_check: stem, option_a, option_b, option_c, option_d, correct_label (A..D).
 * input_json của nl_search: query. Toàn bộ dữ liệu vào đều nằm trong input_json (Server AI không đọc bảng câu hỏi).
 */
@Component
public class AiPromptFactory {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final String CHECK_SYSTEM = """
            Bạn là chuyên gia hiệu đính câu hỏi trắc nghiệm bốn phương án A, B, C, D.
            Hãy kiểm tra danh sách câu hỏi và tìm các lỗi sau:
            %TYPES%
            YÊU CẦU ĐẦU RA:
            - Với mỗi câu, giữ nguyên ID.
            - Trả về `has_error` (true/false).
            - Trả về `error_type` (chọn một trong các giá trị ở trên, hoặc "none" nếu không có lỗi).
            - Trả về `explanation` (Giải thích ngắn gọn lý do sai bằng tiếng Việt).
            - Trả về `original` (Bản gốc gồm sentence, A, B, C, D, correct).
            - Trả về `fixed` (Bản đề xuất sửa gồm sentence, A, B, C, D, correct). Nếu không có lỗi, `fixed` giống hệt `original`.

            CHỈ TRẢ VỀ JSON THEO SCHEMA.""";

    private static final String CHECK_SCHEMA = """
            {"type":"object","properties":{"results":{"type":"array","items":{"type":"object","properties":{
              "id":{"type":"integer"},
              "has_error":{"type":"boolean"},
              "error_type":{"type":"string","enum":%ENUM%},
              "explanation":{"type":"string"},
              "original":{"type":"object","properties":{"sentence":{"type":"string"},"A":{"type":"string"},"B":{"type":"string"},"C":{"type":"string"},"D":{"type":"string"},"correct":{"type":"string"}},"required":["sentence","A","B","C","D","correct"]},
              "fixed":{"type":"object","properties":{"sentence":{"type":"string"},"A":{"type":"string"},"B":{"type":"string"},"C":{"type":"string"},"D":{"type":"string"},"correct":{"type":"string"}},"required":["sentence","A","B","C","D","correct"]}
            },"required":["id","has_error","error_type","explanation","original","fixed"]}}},"required":["results"]}""";

    private static final String NL_SYSTEM = """
            Bạn chuyển câu tìm kiếm của Admin thành bộ lọc JSON cho ngân hàng câu hỏi trắc nghiệm.
            Hôm nay là %DATE%.
            Quy tắc:
            - category: tên bộ đề/môn nếu có (ví dụ "TOEIC"), không có thì null.
            - tags: các nhãn phụ như "Part 5", "Part 6"; không có thì [].
            - difficulty: chỉ easy | medium | hard (dễ=easy, trung bình=medium, khó=hard), không nói thì null.
            - search_in: "question" nếu nhắc dòng câu hỏi/nội dung câu hỏi; "options" nếu nhắc đáp án; mặc định ["all"].
            - keywords: chỉ phần từ khóa còn lại, KHÔNG lặp lại thông tin đã đưa vào các trường khác. Không còn thì "".
            - Ngày tương đối đổi sang yyyy-mm-dd; không nhắc thì null.
            - KHÔNG bịa giá trị không có trong câu. Chỉ trả JSON.

            Ví dụ:
            Câu: "câu dễ ngữ pháp tạo tuần trước ở đáp án"
            JSON: {"keywords":"ngữ pháp","category":null,"tags":[],"difficulty":"easy","search_in":["options"],"created_from":null,"created_to":null}
            """;

    private static final String NL_SCHEMA = """
            {"type":"object","properties":{
              "keywords":{"type":"string"},
              "category":{"type":["string","null"]},
              "tags":{"type":"array","items":{"type":"string"}},
              "difficulty":{"type":["string","null"],"enum":["easy","medium","hard",null]},
              "search_in":{"type":"array","items":{"type":"string","enum":["question","options","all"]}},
              "created_from":{"type":["string","null"]},
              "created_to":{"type":["string","null"]}
            },"required":["keywords","category","tags","difficulty","search_in","created_from","created_to"]}""";

    private final ObjectMapper om;
    private final AiProperties props;

    public AiPromptFactory(ObjectMapper om, AiProperties props) {
        this.om = om;
        this.props = props;
    }

    /** Ném IllegalArgumentException nếu input_json thiếu hoặc sai dạng (task sẽ failed với BAD_INPUT). */
    public ChatSpec build(AiTaskType type, JsonNode input, Collection<String> issueTypes) {
        return switch (type) {
            case QUESTION_CHECK -> questionCheck(input, issueTypes);
            case NL_SEARCH -> nlSearch(input);
        };
    }

    /** Khóa cache của câu tìm kiếm: chữ thường, gom khoảng trắng; ghép prompt_version ở nơi gọi. */
    public static String normalizeQuery(String q) {
        return q == null ? "" : q.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private ChatSpec questionCheck(JsonNode in, Collection<String> issueTypes) {
        ObjectNode item = om.createObjectNode();
        item.put("id", 1);
        item.put("sentence", required(in, "stem"));
        item.put("A", required(in, "option_a"));
        item.put("B", required(in, "option_b"));
        item.put("C", required(in, "option_c"));
        item.put("D", required(in, "option_d"));
        String correct = required(in, "correct_label").toUpperCase(Locale.ROOT);
        if (!correct.matches("[ABCD]")) {
            throw new IllegalArgumentException("correct_label phải là A, B, C hoặc D.");
        }
        item.put("correct", correct);

        Set<String> types = new LinkedHashSet<>(issueTypes);
        types.add("none");
        StringBuilder lines = new StringBuilder();
        int n = 1;
        for (String t : types) {
            if ("none".equals(t)) {
                continue;
            }
            lines.append(n++).append(". ").append(describe(t)).append('\n');
        }
        String system = CHECK_SYSTEM.replace("%TYPES%", lines.toString().stripTrailing());
        JsonNode schema = readJson(CHECK_SCHEMA.replace("%ENUM%", toJsonArray(types)));
        try {
            ArrayNode batch = om.createArrayNode().add(item);
            String user = "Kiểm tra và sửa các câu sau:\n" + om.writerWithDefaultPrettyPrinter().writeValueAsString(batch);
            return new ChatSpec(system, user, schema, props.getLlm().getQuestionCheck());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không tạo được nội dung gửi LLM.", e);
        }
    }

    private ChatSpec nlSearch(JsonNode in) {
        String query = required(in, "query");
        if (query.length() > 500) {
            throw new IllegalArgumentException("query dài quá 500 ký tự.");
        }
        String system = NL_SYSTEM.replace("%DATE%", LocalDate.now(VN).toString());
        return new ChatSpec(system, query, readJson(NL_SCHEMA), props.getLlm().getNlSearch());
    }

    private static String describe(String code) {
        return switch (code) {
            case "spelling" -> "\"spelling\" (Chính tả): Sai từ vựng hoặc sai chính tả.";
            case "logic" -> "\"logic\" (Logic): Câu vô nghĩa, thiếu chỗ trống \"_____\" (với câu điền khuyết), hoặc đáp án không khớp với chỗ trống.";
            case "presentation" -> "\"presentation\" (Trình bày/Ngữ pháp): Lỗi ngữ pháp, sai thì, hoặc đáp án đúng (correct) bị gán sai (ví dụ: đáp án A không đúng ngữ pháp nhưng lại được đặt là đúng).";
            default -> "\"" + code + "\": Lỗi thuộc nhóm này.";
        };
    }

    private static String required(JsonNode in, String field) {
        String v = in == null ? "" : in.path(field).asText("");
        if (v.isBlank()) {
            throw new IllegalArgumentException("input_json thiếu trường " + field + ".");
        }
        return v.trim();
    }

    private String toJsonArray(Collection<String> values) {
        ArrayNode a = om.createArrayNode();
        values.forEach(a::add);
        return a.toString();
    }

    private JsonNode readJson(String json) {
        try {
            return om.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Schema nội bộ không hợp lệ.", e);
        }
    }
}
