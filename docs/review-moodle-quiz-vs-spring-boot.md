# Đối chiếu Moodle 5.0.3 (`mod/quiz`, `qtype_multichoice`) ↔ Spring Boot (`be-web-admin`)

> Tài liệu review kỹ thuật cho đồ án tốt nghiệp — **phần cần vá ở backend Java**.
> Mọi khẳng định bên dưới được đọc trực tiếp từ mã nguồn trong repo, kèm đường dẫn và số dòng.
> Ngày lập: 2026-11-23.
> Nguồn Moodle: `php_moodle_503/moodle-latest-503/moodle/public/`.
> Nguồn Spring Boot: `be-web-admin/`.
> Nguồn CSDL: `be-web-admin/sql/2026-11-19-02-business-tables.sql` (v4).

---

## 0. Kết luận ngắn

Hệ thống hiện tại **mô hình hoá câu hỏi trắc nghiệm theo một hướng khác Moodle**, không phải một
bản rút gọn của Moodle. Điều này **không sai** — đồ án chỉ cần CRUD + kiểm định + đẩy sang Moodle,
không cần chạy quiz — nhưng có **3 nhóm việc phải vá** nếu muốn trao đổi dữ liệu với Moodle thật:

| # | Nhóm việc | Mức độ | Ảnh hưởng |
|---|---|---|---|
| **A** | Luồng Moodle **chưa có tầng service/controller** — chỉ có entity + bảng | 🔴 Chặn | Không thể xuất/nhập câu hỏi sang Moodle |
| **B** | Thiếu field mà Moodle **bắt buộc** (`layout`, `single`, `shuffleanswers`, `answers[].fraction`, `penalty`) | 🔴 Chặn | Câu hỏi xuất sang sẽ sai định dạng hoặc bị từ chối |
| **C** | `AnswerNumberingD` thiếu 2 giá trị (`iii`, `IIII`); `title` ↔ `name` khác ràng buộc NULL | 🟠 Sai lệch | Mất dữ liệu khi đồng bộ hai chiều |

Ngoài ra có **1 điểm cần biết nhưng không phải lỗi**: Moodle **không có** khái niệm
`subject_id`/`topic_id`/`difficulty`/`validation_status` — đó là phần mở rộng riêng của đồ án.

---

## 1. Bảng dữ liệu: đối chiếu cột

### 1.1 Bảng `question` lõi của Moodle

`php_moodle_503/moodle-latest-503/moodle/public/lib/db/install.xml`

```
id, parent, name, questiontext, questiontextformat, generalfeedback,
generalfeedbackformat, defaultmark, penalty, qtype, length, stamp,
timecreated, timemodified, createdby, modifiedby
```

Bảng `question` của Moodle **không có** các cột mà đồ án đang dùng:
`subject_id`, `topic_id`, `title`, `difficulty`, `status`, `validation_status`,
`last_validated_at`, `source`, `content_hash`, `version`, `content_format`.

Đây là **khác biệt thiết kế, không phải thiếu sót**: Moodle phân loại câu hỏi bằng cây
`question_categories` (gắn với `context`), không bằng khoá ngoại môn/chương.

### 1.2 Bảng `question` của đồ án

`be-web-admin/sql/2026-11-19-02-business-tables.sql` — `ntt_questions`, 27 cột:

```
question_id, subject_id, topic_id, title, stem, option_a, option_b, option_c, option_d,
correct_label, content_format, general_feedback, difficulty, status, validation_status,
last_validated_at, source, content_hash, version, created_by, updated_by, created_at, updated_at
```

### 1.3 Bảng tuỳ chọn câu hỏi trắc nghiệm của Moodle

`question/type/multichoice/db/install.xml` — bảng `qtype_multichoice_options`:

```
id, questionid, layout, single, shuffleanswers,
correctfeedback, correctfeedbackformat,
partiallycorrectfeedback, partiallycorrectfeedbackformat,
incorrectfeedback, incorrectfeedbackformat,
answernumbering, shownumcorrect, showstandardinstruction
```

### 1.4 Bảng đáp án của Moodle

`question_answers` (lõi, dùng chung mọi loại câu hỏi):

```
id, question, answer, answerformat, fraction, feedback, feedbackformat
```

