package uk.co.roteala.handlers;

import io.netty.buffer.Unpooled;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.SerializationUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import uk.co.roteala.common.AddressBaseModel;
import uk.co.roteala.net.Peer;
import uk.co.roteala.storage.StorageServices;
import uk.co.roteala.utils.GlacierUtils;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

@Slf4j
@RequiredArgsConstructor
public class TransmissionHandler implements BiFunction<NettyInbound, NettyOutbound, Publisher<Void>> {
    private final StorageServices storageServices;

    @Override
    public Publisher<Void> apply(NettyInbound inbound, NettyOutbound outbound) {
        List<Peer> peers = storageServices.getPeers();

        inbound.withConnection(connection -> {
            AddressBaseModel addressBaseModel = GlacierUtils.formatAddress(connection.address().toString());
            peers.removeIf(peer -> Objects.equals(peer.getAddress(), addressBaseModel.getAddress()));
            //connection.onDispose().doFinally(connectionInternally -> onDisconnect(connection));

            connection.onDispose()
                    .doFinally(signalType -> onDisconnect(connection));
        });

        byte[] bytes = SerializationUtils.serialize(peers);

        outbound.sendObject(Mono.just(Unpooled.wrappedBuffer(bytes))).then().subscribe();

        return Mono.never();
    }


    public Publisher<Void> sendPeers(NettyOutbound outbound, byte[] bytes) {
        log.info("Send peers!");
        return outbound.sendObject(Mono.just(Unpooled.wrappedBuffer(bytes)));
    }

    private void onDisconnect(Connection connection){
        //AddressBaseModel addressBaseModel = GlacierUtils.formatAddress(connection.address().toString());

        try {
            String addressWithPort = connection.address().toString();

            if(storageServices.get(addressWithPort.getBytes()) != null) {
                Peer peer = (Peer) storageServices.get(addressWithPort.getBytes());
                peer.setLastTimeSeen(System.currentTimeMillis());
                peer.setActive(false);

                storageServices.add(addressWithPort.getBytes(), peer);
                log.info("Peer disconnected");
            }

        }catch (Exception e) {
            //
        }

    }

    private void getInitialPeers() {}

    private void getExactPeers() {}
}
