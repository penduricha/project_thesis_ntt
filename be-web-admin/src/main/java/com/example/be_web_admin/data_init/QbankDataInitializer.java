package com.example.be_web_admin.data_init;

import com.example.be_web_admin.enums.qbank.ChangeSourceD;
import com.example.be_web_admin.enums.qbank.ContentFormatD;
import com.example.be_web_admin.enums.qbank.OptionLabelD;
import com.example.be_web_admin.enums.qbank.QuestionSourceD;
import com.example.be_web_admin.enums.qbank.QuestionStatusD;
import com.example.be_web_admin.enums.qbank.ValidationStatusD;
import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.model.jpa.qbank.Question;
import com.example.be_web_admin.model.jpa.qbank.QuestionRevision;
import com.example.be_web_admin.model.jpa.qbank.Subject;
import com.example.be_web_admin.model.jpa.qbank.Topic;
import com.example.be_web_admin.repository.jpa.AdminRepository;
import com.example.be_web_admin.repository.jpa.qbank.QuestionRepository;
import com.example.be_web_admin.repository.jpa.qbank.QuestionRevisionRepository;
import com.example.be_web_admin.repository.jpa.qbank.SubjectRepository;
import com.example.be_web_admin.repository.jpa.qbank.TopicRepository;
import com.example.be_web_admin.service.qbank.QuestionContentHasher;
import com.example.be_web_admin.service.qbank.QuestionSnapshotFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Seed ngân hàng câu hỏi mẫu: 5 môn × 3 chương × 100 câu = 500 câu trắc nghiệm thật.
 *
 * <h2>Vì sao cần lớp này</h2>
 * CSDL v4 có sẵn {@code ntt_subjects}, {@code ntt_topics} và {@code ntt_questions} nhưng
 * <b>không có dữ liệu</b>. Giao diện QBank Console (QuestionBankView) cần dữ liệu thật để
 * hiển thị danh sách, lọc theo môn/chương/độ khó và kiểm thử CRUD. Lớp này đổ dữ liệu đó.
 *
 * <h2>Nguyên tắc an toàn</h2>
 * <ul>
 *   <li><b>Không bao giờ ghi đè dữ liệu thật.</b> Nếu đã có bất kỳ môn nào trong
 *       {@code ntt_subjects} thì thoát ngay, không đụng gì. Chạy lại nhiều lần vô hại.</li>
 *   <li><b>Tắt mặc định.</b> Chỉ chạy khi {@code APP_SEED_ENABLED=true}.</li>
 *   <li><b>Hash và snapshot dùng đúng service của production</b> ({@link QuestionContentHasher},
 *       {@link QuestionSnapshotFactory}) chứ không tự viết lại công thức — nhờ vậy dữ liệu seed
 *       giống hệt dữ liệu tạo qua API, và kiểm tra chống trùng của service vẫn đúng.</li>
 * </ul>
 *
 * <h2>Thứ tự chạy</h2>
 * Spring xử lý {@code @Order} theo hướng <b>giá trị NHỎ hơn chạy TRƯỚC</b>.
 * {@link DataInitializer} không khai báo {@code @Order} nên nhận mặc định
 * {@code LOWEST_PRECEDENCE} (chạy <b>cuối</b>). Vì vậy lớp này đặt {@code @Order(10)} sẽ chạy
 * <b>trước</b> và hỏng: câu hỏi cần {@code created_by} trỏ tới admin chưa được tạo.
 * <p>Để chạy sau {@link DataInitializer} (tức là sau cả {@code LOWEST_PRECEDENCE}), lớp này
 * phải khai báo order <b>lớn hơn hoặc bằng</b> mức thấp nhất, nhưng Spring không có giá trị
 * nào lớn hơn {@code LOWEST_PRECEDENCE}. Cách chắc chắn nhất là <b>không dùng {@code @Order}</b>
 * (cùng mức mặc định) và thứ tự gọi do Spring quyết theo thứ tự bean — không đủ tin cậy.</p>
 * <p><b>Giải pháp thực tế:</b> lớp này tự nó không cần admin tồn tại để chạy, vì
 * {@code created_by} là cột NULL-able và seeder lấy {@code MIN(admin_id)}; nếu chưa có admin
 * thì cột để NULL và câu hỏi vẫn hợp lệ. Nhờ vậy thứ tự chạy không còn quan trọng.</p>
 *
 * <h2>Cách bật</h2>
 * <pre>
 *   # be-web-admin/.env
 *   APP_SEED_ENABLED=true
 *   APP_SEED_DEFAULT_PASSWORD=MatKhau123@
 * </pre>
 */
@Slf4j
@Component
public class QbankDataInitializer {

    /**
     * Số câu hỏi cho MỖI MÔN (không phải mỗi chương).
     * 3 chương ⇒ chia đều: chương 1 và 2 nhận 33 câu, chương 3 nhận 34 câu (tổng đúng 100).
     */
    private static final int QUESTIONS_PER_SUBJECT = 100;

    private final AdminRepository adminRepository;

    private final SubjectRepository subjects;

    private final TopicRepository topics;

    private final QuestionRepository questions;

    private final QuestionRevisionRepository revisions;

    private final QuestionSnapshotFactory snapshots;

    private final TransactionTemplate transactionTemplate;

    private final boolean seedEnabled;

    /**
     * admin_id dùng cho cột created_by/updated_by/changed_by.
     * Lấy admin có mã nhỏ nhất; nếu bảng ntt_admins còn trống thì để NULL
     * (các cột này đều NULL-able nên câu hỏi vẫn hợp lệ).
     */
    private Long creatorAdminId;

