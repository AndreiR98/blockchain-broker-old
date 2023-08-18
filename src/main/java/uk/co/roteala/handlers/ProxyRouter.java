package uk.co.roteala.handlers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;
import reactor.netty.http.server.HttpServerRoutes;

import java.util.concurrent.Flow;
import java.util.function.BiFunction;
import java.util.function.Consumer;

@Slf4j
@Component
@CrossOrigin
@RequiredArgsConstructor
public class ProxyRouter implements Consumer<HttpServerRoutes> {

    private final WebSocketRouterHandler webSocketRouterStorage;
    @Override
    public void accept(HttpServerRoutes httpServerRoutes) {
        httpServerRoutes.ws("/stateChain", webSocketRouterStorage);
    }

    @CrossOrigin
    private static class AccountHandler implements
            BiFunction<HttpServerRequest, HttpServerResponse, Publisher<Void>> {

        @Override
        public Publisher<Void> apply(HttpServerRequest request, HttpServerResponse response) {

            RestTemplate restTemplate = new RestTemplate();

            log.info("Param:{}", request.param("address"));

            log.info("Processing request:{}", request.receive());

            String endpointUrl = "http://localhost:7070/explorer/address/"+request.param("address");

            ResponseEntity<String> respo = restTemplate.getForEntity(endpointUrl, String.class);

            String responseBody = respo.getBody();
            int statusCode = respo.getStatusCodeValue();

            log.info("Body:{}", responseBody);

            response.sendString(Mono.just(responseBody)).then().subscribe();

            return Mono.empty();
        }
        //
    }
}
