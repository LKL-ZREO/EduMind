package com.firedemo.edumind.assistant;

import com.firedemo.edumind.assistant.context.AgentExecutionContext;
import com.firedemo.edumind.shared.exception.BusinessException;
import com.firedemo.edumind.shared.exception.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;

@Configuration
public class AgentServiceConfiguration {

    @Bean
    @ConditionalOnMissingBean(AgentService.class)
    AgentService unavailableAgentService() {
        return new AgentService() {
            @Override
            public String chat(String message, String status) {
                throw unavailable();
            }

            @Override
            public String chat(String message, AgentExecutionContext context, String status) {
                throw unavailable();
            }

            @Override
            public Flux<String> streamChat(
                    String message,
                    AgentExecutionContext context,
                    String status) {
                return Flux.error(unavailable());
            }

            @Override
            public void registerSessionContext(AgentExecutionContext context) {
                // No remote agent session exists for the disabled backend.
            }

            @Override
            public void clearMemory(Long userId) {
                // No Agent memory exists for the disabled backend.
            }

            @Override
            public void clearMemory(Long userId, String sessionId) {
                // No Agent memory exists for the disabled backend.
            }

            @Override
            public boolean checkConnection() {
                return false;
            }

            private BusinessException unavailable() {
                return new BusinessException(
                        ErrorCode.AI_SERVICE_ERROR.getCode(),
                        "AI service is unavailable for the configured LLM backend");
            }
        };
    }
}
