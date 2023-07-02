package uk.co.roteala.handlers;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import uk.co.roteala.common.*;
import uk.co.roteala.common.events.Message;
import uk.co.roteala.storage.StorageServices;

import java.time.Duration;
import java.util.function.BiFunction;

@Slf4j
@RequiredArgsConstructor
public class TransmissionHandler implements BiFunction<NettyInbound, NettyOutbound, Flux<Void>> {
    private final StorageServices storageServices;

    private NettyOutbound outbound;

    private NettyInbound inbound;

    @Override
    public Flux<Void> apply(NettyInbound inbound, NettyOutbound outbound) {
        this.outbound = outbound;
        this.inbound = inbound;

        //Sent genesis accounts

        return Flux.never();
    }

    public Mono<Void> sendData(Message message) {
        Mono<ByteBuf> dataByteBuf = Mono
                .just(Unpooled.copiedBuffer(SerializationUtils.serialize(message)));
        log.info("Sending:{}", message);
        return this.outbound.sendObject(dataByteBuf).then();
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
