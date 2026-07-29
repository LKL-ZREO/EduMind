package com.firedemo.demo.live.config;

import com.firedemo.demo.live.security.WebSocketAuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.session.Session;
import org.springframework.session.web.socket.config.annotation.AbstractSessionWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig extends AbstractSessionWebSocketMessageBrokerConfigurer<Session> {

    private final WebSocketAuthInterceptor authInterceptor;
    private final TaskScheduler brokerTaskScheduler;

    /** CORS 允许的源，与 REST CORS 保持一致 */
    @Value("${app.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
    private String allowedOrigins;

    public WebSocketConfig(
            WebSocketAuthInterceptor authInterceptor,
            @Qualifier("liveBrokerTaskScheduler") TaskScheduler brokerTaskScheduler) {
        this.authInterceptor = authInterceptor;
        this.brokerTaskScheduler = brokerTaskScheduler;
    }

    @Override
    public void configureMessageBroker(@NonNull MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/user")
                .setTaskScheduler(brokerTaskScheduler)
                .setHeartbeatValue(new long[]{10_000, 10_000});
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    protected void configureStompEndpoints(@NonNull StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/live")
                .setAllowedOriginPatterns(allowedOrigins.split(","));
    }

    @Override
    public void configureClientInboundChannel(@NonNull ChannelRegistration registration) {
        super.configureClientInboundChannel(registration);
        registration.interceptors(authInterceptor);
    }
}