> 🔴 **Điểm khác biệt lớn nhất.**
> Moodle lưu **mỗi đáp án là một dòng** trong `question_answers`, và **điểm của từng đáp án
> nằm ở cột `fraction`** (`1.0` = đúng hoàn toàn, `0.5` = đúng một phần, `0` = sai,
> `-1.0` = trừ điểm).
> Đồ án lưu **4 đáp án thành 4 cột** `option_a..option_d` cộng **một** cột `correct_label`.
>
> Hệ quả: đồ án **chỉ biểu diễn được câu trắc nghiệm một đáp án đúng, không trừ điểm**.
> Moodle hỗ trợ cả *single* và *multi* answer, cộng trừ điểm từng phần.
> Nếu bảng `ntt_moodle_exchange_items` chỉ ghi `question_id` rồi để Moodle tự dựng câu hỏi,
> thì **sẽ không tái tạo được** câu hỏi nhiều đáp án / đúng một phần.

---

## 2. Việc **A** — luồng Moodle chưa có tầng service/controller

### 2.1 Hiện trạng

Có **entity + repository + bảng**, nhưng **không có service, không có controller**:

| Thành phần | Tệp | Trạng thái |
|---|---|---|
| Entity | `model/jpa/moodle/MoodleProfile.java` (77 dòng) | ✅ có |
| Entity | `model/jpa/moodle/MoodleExchange.java` (90 dòng) | ✅ có |
| Entity | `model/jpa/moodle/MoodleExchangeItem.java` (72 dòng) | ✅ có |
| Repository | `repository/jpa/moodle/MoodleProfileRepository.java` (18 dòng) | ✅ có |
| Repository | `repository/jpa/moodle/MoodleExchangeRepository.java` (21 dòng) | ✅ có |
| Repository | `repository/jpa/moodle/MoodleExchangeItemRepository.java` (16 dòng) | ✅ có |
| Enum | `enums/moodle/MoodleCheckD.java`, `AnswerNumberingD.java` | ✅ có |
| **Service** | — | ❌ **không có** |
| **Controller** | — | ❌ **không có** |

Kiểm chứng:

```bash
grep -rln "Moodle" be-web-admin/src/main/java/com/example/be_web_admin/service \
                       be-web-admin/src/main/java/com/example/be_web_admin/controller
# → chỉ trả về: service/qbank/QuestionContentHasher.java  (khớp chuỗi "Moodle" trong comment)
```

### 2.2 Hệ quả quan sát được trên UI

`qbank-console-vue3/src/views/MoodleView.vue` thuộc nhóm **MOCK** (xem
`docs/review-api-contract-vue3-springboot.md` §3) — màn hình chạy bằng dữ liệu giả,
không gọi endpoint nào. Có đúng như vậy vì **phía sau không có endpoint để gọi**.

### 2.3 Cần vá gì

Tạo 2 lớp, theo đúng khuôn các module đã có (`service/qbank/`, `controller/web/`):

1. **`service/moodle/MoodleExchangeService.java`**
   - `createExchange(profileId, List<Long> questionIds)` → tạo `ntt_moodle_exchanges` +
     `ntt_moodle_exchange_items`, tính `seq_no` tăng dần, `item_status = OK`.
   - `exportXml(exchangeId)` → sinh XML theo định dạng Moodle
     (`question/format/xml/`), đối chiếu §3 dưới đây.
   - `preview(exchangeId)` → chạy kiểm tra trước khi xuất, ghi `error_code`/`error_message`
     vào `ntt_moodle_exchange_items` nếu câu hỏi không hợp lệ.
2. **`controller/web/MoodleController.java`** — `@RequestMapping("/web/admin/moodle")`,
   các endpoint `POST /exchanges`, `GET /exchanges/{id}`, `GET /exchanges/{id}/xml`,
   `GET /profiles`, `POST /profiles`.
3. **DTO** tương ứng (`dto/moodle/…`) theo đúng quy ước `record` + `@JsonInclude` đang dùng.

> ⚠️ Nhớ `@RepositoryRestResource(exported = false)` cho repository mới nếu có, để không
> tự động mở endpoint REST ngoài ý muốn (các repository hiện tại đều làm vậy).

---

## 3. Việc **B** — thiếu field Moodle bắt buộc

Đây là phần **quan trọng nhất** cho việc xuất câu hỏi sang Moodle. Nếu thiếu, XML sinh ra
sẽ bị Moodle từ chối khi import.

### 3.1 Bảng field còn thiếu

