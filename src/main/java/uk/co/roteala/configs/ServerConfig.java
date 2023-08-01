package uk.co.roteala.configs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.rocksdb.RocksDBException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRoutes;
import reactor.netty.http.websocket.WebsocketOutbound;
import reactor.netty.tcp.TcpServer;
import uk.co.roteala.common.AccountModel;
import uk.co.roteala.common.ChainState;
import uk.co.roteala.common.events.AccountMessage;
import uk.co.roteala.common.events.ChainStateMessage;
import uk.co.roteala.common.events.MessageActions;
import uk.co.roteala.common.monetary.Coin;
import uk.co.roteala.common.monetary.MoveFund;
import uk.co.roteala.handlers.TransmissionHandler;
import uk.co.roteala.handlers.WebSocketRouterHandler;
import uk.co.roteala.net.Peer;
import uk.co.roteala.processor.MessageProcessor;
import uk.co.roteala.processor.Processor;
import uk.co.roteala.services.MoveBalanceExecutionService;
import uk.co.roteala.storage.StorageServices;
import uk.co.roteala.utils.BlockchainUtils;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ServerConfig {
    private final StorageServices storage;

    private List<Connection> connections = new ArrayList<>();

    private List<WebsocketOutbound> webSocketConnections = new ArrayList<>();
    @Bean
    public void genesisConfig() throws IOException, RocksDBException {
        if(storage.getStateTrie() == null){
            ObjectMapper mapper = new ObjectMapper();

            //Read JSON accounts
            ClassPathResource resource = new ClassPathResource("genesis.json");
            InputStream inputStream = resource.getInputStream();

            List<AccountModel> accounts = mapper.readValue(inputStream, new TypeReference<List<AccountModel>>() {});
            List<String> accountsAddresses = new ArrayList<>();

            //Initialzie state trie
            ChainState stateTrie = new ChainState();
            stateTrie.setTarget(3);
            stateTrie.setLastBlockIndex(0);
            stateTrie.setReward(Coin.valueOf(BigDecimal.valueOf(33L)));

            accounts.forEach(accountModel -> accountsAddresses.add(accountModel.getAddress()));

            stateTrie.setAccounts(accountsAddresses);

            storage.addStateTrie(stateTrie, accounts);
            storage.addBlock(stateTrie.getGetGenesisBlock().getIndex().toString(), stateTrie.getGetGenesisBlock(), true);
        }
    }

    @Bean
    public Mono<Void> startServer() {
        return TcpServer.create()
                .doOnConnection(connectionStorageHandler())
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handle(transmissionHandler())
                .port(7331)
                .doOnBound(server -> log.info("Server started on address:{} and port:{}", server.address(), server.port()))
                .doOnUnbound(server -> log.info("Server stopped!"))
                .bindNow()
                .onDispose();
    }

    @Bean
    public Mono<Void> startWebsocket() {
        return HttpServer.create()
                .port(7071)
                .route(routerWebSocket())
                .doOnConnection(connection -> log.info("New explorer connected!"))
                .bindNow()
                .onDispose();
    }

    @Bean
    public List<WebsocketOutbound> webSocketConnections() {
        return this.webSocketConnections;
    }

    @Bean
    public Consumer<HttpServerRoutes> routerWebSocket() {
        return httpServerRoutes -> httpServerRoutes.ws("/stateChain", webSocketRouterStorage());
    }

    @Bean
    public WebSocketRouterHandler webSocketRouterStorage() {
        return new WebSocketRouterHandler(storage);
    }

    @Bean
    public List<Connection> connectionStorage() {
        return this.connections;
    }

    @Bean
    public Consumer<Connection> handleClientDisconnect() {
        return connection -> {
            log.info("Client disconnected!!");
        };
    }

    @Bean
    public Consumer<Connection> connectionStorageHandler() {
        return connection -> {
//            List<AccountModel> accounts = new ArrayList<>();
//
//            ChainState state = storage.getStateTrie();
//
//            ChainStateMessage stateTrie = new ChainStateMessage(state);
//            stateTrie.setVerified(true);
//            stateTrie.setMessageAction(MessageActions.APPEND);
//
//            state.getAccounts().forEach(accountAddress -> {
//                AccountModel account = storage.getAccountByAddress(accountAddress);
//
//                accounts.add(account);
//            });
//
//            Mono<ByteBuf> monoState = (Mono.just(Unpooled.copiedBuffer(SerializationUtils.serialize(stateTrie))));
//
//            Flux.fromIterable(accounts)
//                    .map(account -> {
//                        final AccountMessage accountMessage = new AccountMessage(account);
//                        accountMessage.setVerified(true);
//                        accountMessage.setMessageAction(MessageActions.APPEND);
//
//                        return Unpooled.copiedBuffer(SerializationUtils.serialize(accountMessage));
//                    })
//                            .mergeWith(monoState)
//                    .delayElements(Duration.ofMillis(400))
//                    .doOnNext(message -> {
//                        connection.outbound()
//                                .sendObject(Mono.just(message))
//                                .then()
//                                .subscribe();
//                    })
//                    .then()
//                    .subscribe();

            //Store the new peer
            Peer peer = new Peer();
            peer.setActive(true);
            peer.setPort(7331);
            peer.setAddress(BlockchainUtils.formatIPAddress(connection.address()));

            storage.addPeer(peer);

            log.info("New connection from:{}", peer);
            this.connections.add(connection);

            connection.onDispose(() -> {
                peer.setActive(false);
                peer.setLastTimeSeen(System.currentTimeMillis());

                storage.addPeer(peer);

                log.info("Node disconnected!");
                connections.remove(connection);
            });
        };
    }

    /**
     * Create bean to handle the server-client communications sending and receiving responses
     * */
    @Bean
    public TransmissionHandler transmissionHandler() {
        return new TransmissionHandler(messageProcessor());
    }

    /**
     * Same logic for the node
     * Question for the node we implement List<Handlers> for each? Or is it done by the server separetley?
     * */
    @Bean
    public MessageProcessor messageProcessor() {
        return new MessageProcessor();
    }

    @Bean
    public MoveFund moveFundExecution() {
        return new MoveBalanceExecutionService(storage);
    }
}
