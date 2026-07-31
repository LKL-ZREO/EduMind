package com.firedemo.edumind.live.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.edumind.live.QAMessageDTO;
import com.firedemo.edumind.live.StudentResponseDTO;
import com.firedemo.edumind.live.Interaction;
import com.firedemo.edumind.assistant.AgentService;
import com.firedemo.edumind.teaching.QuestionService;
import com.firedemo.edumind.shared.exception.BusinessException;
import com.firedemo.edumind.assistant.structured.StructuredOutputInvoker;
import com.firedemo.edumind.live.handler.QASessionHandler;
import com.firedemo.edumind.live.service.InteractionService;
import com.firedemo.edumind.live.service.LiveNotificationService;
import com.firedemo.edumind.classroom.ClassStudentMapper;
import com.firedemo.edumind.live.ClassroomQAMapper;
import com.firedemo.edumind.live.ClassroomSessionMapper;
import com.firedemo.edumind.live.InteractionMapper;
import com.firedemo.edumind.live.InteractionResponseMapper;
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
                mock(AgentService.class),
                mock(StructuredOutputInvoker.class),
                mock(LiveNotificationService.class),
                mock(QuestionService.class));
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

        ArgumentCaptor<com.firedemo.edumind.live.ClassroomQA> captor =
                ArgumentCaptor.forClass(com.firedemo.edumind.live.ClassroomQA.class);
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
