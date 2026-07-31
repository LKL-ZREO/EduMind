package com.firedemo.edumind.teaching;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.edumind.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionServiceTest {
    private QuestionBankItemMapper mapper;
    private QuestionService service;

    @BeforeEach
    void setUp() {
        mapper = mock(QuestionBankItemMapper.class);
        service = new QuestionService(mapper, new ObjectMapper());
    }

    @Test
    void createsStructuredChoiceQuestionOnceInUnifiedBank() {
        QuestionUpsertDTO request = choiceRequest("B");
        request.setDifficulty("medium");
        request.setTimeLimit(90);
        request.setSourceDocId("doc-1");
        request.setAiGenerated(true);
        doAnswer(invocation -> {
            QuestionBankItem item = invocation.getArgument(0);
            item.setId(7L);
            return 1;
        }).when(mapper).insertWithJsonb(any(QuestionBankItem.class));

        var created = service.create(5L, request);

        assertThat(created.getId()).isEqualTo(7L);
        assertThat(created.getOptions()).hasSize(2);
        assertThat(created.getDifficulty()).isEqualTo("medium");
        assertThat(created.getTimeLimit()).isEqualTo(90);
        assertThat(created.getAiGenerated()).isTrue();

        ArgumentCaptor<QuestionBankItem> captor = ArgumentCaptor.forClass(QuestionBankItem.class);
        verify(mapper).insertWithJsonb(captor.capture());
        assertThat(captor.getValue().getOptions()).contains("\"key\":\"A\"");
        assertThat(captor.getValue().getSourceDocId()).isEqualTo("doc-1");
    }

    @Test
    void rejectsChoiceAnswerThatDoesNotReferenceAnOption() {
        assertThatThrownBy(() -> service.create(5L, choiceRequest("C")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("正确答案必须对应一个选项");
    }

    @Test
    void homeworkUpdateKeepsExistingStructuredQuestionFields() {
        QuestionBankItem existing = new QuestionBankItem();
        existing.setId(9L);
        existing.setTeacherId(5L);
        existing.setType("CHOICE");
        existing.setTitle("旧题目");
        existing.setOptions("[{\"key\":\"A\",\"text\":\"甲\"},{\"key\":\"B\",\"text\":\"乙\"}]");
        existing.setCorrectKey("A");
        existing.setArchived(false);
        when(mapper.selectById(9L)).thenReturn(existing);

        QuestionBankItem updated = service.saveHomeworkQuestion(
                5L, 9L, "新题目", "作业说明", 20, false);

        assertThat(updated.getType()).isEqualTo("CHOICE");
        assertThat(updated.getCorrectKey()).isEqualTo("A");
        assertThat(updated.getOptions()).contains("\"key\":\"A\"");
        assertThat(updated.getScore()).isEqualTo(20);
        verify(mapper).updateOwnedWithJsonb(existing);
    }

    private QuestionUpsertDTO choiceRequest(String correctKey) {
        QuestionUpsertDTO request = new QuestionUpsertDTO();
        request.setType("CHOICE");
        request.setTitle("下面哪项正确？");
        request.setCorrectKey(correctKey);
        request.setOptions(List.of(option("A", "甲"), option("B", "乙")));
        return request;
    }

    private QuestionUpsertDTO.OptionDTO option(String key, String text) {
        QuestionUpsertDTO.OptionDTO option = new QuestionUpsertDTO.OptionDTO();
        option.setKey(key);
        option.setText(text);
        return option;
    }
}
