package uk.co.roteala.configs;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import uk.co.roteala.api.ApiStateChain;
import uk.co.roteala.common.ChainState;
import uk.co.roteala.storage.StorageServices;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    @Autowired
    private StorageServices storage;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-API")
                .setAllowedOrigins("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/user"); // Enable a simple message broker
        config.setApplicationDestinationPrefixes("/app"); // Prefix for user-defined destinations
    }

    @SubscribeMapping("last-block-index")
    public String getLatestChanges() {
        ObjectMapper objectMapper = new ObjectMapper();

        ChainState state = this.storage.getStateTrie();

        String jsonString = null;

        ApiStateChain apiStateChain = new ApiStateChain();
        apiStateChain.setNetworkFees(state.getNetworkFees());
        apiStateChain.setLastBlockIndex(state.getLastBlockIndex());

        try {
            jsonString = objectMapper.writeValueAsString(apiStateChain);
        }catch (Exception e) {
            log.error("Serialization issue!");
        }

        return jsonString;
    }
}
