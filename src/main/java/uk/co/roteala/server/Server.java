package uk.co.roteala.server;


import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksDBException;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpServer;
import uk.co.roteala.net.Peer;
import uk.co.roteala.storage.BrokerStorage;

import java.util.*;
import java.util.function.Consumer;

@Slf4j
@Component
public class Server{

    private BrokerStorage storage = new BrokerStorage();

    @Bean
    public void startServer() throws RocksDBException {
        storage.start();

        TcpServer.create()
                .doOnConnection(doOnConnectionHandler())
                .handle((in, out) -> {
                    //Prepare 50 random peers to send to this peer
                    Set<String> peersIps = new HashSet<>(storage.getPeers(true));

                    return out
                            .sendByteArray(Mono.just(SerializationUtils.serialize(peersIps)))
                            .neverComplete()
                            .doFinally(signalType -> {
                                in.withConnection(connection -> {
                                    storage.updatePeerStatus(parseIpAddress(connection.address().toString()));
                                    log.info("Peer disconnected status updated!");
                                });
                            });
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
    public Consumer<Connection> doOnConnectionHandler(){
        return connection -> {
            String ipAddress = parseIpAddress(connection.address().toString());
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

    /**
     * ADD those methods to the commons
     * */
    private static String parseIpAddress(String address) {
        return address
                .substring(1)
                .split(":")[0];
    }
}
