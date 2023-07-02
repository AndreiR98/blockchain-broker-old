package uk.co.roteala.server;

import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksDBException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.SerializationUtils;
import reactor.netty.Connection;
import reactor.netty.DisposableServer;
import reactor.netty.tcp.TcpServer;
import uk.co.roteala.common.*;
import uk.co.roteala.common.events.AccountMessage;
import uk.co.roteala.common.monetary.Coin;
import uk.co.roteala.configs.GlacierBrokerConfigs;
import uk.co.roteala.handlers.TransmissionHandler;
import uk.co.roteala.net.Peer;
import uk.co.roteala.storage.StorageServices;
import uk.co.roteala.utils.GlacierUtils;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Consumer;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class Server {

    @Autowired
    private TransmissionHandler transmissionHandler;

    private final StorageServices storage;

}
