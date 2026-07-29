package com.firedemo.demo.Service;

import com.firedemo.demo.DTO.InteractionCreateDTO;
import com.firedemo.demo.DTO.QuestionDTO;
import com.firedemo.demo.DTO.QuestionUpsertDTO;
import com.firedemo.demo.Entity.QuestionBankItem;

import java.util.List;

public interface QuestionService {
    List<QuestionDTO> search(Long teacherId, String keyword, String sourceDocId, String type);

    List<QuestionBankItem> searchEntities(Long teacherId, String keyword, String sourceDocId, String type);

    QuestionDTO get(Long teacherId, Long id);

    QuestionDTO create(Long teacherId, QuestionUpsertDTO request);

    QuestionDTO update(Long teacherId, Long id, QuestionUpsertDTO request);

    void archive(Long teacherId, Long id);

    QuestionBankItem requireOwnedEntity(Long teacherId, Long id);

    QuestionBankItem findEntity(Long id);

    QuestionDTO findDTO(Long id);

    QuestionBankItem saveHomeworkQuestion(
            Long teacherId,
            Long id,
            String title,
            String requirement,
            Integer score,
            Boolean uploadRequired);

    QuestionBankItem createFromInteraction(Long teacherId, InteractionCreateDTO request);
}
