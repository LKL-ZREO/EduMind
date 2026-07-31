package com.firedemo.edumind.assistant.evaluation.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EvalMapperRegistrationTest {

    @Test
    void evaluationMappersAreSelfRegistering() {
        assertThat(EvalRunMapper.class).hasAnnotation(Mapper.class);
        assertThat(EvalCaseResultMapper.class).hasAnnotation(Mapper.class);
    }
}
