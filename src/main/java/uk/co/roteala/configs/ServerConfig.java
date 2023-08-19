package uk.co.roteala.configs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksDBException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRoutes;
import reactor.netty.http.websocket.WebsocketOutbound;
import reactor.netty.tcp.SslProvider;
import reactor.netty.tcp.TcpServer;
import reactor.util.retry.Retry;
import uk.co.roteala.common.AccountModel;
import uk.co.roteala.common.ChainState;
import uk.co.roteala.common.PseudoTransaction;
import uk.co.roteala.common.events.MessageActions;
import uk.co.roteala.common.events.MessageTypes;
import uk.co.roteala.common.events.MessageWrapper;
import uk.co.roteala.common.monetary.Coin;
import uk.co.roteala.common.monetary.MoveFund;
import uk.co.roteala.handlers.ProxyRouter;
import uk.co.roteala.handlers.TransmissionHandler;
import uk.co.roteala.handlers.WebSocketRouterHandler;
import uk.co.roteala.net.Peer;
import uk.co.roteala.processor.MessageProcessor;
import uk.co.roteala.services.MoveBalanceExecutionService;
import uk.co.roteala.storage.StorageServices;
import uk.co.roteala.utils.BlockchainUtils;

import java.io.*;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class ServerConfig {
    private final StorageServices storage;

    private final GlacierBrokerConfigs configs;


    private List<Connection> connections = new ArrayList<>();

    private List<WebsocketOutbound> webSocketConnections = new ArrayList<>();

    /**
     * Every 10 minutes check if any ophan transactions and pushed them to the miners
     * */
    @Scheduled(cron = "0 */10 * * * *")
    public void pusher() {
        Flux.fromIterable(this.storage.getPseudoTransactions())
                .map(transaction -> {
                    MessageWrapper wrapper = new MessageWrapper();
                    wrapper.setVerified(true);
                    wrapper.setAction(MessageActions.APPEND);
                    wrapper.setContent(transaction);
                    wrapper.setType(MessageTypes.MEMPOOL);

                    return wrapper;
                })
                .delayElements(Duration.ofMillis(150))
                .doOnNext(wrapper -> {
                    for(Connection connection : this.connections) {
                        connection.outbound()
                                .sendObject(Mono.just(wrapper.serialize()))
                                .then().subscribe();
                    }
                })
                .then().subscribe();
    }


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
            stateTrie.setAllowEmptyMining(true);
            stateTrie.setReward(Coin.valueOf(BigDecimal.valueOf(12L)));

            accounts.forEach(accountModel -> accountsAddresses.add(accountModel.getAddress()));

            stateTrie.setAccounts(accountsAddresses);

            storage.addStateTrie(stateTrie, accounts);
            storage.addBlock(stateTrie.getGenesisBlock().getHeader().getIndex().toString(),
                    stateTrie.getGenesisBlock(), true);
        }

        Peer initialPeer = new Peer();
        initialPeer.setAddress(configs.getInitialPeer());
        initialPeer.setPort(7331);
        initialPeer.setActive(true);

        this.storage.addPeer(initialPeer);
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
    public SslProvider sslProvider()  {
        try {
            ClassPathResource resource = new ClassPathResource("privatekey.key");
            ClassPathResource certificateResource = new ClassPathResource("certificate.crt");

            InputStream inputStream = resource.getInputStream();
            InputStream certificateStream = certificateResource.getInputStream();

            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            X509Certificate certificate = (X509Certificate) certificateFactory
                    .generateCertificate(certificateStream);

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            StringBuilder privateKeyPEM = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("-----")) {
                    privateKeyPEM.append(line);
                }
            }

            final byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyPEM.toString());

            KeyFactory keyFactory = KeyFactory.getInstance("RSA"); // Or "EC" for ECDSA, etc.
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            PrivateKey privateKeyModel = keyFactory.generatePrivate(keySpec);

            return SslProvider.builder()
                    .sslContext(SslContextBuilder
                            .forServer(privateKeyModel, certificate)
                            .build())
                    .build();
        } catch (Exception e) {
            return null;
        }
    }
    @Bean
    public List<WebsocketOutbound> webSocketConnections() {
        return this.webSocketConnections;
    }

    @Bean
    public ProxyRouter proxyRouter() {
        return new ProxyRouter(webSocketRouterStorage());
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
            Peer peer = new Peer();
            peer.setActive(true);
            peer.setPort(7331);
            peer.setAddress(parseAddress(connection.address()));

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

    private String parseAddress(SocketAddress address) {
        InetSocketAddress inetSocketAddress = (InetSocketAddress) address;
        String hostWithoutPort = inetSocketAddress.getAddress().getHostAddress();

        return hostWithoutPort;
    }
}
