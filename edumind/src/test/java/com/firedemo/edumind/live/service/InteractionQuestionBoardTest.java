package com.firedemo.edumind.live.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.edumind.live.InteractionPushDTO;
import com.firedemo.edumind.live.ClassroomSession;
import com.firedemo.edumind.live.Interaction;
import com.firedemo.edumind.live.InteractionResponse;
import com.firedemo.edumind.teaching.QuestionBankItem;
import com.firedemo.edumind.assistant.AgentService;
import com.firedemo.edumind.teaching.QuestionService;
import com.firedemo.edumind.shared.exception.BusinessException;
import com.firedemo.edumind.assistant.structured.StructuredOutputInvoker;
import com.firedemo.edumind.classroom.ClassStudentMapper;
import com.firedemo.edumind.live.ClassroomSessionMapper;
import com.firedemo.edumind.live.InteractionMapper;
import com.firedemo.edumind.live.InteractionResponseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InteractionQuestionBoardTest {

    private InteractionMapper interactionMapper;
    private InteractionResponseMapper responseMapper;
    private ClassStudentMapper classStudentMapper;
    private ClassroomSessionMapper sessionMapper;
    private QuestionService questionService;
    private InteractionService service;

    @BeforeEach
    void setUp() {
        interactionMapper = mock(InteractionMapper.class);
        responseMapper = mock(InteractionResponseMapper.class);
        classStudentMapper = mock(ClassStudentMapper.class);
        sessionMapper = mock(ClassroomSessionMapper.class);
        questionService = mock(QuestionService.class);
        service = new InteractionService(
                interactionMapper,
                responseMapper,
                classStudentMapper,
                sessionMapper,
                mock(SimpMessagingTemplate.class),
                new ObjectMapper(),
                mock(AgentService.class),
                mock(StructuredOutputInvoker.class),
                mock(LiveNotificationService.class),
                questionService);
    }

    @Test
    void questionBoardCombinesUnifiedQuestionsAndSentAttemptsWithResponseStats() {
        ClassroomSession session = activeSession();
        QuestionBankItem unsent = question(1L, "OPEN", "未发送题");
        QuestionBankItem used = question(2L, "CHOICE", "已发送题");
        used.setOptions("[{\"key\":\"A\",\"text\":\"甲\"},{\"key\":\"B\",\"text\":\"乙\"}]");
        used.setCorrectKey("A");
        Interaction sent = Interaction.builder()
                .id(20L).questionId(2L).sessionId(99L).classId(10L)
                .type("CHOICE").title("已发送题")
                .options("[{\"key\":\"A\",\"text\":\"甲\"},{\"key\":\"B\",\"text\":\"乙\"}]")
                .correctKey("A").status("CLOSED").sortOrder(2)
                .activatedAt(LocalDateTime.now()).build();
        InteractionResponse correct = InteractionResponse.builder()
                .interactionId(20L).studentId("S1").answer("A").isCorrect(true).build();
        InteractionResponse wrong = InteractionResponse.builder()
                .interactionId(20L).studentId("S2").answer("B").isCorrect(false).build();

        when(sessionMapper.selectById(99L)).thenReturn(session);
        when(classStudentMapper.countByClassId(10L)).thenReturn(3);
        when(responseMapper.findBySessionId(99L)).thenReturn(List.of(correct, wrong));
        when(interactionMapper.findBySessionId(99L)).thenReturn(List.of(sent));
        when(questionService.searchEntities(5L, null, null, null)).thenReturn(List.of(unsent, used));

        var board = service.getQuestionBoard(99L);

        assertThat(board).hasSize(2);
        assertThat(board.get(0).getStatus()).isEqualTo("UNSENT");
        assertThat(board.get(0).getInteractionId()).isNull();
        assertThat(board.get(1).getQuestionId()).isEqualTo(2L);
        assertThat(board.get(1).getSendCount()).isEqualTo(1);
        assertThat(board.get(1).getRespondedCount()).isEqualTo(2);
        assertThat(board.get(1).getTotalStudents()).isEqualTo(3);
        assertThat(board.get(1).getCorrectRate()).isEqualTo(50.0);
        assertThat(board.get(1).getDistribution().get("A").getCount()).isEqualTo(1);
        assertThat(board.get(1).getDistribution().get("B").getPercent()).isEqualTo(50.0);
    }

    @Test
    void activePushUsesPersistedDeadlineAndDoesNotRevealAnswer() {
        LocalDateTime deadline = LocalDateTime.now().plusMinutes(2);
        Interaction interaction = Interaction.builder()
                .id(7L).questionId(6L).sessionId(99L).type("CHOICE").title("题目")
                .correctKey("B").status("ACTIVE").timeLimit(120).deadlineAt(deadline).build();

        InteractionPushDTO push = service.buildPushDTO(interaction);

        assertThat(push.getCorrectKey()).isNull();
        assertThat(push.getQuestionId()).isEqualTo(6L);
        assertThat(push.getDeadlineEpochMs()).isEqualTo(
                deadline.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    @Test
    void sendingQuestionIsRejectedWhileAnotherQuestionIsActive() {
        when(sessionMapper.selectByIdForUpdate(99L)).thenReturn(activeSession());
        when(interactionMapper.findActiveBySessionId(99L)).thenReturn(
                Interaction.builder().id(2L).sessionId(99L).status("ACTIVE").build());

        assertThatThrownBy(() -> service.sendQuestion(99L, 1L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请先结束当前题目，再发送下一题");
        verify(questionService, never()).requireOwnedEntity(5L, 1L);
    }

    private ClassroomSession activeSession() {
        return ClassroomSession.builder()
                .id(99L).classId(10L).teacherId(5L).status("ACTIVE").build();
    }

    private QuestionBankItem question(Long id, String type, String title) {
        QuestionBankItem question = new QuestionBankItem();
        question.setId(id);
        question.setTeacherId(5L);
        question.setType(type);
        question.setTitle(title);
        question.setArchived(false);
        question.setCreatedAt(LocalDateTime.now());
        return question;
    }
}
