package uk.co.roteala.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.websocket.WebsocketInbound;
import reactor.netty.http.websocket.WebsocketOutbound;
import uk.co.roteala.common.ChainState;
import uk.co.roteala.common.monetary.Coin;
import uk.co.roteala.storage.StorageServices;

import java.util.List;
import java.util.function.BiFunction;

@Slf4j
@RequiredArgsConstructor
public class WebSocketRouterHandler implements BiFunction<WebsocketInbound, WebsocketOutbound, Flux<Void>> {
    private final StorageServices storage;

    @Autowired
    private List<WebsocketOutbound> websocketOutbounds;

    @Override
    public Flux<Void> apply(WebsocketInbound websocketInbound, WebsocketOutbound websocketOutbound) {
        this.websocketOutbounds.add(websocketOutbound);

        ObjectMapper objectMapper = new ObjectMapper();
        String jsonString = null;

        ChainState state = storage.getStateTrie();
        state.setGetGenesisBlock(null);
        state.setAccounts(null);

        try {
            jsonString = objectMapper.writeValueAsString(state);
        }catch (Exception e) {
            throw new RuntimeException(e);
        }

        websocketOutbound.sendString(Mono.just(jsonString))
                .then()
                .subscribe();



        return Flux.never();
    }
}
