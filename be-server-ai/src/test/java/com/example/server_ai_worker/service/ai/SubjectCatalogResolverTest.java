package com.example.server_ai_worker.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.server_ai_worker.config.AiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

class SubjectCatalogResolverTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void usesInputSubjectsWhenProvidedAndChangesCatalogHash() {
        AiProperties props = new AiProperties();
        AiProperties.SubjectCatalog def = new AiProperties.SubjectCatalog();
        def.setSubjectCode("INT3202");
        def.setSubjectName("Cơ sở dữ liệu");
        def.setTopics(List.of("SQL"));
        props.getNlSearch().setDefaultSubjects(List.of(def));

        SubjectCatalogResolver resolver = new SubjectCatalogResolver(props);
        SubjectCatalogResolver.Catalog defaultCatalog = resolver.resolve(om.createObjectNode());
        assertTrue(defaultCatalog.allowedCodes().contains("INT3202"));

        ObjectNode input = om.createObjectNode();
        ArrayNode arr = input.putArray("subjects");
        ObjectNode custom = arr.addObject();
        custom.put("subject_code", "MATH101");
        custom.put("subject_name", "Giải tích 1");
        custom.putArray("topics").add("Giới hạn").add("Đạo hàm");

        SubjectCatalogResolver.Catalog customCatalog = resolver.resolve(input);
        assertEquals(1, customCatalog.subjects().size());
        assertTrue(customCatalog.allowedCodes().contains("MATH101"));
        assertTrue(customCatalog.allowedTopics().contains("Đạo hàm"));
        assertNotEquals(defaultCatalog.catalogHash(), customCatalog.catalogHash());
    }
}
