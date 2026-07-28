package com.firedemo.demo.live.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.demo.DTO.QAMessageDTO;
import com.firedemo.demo.DTO.StudentResponseDTO;
import com.firedemo.demo.Entity.Interaction;
import com.firedemo.demo.Service.OpenClawService;
import com.firedemo.demo.common.exception.BusinessException;
import com.firedemo.demo.infrastructure.ai.StructuredOutputInvoker;
import com.firedemo.demo.live.handler.QASessionHandler;
import com.firedemo.demo.live.service.InteractionService;
import com.firedemo.demo.live.service.LiveNotificationService;
import com.firedemo.demo.mapper.ClassStudentMapper;
import com.firedemo.demo.mapper.ClassroomQAMapper;
import com.firedemo.demo.mapper.ClassroomSessionMapper;
import com.firedemo.demo.mapper.InteractionMapper;
import com.firedemo.demo.mapper.InteractionResponseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveMessageResourceScopeTest {

    private InteractionMapper interactionMapper;
    private InteractionResponseMapper responseMapper;
    private InteractionService interactionService;

    @BeforeEach
    void setUp() {
        interactionMapper = mock(InteractionMapper.class);
        responseMapper = mock(InteractionResponseMapper.class);
        interactionService = new InteractionService(
                interactionMapper,
                responseMapper,
                mock(ClassStudentMapper.class),
                mock(ClassroomSessionMapper.class),
                mock(SimpMessagingTemplate.class),
                new ObjectMapper(),
                mock(OpenClawService.class),
                mock(StructuredOutputInvoker.class),
                mock(LiveNotificationService.class));
    }

    @Test
    void studentCannotRespondToInteractionFromAnotherClassroom() {
        Interaction interaction = activeInteraction(7L, 100L);
        when(interactionMapper.selectById(7L)).thenReturn(interaction);
        StudentResponseDTO response = new StudentResponseDTO();
        response.setInteractionId(7L);
        response.setAnswer("A");

        assertThatThrownBy(() -> interactionService.handleResponse(99L, response))
                .isInstanceOf(BusinessException.class)
                .hasMessage("互动不属于当前课堂");
        verify(responseMapper, never()).upsert(any());
    }

    @Test
    void teacherCannotCloseInteractionFromAnotherClassroom() {
        when(interactionMapper.selectById(7L)).thenReturn(activeInteraction(7L, 100L));

        assertThatThrownBy(() -> interactionService.closeInteraction(7L, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("互动不属于当前课堂");
        verify(interactionMapper, never()).closeInteraction(any(), any());
    }

    @Test
    void teacherCannotAnswerQuestionFromAnotherClassroom() {
        ClassroomQAMapper qaMapper = mock(ClassroomQAMapper.class);
        QASessionHandler handler = new QASessionHandler(
                qaMapper,
                mock(SimpMessagingTemplate.class));
        QAMessageDTO.QuestionItem answer = new QAMessageDTO.QuestionItem();
        answer.setAnswerText("answer");
        when(qaMapper.markAnswered(8L, 99L, "answer")).thenReturn(0);

        assertThatThrownBy(() -> handler.answer(99L, 8L, answer))
                .isInstanceOf(BusinessException.class)
                .hasMessage("问题不属于当前课堂");
    }

    @Test
    void questionUsesStudentIdentityEstablishedAtConnect() {
        ClassroomQAMapper qaMapper = mock(ClassroomQAMapper.class);
        QASessionHandler handler = new QASessionHandler(
                qaMapper,
                mock(SimpMessagingTemplate.class));
        when(qaMapper.findTopLevelBySessionId(99L)).thenReturn(List.of());
        QAMessageDTO.QuestionItem question = new QAMessageDTO.QuestionItem();
        question.setQuestion("Why?");
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        HashMap<String, Object> attributes = new HashMap<>();
        attributes.put("studentId", "S001");
        attributes.put("username", "student");
        accessor.setSessionAttributes(attributes);

        handler.ask(99L, question, accessor);

        ArgumentCaptor<com.firedemo.demo.Entity.ClassroomQA> captor =
                ArgumentCaptor.forClass(com.firedemo.demo.Entity.ClassroomQA.class);
        verify(qaMapper).insertQuestion(captor.capture());
        assertThat(captor.getValue().getStudentId()).isEqualTo("S001");
        assertThat(captor.getValue().getStudentName()).isEqualTo("student");
        assertThat(captor.getValue().getSessionId()).isEqualTo(99L);
    }

    private Interaction activeInteraction(Long id, Long sessionId) {
        Interaction interaction = new Interaction();
        interaction.setId(id);
        interaction.setSessionId(sessionId);
        interaction.setStatus("ACTIVE");
        return interaction;
    }
}
