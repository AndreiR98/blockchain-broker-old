package uk.co.roteala.processor;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.http.websocket.WebsocketOutbound;
import uk.co.roteala.common.NodeState;
import uk.co.roteala.common.events.MessageActions;
import uk.co.roteala.common.events.MessageTypes;
import uk.co.roteala.common.events.MessageWrapper;
import uk.co.roteala.common.events.PeersContainer;
import uk.co.roteala.common.monetary.MoveFund;
import uk.co.roteala.storage.StorageServices;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
     * Respond to node with a list of IPs if possible if not sync the node
     * */
    public void processNoConnection() {
        try {
            if(this.connectionStorage.size() - 1 <= 0){
                syncNode();
            } else {
                List<String> ipsList = new ArrayList<>();

                this.connectionStorage.forEach(conn -> {
                    if(conn.address() == this.connection.address()) {
                        ipsList.add(parseAddress(conn.address()));
                    }
                });

                Collections.shuffle(ipsList);

                ipsList.subList(0, 10);

                PeersContainer container = new PeersContainer();
                container.setPeersList(ipsList);

                MessageWrapper messageWrapper = new MessageWrapper();
                messageWrapper.setVerified(true);
                messageWrapper.setType(MessageTypes.PEERS);
                messageWrapper.setAction(MessageActions.REQUEST_SYNC);
                messageWrapper.setContent(container);

                this.connection.outbound()
                        .sendObject(Mono.just(messageWrapper.serialize()))
                        .then().subscribe();
            }
        } catch (Exception e) {
            log.error("Error while processing");
        }
    }

    /**
     * */
    private void syncNode() {
        try {

        } catch (Exception e) {
            log.error("Error while sync the node!", e.toString());
        }
    }

    private String parseAddress(SocketAddress address) {
        InetSocketAddress inetSocketAddress = (InetSocketAddress) address;
        String hostWithoutPort = inetSocketAddress.getHostString();

        return hostWithoutPort;
    }
}
