package uk.co.roteala.server;


import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.rocksdb.RocksDBException;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.DisposableServer;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import reactor.netty.tcp.TcpServer;
import uk.co.roteala.common.BaseEmptyModel;
import uk.co.roteala.common.TransactionBaseModel;
import uk.co.roteala.net.OPCODES;
import uk.co.roteala.net.Peer;
import uk.co.roteala.storage.BrokerStorage;
import uk.co.roteala.utils.GlacierUtils;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Slf4j
@Component
public class Server{

    private BrokerStorage storage = new BrokerStorage();

    private DisposableServer server;

    @Bean
    public void testConnection() {

            TcpServer.create()
                    .handle((in, out) -> {

                        Flux<byte[]> v = in.receive().asByteArray();
                        v.doOnNext(t -> log.info("Data:{}", SerializationUtils.deserialize(t))).subscribe();

                        final TransactionBaseModel tx = new TransactionBaseModel();

                        tx.setTo("test");
                        tx.setTransactionIndex(1);
                        tx.setBlockHash("asdasdasda");

                        return out
                                .sendByteArray(Mono.just(SerializationUtils.serialize(tx)))
                                .neverComplete();
                    })
                    .port(7331)
                    .doOnBound(server -> {
                        log.info("Server started on address:{} and port:{}", server.address(), server.port());
                    })
                    .doOnUnbound(server -> {
                        log.info("Server stopped!");
                    })
                    .bindNow()
                    .onDispose()
                    .block();
    }


//    @Bean
//    public void startServer() throws RocksDBException {
//        this.storage.start();
//
//        TcpServer.create()
//                .doOnConnection(doOnConnectionHandler())//Update peer status, add it to database
//                .handle((in, out) -> {
//                    Flux<BaseEmptyModel> transactionsFlux = in.receive().asByteArray().map(bytes -> {
//                        final BaseEmptyModel tx = (TransactionBaseModel) SerializationUtils.deserialize(bytes);
//
//                        return tx;
//                    });
//                    transactionsFlux.doOnNext(
//                            t->log.info("Transaction:{}", t)
//                    );
//
//                    TransactionBaseModel transaction = new TransactionBaseModel();
//                    transaction.setBlockHash("asasaa");
//                    transaction.setFrom("asasa111");
//
//                    out.sendByteArray(Mono.just(SerializationUtils.serialize(transaction)));
//
//                    return Mono.empty();
//                })//Handle message from peer such as give n peer or fetch a list
//                .port(7331)
//                .doOnBound(server -> {
//                    log.info("Server started on address:{} and port:{}", server.address(), server.port());
//                })
//                .doOnUnbound(server -> {
//                    log.info("Server stopped!");
//                })
//                .bindNow();
//    }

    private Publisher<Void> doWithHandler(NettyInbound inbound, NettyOutbound outbound){
        //Read the incoming message
        //Check for the opcodes
        Flux<Integer> opcodeFlux = inbound.receive().asByteArray().map(bytes -> {
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
            return byteBuffer.getInt();
        });

        opcodeFlux.next().log().toString();
        opcodeFlux.doOnNext(o -> log.info("T:{}",o));

        outbound.sendByteArray(Mono.just(SerializationUtils.serialize("TESTSSSS!")));

        AtomicReference<String> ipAddress = new AtomicReference<>();

        inbound.withConnection(connection -> {
            ipAddress.set(GlacierUtils.parseIpAddress(connection.address().toString()));
        });

        log.info("Adress:{}", ipAddress);
        opcodeFlux.subscribe(s-> log.info("OPCODE:{}", s));

        opcodeFlux.doOnNext(opCodeHandler(ipAddress.get(), outbound, inbound));

        return Mono.empty();
    }

    public Consumer<Integer> opCodeHandler(String ip, NettyOutbound outbound, NettyInbound inbound) {
        return opcode -> {
            if(Objects.equals(opcode, OPCODES.reqPeers.getCode())) {
                Set<String> peersIps = new HashSet<>(storage.getPeers(true));

                if(peersIps.contains(ip)){
                    peersIps.remove(ip);
                }

                log.info("Sending peers:{}", peersIps);

                outbound.sendByteArray(Mono.just(SerializationUtils.serialize(peersIps)))
                        .neverComplete()
                        .doFinally(signalType -> {
                            inbound.withConnection(connection -> {
                                storage.updatePeerStatus(GlacierUtils.parseIpAddress(connection.address().toString()));
                                log.info("Peer disconnected, status updated!");
                            });
                        });
            }
        };
    }

    public Consumer<Connection> doOnConnectionHandler(){
        return connection -> {
            String ipAddress = GlacierUtils.parseIpAddress(connection.address().toString());
            try {
                final byte[] serializedPeer = storage.getPeer(ipAddress);
                if(serializedPeer == null) {
                    Peer peer = new Peer();
                    peer.setActive(true);
                    peer.setAddress(ipAddress);
                    peer.setLastTimeSeen(System.currentTimeMillis());

                    storage.addPeer(peer);
                    log.info("New peer added:{}", peer);
                } else {
                    Peer peer = (Peer) SerializationUtils.deserialize(serializedPeer);
                    peer.setLastTimeSeen(System.currentTimeMillis());
                    peer.setActive(true);

                    storage.addPeer(peer);
                    log.info("Peer updated:{}", peer);
                }
            } catch (Exception e) {
                log.error("Error while retrieving peer!{}", e);
            }

        };
    }
}
