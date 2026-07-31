package com.firedemo.edumind.shared.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeHttpStatusTest {

    @Test
    void mapsDomainCodesIndependentlyFromTransportCodes() {
        assertThat(ErrorCode.httpStatusFor(4001)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.httpStatusFor(4002)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.httpStatusFor(3001)).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ErrorCode.httpStatusFor(300)).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void mapsStandardAndUnknownCodesConservatively() {
        assertThat(ErrorCode.httpStatusFor(404)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.httpStatusFor(9999)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
