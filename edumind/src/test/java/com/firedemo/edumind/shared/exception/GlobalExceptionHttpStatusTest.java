package com.firedemo.edumind.shared.exception;

import com.firedemo.edumind.shared.result.Result;
import com.firedemo.edumind.platform.web.ResultHttpStatusAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHttpStatusTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(
                        new GlobalExceptionHandler(),
                        new ResultHttpStatusAdvice())
                .build();
    }

    @Test
    void mapsBusinessErrorCodeToHttpStatusWithoutChangingEnvelope() throws Exception {
        mvc.perform(get("/test/user-not-found"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.USER_NOT_FOUND.getMessage()))
                .andExpect(jsonPath("$.requestId").exists());

        mvc.perform(get("/test/data-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.DATA_NOT_FOUND.getCode()));
    }

    @Test
    void mapsLegacyDirectResultErrorsToTransportStatus() throws Exception {
        mvc.perform(get("/test/direct-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.DATA_ALREADY_EXISTS.getCode()))
                .andExpect(jsonPath("$.requestId").exists());

        mvc.perform(get("/test/confirmation-required"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CONFIRMATION_REQUIRED.getCode()));

        mvc.perform(get("/test/precondition-required"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value(ErrorCode.PRECONDITION_REQUIRED.getCode()));
    }

    @Test
    void preservesExplicitNonSuccessStatusAndSuccessfulResponses() throws Exception {
        mvc.perform(get("/test/payload-too-large"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value(ErrorCode.FILE_UPLOAD_ERROR.getCode()));

        mvc.perform(get("/test/success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void mapsUnexpectedExceptionToInternalServerError() throws Exception {
        mvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.SYSTEM_ERROR.getCode()))
                .andExpect(jsonPath("$.requestId").exists());
    }

    @RestController
    static class ProbeController {

        @GetMapping("/test/user-not-found")
        void userNotFound() {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        @GetMapping("/test/data-not-found")
        void dataNotFound() {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND);
        }

        @GetMapping("/test/direct-conflict")
        Result<Void> directConflict() {
            return Result.error(ErrorCode.DATA_ALREADY_EXISTS);
        }

        @GetMapping("/test/confirmation-required")
        ResponseEntity<Result<Void>> confirmationRequired() {
            return ResponseEntity.ok(Result.error(ErrorCode.CONFIRMATION_REQUIRED));
        }

        @GetMapping("/test/precondition-required")
        Result<Void> preconditionRequired() {
            return Result.error(ErrorCode.PRECONDITION_REQUIRED);
        }

        @GetMapping("/test/payload-too-large")
        ResponseEntity<Result<Void>> payloadTooLarge() {
            return ResponseEntity.status(413).body(Result.error(ErrorCode.FILE_UPLOAD_ERROR));
        }

        @GetMapping("/test/success")
        Result<String> success() {
            return Result.success("ok");
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new IllegalStateException("internal detail");
        }
    }
}
