package uk.co.roteala.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Service;
import uk.co.roteala.api.ApiStateChain;
import uk.co.roteala.common.ChainState;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import uk.co.roteala.storage.StorageServices;

@Service
@Slf4j
public class WebSocketServices {



    @Autowired
    private SimpMessagingTemplate simpMessagingTemplate;

    // Method to broadcast updates to all clients
    public void broadcastToAll(String message) {
        this.simpMessagingTemplate.convertAndSend("/topic/broadcast", message);
    }
}
