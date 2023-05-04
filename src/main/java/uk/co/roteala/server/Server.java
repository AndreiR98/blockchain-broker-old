package uk.co.roteala.server;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.Connection;
import reactor.netty.DisposableServer;
import reactor.netty.tcp.TcpServer;
import reactor.netty.transport.Transport;
import uk.co.roteala.broker.Broker;
import uk.co.roteala.common.BaseEmptyModel;
import uk.co.roteala.common.TransactionBaseModel;
import uk.co.roteala.common.events.Event;
import uk.co.roteala.net.Peer;
import uk.co.roteala.processor.EventTransformer;
import uk.co.roteala.storage.StorageCreatorComponent;
import uk.co.roteala.utils.GlacierUtils;

import java.util.function.Consumer;

@Slf4j
@Component
public class Server{

}