| Field Moodle | Ý nghĩa | Đồ án có? | Cần làm |
|---|---|---|---|
| `layout` | Cách bày đáp án: `0`=dropdown, `1`=dọc, `2`=ngang | ❌ | Thêm cột hoặc hằng số khi xuất |
| `single` | `1` = chỉ một đáp án đúng, `0` = nhiều đáp án | ❌ | Thêm cột; mặc định `1` |
| `shuffleanswers` | Có đảo thứ tự đáp án khi thi | ⚠️ chỉ có ở `ntt_moodle_profiles` | Quyết định: theo profile hay theo câu |
| `answernumbering` | Kiểu đánh số: `abc`, `ABCD`, `123`, `iii`, `IIII`, `none` | ⚠️ thiếu `iii`, `IIII` | Sửa enum (§4.1) |
| `showstandardinstruction` | Hiện câu hướng dẫn chuẩn | ❌ | Mặc định `0` khi xuất |
| `shownumcorrect` | Hiện số đáp án đúng | ❌ | Mặc định `0` khi xuất |
| `correctfeedback` | Phản hồi khi đúng | ❌ | Dùng `general_feedback` thay thế |
| `partiallycorrectfeedback` | Phản hồi khi đúng một phần | ❌ | Không có khái niệm tương ứng |
| `incorrectfeedback` | Phản hồi khi sai | ❌ | Dùng `general_feedback` thay thế |
| `defaultmark` | Điểm mặc định của câu | ⚠️ chỉ có `default_grade` ở profile | Lấy từ profile khi xuất |
| `penalty` | Hệ số trừ điểm mỗi lần làm lại | ❌ | Mặc định `0.3333333` khi xuất |
| `fraction` (từng đáp án) | Điểm của từng đáp án | ❌ | Sinh từ `correct_label`: `1.0` / `0` |
| `qtype` | Luôn là `multichoice` | ❌ | Hằng số khi xuất |

### 3.2 Ánh xạ tối thiểu đề xuất

Khi xuất một dòng `ntt_questions` sang XML Moodle:

```
questiontext      ← stem
questiontextformat← 1 (HTML) nếu content_format = 'html', ngược lại 0 (moodle)
name              ← title, NULL thì sinh "Câu #<question_id>"
generalfeedback   ← general_feedback
defaultmark       ← moodle_profile.default_grade
penalty           ← 0.3333333 (hằng số, mặc định của Moodle)
qtype             ← 'multichoice'
single            ← 1 (đồ án chỉ có 1 đáp án đúng)
shuffleanswers    ← moodle_profile.shuffle_answers
answernumbering   ← moodle_profile.answer_numbering
layout            ← 1 (dọc)
answer[0..3]      ← option_a..option_d, kèm fraction = 1.0 nếu label = correct_label, ngược lại 0
```

> 📌 Điểm cần cân nhắc: `title` trong đồ án là **nullable, không bắt buộc**
> (`ntt_questions.title VARCHAR(255) NULL COMMENT 'Tiêu đề ngắn để tra cứu, không bắt buộc'`),
> nhưng `question.name` của Moodle là **NOT NULL** (`install.xml`: `name` không có
> `NOTNULL="false"`). Vậy khi xuất **buộc phải sinh tên thay thế** cho các câu chưa đặt
> tiêu đề — nếu để `null` thì XML sẽ vi phạm lược đồ.

### 3.3 Trường hợp câu hỏi nhiều đáp án đúng

Đồ án **không biểu diễn được**. `correct_label` là một ký tự `A|B|C|D`, không phải một tập.
Nếu đồ án cam kết chỉ hỗ trợ trắc nghiệm một đáp án đúng, **phải ghi rõ trong báo cáo đồ án**
và đặt `single = 1` cố định. Nếu không, đây sẽ là câu hỏi phản biện khi bảo vệ.

---

## 4. Việc **C** — sai lệch nhỏ nhưng gây mất dữ liệu

### 4.1 `AnswerNumberingD` thiếu 2 giá trị — ✅ **đã vá (2026-11-23)**

Moodle định nghĩa **6** kiểu đánh số (`question/type/multichoice/questiontype.php:322-329`):

```php
foreach (array('abc', 'ABCD', '123', 'iii', 'IIII', 'none') as $numberingoption) {
```

Đồ án chỉ có **4** (`enums/moodle/AnswerNumberingD.java`):

```java
NONE("none"), ABC("abc"), ABCD("ABCD"), NUMERIC("123");
public static final String SQL_TYPE = "enum('none','abc','ABCD','123')";
```

**Hệ quả:** profile Moodle dùng `iii` hoặc `IIII` **không nhập được** vào đồ án — enum
`fromDbValue` ném `IllegalArgumentException`. Nếu nhập từ Moodle thật, đây là lỗi 500.