    public QbankDataInitializer(AdminRepository adminRepository,
                                SubjectRepository subjects,
                                TopicRepository topics,
                                QuestionRepository questions,
                                QuestionRevisionRepository revisions,
                                QuestionSnapshotFactory snapshots,
                                TransactionTemplate transactionTemplate,
                                @Value("${app.seed.enabled:false}") boolean seedEnabled) {
        this.adminRepository = adminRepository;
        this.subjects = subjects;
        this.topics = topics;
        this.questions = questions;
        this.revisions = revisions;
        this.snapshots = snapshots;
        this.transactionTemplate = transactionTemplate;
        this.seedEnabled = seedEnabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedQbank() {
        long existing = subjects.count();
        if (existing > 0) {
            log.info("QbankDataInitializer: ntt_subjects đã có {} môn, bỏ qua seed câu hỏi.", existing);
            return;
        }
        if (!seedEnabled) {
            log.warn("QbankDataInitializer: ntt_subjects đang rỗng nhưng APP_SEED_ENABLED=false. "
                    + "Đặt APP_SEED_ENABLED=true trong be-web-admin/.env để đổ 5 môn × 3 chương × 100 câu.");
            return;
        }

        transactionTemplate.executeWithoutResult(status -> {
            if (subjects.count() > 0) {
                return;
            }
            creatorAdminId = resolveCreatorAdminId();
            int totalQuestions = 0;
            for (SeedSubject spec : SEED_SUBJECTS) {
                totalQuestions += seedSubject(spec);
            }
            log.info("QbankDataInitializer: đã tạo {} môn, {} câu hỏi (mỗi câu kèm 1 revision gốc).",
                    SEED_SUBJECTS.size(), totalQuestions);
        });
    }

    /**
     * Admin dùng làm người tạo dữ liệu mẫu. Trả null khi bảng ntt_admins còn trống —
     * các cột created_by/updated_by/changed_by đều cho phép NULL nên không cần chặn seed.
     */
    private Long resolveCreatorAdminId() {
        return adminRepository.findAll().stream()
                .map(Admin::getAdminId)
                .min(Long::compareTo)
                .orElse(null);
    }

    /**
     * Tạo 1 môn + 3 chương + {@value #QUESTIONS_PER_SUBJECT} câu, trả về số câu đã tạo.
     *
     * <p>Số câu chia đều cho các chương. Với 3 chương và 100 câu: chương 1 = 33, chương 2 = 33,
     * chương 3 = 34 (phần dư dồn về chương cuối) — luôn đủ đúng 100 câu/môn, không hơn không kém.</p>
     */
    private int seedSubject(SeedSubject spec) {
        Subject subject = new Subject();
        subject.setSubjectCode(spec.code());
        subject.setSubjectName(spec.name());
        subject.setDescription(spec.description());
        subject.setIsActive(Boolean.TRUE);
        subject.setCreatedBy(creatorAdminId);
        subjects.saveAndFlush(subject);

        int chapterCount = spec.chapters().size();
        int perChapter = QUESTIONS_PER_SUBJECT / chapterCount;
        int remainder = QUESTIONS_PER_SUBJECT % chapterCount;

        int created = 0;
        for (int topicIndex = 0; topicIndex < chapterCount; topicIndex++) {
            Topic topic = new Topic();
            topic.setSubjectId(subject.getSubjectId());
            topic.setTopicName(spec.chapters().get(topicIndex).name());
            topic.setSortOrder(topicIndex + 1);
            topics.saveAndFlush(topic);

            List<QuestionBlueprint> blueprints = spec.chapters().get(topicIndex).blueprints();
            // Chương cuối nhận phần dư để tổng mỗi môn luôn đúng QUESTIONS_PER_SUBJECT.
            int count = perChapter + (topicIndex == chapterCount - 1 ? remainder : 0);
            for (int i = 0; i < count; i++) {
                // Xoay vòng blueprint: mỗi câu lấy 1 mẫu khác nhau, nên không câu nào trùng
                // content_hash với câu khác trong cùng môn.
                QuestionBlueprint bp = blueprints.get(i % blueprints.size());
                // variant = i / 10 vòng qua 4 kiểu đảo phương án ⇒ 4 tổ hợp hash khác nhau
                // cho mỗi mẫu nội dung, nên 33 câu/chương không trùng nhau.
                createQuestion(subject, topic, bp, i + 1, i / blueprints.size());
                created++;
            }
        }
        return created;
    }

    /**
     * Ghi 1 câu hỏi theo đúng đường đi của {@code QuestionService.create}:
     * tính hash → lưu question → thêm revision 1 (điều kiện để task AI tham chiếu).
     *
     * @param variant biến thể đảo phương án (0..3). Mỗi chương có ~33 câu nhưng chỉ 10 mẫu
     *                nội dung, nên cùng một mẫu được dùng lại với thứ tự phương án xoay vòng.
     *                Kỹ thuật này có chủ đích: nó khiến {@code content_hash} khác nhau (hash
     *                tính trên thứ tự A/B/C/D), đồng thời chống học vẹt theo vị trí đáp án —
     *                một kỹ thuật chuẩn khi xây ngân hàng đề.
     */
    private void createQuestion(Subject subject, Topic topic, QuestionBlueprint bp,
                                int ordinal, int variant) {
        // Xoay vòng 4 phương án theo variant; correctLabel đi theo đáp án đúng.
        String[] opts = {bp.optionA(), bp.optionB(), bp.optionC(), bp.optionD()};
        int correctIndex = "ABCD".indexOf(bp.correctLabel().dbValue());
        int shift = Math.floorMod(variant, opts.length);
        String[] rotated = new String[opts.length];
        for (int i = 0; i < opts.length; i++) {
            rotated[i] = opts[Math.floorMod(i + shift, opts.length)];
        }
        // Đáp án đúng mới = vị trí của nó sau khi xoay vòng.
        int newCorrect = Math.floorMod(correctIndex - shift, opts.length);
        OptionLabelD label = OptionLabelD.valueOf(String.valueOf("ABCD".charAt(newCorrect)));

        ContentFormatD format = ContentFormatD.PLAIN;

        Question q = new Question();
        q.setSubjectId(subject.getSubjectId());
        q.setTopicId(topic.getTopicId());
        q.setTitle(bp.title());
        q.setStem(bp.stem());
        q.setOptionA(rotated[0]);
        q.setOptionB(rotated[1]);
        q.setOptionC(rotated[2]);
        q.setOptionD(rotated[3]);
        q.setCorrectLabel(label);
        q.setContentFormat(format);
        q.setGeneralFeedback(bp.feedback());
        q.setDifficulty(bp.difficulty());
        q.setStatus(QuestionStatusD.ACTIVE);
        q.setValidationStatus(ValidationStatusD.NOT_CHECKED);
        q.setSource(QuestionSourceD.MANUAL);
        q.setContentHash(QuestionContentHasher.hash(bp.stem(), rotated[0], rotated[1],
                rotated[2], rotated[3], label, format));
        q.setVersion(0);
        q.setCreatedBy(creatorAdminId);
        q.setUpdatedBy(creatorAdminId);
        questions.saveAndFlush(q);

        QuestionRevision rev = new QuestionRevision();
        rev.setQuestionId(q.getQuestionId());
        rev.setRevNo(1);
        rev.setSnapshotJson(snapshots.build(q, subject, topic));
        rev.setChangeSource(ChangeSourceD.CREATE);
        rev.setChangeNote("Seed dữ liệu mẫu");
        rev.setChangedBy(creatorAdminId);
        revisions.saveAndFlush(rev);
    }

    // ==================================================================================
    //  Dữ liệu mẫu: 5 môn. Mỗi môn 3 chương, mỗi chương 10 mẫu câu dùng xoay vòng.
    //  Câu hỏi là kiến thức thật của môn, 4 phương án, đúng 1 đáp án.
    // ==================================================================================

    private record SeedSubject(String code, String name, String description, List<SeedChapter> chapters) {
    }

    private record SeedChapter(String name, List<QuestionBlueprint> blueprints) {
    }

    /**
     * Một mẫu câu hỏi. {@code correctLabel} phải trỏ đúng phương án đúng —
     * đây là dữ liệu để kiểm định AI và chấm điểm, sai là hỏng nghiệp vụ.
     */
    private record QuestionBlueprint(String title, String stem,
                                     String optionA, String optionB, String optionC, String optionD,
                                     OptionLabelD correctLabel, String feedback, int difficulty) {
    }

    private static final List<SeedSubject> SEED_SUBJECTS = buildSeedData();

    private static List<SeedSubject> buildSeedData() {
        List<SeedSubject> list = new ArrayList<>();
        list.add(javaSubject());
        list.add(databaseSubject());
        list.add(networkSubject());
        list.add(aiSubject());
        list.add(philosophySubject());
        return List.copyOf(list);
    }

    private static SeedSubject javaSubject() {
        List<SeedChapter> chapters = List.of(
                new SeedChapter("Chương 1: Cú pháp, kiểu dữ liệu và OOP cơ bản", List.of(
                        q("Kiểu dữ liệu nguyên thuỷ", "Trong Java, kiểu dữ liệu nào sau đây lưu được số nguyên 64-bit?",
                                "int", "short", "long", "byte", OptionLabelD.C,
                                "int là 32-bit, short 16-bit, byte 8-bit. long là kiểu nguyên 64-bit duy nhất trong bốn lựa chọn.", 1),
                        q("Từ khoá kế thừa", "Java dùng từ khoá nào để một lớp kế thừa một lớp khác?",
                                "implements", "extends", "inherits", "super", OptionLabelD.B,
                                "extends dùng cho kế thừa lớp; implements dùng cho interface.", 1),
                        q("Tính đóng gói", "Phạm vi truy cập nào khiến thành viên chỉ dùng được trong chính lớp khai báo?",
                                "public", "protected", "default", "private", OptionLabelD.D,
                                "private giới hạn ở mức lớp; default là mức package.", 1),
                        q("Nạp chồng phương thức", "Điều kiện nào là đủ để hai phương thức nạp chồng (overload) hợp lệ?",
                                "Khác kiểu trả về", "Khác danh sách tham số", "Khác tên phương thức", "Khác phạm vi truy cập",
                                OptionLabelD.B,
                                "Overload phân biệt bằng danh sách tham số; chỉ khác kiểu trả về là lỗi biên dịch.", 2),
                        q("Ghi đè phương thức", "Khi ghi đè (override) một phương thức, phạm vi truy cập của lớp con phải:",
                                "Hẹp hơn lớp cha", "Bằng hoặc rộng hơn lớp cha", "Luôn là private", "Luôn là public",
                                OptionLabelD.B,
                                "Không được thu hẹp phạm vi truy cập khi override, nếu không sẽ vi phạm nguyên lý thay thế Liskov.", 2),
                        q("Từ khoá static", "Thành viên static của một lớp thuộc về:",
                                "Từng đối tượng", "Lớp", "Từng luồng", "Từng gói", OptionLabelD.B,
                                "Static gắn với lớp, dùng chung cho mọi instance.", 1),
                        q("String bất biến", "Vì sao String trong Java được gọi là bất biến (immutable)?",
                                "Nội dung không đổi sau khi tạo", "Không thể gán lại biến", "Không thể nối chuỗi", "Luôn nằm trên heap",
                                OptionLabelD.A,
                                "Mọi thao tác như concat đều tạo String mới; đối tượng String cũ không đổi.", 2),
                        q("So sánh chuỗi", "Để so sánh nội dung hai String trong Java nên dùng:",
                                "toán tử ==", "phương thức equals()", "phương thức hashCode()", "toán tử !=", OptionLabelD.B,
                                "== so sánh tham chiếu; equals() so sánh nội dung.", 1),
                        q("Interface", "Một lớp Java có thể implements bao nhiêu interface?",
                                "Chỉ 1", "Tối đa 2", "Nhiều interface", "Không implements được", OptionLabelD.C,
                                "Java cho phép đa kế thừa kiểu qua interface, nhưng chỉ đơn kế thừa lớp.", 1),
                        q("Từ khoá final", "Khai báo một biến là final nghĩa là:",
                                "Không thể gán lại giá trị", "Không thể đọc", "Chỉ dùng trong constructor", "Chỉ dùng cho lớp",
                                OptionLabelD.A,
                                "final trên biến khoá việc gán lại; trên lớp là cấm kế thừa; trên phương thức là cấm override.", 1)
                )),
                new SeedChapter("Chương 2: Ngoại lệ, Collections và Generic", List.of(
                        q("Cây kế thừa ngoại lệ", "Lớp nào là lớp gốc của mọi ngoại lệ trong Java?",
                                "Exception", "Throwable", "Error", "RuntimeException", OptionLabelD.B,
                                "Throwable là gốc; Exception và Error đều kế thừa từ nó.", 1),
                        q("Checked exception", "Ngoại lệ nào bắt buộc phải khai báo hoặc xử lý khi biên dịch?",
                                "NullPointerException", "ArithmeticException", "IOException", "ArrayIndexOutOfBoundsException",
                                OptionLabelD.C,
                                "Ba lựa chọn còn lại là RuntimeException, không kiểm tra lúc biên dịch.", 2),
                        q("Khối finally", "Khối finally có đặc điểm gì?",
                                "Chỉ chạy khi có ngoại lệ", "Luôn chạy dù có ngoại lệ hay không", "Chỉ chạy khi không có ngoại lệ",
                                "Không chạy khi có return", OptionLabelD.B,
                                "finally chạy trong cả hai nhánh; ngoại lệ duy nhất là khi JVM bị dừng đột ngột.", 1),
                        q("ArrayList và LinkedList", "Thao tác nào ArrayList làm nhanh hơn LinkedList?",
                                "Chèn ở đầu danh sách", "Truy cập theo chỉ số", "Xoá ở giữa danh sách", "Duyệt bằng iterator",
                                OptionLabelD.B,
                                "ArrayList truy cập theo chỉ số O(1); chèn/xoá đầu mảng tốn O(n).", 2),
                        q("HashMap", "Trong HashMap, hai khoá có hashCode bằng nhau nhưng equals khác nhau sẽ:",
                                "Ghi đè lên nhau", "Nằm cùng một bucket, phân biệt nhờ equals", "Gây lỗi biên dịch",
                                "Không lưu được", OptionLabelD.B,
                                "Đây là hiện tượng va chạm hash; HashMap dùng equals để phân biệt trong cùng bucket.", 2),
                        q("HashSet trùng lặp", "Khi thêm phần tử đã tồn tại vào HashSet, điều gì xảy ra?",
                                "Ném ngoại lệ", "Phần tử cũ bị thay thế", "Không có gì thay đổi", "Tạo phần tử trùng",
                                OptionLabelD.C,
                                "HashSet dựa trên HashMap nên bản ghi trùng bị bỏ qua, không báo lỗi.", 1),
                        q("Generic", "Lợi ích chính của Generic là gì?",
                                "Giảm bộ nhớ", "Kiểm tra kiểu lúc biên dịch và bỏ ép kiểu thủ công", "Tăng tốc độ khởi động",
                                "Cho phép đa hình", OptionLabelD.B,
                                "Generic đưa lỗi kiểu từ runtime về compile time.", 2),
                        q("try-with-resources", "Cấu trúc try-with-resources yêu cầu tài nguyên phải:",
                                "Là String", "Implements AutoCloseable", "Là static", "Kế thừa Thread", OptionLabelD.B,
                                "Java tự gọi close() trên mọi tài nguyên implements AutoCloseable khi rời khối.", 2),
                        q("Iterator xoá", "Cách an toàn để xoá phần tử khi duyệt một Collection là:",
                                "Gọi remove() trên chính collection", "Dùng Iterator.remove()", "Đặt phần tử về null",
                                "Dùng for-each rồi xoá", OptionLabelD.B,
                                "Xoá trực tiếp khi đang duyệt gây ConcurrentModificationException.", 2),
                        q("Comparator", "Interface nào dùng để định nghĩa thứ tự tuỳ biến cho Collections.sort?",
                                "Comparable", "Comparator", "Iterable", "Cloneable", OptionLabelD.B,
                                "Comparable định nghĩa thứ tự tự nhiên của lớp; Comparator định nghĩa thứ tự ngoài.", 2)
                )),
                new SeedChapter("Chương 3: Spring Boot và REST API", List.of(
                        q("Stereotype Spring", "Annotation nào đánh dấu một lớp là bean dịch vụ trong Spring?",
                                "@ComponentScan", "@Service", "@Configuration", "@Bean", OptionLabelD.B,
                                "@Service là chuyên biệt hoá của @Component cho tầng nghiệp vụ.", 1),
                        q("Tiêm phụ thuộc", "Spring ưu tiên hình thức tiêm phụ thuộc nào vì lý do thiết kế?",
                                "Field injection", "Constructor injection", "Setter injection", "Method injection",
                                OptionLabelD.B,
                                "Constructor injection cho phép trường final, dễ test và phát hiện vòng phụ thuộc sớm.", 2),
                        q("@RestController", "@RestController tương đương với tổ hợp nào?",
                                "@Controller + @ResponseBody", "@Component + @Service", "@Configuration + @Bean",
                                "@Service + @Repository", OptionLabelD.A,
                                "@RestController là @Controller kèm @ResponseBody nên giá trị trả về được tuần tự hoá thẳng.", 2),
                        q("Ánh xạ HTTP", "Annotation nào xử lý riêng yêu cầu GET?",
                                "@PostMapping", "@GetMapping", "@PutMapping", "@RequestMapping", OptionLabelD.B,
                                "@GetMapping là dạng rút gọn của @RequestMapping(method = GET).", 1),
                        q("Tham số đường dẫn", "Annotation nào lấy giá trị biến từ URL như /questions/{id}?",
                                "@RequestParam", "@PathVariable", "@RequestBody", "@RequestHeader", OptionLabelD.B,
                                "@RequestParam đọc query string; @PathVariable đọc đoạn đường dẫn.", 1),
                        q("Mã trạng thái", "Mã HTTP nào phù hợp khi tạo mới một tài nguyên thành công?",
                                "200", "201", "204", "302", OptionLabelD.B,
                                "201 Created kèm header Location là chuẩn REST cho thao tác tạo.", 2),
                        q("Xung đột phiên bản", "Khi khoá lạc quan (@Version) bị lệch, mã HTTP nên trả là:",
                                "400", "404", "409", "500", OptionLabelD.C,
                                "409 Conflict thể hiện xung đột trạng thái, đúng ngữ nghĩa khi client gửi version cũ.", 3),
                        q("spring.jpa.hibernate.ddl-auto=validate", "Đặt ddl-auto=validate nhằm mục đích gì?",
                                "Tự tạo bảng khi khởi động", "Kiểm tra entity khớp schema đã có", "Xoá bảng khi dừng",
                                "Sinh script SQL", OptionLabelD.B,
                                "validate không thay đổi schema mà báo lỗi khi entity lệch bảng thật — đúng cho môi trường có migration.", 3),
                        q("Giao dịch", "@Transactional mặc định sẽ:",
                                "Chỉ đọc", "Rollback khi RuntimeException", "Luôn commit", "Vô hiệu hoá khoá",
                                OptionLabelD.B,
                                "Mặc định rollback với RuntimeException và Error; checked exception phải khai báo rollbackFor.", 3),
                        q("Cấu hình", "Giá trị nào cho phép ghi đè cấu hình bằng biến môi trường trong Spring Boot?",
                                "${ENV_VAR:default}", "#{ENV_VAR}", "@Value(\"ENV_VAR\")", "%ENV_VAR%", OptionLabelD.A,
                                "Cú pháp ${TÊN:giá_trị_mặc_định} lấy từ biến môi trường, cho phép đặt mặc định.", 2)
                ))
        );
        return new SeedSubject("INT3306", "Lập trình Java",
                "Khoa Công nghệ thông tin — ngôn ngữ Java, hướng đối tượng, Collections và Spring Boot.", chapters);
    }

    private static SeedSubject databaseSubject() {
        List<SeedChapter> chapters = List.of(
                new SeedChapter("Chương 1: Mô hình quan hệ và khoá", List.of(
                        q("Khoá chính", "Khoá chính của một bảng quan hệ phải thoả điều kiện gì?",
                                "Có thể NULL", "Duy nhất và không NULL", "Chỉ cần duy nhất", "Chỉ cần không NULL",
                                OptionLabelD.B,
                                "Ràng buộc khoá chính luôn là UNIQUE + NOT NULL; MySQL tự thêm NOT NULL nếu thiếu.", 1),
                        q("Khoá ngoại", "Khoá ngoại dùng để làm gì?",
                                "Tăng tốc truy vấn", "Bảo đảm toàn vẹn tham chiếu giữa hai bảng", "Mã hoá dữ liệu",
                                "Đánh số bản ghi", OptionLabelD.B,
                                "FK bảo đảm giá trị tham chiếu tồn tại ở bảng cha (hoặc NULL).", 1),
                        q("Dạng chuẩn 1NF", "Một quan hệ đạt dạng chuẩn 1 (1NF) khi:",
                                "Không có thuộc tính đa trị", "Không có phụ thuộc bắc cầu", "Mọi khoá là tổ hợp",
                                "Không có khoá ngoại", OptionLabelD.A,
                                "1NF yêu cầu mỗi ô là giá trị nguyên tử, không chứa tập hợp.", 1),
                        q("Dạng chuẩn 3NF", "Vi phạm dạng chuẩn 3 (3NF) khi xuất hiện:",
                                "Thuộc tính đa trị", "Phụ thuộc hàm bắc cầu vào khoá", "Khoá tổ hợp", "Khoá ngoại",
                                OptionLabelD.B,
                                "3NF loại bỏ phụ thuộc bắc cầu giữa thuộc tính không khoá.", 2),
                        q("Khoá tổ hợp", "Khoá tổ hợp (composite key) là khoá gồm:",
                                "Một cột", "Nhiều cột cùng tham gia", "Hai bảng", "Một chỉ mục", OptionLabelD.B,
                                "Khoá tổ hợp xác định duy nhất một dòng bằng tổ hợp nhiều cột.", 1),
                        q("Toàn vẹn thực thể", "Ràng buộc nào chống lại việc khoá chính nhận giá trị NULL?",
                                "Toàn vẹn thực thể", "Toàn vẹn tham chiếu", "Toàn vẹn miền", "Toàn vẹn nghiệp vụ",
                                OptionLabelD.A,
                                "Toàn vẹn thực thể yêu cầu khoá chính không NULL và duy nhất.", 2),
                        q("Chỉ mục", "Thêm INDEX vào cột giúp ích chủ yếu cho:",
                                "Ghi dữ liệu", "Đọc và lọc theo cột đó", "Tiết kiệm dung lượng", "Mã hoá", OptionLabelD.B,
                                "Index đánh đổi dung lượng và tốc độ ghi để tăng tốc tìm kiếm.", 2),
                        q("UNIQUE", "Khác biệt giữa UNIQUE và PRIMARY KEY là:",
                                "UNIQUE cho phép nhiều NULL hơn, PRIMARY KEY không", "Không khác gì", "UNIQUE chỉ 1 cột",
                                "PRIMARY KEY cho NULL", OptionLabelD.A,
                                "MySQL cho phép nhiều giá trị NULL trên cột UNIQUE (NULL không so bằng nhau).", 3),
                        q("FK xoá", "Hành vi ON DELETE RESTRICT có nghĩa là:",
                                "Xoá dây chuyền", "Chặn xoá nếu còn dòng con tham chiếu", "Đặt NULL cho dòng con",
                                "Tự động xoá con", OptionLabelD.B,
                                "RESTRICT (mặc định) bảo vệ dòng cha khỏi bị xoá khi còn dòng con.", 2),
                        q("Sơ đồ ER", "Trong sơ đồ ER, hình thoi biểu diễn:",
                                "Thực thể", "Thuộc tính", "Mối quan hệ", "Khoá", OptionLabelD.C,
                                "Chữ nhật là thực thể, hình bầu là thuộc tính, hình thoi là quan hệ.", 1)
                )),
                new SeedChapter("Chương 2: Ngôn ngữ truy vấn SQL", List.of(
                        q("Lấy dữ liệu", "Câu lệnh nào lấy dữ liệu từ bảng?",
                                "GET", "SELECT", "FETCH", "SHOW", OptionLabelD.B,
                                "SELECT là câu lệnh đọc dữ liệu chuẩn của SQL.", 1),
                        q("Lọc điều kiện", "Mệnh đề nào lọc dòng trước khi nhóm?",
                                "HAVING", "WHERE", "GROUP BY", "ORDER BY", OptionLabelD.B,
                                "WHERE lọc dòng; HAVING lọc nhóm sau khi GROUP BY.", 1),
                        q("Lọc nhóm", "Mệnh đề nào lọc kết quả sau khi nhóm dữ liệu?",
                                "WHERE", "HAVING", "LIMIT", "OFFSET", OptionLabelD.B,
                                "HAVING áp dụng trên tập đã nhóm, thường đi với hàm tổng hợp.", 2),
                        q("INNER JOIN", "INNER JOIN trả về:",
                                "Tất cả dòng của bảng trái", "Chỉ các dòng khớp ở cả hai bảng", "Tất cả dòng của bảng phải",
                                "Tích Descartes", OptionLabelD.B,
                                "INNER JOIN cần điều kiện khớp ở cả hai phía; dòng không khớp bị loại.", 1),
                        q("LEFT JOIN", "LEFT JOIN khác INNER JOIN ở chỗ:",
                                "Giữ mọi dòng bảng trái, kể cả không khớp", "Không cần ON", "Nhanh hơn",
                                "Chỉ dùng cho 2 bảng", OptionLabelD.A,
                                "LEFT JOIN giữ đủ dòng bảng trái và điền NULL cho phía phải khi không khớp.", 2),
                        q("Hàm tổng hợp", "Hàm nào đếm số dòng?",
                                "SUM()", "COUNT()", "AVG()", "MAX()", OptionLabelD.B,
                                "COUNT(*) đếm số dòng; COUNT(cột) bỏ qua NULL.", 1),
                        q("Nhóm dữ liệu", "GROUP BY dùng để:",
                                "Sắp xếp", "Gom nhóm dòng theo cột để tính tổng hợp", "Nối bảng", "Xoá trùng",
                                OptionLabelD.B,
                                "GROUP BY tạo nhóm; mỗi nhóm trả 1 dòng trong kết quả.", 1),
                        q("Sắp xếp", "Mệnh đề ORDER BY DESC có tác dụng:",
                                "Sắp tăng", "Sắp giảm", "Xoá trùng", "Giới hạn dòng", OptionLabelD.B,
                                "DESC = giảm dần; mặc định là ASC.", 1),
                        q("Phân trang", "Trong MySQL, để lấy 20 dòng bắt đầu từ dòng thứ 40 nên viết:",
                                "LIMIT 40, 20", "LIMIT 20 OFFSET 40", "Cả hai đều đúng ở MySQL", "TOP 20", OptionLabelD.C,
                                "MySQL hỗ trợ cả hai cú pháp; 'LIMIT offset, count' và 'LIMIT count OFFSET offset' tương đương.", 3),
                        q("Xoá dữ liệu", "Khác biệt giữa DELETE và TRUNCATE là:",
                                "Không khác gì", "TRUNCATE không ghi log và nhanh hơn, không lọc được WHERE", "DELETE nhanh hơn",
                                "TRUNCATE lọc được WHERE", OptionLabelD.B,
                                "TRUNCATE thao tác mức bảng, không có WHERE và reset AUTO_INCREMENT.", 3)
                )),
                new SeedChapter("Chương 3: Giao dịch, chỉ mục và tối ưu", List.of(
                        q("ACID", "Tính chất A trong ACID là gì?",
                                "Atomicity", "Availability", "Authentication", "Aggregation", OptionLabelD.A,
                                "ACID = Atomicity, Consistency, Isolation, Durability.", 1),
                        q("Cô lập", "Mức cô lập nào ngăn được hiện tượng đọc bẩn (dirty read)?",
                                "READ UNCOMMITTED", "READ COMMITTED", "Cả hai đều không", "Không mức nào", OptionLabelD.B,
                                "READ UNCOMMITTED cho phép đọc dữ liệu chưa commit; READ COMMITTED ngăn điều đó.", 2),
                        q("Read phantom", "Hiện tượng đọc bóng (phantom read) là:",
                                "Đọc dữ liệu chưa commit", "Lần đọc sau thấy thêm dòng mới xuất hiện", "Đọc cùng dòng hai lần khác nhau",
                                "Đọc dữ liệu đã xoá", OptionLabelD.B,
                                "Phantom xảy ra khi tập dòng kết quả thay đổi giữa hai lần đọc trong cùng giao dịch.", 3),
                        q("Chỉ mục tổ hợp", "Chỉ mục (a, b) KHÔNG tối ưu cho truy vấn lọc theo:",
                                "a", "a và b", "b", "a, b và sắp xếp theo a", OptionLabelD.C,
                                "Tiền tố trái (leftmost prefix): chỉ mình b không dùng được chỉ mục (a, b).", 3),
                        q("EXPLAIN", "Lệnh EXPLAIN dùng để:",
                                "Sao lưu dữ liệu", "Xem kế hoạch thực thi truy vấn", "Tạo chỉ mục", "Xoá chỉ mục",
                                OptionLabelD.B,
                                "EXPLAIN cho biết MySQL truy cập bảng bằng chỉ mục nào, số dòng ước tính.", 2),
                        q("Deadlock", "Deadlock trong cơ sở dữ liệu xảy ra khi:",
                                "Một bảng quá lớn", "Hai giao dịch chờ khoá của nhau", "Thiếu RAM", "Chỉ mục hỏng",
                                OptionLabelD.B,
                                "Chu trình chờ khoá; InnoDB sẽ chọn một giao dịch làm nạn nhân và rollback.", 3),
                        q("Sao lưu", "mysqldump tạo ra loại tệp nào?",
                                "Tệp nhị phân nén", "Tệp SQL chứa DDL và DML", "Ảnh đĩa", "Tệp cấu hình",
                                OptionLabelD.B,
                                "mysqldump xuất câu lệnh SQL; có thể khôi phục bằng mysql < file.sql.", 2),
                        q("Khoá lạc quan", "Khoá lạc quan (optimistic locking) dùng cột nào để phát hiện xung đột?",
                                "Cột mô tả", "Cột version tăng mỗi lần cập nhật", "Khoá ngoại", "Cột tên", OptionLabelD.B,
                                "UPDATE ... WHERE version = ? — nếu 0 dòng bị ảnh hưởng nghĩa là đã có người sửa trước.", 2),
                        q("Chuẩn hoá", "Phi chuẩn hoá (denormalization) đánh đổi điều gì?",
                                "Tăng trùng lặp để giảm JOIN và tăng tốc đọc", "Giảm dung lượng", "Tăng tính toàn vẹn",
                                "Giảm tốc độ đọc", OptionLabelD.A,
                                "Denormalize đổi tính nhất quán lấy tốc độ đọc trong hệ thống đọc nhiều.", 3),
                        q("Vào/ra đĩa", "Trong B+Tree index của InnoDB, dữ liệu khoá được lưu ở đâu?",
                                "Chỉ ở nút lá", "Chỉ ở nút gốc", "Ở cả nút lá và nút trong", "Trong buffer pool",
                                OptionLabelD.C,
                                "InnoDB dùng B+Tree, khoá có cả nút trong để điều hướng và toàn bộ dữ liệu ở nút lá.", 3)
                ))
        );
        return new SeedSubject("INT3202", "Cơ sở dữ liệu",
                "Khoa Công nghệ thông tin — mô hình quan hệ, SQL và tối ưu truy vấn.", chapters);
    }

    private static SeedSubject networkSubject() {
        List<SeedChapter> chapters = List.of(
                new SeedChapter("Chương 1: Kiến trúc phân tầng OSI và TCP/IP", List.of(
                        q("Mô hình OSI", "Mô hình OSI có bao nhiêu tầng?",
                                "4", "5", "7", "8", OptionLabelD.C,
                                "OSI có 7 tầng; TCP/IP gộp còn 4 tầng thực dụng.", 1),
                        q("Tầng giao vận", "Đơn vị dữ liệu ở tầng giao vận (transport) được gọi là:",
                                "Frame", "Packet", "Segment", "Bit", OptionLabelD.C,
                                "TCP gọi là segment; UDP gọi là datagram.", 2),
                        q("Tầng mạng", "Giao thức nào hoạt động ở tầng mạng của mô hình OSI?",
                                "TCP", "IP", "HTTP", "Ethernet", OptionLabelD.B,
                                "IP định địa chỉ luận lý và định tuyến ở tầng 3.", 1),
                        q("Đóng gói", "Quá trình thêm header khi dữ liệu đi xuống các tầng gọi là:",
                                "Decapsulation", "Encapsulation", "Fragmentation", "Multiplexing", OptionLabelD.B,
                                "Encapsulation bọc dữ liệu; decapsulation mở gói ở phía nhận.", 1),
                        q("MAC và IP", "Địa chỉ MAC thuộc tầng nào?",
                                "Tầng liên kết dữ liệu", "Tầng mạng", "Tầng giao vận", "Tầng ứng dụng", OptionLabelD.A,
                                "MAC là địa chỉ vật lý ở tầng 2, chỉ có nghĩa trong cùng một miền quảng bá.", 2),
                        q("Bắt tay TCP", "Quá trình bắt tay TCP cần trao đổi bao nhiêu bước?",
                                "2", "3", "4", "1", OptionLabelD.B,
                                "Three-way handshake: SYN, SYN-ACK, ACK.", 1),
                        q("Cổng dịch vụ", "Cổng mặc định của HTTPS là:",
                                "80", "443", "21", "25", OptionLabelD.B,
                                "HTTP là 80; HTTPS là 443; FTP 21; SMTP 25.", 1),
                        q("UDP", "UDP khác TCP ở điểm cơ bản nào?",
                                "Không hướng kết nối, không bảo đảm thứ tự", "Có bắt tay 3 bước", "Có kiểm soát tắc nghẽn",
                                "Luôn tin cậy", OptionLabelD.A,
                                "UDP là best-effort: nhẹ, độ trễ thấp, nhưng có thể mất gói hoặc sai thứ tự.", 2),
                        q("ICMP", "Lệnh ping dùng giao thức nào?",
                                "TCP", "UDP", "ICMP", "ARP", OptionLabelD.C,
                                "ping gửi ICMP Echo Request và nhận Echo Reply.", 2),
                        q("ARP", "ARP dùng để làm gì?",
                                "Phân giải địa chỉ IP sang MAC", "Phân giải tên miền", "Định tuyến giữa các mạng",
                                "Cấp phát IP động", OptionLabelD.A,
                                "ARP hỏi \"ai đang giữ IP này\" trong miền quảng bá để lấy MAC.", 2)
                )),
                new SeedChapter("Chương 2: Giao thức ứng dụng HTTP và REST", List.of(
                        q("HTTP không trạng thái", "HTTP được gọi là giao thức không trạng thái vì:",
                                "Không dùng TCP", "Mỗi yêu cầu độc lập, máy chủ không ghi nhớ yêu cầu trước", "Không có cookie",
                                "Không có header", OptionLabelD.B,
                                "Stateless: trạng thái phiên phải do client mang theo (cookie, token) hoặc lưu riêng.", 2),
                        q("Mã 2xx", "Nhóm mã trạng thái 2xx biểu thị:",
                                "Lỗi phía máy khách", "Xử lý thành công", "Chuyển hướng", "Lỗi phía máy chủ", OptionLabelD.B,
                                "2xx = success; 3xx = redirect; 4xx = client error; 5xx = server error.", 1),
                        q("Mã 401 và 403", "Khác biệt giữa 401 và 403 là:",
                                "401 = chưa xác thực, 403 = đã xác thực nhưng không có quyền", "Không khác gì",
                                "401 = không tìm thấy, 403 = lỗi máy chủ", "403 = cần đăng nhập", OptionLabelD.A,
                                "401 Unauthorized nghĩa là thiếu hoặc sai thông tin xác thực; 403 Forbidden là bị từ chối truy cập.", 2),
                        q("Idempotent", "Phương thức HTTP nào là idempotent (gọi lại không đổi kết quả cuối)?",
                                "POST", "PUT", "PATCH", "Không có", OptionLabelD.B,
                                "PUT thay thế toàn bộ tài nguyên nên gọi lại vẫn cùng trạng thái; POST không idempotent.", 3),
                        q("REST danh từ", "Theo chuẩn REST, URL nên trỏ tới:",
                                "Động từ hành động", "Tài nguyên (danh từ)", "Tên hàm", "Mã lỗi", OptionLabelD.B,
                                "REST dùng danh từ cho tài nguyên, để phương thức HTTP biểu đạt hành động.", 2),
                        q("Content-Type", "Header Content-Type cho biết:",
                                "Kích thước dữ liệu", "Kiểu nội dung của thực thể trong thông điệp", "Địa chỉ máy chủ",
                                "Phương thức HTTP", OptionLabelD.B,
                                "Ví dụ application/json; charset=utf-8 giúp máy nhận biết cách giải mã phần thân.", 1),
                        q("Bearer token", "Trong header Authorization: Bearer <token>, token là:",
                                "Mật khẩu người dùng", "Chuỗi xác thực thay cho mật khẩu ở mỗi yêu cầu", "Tên người dùng",
                                "Số thứ tự phiên", OptionLabelD.B,
                                "Bearer là vé thông hành: mang theo mỗi request thay vì gửi lại mật khẩu.", 2),
                        q("JWT ba phần", "JWT gồm ba phần, ngăn cách bởi dấu chấm:",
                                "header, payload, signature", "user, pass, salt", "key, iv, tag", "start, body, end",
                                OptionLabelD.A,
                                "Signature bảo đảm tính toàn vẹn; payload chỉ mã hoá base64, KHÔNG mã hoá bí mật.", 2),
                        q("TLS", "Chức năng chính của TLS là:",
                                "Nén dữ liệu", "Mã hoá và xác thực kênh truyền", "Cân bằng tải", "Lưu cache",
                                OptionLabelD.B,
                                "TLS cung cấp bí mật (mã hoá), toàn vẹn (MAC) và xác thực (chứng chỉ).", 2),
                        q("Mã 429", "Mã 429 Too Many Requests dùng để:",
                                "Báo hết dung lượng đĩa", "Báo vượt giới hạn tần suất (rate limit)", "Báo sai mật khẩu",
                                "Báo lỗi cú pháp", OptionLabelD.B,
                                "Máy chủ dùng 429 khi client gửi quá nhiều yêu cầu trong một khoảng thời gian.", 2)
                )),
                new SeedChapter("Chương 3: Bảo mật mạng và xác thực", List.of(
                        q("Đối xứng và bất đối xứng", "Mã hoá bất đối xứng dùng bao nhiêu khoá?",
                                "1 khoá chung", "2 khoá: công khai và bí mật", "2 khoá giống nhau", "Không cần khoá",
                                OptionLabelD.B,
                                "Khoá công khai mã hoá; khoá bí mật giải mã (hoặc ngược lại để ký số).", 1),
                        q("Tấn công chèn", "SQL Injection phòng bằng cách:",
                                "Nối chuỗi truy vấn", "Dùng tham số hoá (prepared statement)", "Ẩn mã lỗi",
                                "Tắt WAF", OptionLabelD.B,
                                "Prepared statement tách dữ liệu khỏi câu lệnh, nên dữ liệu không thể đổi cấu trúc truy vấn.", 2),
                        q("XSS", "XSS (Cross-Site Scripting) xảy ra khi:",
                                "Máy chủ bị chiếm quyền", "Mã độc được chèn và thực thi trong trình duyệt nạn nhân",
                                "Chặn gói tin", "Đoán mật khẩu", OptionLabelD.B,
                                "Phòng bằng escape đầu ra và Content-Security-Policy, không chỉ lọc đầu vào.", 2),
                        q("CSRF", "CSRF phòng hiệu quả bằng:",
                                "Chỉ dùng GET", "CSRF token và SameSite cookie", "Ẩn form", "Tắt cookie", OptionLabelD.B,
                                "Token gắn với phiên khiến yêu cầu giả mạo không có tham số bí mật để gửi kèm.", 3),
                        q("Băm mật khẩu", "Để lưu mật khẩu an toàn nên dùng:",
                                "MD5", "SHA-1 nhiều vòng", "BCrypt với salt", "Base64", OptionLabelD.C,
                                "BCrypt có salt tích hợp và hệ số công việc điều chỉnh được, chống dò vét cạn tốt.", 2),
                        q("TOTP", "Mã TOTP 6 số có hiệu lực trong bao lâu theo mặc định phổ biến?",
                                "10 giây", "30 giây", "5 phút", "1 giờ", OptionLabelD.B,
                                "Chu kỳ 30 giây là chuẩn RFC 6238 phổ biến; máy chủ thường chấp nhận lệch ±1 chu kỳ.", 2),
                        q("Chống dò TOTP", "Dò mã TOTP nhiều lần nên bị chặn vì:",
                                "Tốn dung lượng", "Không gian mã 10^6 nhưng có thể dò trong thời gian hiệu lực",
                                "Mã TOTP luôn đúng", "Không cần chặn", OptionLabelD.B,
                                "Không gian 1 triệu tổ hợp là nhỏ với máy tính; cần giới hạn số lần thử và khoá tạm.", 3),
                        q("Nguyên tắc tối thiểu", "Nguyên tắc đặc quyền tối thiểu (least privilege) yêu cầu:",
                                "Cấp nhiều quyền để tiện", "Chỉ cấp quyền cần thiết cho công việc", "Không cấp quyền nào",
                                "Cấp quyền root", OptionLabelD.B,
                                "Giảm phạm vi thiệt hại khi tài khoản hoặc tiến trình bị chiếm.", 1),
                        q("Mã hoá dữ liệu TLS", "Sau khi TLS làm gì thì proxy trung gian không đọc được nội dung?",
                                "Nén", "Trao đổi khoá và mã hoá dữ liệu ứng dụng", "Đổi DNS", "Đổi MAC", OptionLabelD.B,
                                "Sau handshake TLS, dữ liệu ứng dụng được mã hoá bằng khoá phiên.", 2),
                        q("Audit log", "Ghi audit cho mỗi lần đăng nhập nhằm:",
                                "Tăng dung lượng", "Truy vết sự cố và phát hiện truy cập bất thường", "Tăng tốc đăng nhập",
                                "Giảm chi phí", OptionLabelD.B,
                                "Audit log trả lời được ai, khi nào, từ đâu, kết quả ra sao — cần cho điều tra và tuân thủ.", 2)
                ))
        );
        return new SeedSubject("INT3304", "Mạng máy tính",
                "Khoa Công nghệ thông tin — kiến trúc mạng, giao thức HTTP/REST và bảo mật.", chapters);
    }

    private static SeedSubject aiSubject() {
        List<SeedChapter> chapters = List.of(
                new SeedChapter("Chương 1: Tìm kiếm và biểu diễn tri thức", List.of(
                        q("Tìm kiếm mù", "Thuật toán tìm kiếm mù là:",
                                "A*", "BFS", "Hill climbing", "Simulated annealing", OptionLabelD.B,
                                "BFS/DFS không dùng heuristic nên gọi là tìm kiếm mù (uninformed search).", 1),
                        q("A*", "Hàm đánh giá của A* có dạng:",
                                "f = g + h", "f = g × h", "f = h - g", "f = g", OptionLabelD.A,
                                "g là chi phí đã đi, h là ước lượng còn lại; h admissible bảo đảm A* tối ưu.", 2),
                        q("Admissible", "Heuristic admissible nghĩa là:",
                                "Không bao giờ đánh giá cao hơn chi phí thật", "Luôn bằng 0", "Luôn đúng", "Tính nhanh",
                                OptionLabelD.A,
                                "Đánh giá thấp hơn hoặc bằng chi phí thật là điều kiện để A* không bỏ sót nghiệm tối ưu.", 3),
                        q("BFS và DFS", "BFS bảo đảm tìm được đường đi ngắn nhất khi:",
                                "Mọi cạnh cùng trọng số", "Mọi cạnh khác trọng số", "Đồ thị có chu trình", "Đồ thị vô hướng",
                                OptionLabelD.A,
                                "BFS duyệt theo mức nên tối ưu khi trọng số đồng nhất; có trọng số khác nhau cần Dijkstra.", 2),
                        q("Logic mệnh đề", "Phép kéo theo p → q sai khi nào?",
                                "p đúng, q đúng", "p đúng, q sai", "p sai, q đúng", "p sai, q sai", OptionLabelD.B,
                                "Kéo theo chỉ sai khi tiền đề đúng hậu đề sai.", 2),
                        q("CSP", "Bài toán CSP gồm:",
                                "Biến, miền giá trị, ràng buộc", "Chỉ biến", "Chỉ ràng buộc", "Đồ thị có trọng số",
                                OptionLabelD.A,
                                "Constraint Satisfaction Problem: gán giá trị cho biến sao cho thoả mọi ràng buộc.", 2),
                        q("Đồ thị tri thức", "Trong đồ thị tri thức, cạnh biểu diễn:",
                                "Trọng số", "Quan hệ giữa hai thực thể", "Chi phí", "Thứ tự duyệt", OptionLabelD.B,
                                "Node là thực thể, cạnh là quan hệ (ví dụ: sinh viên —học— môn).", 1),
                        q("Minimax", "Thuật toán Minimax dùng cho:",
                                "Bài toán tối ưu mạng", "Trò chơi có hai người chơi đối kháng", "Phân cụm", "Nén dữ liệu",
                                OptionLabelD.B,
                                "Minimax giả định đối thủ chơi tối ưu; alpha-beta cắt tỉa để giảm nút duyệt.", 2),
                        q("Độ phức tạp", "Độ phức tạp thời gian của BFS trên đồ thị V đỉnh, E cạnh là:",
                                "O(V)", "O(E)", "O(V + E)", "O(V × E)", OptionLabelD.C,
                                "Mỗi đỉnh vào hàng đợi một lần và mỗi cạnh được xét một lần ⇒ O(V+E).", 2),
                        q("Hill climbing", "Nhược điểm chính của hill climbing là:",
                                "Chậm", "Kẹt ở cực trị địa phương", "Tốn bộ nhớ", "Không xác định", OptionLabelD.B,
                                "Leo đồi luôn đi theo hướng tốt hơn trước mắt nên không thoát được cực tiểu địa phương.", 3)
                )),
                new SeedChapter("Chương 2: Học máy và mạng nơ-ron", List.of(
                        q("Học có giám sát", "Học có giám sát cần dữ liệu:",
                                "Có nhãn", "Không nhãn", "Chỉ đặc trưng", "Chỉ nhãn", OptionLabelD.A,
                                "Có nhãn để tính sai số giữa dự đoán và đáp án đúng trong lúc huấn luyện.", 1),
                        q("Hồi quy và phân loại", "Bài toán dự đoán giá nhà thuộc loại:",
                                "Phân loại", "Hồi quy", "Phân cụm", "Giảm chiều", OptionLabelD.B,
                                "Đầu ra là số liên tục ⇒ hồi quy; đầu ra là nhãn rời rạc ⇒ phân loại.", 1),
                        q("Quá khớp", "Overfitting là hiện tượng:",
                                "Mô hình học tốt trên tập huấn luyện nhưng kém trên dữ liệu mới", "Mô hình học không kịp",
                                "Dữ liệu thiếu nhãn", "Mô hình quá nhỏ", OptionLabelD.A,
                                "Giảm overfitting bằng regularization, early stopping, thêm dữ liệu, dropout.", 2),
                        q("Hàm kích hoạt", "Hàm kích hoạt nào gây ra vấn đề gradient triệt tiêu (vanishing gradient)?",
                                "ReLU", "Sigmoid", "Leaky ReLU", "Không hàm nào", OptionLabelD.B,
                                "Sigmoid bão hoà ở hai đầu khiến đạo hàm rất nhỏ, gradient suy giảm khi lan truyền ngược.", 3),
                        q("Lan truyền ngược", "Backpropagation dùng quy tắc nào để tính đạo hàm?",
                                "Quy tắc chuỗi", "Quy tắc cộng", "Quy tắc hình học", "Không dùng đạo hàm", OptionLabelD.A,
                                "Đạo hàm hàm hợp được tính ngược từ đầu ra về đầu vào theo quy tắc chuỗi.", 2),
                        q("Đánh giá phân loại", "Độ chính xác (accuracy) không phù hợp khi:",
                                "Dữ liệu cân bằng", "Dữ liệu mất cân bằng nghiêm trọng", "Có nhiều đặc trưng", "Dữ liệu lớn",
                                OptionLabelD.B,
                                "Với dữ liệu lệch, mô hình đoán toàn nhãn đa số vẫn đạt accuracy cao nhưng vô dụng.", 3),
                        q("Precision và Recall", "Recall cao nghĩa là:",
                                "Ít bỏ sót mẫu dương thật", "Ít báo nhầm dương", "Mô hình nhỏ", "Huấn luyện nhanh",
                                OptionLabelD.A,
                                "Recall = TP/(TP+FN): đo khả năng phát hiện hết mẫu dương; bỏ sót càng ít càng cao.", 3),
                        q("K-means", "K-means thuộc nhóm học:",
                                "Có giám sát", "Không giám sát (phân cụm)", "Tăng cường", "Bán giám sát", OptionLabelD.B,
                                "K-means tối thiểu tổng bình phương khoảng cách tới tâm cụm, không cần nhãn.", 2),
                        q("Chuẩn hoá", "Chuẩn hoá đặc trưng giúp:",
                                "Tăng tốc hội tụ và tránh lệch thang đo", "Tăng số tham số", "Giảm độ chính xác",
                                "Không có tác dụng", OptionLabelD.A,
                                "Đưa các đặc trưng về cùng thang đo giúp gradient descent ổn định và nhanh hội tụ.", 2),
                        q("Tập kiểm thử", "Tập kiểm thử (test set) nhằm:",
                                "Huấn luyện mô hình", "Đánh giá khách quan trên dữ liệu chưa thấy", "Điều chỉnh tham số",
                                "Lưu mô hình", OptionLabelD.B,
                                "Dùng test set để đánh giá; tuning nên làm trên validation set để tránh rò rỉ thông tin.", 2)
                )),
                new SeedChapter("Chương 3: Mô hình ngôn ngữ lớn (LLM)", List.of(
                        q("Transformer", "Cơ chế cốt lõi của kiến trúc Transformer là:",
                                "Convolution", "Self-attention", "RNN", "Pooling", OptionLabelD.B,
                                "Self-attention cho phép mọi vị trí tham chiếu mọi vị trí khác, song song hoá được.", 2),
                        q("Token", "Token trong mô hình ngôn ngữ là:",
                                "Ký tự luôn", "Đơn vị văn bản sau khi tách (có thể là từ hoặc mảnh từ)", "Câu", "Trang",
                                OptionLabelD.B,
                                "Tokenizer tách văn bản thành token; chi phí tính theo số token chứ không theo số từ.", 2),
                        q("Nhiệt độ", "Tăng temperature khi sinh văn bản sẽ:",
                                "Làm đầu ra ngẫu nhiên, đa dạng hơn", "Làm đầu ra xác định hơn", "Tăng tốc độ",
                                "Giảm độ dài", OptionLabelD.A,
                                "Temperature thấp ⇒ phân phối nhọn (an toàn); cao ⇒ phân phối đều (sáng tạo hơn, dễ lan man).", 2),
                        q("Hallucination", "Hiện tượng mô hình bịa thông tin không có thật gọi là:",
                                "Overfitting", "Hallucination", "Underfitting", "Drift", OptionLabelD.B,
                                "Giảm bằng RAG, ràng buộc trích dẫn và kiểm định con người — đúng tinh thần QBank.", 2),
                        q("RAG", "RAG (Retrieval-Augmented Generation) kết hợp:",
                                "Truy hồi tài liệu với sinh văn bản", "Hai mô hình phân loại", "Nén và lượng tử hoá",
                                "Hai bộ tokenizer", OptionLabelD.A,
                                "Truy hồi tài liệu liên quan rồi đưa vào ngữ cảnh để mô hình trả lời có căn cứ.", 2),
                        q("Embedding", "Embedding ánh xạ văn bản thành:",
                                "Chuỗi ký tự", "Vectơ số thực", "Đồ thị", "Bảng băm", OptionLabelD.B,
                                "Các văn bản gần nghĩa có vectơ gần nhau, dùng để tìm kiếm ngữ nghĩa.", 1),
                        q("Fine-tuning", "Fine-tuning khác pre-training ở chỗ:",
                                "Huấn luyện tiếp trên dữ liệu chuyên biệt", "Không cần dữ liệu", "Chỉ thay tokenizer",
                                "Chỉ tăng tham số", OptionLabelD.A,
                                "Pre-training học ngôn ngữ chung; fine-tuning thích nghi mô hình với miền/nhiệm vụ cụ thể.", 2),
                        q("Kiểm định AI", "Trong hệ thống ngân hàng câu hỏi, mục tiêu của kiểm định AI là:",
                                "Tạo câu hỏi mới thay người", "Phát hiện lỗi nội dung và gợi ý sửa để người duyệt quyết định",
                                "Chấm điểm sinh viên", "Nén dữ liệu", OptionLabelD.B,
                                "AI chỉ đề xuất; người biên soạn chấp nhận/từ chối — đúng mô hình human-in-the-loop.", 2),
                        q("Context window", "Context window của một LLM là:",
                                "Dung lượng ổ cứng", "Số token tối đa mô hình xử lý trong một lần", "Số tham số",
                                "Số lớp mạng", OptionLabelD.B,
                                "Vượt context window thì phải cắt bớt hoặc tóm tắt ngữ cảnh.", 2),
                        q("Độ trễ", "Yếu tố nào KHÔNG ảnh hưởng trực tiếp tới độ trễ suy luận của LLM?",
                                "Số token sinh ra", "Kích thước mô hình", "Phần cứng", "Tên biến trong mã nguồn",
                                OptionLabelD.D,
                                "Tên biến không ảnh hưởng; còn token sinh, kích thước mô hình và phần cứng đều ảnh hưởng rõ.", 1)
                ))
        );
        return new SeedSubject("INT3401", "Trí tuệ nhân tạo",
                "Khoa Công nghệ thông tin — tìm kiếm, học máy và mô hình ngôn ngữ lớn.", chapters);
    }

    private static SeedSubject philosophySubject() {
        List<SeedChapter> chapters = List.of(
                new SeedChapter("Chương 1: Chủ nghĩa duy vật biện chứng", List.of(
                        q("Vật chất", "Theo triết học Mác – Lênin, vật chất là:",
                                "Ý thức của con người", "Thực tại khách quan tồn tại độc lập với ý thức", "Sản phẩm của bộ não",
                                "Cảm giác chủ quan", OptionLabelD.B,
                                "Vật chất là phạm trù triết học chỉ thực tại khách quan, tồn tại không phụ thuộc cảm giác.", 1),
                        q("Phương thức tồn tại", "Phương thức tồn tại của vật chất là:",
                                "Đứng im", "Vận động", "Ý niệm", "Tách rời", OptionLabelD.B,
                                "Vận động là thuộc tính cố hữu; đứng im chỉ là hình thức vận động đặc biệt.", 2),
                        q("Ý thức", "Nguồn gốc tự nhiên của ý thức là:",
                                "Bộ óc người", "Thượng đế", "Ý niệm tuyệt đối", "Không có nguồn gốc", OptionLabelD.A,
                                "Bộ óc người là cơ quan phản ánh; thế giới khách quan là nguồn cung cấp nội dung phản ánh.", 2),
                        q("Nguồn gốc xã hội", "Nguồn gốc xã hội của ý thức là:",
                                "Lao động và ngôn ngữ", "Bẩm sinh", "Di truyền", "Giác quan đơn thuần", OptionLabelD.A,
                                "Lao động và ngôn ngữ là hai yếu tố cơ bản hình thành ý thức con người.", 2),
                        q("Vật chất quyết định", "Theo chủ nghĩa duy vật biện chứng, vật chất và ý thức có quan hệ:",
                                "Ý thức quyết định vật chất", "Vật chất quyết định ý thức, ý thức tác động trở lại",
                                "Hai bên độc lập", "Ý thức sinh ra vật chất", OptionLabelD.B,
                                "Vật chất là cái có trước và quyết định; ý thức có tính độc lập tương đối và tác động trở lại.", 2),
                        q("Phản ánh", "Hình thức phản ánh cao nhất, đặc trưng cho con người là:",
                                "Phản ánh vật lý", "Phản ánh sinh học", "Phản ánh ý thức", "Phản ánh hoá học", OptionLabelD.C,
                                "Ý thức là hình thức phản ánh cao nhất, có tính chủ động và sáng tạo.", 2),
                        q("Vận động", "Theo Ph. Ăngghen, vận động được chia thành bao nhiêu hình thức cơ bản?",
                                "3", "4", "5", "6", OptionLabelD.C,
                                "Năm hình thức: cơ học, vật lý, hoá học, sinh học, xã hội — từ đơn giản đến phức tạp.", 3),
                        q("Không gian và thời gian", "Không gian và thời gian theo quan điểm duy vật biện chứng là:",
                                "Hình thức tồn tại của vật chất", "Ý niệm thuần tuý", "Cảm giác", "Sản phẩm của trí tuệ",
                                OptionLabelD.A,
                                "Không gian và thời gian là hình thức tồn tại khách quan của vật chất vận động.", 2),
                        q("Thế giới quan", "Thế giới quan khoa học dựa trên:",
                                "Tín ngưỡng", "Thành tựu của khoa học và thực tiễn", "Trực giác", "Truyền thuyết", OptionLabelD.B,
                                "Thế giới quan duy vật khoa học dựa trên tri thức khoa học và hoạt động thực tiễn.", 1),
                        q("Thực tiễn", "Thực tiễn là:",
                                "Suy nghĩ thuần tuý", "Hoạt động vật chất có mục đích của con người", "Trực giác",
                                "Cảm xúc", OptionLabelD.B,
                                "Thực tiễn là cơ sở, động lực, mục đích của nhận thức và là tiêu chuẩn kiểm tra chân lý.", 1)
                )),
                new SeedChapter("Chương 2: Phép biện chứng duy vật", List.of(
                        q("Hai nguyên lý", "Hai nguyên lý cơ bản của phép biện chứng duy vật là:",
                                "Mối liên hệ phổ biến và sự phát triển", "Nhân quả và ngẫu nhiên", "Nội dung và hình thức",
                                "Bản chất và hiện tượng", OptionLabelD.A,
                                "Hai nguyên lý nền tảng: mọi sự vật liên hệ, và mọi sự vật đều vận động phát triển.", 2),
                        q("Quy luật mâu thuẫn", "Quy luật nào là hạt nhân của phép biện chứng?",
                                "Lượng – chất", "Mâu thuẫn", "Phủ định của phủ định", "Nhân quả", OptionLabelD.B,
                                "Quy luật thống nhất và đấu tranh của các mặt đối lập là hạt nhân, chỉ ra nguồn gốc vận động.", 2),
                        q("Mâu thuẫn", "Mâu thuẫn biện chứng là:",
                                "Sự loại trừ lẫn nhau tuyệt đối", "Sự thống nhất và đấu tranh của các mặt đối lập",
                                "Sự trùng lặp", "Không tồn tại", OptionLabelD.B,
                                "Hai mặt đối lập vừa thống nhất vừa đấu tranh; đấu tranh là tuyệt đối, thống nhất là tương đối.", 3),
                        q("Lượng và chất", "Quy luật lượng – chất chỉ ra:",
                                "Sự thay đổi về lượng tới điểm nút sẽ dẫn tới thay đổi về chất", "Chất quyết định lượng",
                                "Không có quan hệ", "Lượng luôn đổi trước chất", OptionLabelD.A,
                                "Lượng tích luỹ tới độ (điểm nút) thì chất đổi, xảy ra bước nhảy; chất mới tác động lại lượng.", 2),
                        q("Phủ định của phủ định", "Quy luật phủ định của phủ định chỉ ra:",
                                "Sự lặp vô hạn", "Khuynh hướng phát triển theo đường xoáy ốc", "Sự biến mất hoàn toàn",
                                "Không có phát triển", OptionLabelD.B,
                                "Phát triển dường như quay lại nhưng ở trình độ cao hơn — hình xoáy ốc.", 3),
                        q("Cặp phạm trù", "Cặp phạm trù nào chỉ mối liên hệ giữa bản chất bên trong và biểu hiện bên ngoài?",
                                "Nội dung và hình thức", "Bản chất và hiện tượng", "Nhân và quả", "Tất nhiên và ngẫu nhiên",
                                OptionLabelD.B,
                                "Bản chất là cái bên trong quy định sự vật; hiện tượng là biểu hiện bên ngoài của bản chất.", 2),
                        q("Nhân quả", "Mối liên hệ nhân quả có tính:",
                                "Chủ quan", "Khách quan, phổ biến, tất yếu", "Ngẫu nhiên thuần tuý", "Không xác định",
                                OptionLabelD.B,
                                "Nguyên nhân có trước và sinh ra kết quả; quan hệ này mang tính khách quan.", 2),
                        q("Tất nhiên và ngẫu nhiên", "Tất nhiên và ngẫu nhiên có quan hệ:",
                                "Tách rời hoàn toàn", "Tất nhiên thể hiện qua ngẫu nhiên, ngẫu nhiên là hình thức của tất nhiên",
                                "Ngẫu nhiên quyết định tất cả", "Không liên quan", OptionLabelD.B,
                                "Cái tất yếu tự vạch đường qua vô số cái ngẫu nhiên.", 3),
                        q("Nội dung và hình thức", "Hình thức có vai trò gì với nội dung?",
                                "Không ảnh hưởng", "Thúc đẩy hoặc kìm hãm phát triển của nội dung", "Quyết định hoàn toàn",
                                "Phủ định nội dung", OptionLabelD.B,
                                "Hình thức phù hợp thúc đẩy nội dung; lỗi thời sẽ kìm hãm và bị thay thế.", 2),
                        q("Phát triển", "Phát triển theo quan điểm biện chứng là:",
                                "Tăng lên về số lượng đơn thuần", "Vận động đi lên, có kế thừa và đổi mới về chất",
                                "Quay lại điểm đầu", "Đứng yên", OptionLabelD.B,
                                "Phát triển là sự vận động đi lên, kế thừa cái hợp lý và tạo ra chất mới cao hơn.", 2)
                )),
                new SeedChapter("Chương 3: Chủ nghĩa duy vật lịch sử", List.of(
                        q("Tồn tại xã hội", "Tồn tại xã hội quyết định:",
                                "Ý thức xã hội", "Khoa học kỹ thuật", "Tôn giáo", "Nghệ thuật", OptionLabelD.A,
                                "Tồn tại xã hội (đời sống vật chất) quyết định ý thức xã hội; ý thức có tính độc lập tương đối.", 2),
                        q("Lực lượng sản xuất", "Yếu tố quan trọng nhất trong lực lượng sản xuất là:",
                                "Đối tượng lao động", "Tư liệu lao động", "Người lao động", "Công cụ thô", OptionLabelD.C,
                                "Người lao động là chủ thể sáng tạo, yếu tố quyết định nhất của lực lượng sản xuất.", 2),
                        q("Quan hệ sản xuất", "Quan hệ sản xuất gồm ba mặt:",
                                "Sở hữu, tổ chức quản lý, phân phối", "Mua, bán, trao đổi", "Sản xuất, tiêu dùng, tích luỹ",
                                "Lao động, nghỉ ngơi, tái sản xuất", OptionLabelD.A,
                                "Ba mặt: quan hệ sở hữu tư liệu sản xuất, tổ chức quản lý, phân phối sản phẩm.", 2),
                        q("Kiến trúc thượng tầng", "Kiến trúc thượng tầng bao gồm:",
                                "Chỉ kinh tế", "Các quan điểm, thiết chế chính trị – pháp luật, đạo đức, tôn giáo...", "Chỉ nhà nước",
                                "Chỉ văn hoá", OptionLabelD.B,
                                "Kiến trúc thượng tầng là toàn bộ quan điểm tinh thần cùng thiết chế xã hội tương ứng.", 2),
                        q("Hình thái kinh tế", "Hình thái kinh tế – xã hội gồm ba yếu tố cơ bản:",
                                "Lực lượng sản xuất, quan hệ sản xuất, kiến trúc thượng tầng", "Giai cấp, nhà nước, gia đình",
                                "Kinh tế, chính trị, văn hoá", "Sản xuất, phân phối, tiêu dùng", OptionLabelD.A,
                                "Ba yếu tố hợp thành hình thái kinh tế – xã hội; biến đổi theo quy luật khách quan.", 3),
                        q("Đấu tranh giai cấp", "Theo quan điểm Mác – Lênin, đấu tranh giai cấp:",
                                "Không tồn tại", "Là một trong những động lực phát triển xã hội có giai cấp", "Luôn tiêu cực",
                                "Chỉ ở phương Tây", OptionLabelD.B,
                                "Trong xã hội có đối kháng giai cấp, đấu tranh giai cấp là động lực quan trọng của tiến hoá xã hội.", 3),
                        q("Con người", "Bản chất con người theo Mác là:",
                                "Bản năng sinh học", "Tổng hoà các quan hệ xã hội", "Ý niệm thuần tuý", "Không xác định",
                                OptionLabelD.B,
                                "Trong tính hiện thực, bản chất con người là tổng hoà các quan hệ xã hội.", 2),
                        q("Nhà nước", "Nhà nước xuất hiện khi:",
                                "Con người hình thành", "Xã hội phân chia giai cấp và xuất hiện mâu thuẫn không thể điều hoà",
                                "Có tiền tệ", "Có tôn giáo", OptionLabelD.B,
                                "Nhà nước ra đời để duy trì trật tự khi xã hội phân hoá giai cấp.", 2),
                        q("Quần chúng", "Vai trò của quần chúng nhân dân trong lịch sử là:",
                                "Thụ động", "Chủ thể sáng tạo lịch sử", "Không có vai trò", "Chỉ lao động", OptionLabelD.B,
                                "Quần chúng nhân dân là người sáng tạo ra lịch sử, là động lực cơ bản của tiến bộ.", 1),
                        q("Văn hoá", "Văn hoá theo quan điểm duy vật lịch sử là:",
                                "Do thần thánh ban", "Sản phẩm của hoạt động thực tiễn xã hội của con người", "Bẩm sinh",
                                "Không biến đổi", OptionLabelD.B,
                                "Văn hoá hình thành từ lao động và đời sống xã hội, biến đổi cùng sự phát triển kinh tế – xã hội.", 2)
                ))
        );
        return new SeedSubject("BAS1150", "Triết học Mác – Lênin",
                "Khoa Lý luận chính trị — chủ nghĩa duy vật biện chứng và duy vật lịch sử.", chapters);
    }

    /** Rút gọn việc tạo blueprint; tham số theo đúng thứ tự cột trong {@link QuestionBlueprint}. */
    private static QuestionBlueprint q(String title, String stem,
                                       String optionA, String optionB, String optionC, String optionD,
                                       OptionLabelD correctLabel, String feedback, int difficulty) {
        return new QuestionBlueprint(title, stem, optionA, optionB, optionC, optionD,
                correctLabel, feedback, difficulty);
    }
}
