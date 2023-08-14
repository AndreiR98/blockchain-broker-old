package uk.co.roteala.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.tomcat.util.buf.ByteBufferUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.netty.*;
import reactor.netty.http.websocket.WebsocketOutbound;
import uk.co.roteala.api.ApiStateChain;
import uk.co.roteala.common.*;
import uk.co.roteala.common.events.*;
import uk.co.roteala.common.monetary.AmountDTO;
import uk.co.roteala.common.monetary.Fund;
import uk.co.roteala.common.monetary.MoveFund;
import uk.co.roteala.exceptions.MiningException;
import uk.co.roteala.exceptions.errorcodes.MiningErrorCode;
import uk.co.roteala.net.Peer;
import uk.co.roteala.storage.StorageServices;
import uk.co.roteala.utils.BlockchainUtils;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class MessageProcessor implements Processor {
    @Autowired
    private StorageServices storage;

    @Autowired
    private List<Connection> connectionStorage;

    @Autowired
    private List<WebsocketOutbound> websocketOutbounds;

    @Autowired
    private MoveFund moveFund;

    private Connection connection;

    //Pass the processor in handler
    public void forwardMessage(NettyInbound inbound, NettyOutbound outbound) {
        inbound.receive().retain()
                .map(this::mapper)
                .parallel()
                //.delayElements(Duration.ofMillis(100))
                .doOnNext(message -> {
                    inbound.withConnection(connection ->{
                        message.setConnection(connection);
                        this.connection = connection;
                    });
                    this.process(message);
                })
                .then()
                .subscribe();
    }

    /**
     * Process the incoming message check type and action then either respond the all clients(broadcast) or single client(outbound)
     * */
    @Override
    public void process(Message message) {
        log.info("Received:{}", message);
        MessageTypes messageTypes = message.getMessageType();

        switch (messageTypes) {
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
            case BLOCKHEADER:
                processBlockHeader(message);
                break;
            case NODESTATE:
                processNodeState(message);
                break;
            default:
                // Code to handle cases when type does not match any of the above
        }
    }


    private void processNodeState(Message message) {
        MessageActions action = message.getMessageAction();

        NodeState nodeState = (NodeState) message.getMessage();

        ChainState state = storage.getStateTrie();

        Connection toNode = message.getConnection();

        switch (action) {
            case REQUEST_SYNC:
                if(nodeState == null) {
                    //
                }

                if(((connectionStorage.size() - 1) <= 0) && (nodeState.getRemainingBlocks() != 0)){
                    int startIndex = (state.getLastBlockIndex() - nodeState.getRemainingBlocks()) + 1;//Exclude the genesis block

                    Flux.range(startIndex, state.getLastBlockIndex())
                            .flatMap(index -> {
                                Block block = storage.getBlockByIndex(String.valueOf(index));

                                Flux<MessageWrapper> transactionFlux = Flux.fromIterable(block.getTransactions())
                                        .flatMap(hash -> {
                                            Transaction transaction = storage.getTransactionByKey(hash);

                                            MessageWrapper transactionWrapper = new MessageWrapper();
                                            transactionWrapper.setAction(MessageActions.APPEND);
                                            transactionWrapper.setType(MessageTypes.TRANSACTION);
                                            transactionWrapper.setVerified(true);
                                            transactionWrapper.setContent(transaction);

                                            return Mono.just(transactionWrapper);
                                        });

                                MessageWrapper blockWrapper = new MessageWrapper();
                                blockWrapper.setContent(block.getHeader());
                                blockWrapper.setType(MessageTypes.BLOCKHEADER);
                                blockWrapper.setAction(MessageActions.APPEND);
                                blockWrapper.setVerified(true);

                                return transactionFlux.mergeWith(Mono.just(blockWrapper));
                            })
                            .delayElements(Duration.ofMillis(150))
                            .doOnNext(wrapper -> {
                                log.info("Sending: {}", wrapper.getType());

                                toNode.outbound()
                                        .sendObject(Mono.just(wrapper.serialize()))
                                        .then()
                                        .subscribe();
                            })
                            .then()
                            .subscribe();
                }
                break;
        }
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
    private void processStateChainMessage(Message message) {
        MessageActions action = message.getMessageAction();

        ChainState state = storage.getStateTrie();

        MessageWrapper.MessageWrapperBuilder messageWrapperBuilder = MessageWrapper.builder();

        switch (action) {
            case REQUEST:
                messageWrapperBuilder
                            .action(MessageActions.APPEND)
                            .content(state)
                            .type(MessageTypes.STATECHAIN)
                            .verified(true)
                            .build();


                this.connection.outbound().sendObject(Mono.just(messageWrapperBuilder.build().serialize()))
                            .then().subscribe();
                break;
            case REQUEST_SYNC:
                messageWrapperBuilder
                        .action(MessageActions.MODIFY)
                        .content(state)
                        .type(MessageTypes.STATECHAIN)
                        .verified(true)
                        .build();

                this.connection.outbound().sendObject(Mono.just(messageWrapperBuilder.build().serialize()))
                        .then().subscribe();

                break;
        }
    }

    /**
     * Process block message
     * Possible actions: Add/Append, Modify, Sync
     *
     * For sync also send all transactions within the block
     * */
    private void processBlockMessage(Message message){
        MessageActions action = message.getMessageAction();

        Block minedBlock = (Block) message.getMessage();

        switch (action) {
            /**
             * Verify block integrity
             * */
            case MINED_BLOCK:
                break;
                /**
                 * When a psuedo block is confirmed then move funds, if block already processed and exists in the store
                 * then only update confirmations
                 * */
            case VERIFIED_MINED_BLOCK:
                break;
        }
    }

    /**
     * Process mined block headers
     * */
    private void processBlockHeader(Message message) {
        MessageActions messageAction = message.getMessageAction();

        BlockHeader blockHeader = (BlockHeader) message.getMessage();

        final BlockHeaderProcessor blockHeaderProcessor = new BlockHeaderProcessor(
                blockHeader, connectionStorage, connection, websocketOutbounds, storage, moveFund);

        switch (messageAction) {
            //Check block integrity and add it to mempool
            case MINED_BLOCK:
                blockHeaderProcessor.processNewlyMinedBlock();
                break;
                /**
                 * Count how many confirmations
                 * */
            case VERIFIED_MINED_BLOCK:
                blockHeaderProcessor.processVerifiedBlockRequest();
                break;
        }
    }

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
    private void processPeersMessage(Message message){
        MessageActions action = message.getMessageAction();

        switch (action) {
            case REQUEST:
                if((connectionStorage.size() - 1) <= 0) {
                    MessageWrapper messageWrapper = new MessageWrapper();
                    messageWrapper.setVerified(true);
                    messageWrapper.setAction(MessageActions.NO_CONNECTIONS);
                    messageWrapper.setType(MessageTypes.PEERS);

                    this.connection.outbound()
                            .sendObject(Mono.just(messageWrapper.serialize()))
                            .then()
                            .subscribe();
                }
                break;
        }
    }

    /**
     * This method will return all pseudoBlocks and current block hash, check if the current block has a previous hash
     * */

}