**Đã làm:**

1. ✅ Thêm `LOWER_ROMAN("iii")` và `UPPER_ROMAN("IIII")` vào enum.
2. ✅ Cập nhật `SQL_TYPE` thành `enum('none','abc','ABCD','123','iii','IIII')`.
3. ✅ **Migration** `sql/2026-11-23-answer-numbering-moodle-6-gia-tri.sql`:
   ```sql
   ALTER TABLE ntt_moodle_profiles
     MODIFY COLUMN answer_numbering ENUM('none','abc','ABCD','123','iii','IIII')
     NOT NULL DEFAULT 'none';
   ```
4. ✅ `converter/AnswerNumberingDConverter.java` dùng `dbValue()` — vốn đã tổng quát, không phải sửa.
5. ✅ Không có test/danh sách giá trị cố định nào khác trong repo (`grep "'iii'"` → rỗng).

### 4.2 `shuffleanswers` đặt sai cấp

Moodle để `shuffleanswers` **trên từng câu hỏi** (`qtype_multichoice_options.shuffleanswers`).
Đồ án để **trên profile** (`ntt_moodle_profiles.shuffle_answers`).
⇒ Khi xuất, mọi câu trong cùng một profile dùng chung một giá trị. **Chấp nhận được** cho đồ án
(gọn hơn), nhưng phải ghi rõ vì đó là điểm khác Moodle.

### 4.3 Moodle `length` vs. `version` — hai khái niệm khác nhau

Moodle có cột `question.length` (thời gian làm bài ước tính, giây; `0` = không giới hạn) và
`question_versions.version` (số phiên bản của nội dung câu hỏi). Đồ án chỉ có
`ntt_questions.version` (`@Version` cho khoá lạc quan) — **mang nghĩa hoàn toàn khác**.

> ⚠️ **Bẫy dễ sai:** đừng map `ntt_questions.version` sang `question_versions.version` khi
> xuất. `ntt_questions.version` là bộ đếm khoá lạc quan (tăng mỗi lần UPDATE, mục đích chống
> ghi đè đồng thời). `question_versions.version` do Moodle tự quản lý khi import.
> Đồ án có bảng `ntt_question_revisions` (với `rev_no`) mới là thứ tương ứng về mặt ý nghĩa.

---

## 5. Những điểm **không** cần vá (nhưng nên ghi trong báo cáo)

Phần này quan trọng để tránh hội đồng hỏi "sao không giống Moodle".

| Khái niệm Moodle | Đồ án | Kết luận |
|---|---|---|
| `question_categories` (cây phân loại theo `context`) | `ntt_subjects` + `ntt_topics` | Đồ án dùng phân cấp **2 cấp cố định**, đơn giản hơn. Hợp lý cho phạm vi đồ án. |
| `question_bank_entries` + `question_versions` (một câu hỏi có nhiều phiên bản) | `ntt_question_revisions` (`snapshot_json`) | Tương đương về ý nghĩa. Đồ án chụp **toàn bộ** câu hỏi dạng JSON thay vì tách cột — chấp nhận được. |
| `question_attempts` / `question_attempt_steps` / `question_usages` (phiên làm bài) | **không có** | Ngoài phạm vi: đồ án quản lý ngân hàng câu hỏi, **không tổ chức thi**. |
| `quiz`, `quiz_slots`, `quiz_sections`, `quiz_overrides`, `quiz_grades` | **không có** | Ngoài phạm vi: tương tự trên. |
| `question_hints` (`clearwrong`, `options`, `shownumcorrect`) | **không có** | Ngoài phạm vi: chỉ dùng khi thi có gợi ý nhiều bước. |
| `question_version_status` (`draft` / `ready` / `hidden`) | `ntt_questions.status` (`DRAFT/ACTIVE/ARCHIVED`) | **Tương đương 1-1**: `draft`↔`DRAFT`, `ready`↔`ACTIVE`, `hidden`↔`ARCHIVED`. Ghi rõ ánh xạ này khi xuất. |

Kiểm chứng `question_version_status`:
`question/classes/local/bank/question_version_status.php:32,37,42`
→ `QUESTION_STATUS_READY = 'ready'`, `QUESTION_STATUS_HIDDEN = 'hidden'`, `QUESTION_STATUS_DRAFT = 'draft'`.

---

## 6. Thứ tự thi công đề xuất

