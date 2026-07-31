package com.firedemo.edumind.homework;

import com.firedemo.edumind.teaching.QuestionBankItem;
import com.firedemo.edumind.teaching.QuestionDTO;
import com.firedemo.edumind.teaching.QuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class HomeworkDraftService {

    private final HomeworkDraftMapper draftMapper;
    private final HomeworkDraftQuestionMapper draftQuestionMapper;
    private final QuestionService questionService;

    public List<HomeworkDraft> listByTeacherId(Long teacherId) {
        return draftMapper.selectByTeacherId(teacherId);
    }

    public HomeworkDraft getById(Long draftId) {
        return draftMapper.selectById(draftId);
    }

    public void create(HomeworkDraft draft) {
        draftMapper.insert(draft);
    }

    public void update(HomeworkDraft draft) {
        draftMapper.updateById(draft);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long draftId) {
        draftQuestionMapper.deleteByDraftId(draftId);
        draftMapper.deleteById(draftId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void replaceQuestions(Long draftId, Long teacherId, List<QuestionDTO> questions) {
        draftQuestionMapper.deleteByDraftId(draftId);
        if (questions == null || questions.isEmpty()) {
            return;
        }

        int order = 0;
        for (QuestionDTO dto : questions) {
            if (dto == null) {
                continue;
            }
            QuestionBankItem item = questionService.saveHomeworkQuestion(
                    teacherId,
                    dto.getId(),
                    dto.getTitle(),
                    dto.getRequirement(),
                    dto.getScore(),
                    dto.getUploadRequired());

            HomeworkDraftQuestion relation = new HomeworkDraftQuestion();
            relation.setDraftId(draftId);
            relation.setQuestionId(item.getId());
            relation.setSortOrder(order++);
            relation.setCreatedAt(LocalDateTime.now());
            draftQuestionMapper.insert(relation);
        }
    }

    public List<QuestionDTO> listQuestions(Long draftId) {
        return draftQuestionMapper.selectByDraftId(draftId).stream()
                .map(HomeworkDraftQuestion::getQuestionId)
                .map(questionService::findDTO)
                .filter(Objects::nonNull)
                .toList();
    }
}
