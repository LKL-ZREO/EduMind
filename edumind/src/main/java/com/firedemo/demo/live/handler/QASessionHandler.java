package com.firedemo.demo.live.handler;

import com.firedemo.demo.DTO.QAMessageDTO;
import com.firedemo.demo.Entity.ClassroomQA;
import com.firedemo.demo.common.exception.BusinessException;
import com.firedemo.demo.common.exception.ErrorCode;
import com.firedemo.demo.mapper.ClassroomQAMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class QASessionHandler {

    private final ClassroomQAMapper qaMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/session/{sessionId}/qa/ask")
    public void ask(@DestinationVariable Long sessionId, @Payload QAMessageDTO.QuestionItem dto,
                    SimpMessageHeaderAccessor headerAccessor) {
        // 从课堂令牌身份读取学生信息（DB 记录真实身份，广播时匿名展示）
        String[] identity = resolveStudentIdentity(headerAccessor);
        ClassroomQA qa = new ClassroomQA();
        qa.setSessionId(sessionId);
        qa.setQuestion(dto.getQuestion());
        qa.setStudentId(identity[0]);
        qa.setStudentName(identity[1]);
        qaMapper.insertQuestion(qa);
        pushQAToTeacher(sessionId);
    }

    @MessageMapping("/session/{sessionId}/qa/{qaId}/answer")
    public void answer(@DestinationVariable Long sessionId, @DestinationVariable Long qaId,
                       @Payload QAMessageDTO.QuestionItem dto) {
        if (qaMapper.markAnswered(qaId, sessionId, dto.getAnswerText()) == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "问题不属于当前课堂");
        }
        pushQAToTeacher(sessionId);
    }

    private void pushQAToTeacher(Long sessionId) {
        List<ClassroomQA> top = qaMapper.findTopLevelBySessionId(sessionId);
        List<QAMessageDTO.QuestionItem> items = top.stream().map(q -> {
            QAMessageDTO.QuestionItem item = new QAMessageDTO.QuestionItem();
            item.setId(q.getId());
            item.setQuestion(q.getQuestion());
            item.setSimilarCount(q.getSimilarCount() != null ? q.getSimilarCount() : 0);
            item.setAnswered(q.getIsAnswered() != null && q.getIsAnswered());
            item.setAnswerText(q.getAnswerText());
            item.setCreatedAt(q.getCreatedAt() != null ? q.getCreatedAt().toString() : "");
            return item;
        }).toList();
        messagingTemplate.convertAndSend("/topic/session/" + sessionId + "/qa",
                QAMessageDTO.builder().topQuestions(items).build());
    }

    /** 从 STOMP 会话属性读取学生身份。 */
    private static String[] resolveStudentIdentity(SimpMessageHeaderAccessor accessor) {
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs == null) return new String[]{"anonymous", "匿名"};
        String studentId = String.valueOf(attrs.getOrDefault("studentId", "anonymous"));
        String studentName = String.valueOf(attrs.getOrDefault("username", "匿名"));
        return new String[]{studentId, studentName};
    }
}
