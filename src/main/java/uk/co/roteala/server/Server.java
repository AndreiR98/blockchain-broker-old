package uk.co.roteala.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.rocksdb.RocksDBException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.DisposableServer;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import reactor.netty.tcp.TcpServer;
import uk.co.roteala.net.Peer;
import uk.co.roteala.net.StreamConnectionFactory;
import uk.co.roteala.storage.BrokerStorage;
import com.google.common.util.concurrent.AbstractExecutionThreadService;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.*;
import java.util.concurrent.Flow;
import java.util.function.BiFunction;
import java.util.function.Consumer;

@Slf4j
@Component
public class Server{

    private BrokerStorage storage = new BrokerStorage();


    private int port = 7331;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    @Bean
    public void startServer() throws RocksDBException {
        storage.start();

        TcpServer.create()
//                .doOnConnection(connection -> {
//                    //Add ip to the peers database
//                    Peer peer = new Peer();
//                    peer.setLastTimeSeen(System.currentTimeMillis());
//                    peer.setAddress(connection.channel().remoteAddress().toString());
//
//                    try {
//                        storage.addPeer(peer);
//                    } catch (RocksDBException e) {
//                        throw new RuntimeException(e);
//                    }
//
//                })
                .doOnConnection(doOnConnectionHandler())
                .port(7331)
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
                    Peer peer = (Peer) SerializationUtils.deserialize(serializedPeer);

                    storage.addPeer(peer);
                } else {
                    //Update last time
                    Peer peer = (Peer) SerializationUtils.deserialize(serializedPeer);

                    peer.setLastTimeSeen(System.currentTimeMillis());

                    storage.addPeer(peer);

                    //TODO: Add active status for peer

                    //Prepare 50 random peers to send to this peer
                    Set<String> peersIps = new HashSet<>(storage.getPeers(true));

                    if(peersIps.contains(ipAddress)){
                        peersIps.remove(ipAddress);
                    }

                    //Send peer 50 ranodm peers
                }
            } catch (Exception e) {
                log.error("Error while retrieving peer!");
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
