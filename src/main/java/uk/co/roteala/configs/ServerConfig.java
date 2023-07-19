package uk.co.roteala.configs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksDBException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpServer;
import uk.co.roteala.common.AccountModel;
import uk.co.roteala.common.ChainState;
import uk.co.roteala.common.PseudoTransaction;
import uk.co.roteala.common.events.AccountMessage;
import uk.co.roteala.common.events.ChainStateMessage;
import uk.co.roteala.common.events.MempoolTransaction;
import uk.co.roteala.common.events.Message;
import uk.co.roteala.common.monetary.MoveFund;
import uk.co.roteala.handlers.TransmissionHandler;
import uk.co.roteala.net.Peer;
import uk.co.roteala.processor.MessageProcessor;
import uk.co.roteala.processor.Processor;
import uk.co.roteala.services.MoveBalanceExecutionService;
import uk.co.roteala.storage.StorageServices;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
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
            stateTrie.setTarget(2);
            stateTrie.setLastBlockIndex(0);

            accounts.forEach(accountModel -> accountsAddresses.add(accountModel.getAddress()));

            stateTrie.setAccounts(accountsAddresses);

            storage.addStateTrie(stateTrie, accounts);
            storage.addBlock(stateTrie.getGetGenesisBlock().getHash(), stateTrie.getGetGenesisBlock(), true);
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
    public List<Connection> connectionStorage() {
        return this.connections;
    }

    @Bean
    public Consumer<Connection> connectionStorageHandler() {
        return connection -> {
            this.connections.add(connection);
        };
    }

    /**
     * Create bean to handle the server-client communications sending and receiving responses
     * */
    @Bean
    public TransmissionHandler transmissionHandler() {
        return new TransmissionHandler();
    }

    /**
     * Same logic for the node
     * Question for the node we implement List<Handlers> for each? Or is it done by the server separetley?
     * */
    @Bean
    public Processor messageProcessor() {
        return new MessageProcessor(storage);
    }

    @Bean
    public MoveFund moveFundExecution() {
        return new MoveBalanceExecutionService(storage);
    }


//    AddressBaseModel addressModel = GlacierUtils.formatAddress(connection.address().toString());
//            try {
//        String address = addressModel.getAddress();
//
//        Peer peer;
//
//        if(storage.getPeer(address.getBytes()) != null) {
//            peer = (Peer) storage.getPeer(address.getBytes());
//            peer.setActive(true);
//        } else {
//            peer = new Peer();
//            peer.setActive(true);
//            //peer.setPort(addressModel.getPort());
//            peer.setAddress(addressModel.getAddress());
//
//            log.info("Peer updated:{}", peer);
//
//            if(configs.isNetWorkMode()){
//                peer.setPort(addressModel.getPort());
//                log.info("New peer added:{} with key:{}", peer, address);
//            } else {
//                log.info("New peer added:{} with key:{}", peer, address);
//                peer.setPort(configs.getDefaultPort());
//            }
//        }
//        storage.addPeer(address.getBytes(), peer);
//
//        connection.onDispose()
//                .doFinally(signalType -> onDisconnect(connection));
//    } catch (Exception e) {
//        log.error("Error while retrieving peer!{}", e);
//    }

    private void onDisconnect(Connection connection){
        try {
            String addressWithPort = connection.address().toString();

            if(storage.getPeer(addressWithPort.getBytes()) != null) {
                Peer peer = (Peer) storage.getPeer(addressWithPort.getBytes());
                peer.setLastTimeSeen(System.currentTimeMillis());
                peer.setActive(false);

                storage.addPeer(addressWithPort.getBytes(), peer);
                log.info("Peer disconnected");
            }

        }catch (Exception e) {
            //
        }

    }
}
