package uk.co.roteala.processor;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.http.websocket.WebsocketOutbound;
import uk.co.roteala.common.Block;
import uk.co.roteala.common.NodeState;
import uk.co.roteala.common.Transaction;
import uk.co.roteala.common.events.MessageActions;
import uk.co.roteala.common.events.MessageTypes;
import uk.co.roteala.common.events.MessageWrapper;
import uk.co.roteala.common.events.PeersContainer;
import uk.co.roteala.common.monetary.MoveFund;
import uk.co.roteala.net.Peer;
import uk.co.roteala.storage.StorageServices;
import uk.co.roteala.utils.BlockchainUtils;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@AllArgsConstructor
@NoArgsConstructor
public class PeerProcessor {
    @Autowired
    private StorageServices storage;

    @Autowired
    private List<Connection> connectionStorage;

    private Connection connection;

    private NodeState nodeState;//peer node state

    /**
     * The node can't connect even after we send the list of ips, update to inactive peers and sync the node
     * */
    public void processNoConnection() {
        try {
            Flux.range(nodeState.getLastBlockIndex()+1, this.storage.getStateTrie().getLastBlockIndex())
                    .doOnNext(blockIndex -> {
                        Block block = this.storage.getBlockByIndex(String.valueOf(blockIndex));

                        Flux<MessageWrapper> transactionWrapeprs = Flux.fromIterable(block.getTransactions())
                                .map(transactionHash -> {
                                    Transaction transaction = this.storage.getTransactionByKey(transactionHash);

                                    MessageWrapper transactionWrapper = new MessageWrapper();
                                    transactionWrapper.setType(MessageTypes.TRANSACTION);
                                    transactionWrapper.setContent(transaction);
                                    transactionWrapper.setAction(MessageActions.APPEND);
                                    transactionWrapper.setVerified(true);

                                    return transactionWrapper;
                                })
                                .delayElements(Duration.ofMillis(150));

                        block.setTransactions(new ArrayList<>());

                        MessageWrapper blockWrapper = new MessageWrapper();
                        blockWrapper.setVerified(true);
                        blockWrapper.setContent(block);
                        blockWrapper.setType(MessageTypes.BLOCK);
                        blockWrapper.setAction(MessageActions.APPEND);

                        Mono.just(blockWrapper)
                                .mergeWith(transactionWrapeprs)
                                .doOnNext(wrapper -> {
                                    this.connection.outbound()
                                            .sendObject(wrapper.serialize())
                                            .then().subscribe();
                                });
                    }).then().subscribe();
        }catch (Exception e) {
            log.error("Error to process no connection from node");
        }
    }


    /**
     * Send the node a some ips to connect to
     * */
    public void processEmptyPeers() {
        try {
            List<Peer> list = this.storage.getPeers();
            List<String> ips = new ArrayList<>();

            for(Peer peer : list) {
                if(!Objects.equals(peer.getAddress(), parseAddress(this.connection.address()))) {
                    ips.add(peer.getAddress());
                }
            }

            PeersContainer container = new PeersContainer(ips);

            MessageWrapper peersWrapper = new MessageWrapper();
            peersWrapper.setAction(MessageActions.TRY_CONNECTIONS);
            peersWrapper.setContent(container);
            peersWrapper.setVerified(true);
            peersWrapper.setType(MessageTypes.PEERS);

            this.connection.outbound()
                    .sendObject(peersWrapper.serialize())
                    .then().subscribe();

        } catch (Exception e) {
            log.error("Failing sending list of peers!");
        }
        //MessageActions.TRY_CONNECTIONS;
    }
    private String parseAddress(SocketAddress address) {
        InetSocketAddress inetSocketAddress = (InetSocketAddress) address;
        String hostWithoutPort = inetSocketAddress.getHostString();

        return hostWithoutPort;
    }
}
