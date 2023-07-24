package uk.co.roteala.processor;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;
import reactor.netty.Connection;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import uk.co.roteala.common.ChainState;
import uk.co.roteala.common.events.ChainStateMessage;
import uk.co.roteala.common.events.Message;
import uk.co.roteala.common.events.MessageActions;
import uk.co.roteala.common.events.MessageTypes;
import uk.co.roteala.handlers.TransmissionHandler;
import uk.co.roteala.storage.StorageServices;

import java.time.Duration;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Configuration
public class MessageProcessor implements Processor {
    private final StorageServices storage;

    @Autowired
    private List<Connection> connectionStorage;

    //Pass the processor in handler
    @Override
    public void process(NettyInbound inbound, NettyOutbound outbound) {
        inbound.receive().retain()
                .map(this::mapToMessage)
                .doOnNext(message -> {
                    inbound.withConnection(message::setConnection);
                    log.info("Received:{}", message);
                    this.processMessage(message, outbound);
                }).then().subscribe();
    }

    /**
     * Process the incoming message check type and action then either respond the all clients(broadcast) or single client(outbound)
     * */
    private void processMessage(Message message, NettyOutbound outbound) {
        MessageTypes type = message.getMessageType();
        MessageActions actions = message.getMessageAction();

        switch (type) {
            case ACCOUNT:
                processMessageAccount(message);
                break;
            case STATECHAIN:
                processStateChainMessage(message);
                break;
            case BLOCK:
                processBlockMessage(message);
                break;
            case TRANSACTION:
                processTransactionMessage(message);
                break;
            case MEMPOOL:
                processMempoolMessage(message);
                break;
            case PEERS:
                processPeersMessage(message);
                break;
            default:
                // Code to handle cases when type does not match any of the above
        }
    }

    private Message mapToMessage(ByteBuf byteBuf) {
        byte[] bytes = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(bytes);

        Message message = SerializationUtils.deserialize(bytes);
        ReferenceCountUtil.release(byteBuf);

        return message;
    }

    /**
     * Process account messages
     * Possible actions: VERIFY, ADD/MODIFY, SYNC
     *
     * For SYNC return all accounts
     * */
    private void processMessageAccount(Message message){}

    /**
     * Process state chain message
     * Possible actions: Modify/Add, Sync
     * */
    private void processStateChainMessage(Message message){}

    /**
     * Process block message
     * Possible actions: Add/Append, Modify, Sync
     *
     * For sync also send all transactions within the block
     * */
    private void processBlockMessage(Message message){}

    /**
     * Process transaction message
     * Possible actions: Add/Append, Modify(confirmations status),Verify
     * */
    private void processTransactionMessage(Message message){}

    /**
     * Process mempool transactions
     * Possible actions: Verify, Add, Modify, Delete
     * */
    private void processMempoolMessage(Message message) {}

    /**
     * Process peers message
     * Possible actions: Delete, Modify, Add
     * */
    private void processPeersMessage(Message message){}
}
