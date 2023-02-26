package uk.co.roteala.server;

import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksDBException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import uk.co.roteala.net.Peer;
import uk.co.roteala.net.StreamConnectionFactory;
import uk.co.roteala.storage.BrokerStorage;
import com.google.common.util.concurrent.AbstractExecutionThreadService;

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

@Slf4j
@Component
public class Server{

//    @Autowired
//    private BrokerStorage storage;

    private Selector selector;

    private ServerSocketChannel serverSocketChannel;

    private ByteBuffer readBuffer = ByteBuffer.allocate(1024);
    private ByteBuffer writeBuffer = ByteBuffer.allocate(1024);

    @Bean
    public void startBroker() throws IOException {
        try{
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.socket().bind(new InetSocketAddress(7331));
            selector = SelectorProvider.provider().openSelector();
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            log.info("Server started!");

            while (true) {
                selector.select();
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectionKeys.iterator();

                while(keyIterator.hasNext()){
                    SelectionKey key = keyIterator.next();

                    if(key.isAcceptable()){
                        handleAccept(key);
                    }

                    if(key.isReadable()){
                        handleRead(key);
                    }

                    if(key.isWritable()){
                        handleWrite(key);
                    }

                    keyIterator.remove();
                }
            }
        }catch (IOException e){
            log.error("Error starting server: {}", e.getMessage());
        } finally {
            stop();
        }
    }

    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel socketChannel = serverSocketChannel.accept();

        socketChannel.configureBlocking(false);
        socketChannel.register(selector, SelectionKey.OP_READ);

        log.info("New client connected: {}", socketChannel.getRemoteAddress());
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        readBuffer.clear();

        int numBytes = socketChannel.read(readBuffer);

        if(numBytes == -1) {
            key.cancel();
            socketChannel.close();
            log.error("Client disconnected: {}", socketChannel.getRemoteAddress());

            return;
        }

        readBuffer.flip();

        handleRequest(socketChannel, readBuffer);
        key.interestOps(SelectionKey.OP_WRITE);
    }

    private void handleWrite(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        writeBuffer.flip();

        socketChannel.write(writeBuffer);

        writeBuffer.clear();

        key.interestOps(SelectionKey.OP_READ);
    }

    private void handleRequest(SocketChannel socketChannel, ByteBuffer readBuffer) {
        int number = readBuffer.getInt();

        log.info("Received number: {}", number);

        int result = number * 2;

        writeBuffer.clear();

        writeBuffer.putInt(result);

        writeBuffer.flip();
    }

    private void stop() {
        try {
            if(selector != null) {
                selector.close();
            }

            if(serverSocketChannel != null) {
                serverSocketChannel.close();
            }

            log.info("Server stoped!");
        } catch (IOException e) {
            log.error("Error stoping server: {}", e.getMessage());
        }
    }



//    private String[] getIpAddresses() {
//        List<Peer> peers = storage.getPeers(true);
//        Set<String> ips = new HashSet<>();
//
//        for(Peer peer : peers) {
//            ips.add(peer.getAddress());
//        }
//
//        return ips.toArray(ips.toArray(new String[0]));
//    }
}
