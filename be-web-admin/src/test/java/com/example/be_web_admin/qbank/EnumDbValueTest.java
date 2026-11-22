package com.example.be_web_admin.qbank;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bảo vệ quy ước enum giữa 3 tầng: hằng số Java viết HOA, giá trị trong CSDL viết thường.
 *
 * <p>Test quét toàn bộ enum mới và kiểm tra:</p>
 * <ol>
 *   <li>{@code SQL_TYPE} (dùng cho columnDefinition) phân rã ra ĐÚNG danh sách dbValue,</li>
 *   <li>không có hai hằng số trùng dbValue,</li>
 *   <li>{@code fromDbValue} chuyển ngược đúng cho mọi giá trị.</li>
 * </ol>
 *
 * <p>Không khởi động Spring nên chạy được cả khi không có MariaDB/Redis.</p>
 */
class EnumDbValueTest {

    /** Chạy từ thư mục be-web-admin (Gradle) hoặc từ gốc repo (IDE) đều tìm được. */
    private static final Path ENUMS_ROOT = Stream.of(
                    Path.of("src/main/java/com/example/be_web_admin/enums"),
                    Path.of("be-web-admin/src/main/java/com/example/be_web_admin/enums"))
            .filter(Files::isDirectory)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Không tìm thấy thư mục enums"));

    @Test
    @DisplayName("SQL_TYPE của mọi enum khớp đúng tập dbValue và round-trip được")
    void sqlTypeMatchesEnumValues() throws Exception {
        List<Class<?>> enums = loadEnums();
        assertTrue(enums.size() >= 20, "phải quét được toàn bộ enum mới, thực tế: " + enums.size());

        for (Class<?> type : enums) {
            String sqlType = (String) type.getField("SQL_TYPE").get(null);
            assertNotNull(sqlType, type.getSimpleName() + " thiếu SQL_TYPE");
            assertTrue(sqlType.startsWith("enum(") && sqlType.endsWith(")"),
                    type.getSimpleName() + ".SQL_TYPE phải có dạng enum('a','b')");

            List<String> fromSqlType = parseEnumLiterals(sqlType);
            Method dbValue = type.getMethod("dbValue");
            Method fromDbValue = type.getMethod("fromDbValue", String.class);

            List<String> fromConstants = new ArrayList<>();
            for (Object constant : type.getEnumConstants()) {
                String value = (String) dbValue.invoke(constant);
                fromConstants.add(value);
                assertEquals(constant, fromDbValue.invoke(null, value),
                        type.getSimpleName() + ": fromDbValue không trả lại đúng hằng số");
            }

            assertEquals(new LinkedHashSet<>(fromConstants), new LinkedHashSet<>(fromSqlType),
                    type.getSimpleName() + ": SQL_TYPE và dbValue lệch nhau");
            assertEquals(fromConstants.size(), new LinkedHashSet<>(fromConstants).size(),
                    type.getSimpleName() + ": có hai hằng số trùng dbValue");
        }
    }

    @Test
    @DisplayName("Enum có giá trị CSDL khác tên hằng số vẫn đúng (A-D, E1-E8, '123', 'ABCD')")
    void specialEnumsKeepExactDatabaseValues() throws Exception {
        Class<?> label = Class.forName("com.example.be_web_admin.enums.qbank.OptionLabelD");
        assertEquals("enum('A','B','C','D')", label.getField("SQL_TYPE").get(null));

        Class<?> scenario = Class.forName("com.example.be_web_admin.enums.eval.EvalScenarioD");
        assertEquals("enum('E1','E2','E3','E4','E5','E6','E7','E8')", scenario.getField("SQL_TYPE").get(null));

        Class<?> numbering = Class.forName("com.example.be_web_admin.enums.moodle.AnswerNumberingD");
        assertEquals("enum('none','abc','ABCD','123')", numbering.getField("SQL_TYPE").get(null));
    }

    private static List<Class<?>> loadEnums() throws Exception {
        List<Class<?>> result = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(ENUMS_ROOT)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String relative = ENUMS_ROOT.relativize(file).toString()
                        .replace(java.io.File.separatorChar, '.')
                        .replace(".java", "");
                Class<?> type = Class.forName("com.example.be_web_admin.enums." + relative);
                if (type.isEnum()) {
                    result.add(type);
                }
            }
        }
        return result;
    }

    /** "enum('a','b')" -> ["a","b"]. */
    private static List<String> parseEnumLiterals(String sqlType) {
        String inner = sqlType.substring("enum(".length(), sqlType.length() - 1);
        Set<String> values = new LinkedHashSet<>();
        Arrays.stream(inner.split(","))
                .map(String::trim)
                .filter(token -> token.length() >= 2)
                .map(token -> token.substring(1, token.length() - 1))
                .forEach(values::add);
        return new ArrayList<>(values);
    }
}