| Bước | Việc | Tệp chạm vào | Kiểm chứng |
|---|---|---|---|
| **1** | Vá `AnswerNumberingD` (thêm `iii`, `IIII`) + migration SQL | `enums/moodle/AnswerNumberingD.java`, `sql/` | Enum có 6 giá trị; `SQL_TYPE` khớp; chạy migration trên DB rỗng |
| **2** | Tạo `MoodleExchangeService` + DTO xuất XML | `service/moodle/`, `dto/moodle/` | Xuất 1 câu → XML hợp lệ |
| **3** | Tạo `MoodleController` (`/web/admin/moodle`) | `controller/web/` | `curl` được `POST /exchanges` |
| **4** | Nối `MoodleView.vue` từ MOCK sang THẬT | `qbank-console-vue3/src/views/MoodleView.vue`, `store.js`, `api/qbankApi.js` | Màn hình Moodle không còn dữ liệu giả |
| **5** | Test: import file XML vừa xuất vào Moodle 5.0.3 thật | `question/format/xml/` | Moodle nhận, hiện đúng 4 đáp án |

> Ưu tiên bước **1** vì chỉ sửa 1 file + 1 migration, rủi ro thấp, và là **điều kiện cần**
> để bước 2 có thể chạy đúng với profile thật.

---

## 7. Bảng tra nhanh vị trí mã nguồn Moodle đã đọc

| Nội dung | Tệp · dòng |
|---|---|
| Cột bảng `question` lõi | `public/lib/db/install.xml` |
| Cột bảng `qtype_multichoice_options` | `public/question/type/multichoice/db/install.xml` |
| Cột bảng `question_answers` | `public/lib/db/install.xml` |
| 3 giá trị `layout` | `public/question/type/multichoice/question.php:39-41` |
| 6 kiểu `answernumbering` | `public/question/type/multichoice/questiontype.php:322-329` |
| Chấm điểm *single* | `public/question/type/multichoice/question.php:311-319` |
| Chấm điểm *multi* (cộng `fraction`, kẹp `[0,1]`) | `public/question/type/multichoice/question.php:532-541` |
| 17 trạng thái của một bước làm bài | `public/question/engine/states.php:306-469` |
| 3 trạng thái phiên bản câu hỏi | `public/question/classes/local/bank/question_version_status.php:32-42` |
| Cột bảng `quiz` (44 cột) | `public/mod/quiz/db/install.xml` |
| Cột bảng `quiz_attempts`, `quiz_slots`, `quiz_sections` | `public/mod/quiz/db/install.xml` |

### Ghi chú về 17 trạng thái của Moodle

`question/engine/states.php` định nghĩa 17 lớp trạng thái cho **một bước làm bài**:
`notstarted`, `unprocessed`, `todo`, `invalid`, `complete`, `needsgrading`, `finished`,
`gaveup`, `gradedwrong`, `gradedpartial`, `gradedright`, `manfinished`, `mangaveup`,
`mangrwrong`, `mangrpartial`, `mangrright`.

Đồ án **không cần** mô hình này vì không tổ chức thi. Chỉ cần biết để trả lời phản biện:
đó là trạng thái **của lượt làm bài**, không phải trạng thái **của câu hỏi trong ngân hàng**.
Trạng thái câu hỏi trong ngân hàng chỉ có 3 (`draft`/`ready`/`hidden`), đã ánh xạ ở §5.

---

## 8. Tóm lại — việc cần làm ở Java Spring Boot

1. 🔴 **`MoodleExchangeService` + `MoodleController`** — hiện chỉ có entity/repository, không có
   tầng nghiệp vụ nào. Không có nó thì không xuất được câu hỏi sang Moodle.
2. 🔴 **Bổ sung khi xuất XML**: `layout=1`, `single=1`, `shuffleanswers`, `answernumbering`,
   `defaultmark`, `penalty`, `qtype='multichoice'`, và **`fraction` cho từng đáp án**
   (sinh từ `correct_label`).
3. 🔴 **Sinh `name` thay thế khi `title` là NULL** — `question.name` của Moodle là NOT NULL,
   `ntt_questions.title` của đồ án là NULL-được.
4. 🟠 **`AnswerNumberingD`**: thêm `iii`, `IIII`; sửa `SQL_TYPE`; viết migration `ALTER TABLE`.
5. 🟡 **Ghi rõ giới hạn** trong báo cáo: chỉ hỗ trợ trắc nghiệm **một đáp án đúng**,
   **không trừ điểm**, **không có** phiên thi.
