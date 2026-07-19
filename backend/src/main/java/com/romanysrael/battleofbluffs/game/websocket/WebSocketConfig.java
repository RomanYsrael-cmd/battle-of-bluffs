package com.romanysrael.battleofbluffs.game.websocket;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final MatchSubscriptionInterceptor subscriptionInterceptor;
    private final String frontendOrigin;
    private final ThreadPoolTaskScheduler heartbeatScheduler;
    private final JsonMapper jsonMapper;

    public WebSocketConfig(
            MatchSubscriptionInterceptor subscriptionInterceptor,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendOrigin,
            @Qualifier("webSocketHeartbeatScheduler")
            ThreadPoolTaskScheduler heartbeatScheduler,
            JsonMapper jsonMapper) {
        this.subscriptionInterceptor = subscriptionInterceptor;
        this.frontendOrigin = frontendOrigin;
        this.heartbeatScheduler = heartbeatScheduler;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue")
                .setHeartbeatValue(new long[] {10_000, 10_000})
                .setTaskScheduler(heartbeatScheduler);
        registry.setUserDestinationPrefix("/user");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(frontendOrigin);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(subscriptionInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration
                .setMessageSizeLimit(16 * 1024)
                .setSendBufferSizeLimit(64 * 1024)
                .setSendTimeLimit(10_000)
                .setTimeToFirstMessage(15_000);
    }

    @Override
    public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
        messageConverters.add(new JacksonJsonMessageConverter(jsonMapper));
        return false;
    }

    @Bean
    static ThreadPoolTaskScheduler webSocketHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("gotg-ws-heartbeat-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
