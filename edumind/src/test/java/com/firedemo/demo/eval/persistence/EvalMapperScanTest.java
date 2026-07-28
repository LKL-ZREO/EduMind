package com.firedemo.demo.eval.persistence;

import com.firedemo.demo.DemoApplication;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.core.annotation.AnnotatedElementUtils;

import static org.assertj.core.api.Assertions.assertThat;

class EvalMapperScanTest {

    @Test
    void applicationRegistersEvaluationMappers() {
        MapperScan mapperScan = AnnotatedElementUtils.findMergedAnnotation(
                DemoApplication.class, MapperScan.class);

        assertThat(mapperScan).isNotNull();
        assertThat(mapperScan.value()).contains("com.firedemo.demo.eval.persistence");
    }
}
