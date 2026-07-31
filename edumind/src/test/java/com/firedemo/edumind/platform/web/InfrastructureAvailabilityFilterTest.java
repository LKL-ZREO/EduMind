package com.firedemo.edumind.platform.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class InfrastructureAvailabilityFilterTest {

    @Test
    void mapsRedisSessionOutageToControlled503() throws Exception {
        InfrastructureAvailabilityFilter filter = new InfrastructureAvailabilityFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doThrow(new RedisConnectionFailureException("redis down"))
                .when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("认证服务暂时不可用");
    }
}
