package vn.edu.hcmute.drss.configs;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import vn.edu.hcmute.drss.realtime.RealtimeSubscriptionAuthorizationInterceptor;

@Configuration
@EnableWebSocketMessageBroker
public class RealtimeWebSocketConfig
        implements WebSocketMessageBrokerConfigurer {

    private final RealtimeSubscriptionAuthorizationInterceptor interceptor;

    public RealtimeWebSocketConfig(
            RealtimeSubscriptionAuthorizationInterceptor interceptor
    ) {
        this.interceptor = interceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Không mở wildcard origin và không dùng SockJS trong backend T2-05.
        registry.addEndpoint("/ws");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue")
                .setTaskScheduler(realtimeHeartbeatScheduler())
                .setHeartbeatValue(new long[] { 10_000, 10_000 });
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Bean
    public ThreadPoolTaskScheduler realtimeHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("drss-stomp-heartbeat-");
        return scheduler;
    }

    @Override
    public void configureClientInboundChannel(
            ChannelRegistration registration
    ) {
        registration.interceptors(interceptor);
    }
}