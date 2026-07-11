package com.firedemo.demo.Controller;

import com.firedemo.demo.Entity.Submission;
import com.firedemo.demo.Service.FileStorageService;
import com.firedemo.demo.Service.SubmissionService;
import com.firedemo.demo.common.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("SubmissionController — 提交记录")
class SubmissionControllerTest {

    private SubmissionService submissionService;
    private FileStorageService fileStorageService;
    private SubmissionController controller;

    @BeforeEach
    void setUp() {
        submissionService = mock(SubmissionService.class);
        fileStorageService = mock(FileStorageService.class);
        controller = new SubmissionController(submissionService, fileStorageService);
    }

    @Nested
    @DisplayName("GET /api/submissions/{id}/content")
    class GetContent {

        @Test
        @DisplayName("正常读取内容 → 200")
        void shouldReturnContent() {
            Submission sub = new Submission();
            sub.setId(1L);
            sub.setStudentName("张三");
            sub.setFileName("作业.pdf");
            sub.setFilePath("/tmp/abc.pdf");

            when(submissionService.getById(1L)).thenReturn(sub);
            when(fileStorageService.readFileContent("/tmp/abc.pdf")).thenReturn("作业正文内容");

            Result<Map<String, Object>> result = controller.getContent(1L);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData()).containsEntry("submissionId", 1L);
            assertThat(result.getData()).containsEntry("studentName", "张三");
            assertThat(result.getData()).containsEntry("content", "作业正文内容");
        }

        @Test
        @DisplayName("提交记录不存在 → 404")
        void shouldReturn404WhenNotFound() {
            when(submissionService.getById(999L)).thenReturn(null);

            Result<Map<String, Object>> result = controller.getContent(999L);

            assertThat(result.getCode()).isEqualTo(404);
        }

        @Test
        @DisplayName("文件读取失败 → 500")
        void shouldReturn500WhenReadFails() {
            Submission sub = new Submission();
            sub.setId(1L);
            sub.setFilePath("/tmp/bad.pdf");

            when(submissionService.getById(1L)).thenReturn(sub);
            when(fileStorageService.readFileContent("/tmp/bad.pdf"))
                    .thenThrow(new RuntimeException("磁盘故障"));

            Result<Map<String, Object>> result = controller.getContent(1L);

            assertThat(result.getCode()).isEqualTo(500);
        }
    }
}
