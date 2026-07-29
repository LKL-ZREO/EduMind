package com.firedemo.demo.Controller;

import com.firedemo.demo.DTO.QuestionDTO;
import com.firedemo.demo.DTO.QuestionUpsertDTO;
import com.firedemo.demo.Service.QuestionService;
import com.firedemo.demo.common.exception.BusinessException;
import com.firedemo.demo.common.exception.ErrorCode;
import com.firedemo.demo.common.result.Result;
import com.firedemo.demo.config.OwnershipGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/questions")
@RequiredArgsConstructor
public class QuestionController {
    private final QuestionService questionService;
    private final OwnershipGuard ownershipGuard;

    @GetMapping
    public Result<List<QuestionDTO>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sourceDocId,
            @RequestParam(required = false) String type) {
        Long userId = requireCurrentUserId();
        requireDocumentAccess(userId, sourceDocId);
        return Result.success(questionService.search(userId, keyword, sourceDocId, type));
    }

    @GetMapping("/{id}")
    public Result<QuestionDTO> get(@PathVariable Long id) {
        return Result.success(questionService.get(requireCurrentUserId(), id));
    }

    @PostMapping
    public Result<QuestionDTO> create(@Valid @RequestBody QuestionUpsertDTO request) {
        Long userId = requireCurrentUserId();
        requireDocumentAccess(userId, request.getSourceDocId());
        return Result.success(questionService.create(userId, request));
    }

    @PatchMapping("/{id}")
    public Result<QuestionDTO> update(@PathVariable Long id,
                                      @Valid @RequestBody QuestionUpsertDTO request) {
        Long userId = requireCurrentUserId();
        requireDocumentAccess(userId, request.getSourceDocId());
        return Result.success(questionService.update(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> archive(@PathVariable Long id) {
        questionService.archive(requireCurrentUserId(), id);
        return Result.success(null);
    }

    private void requireDocumentAccess(Long userId, String sourceDocId) {
        if (sourceDocId != null && !sourceDocId.isBlank()
                && !ownershipGuard.canReadDocument(userId, sourceDocId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private Long requireCurrentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getDetails() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
