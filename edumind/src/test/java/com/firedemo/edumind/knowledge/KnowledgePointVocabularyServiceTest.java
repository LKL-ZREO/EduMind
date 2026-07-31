package com.firedemo.edumind.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgePointVocabularyServiceTest {

    @Test
    void acceptsOnlyTeacherDefinedCanonicalNames() {
        TeacherKnowledgeMapper mapper = mock(TeacherKnowledgeMapper.class);
        TeacherKnowledge pointer = new TeacherKnowledge();
        pointer.setName("指针");
        TeacherKnowledge array = new TeacherKnowledge();
        array.setName("数组");
        when(mapper.selectByClassId(1L)).thenReturn(List.of(pointer, array));

        KnowledgePointVocabularyService service = new KnowledgePointVocabularyService(mapper);
        Map<String, String> vocabulary = service.loadCanonicalNames(1L);

        assertThat(service.normalize(" 指针 ", vocabulary)).isEqualTo("指针");
        assertThat(service.normalize("循环结构", vocabulary)).isEqualTo("其他");
        assertThat(service.normalize(null, vocabulary)).isEqualTo("其他");
        assertThat(vocabulary).containsKeys("指针", "数组", "其他");
    }
}
