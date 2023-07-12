package uk.co.roteala.handlers;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.Connection;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import uk.co.roteala.common.*;
import uk.co.roteala.common.events.Message;
import uk.co.roteala.processor.Processor;
import uk.co.roteala.storage.StorageServices;

import java.time.Duration;
import java.util.Arrays;
import java.util.function.BiFunction;

/**
 * Handles the connection between the clients and server
 * */
@Slf4j
@RequiredArgsConstructor
public class TransmissionHandler implements BiFunction<NettyInbound, NettyOutbound, Flux<Void>> {
    private NettyOutbound outbound;

    private NettyInbound inbound;

    @Override
    public Flux<Void> apply(NettyInbound inbound, NettyOutbound outbound) {
        this.outbound = outbound;
        this.inbound = inbound;

        return Flux.never();
    }

    public NettyInbound getInbound() {
        return inbound;
    }

    public NettyOutbound getOutbound() {
        return outbound;
    }

    public void sendDataToClient(Flux<Message> messageFlux){}

//    public Flux<Message> receiveDataFromClient() {
//        this.inbound.receive().retain().subscribe(new SubscriberProcessor());
//    }

//    public class SubscriberProcessor implements CoreSubscriber<Message> {
//        @Override
//        public void onSubscribe(Subscription subscription) {
//        }
//
//        @Override
//        public void onNext(Message message) {
//        }
//
//        @Override
//        public void onError(Throwable throwable) {
//        }
//
//        @Override
//        public void onComplete() {
//        }
//    }

    private Message mapToMessage(ByteBuf byteBuf) {
        byte[] bytes = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(bytes);

        Message message = SerializationUtils.deserialize(bytes);
        ReferenceCountUtil.release(byteBuf);

        return message;
    }

    private Flux<Void> receiveData(ByteBuf byteBuf) {
        byte[] bytes = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(bytes);

        Message message =  (Message) SerializationUtils.deserialize(bytes);
        ReferenceCountUtil.release(byteBuf);

        //Set connection
        this.inbound.
                withConnection(connection -> message.setAddress(connection.address().toString()));

        log.info("Returned message:{}", message);
        return Flux.empty();
    }

    public Disposable sendPseudoTransaction(Message message) {
        return Mono
                .just(Unpooled.copiedBuffer(SerializationUtils.serialize(message)))
                .delayElement(Duration.ofMillis(500))
                .flatMap(b -> {
                    log.info("Sending:{}", message);
                    return this.outbound.sendObject(b).then();
                }).then().subscribe();
    }

//    public Mono<Void> sendMultipleData(Flux<Message> messageFlux){
//        return messageFlux
//                .flatMap(message -> {
//                    log.info("Sending:{}", message);
//                    return this.outbound.sendObject(Unpooled.copiedBuffer(SerializationUtils.serialize(message)));
//                }).then();
//    }

    public Mono<Void> sendMultipleData(Flux<Message> messageFlux){
        return Flux.interval(Duration.ofSeconds(1)).flatMap(s->{
            return this.outbound.sendObject(Unpooled.copiedBuffer(SerializationUtils.serialize(s)));
        }).then();
    }

    ////        Flux.fromIterable(chainState.getAccounts())
////                .flatMap(address -> Mono.fromCallable(() -> storage.getAccountByAddress(address))
////                        .onErrorMap(RocksDBException.class, RuntimeException::new)
////                )
////                .delayElements(Duration.ofMillis(500))
////                .flatMap(account -> transmissionHandler.sendData(account))
////                .then()
////                .concatWith(chainStateMessageMono))
////                .flatMap();

    public Disposable sendMultipleInstances(Flux<Message> messageFlux){
        return messageFlux
                .flatMap(message -> {
                    log.info("Sending:{}", message);
                    return this.outbound.sendObject(Flux.just(SerializationUtils.serialize(message)));
                }).subscribe();
    }

    /**
     * Check if we return Mono or Flux
     * For example when client send multiple transaction
     * Flux.just(List of transactions or List of Mono<Transaction>); as bulk
     * Or client send one by one as Mono and we process one by one
     * */
    public Flux<BaseModel> getClientResponse() {
        return this.inbound.receive().retain()
                .map(byteBuf -> {
                    byte[] bytes = new byte[byteBuf.readableBytes()];
                    return SerializationUtils.deserialize(bytes);
                });
    }
}
