package com.firedemo.demo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.demo.live.security.StudentTokenAuthenticationFilter;
import com.firedemo.demo.utils.McpApiKeyFilter;
import com.firedemo.demo.utils.TeacherSessionContextFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityConfigCsrfTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityConfig securityConfig = new SecurityConfig(
                mock(StudentTokenAuthenticationFilter.class),
                mock(TeacherSessionContextFilter.class),
                mock(McpApiKeyFilter.class),
                new ObjectMapper());
        ReflectionTestUtils.setField(securityConfig, "secureCookies", false);
        CookieCsrfTokenRepository repository = securityConfig.csrfTokenRepository();
        CsrfFilter csrfFilter = new CsrfFilter(repository);
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName("_csrf");
        csrfFilter.setRequestHandler(handler);
        mockMvc = MockMvcBuilders.standaloneSetup(new CsrfProbeController())
                .addFilters(csrfFilter)
                .build();
    }

    @Test
    void unsafeRequestWithoutTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/write").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void cookieAndHeaderTokenAllowUnsafeRequest() throws Exception {
        var csrfResponse = mockMvc.perform(get("/csrf"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        String token = csrfResponse.getContentAsString();
        Cookie cookie = csrfResponse.getCookie("XSRF-TOKEN");

        mockMvc.perform(post("/write")
                        .cookie(cookie)
                        .header("X-XSRF-TOKEN", token))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @RestController
    static class CsrfProbeController {
        @GetMapping("/csrf")
        String csrf(HttpServletRequest request) {
            CsrfToken token = (CsrfToken) request.getAttribute("_csrf");
            return token.getToken();
        }

        @PostMapping("/write")
        String write() {
            return "ok";
        }
    }
}
