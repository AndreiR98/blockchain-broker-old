package uk.co.roteala.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
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
            default:
                // Code to handle cases when type does not match any of the above
        }
        log.info("Precessing message:{}", message);
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
        state.setAccounts(new ArrayList<>());

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

        ChainState state = storage.getStateTrie();

        Block prevBlock = (state.getLastBlockIndex() - 1) <= 0 ? state.getGenesisBlock()
                : storage.getBlockByIndex(String.valueOf(state.getLastBlockIndex()));

        switch (messageAction) {
            //Check block integrity and add it to mempool
            case MINED_BLOCK:
                try {
                    ObjectMapper mapper = new ObjectMapper();

                    if(blockHeader == null) {
                        log.info("Block message is empty!");
                        throw new MiningException(MiningErrorCode.MINED_BLOCK_EMPTY);
                    }

                    //Match the hash with the previous and or the previous ones in case of queue
                    if((!Objects.equals(prevBlock.getHash(), blockHeader.getPreviousHash()))
                            || !(matchWithPrevious(blockHeader.getPreviousHash()))){
                        log.info("Could not match with a previous hash:{} with:{}!", prevBlock.getHash(), blockHeader.getPreviousHash());
                        throw new MiningException(MiningErrorCode.PREVIOUS_HASH);
                    }

                    //Return the list of pseudoHashes that matched the markleRoot
                    List<String> bothHashes = matchMerkleRoot(blockHeader);

                    if(bothHashes.isEmpty()) {
                        log.error("Could not match markle root!");
                        throw new MiningException(MiningErrorCode.PSEUDO_MATCH);
                    }

                    /**
                     * Split returned hashes
                     * */
                    List<String> transactionHashes = new ArrayList<>();
                    List<String> pseudoHashes = new ArrayList<>();

                    for(String hash : bothHashes) {
                        String[] splitHashes = hash.split("_");

                        transactionHashes.add(splitHashes[1]);
                        pseudoHashes.add(splitHashes[0]);
                    }

                    updateMempoolTransactions(pseudoHashes, TransactionStatus.PROCESSED);

                    //Check if the number of connections are > 1
                    /**
                     * Create the block from the header
                     * Create transactions from the header
                     * Remove them from mempool
                     * */
                    if(connectionStorage.size() < 1) {
                        updateMempoolTransactions(pseudoHashes, TransactionStatus.SUCCESS);

                        //Create the block from the header and append to the main chain
                        Block chainBlock = new Block();
                        chainBlock.setHeader(blockHeader);
                        chainBlock.setStatus(BlockStatus.MINED);
                        chainBlock.setTransactions(transactionHashes);
                        chainBlock.setConfirmations(1);
                        chainBlock.setForkHash("0000000000000000000000000000000000000000000000000000000000000000");
                        chainBlock.setNumberOfBits(SerializationUtils.serialize(chainBlock).length);

                        int index = 0;
                        for(String hash : pseudoHashes) {
                            PseudoTransaction pseudoTransaction = storage.getMempoolTransaction(hash);

                            Transaction transaction = BlockchainUtils
                                    .mapPsuedoTransactionToTransaction(pseudoTransaction, blockHeader, index);

                            index++;

                            AccountModel sourceAccount = storage.getAccountByAddress(transaction.getFrom());

                            Fund fund = Fund.builder()
                                    .sourceAccount(sourceAccount)
                                    .isProcessed(true)
                                    .amount(AmountDTO.builder()
                                            .rawAmount(transaction.getValue())
                                            .fees(transaction.getFees())
                                            .build())
                                    .targetAccountAddress(transaction.getTo())
                                    .build();

                            moveFund.execute(fund);

                            storage.addTransaction(transaction.getHash(), transaction);
                        }

                        storage.addBlock(String.valueOf(chainBlock.getHeader().getIndex()), chainBlock, true);


                        //TODO: Network fee calculation based on the block value amount and more, same for blocktarget
                        //Update state chain
                        state.setLastBlockIndex(blockHeader.getIndex());

                        storage.updateStateTrie(state);

                        ApiStateChain apiStateChain = new ApiStateChain();
                        apiStateChain.setNetworkFees(state.getNetworkFees());
                        apiStateChain.setLastBlockIndex(state.getLastBlockIndex());

                        String jsonString;


                        jsonString = mapper.writeValueAsString(apiStateChain);

                        for(WebsocketOutbound websocketOutbound : websocketOutbounds) {
                            websocketOutbound.sendString(Mono.just(jsonString))
                                    .then()
                                    .subscribe();
                        }

                        for(Connection conn : connectionStorage) {
                            MessageWrapper messageWrapper = new MessageWrapper();
                            messageWrapper.setVerified(true);
                            messageWrapper.setAction(MessageActions.APPEND_MINED_BLOCK);
                            messageWrapper.setType(MessageTypes.BLOCKHEADER);
                            messageWrapper.setContent(blockHeader);

                            conn.outbound().sendObject(Mono.just(messageWrapper.serialize()))
                                    .then()
                                    .subscribe();
                        }

                    } else {
                        //Create the block but add it to the mempool, this block will wait confirmations
                        Block mempoolBlock = new Block();
                        mempoolBlock.setHeader(blockHeader);
                        mempoolBlock.setStatus(BlockStatus.PENDING);
                        mempoolBlock.setConfirmations(1);
                        mempoolBlock.setForkHash("0000000000000000000000000000000000000000000000000000000000000000");
                        mempoolBlock.setNumberOfBits(SerializationUtils.serialize(mempoolBlock).length);

                        storage.addBlockMempool(blockHeader.getHash(), mempoolBlock);

                        //Broadcast the block to other nodes;
                    }
                } catch (Exception e) {
                    log.info("Error:{}", e);
                    throw new MiningException(MiningErrorCode.OPERATION_FAILED);
                }
                break;
                //Update block confirmations
            case VERIFY:
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
                List<Peer> peers = storage.getPeers();

                peers.stream()
                        .filter(peer -> !Objects.equals(peer.getAddress(),
                                BlockchainUtils.formatIPAddress(message.getConnection().address())))
                        .collect(Collectors.toList());

                if((peers.size() - 1) <= 0) {
                    ChainState state = storage.getStateTrie();
                    MessageWrapper stateMessage = new MessageWrapper();

                    //Prepare all blocks data, transactions and pseudo transaction(VALIDATED, PROCESSED, PENDING)
                    /**
                     * Query each block retrieve transactions first, send all transaction first then the block
                     * Send state first, then psuedo transactions then all chain data
                     * */
                    Flux.fromIterable(storage.getPseudoTransactions())
                            .map(pseudoTransaction -> {
                                return MessageWrapper.builder()
                                        .type(MessageTypes.MEMPOOL)
                                        .action(MessageActions.APPEND)
                                        .verified(true)
                                        .content(pseudoTransaction)
                                        .build()
                                        .serialize();
                            })
                            .delayElements(Duration.ofMillis(120))
                            .flatMap(byteBuf -> message.getConnection().outbound().sendObject(Mono.just(byteBuf)))
                            .then()
                            .doFinally(signalType -> {
                                if (signalType == SignalType.ON_COMPLETE) {
                                    final Flux<Integer> indexFlux = Flux.range(0, state.getLastBlockIndex());

                                    indexFlux.doOnNext(index -> {
                                        final Block block = storage.getBlockByIndex(String.valueOf(index));

                                        MessageWrapper wrapper = MessageWrapper.builder()
                                                        .type(MessageTypes.BLOCK)
                                                        .action(MessageActions.APPEND)
                                                        .verified(true)
                                                        .content(block)
                                                        .build();

                                        Flux.fromIterable(block.getTransactions())
                                                .map(hash -> {
                                                    final Transaction transaction = storage.getTransactionByKey(hash);

                                                    MessageWrapper wrapperTransaction = MessageWrapper.builder()
                                                            .type(MessageTypes.TRANSACTION)
                                                            .action(MessageActions.APPEND)
                                                            .verified(true)
                                                            .content(transaction)
                                                            .build();

                                                    return wrapperTransaction.serialize();
                                                })
                                                .mergeWith(Mono.just(wrapper.serialize()))
                                                .delayElements(Duration.ofMillis(120))
                                                .flatMap(byteBuf -> message.getConnection().outbound().sendObject(Mono.just(byteBuf)));
                                    }).then()
                                            .subscribe();
                                }
                            })
                            .subscribe();
                }

                break;
        }
    }

    /**
     * This method will return all pseudoBlocks and current block hash, check if the current block has a previous hash
     * */
    private boolean matchWithPrevious(String hash) {
        List<Block> queuedBlocks = storage.getPseudoBlocks();
        List<String> possiblePreviousHash = new ArrayList<>();

        //return possiblePreviousHash.contains(hash);
        if(!queuedBlocks.isEmpty()) {
            queuedBlocks.forEach(block -> {
                possiblePreviousHash.add(block.getHash());
            });

            return possiblePreviousHash.contains(hash);
        } else {
            return true;
        }
    }

    /**
     * Computes the markle root based on the number of transactions and time window
     * */
    private List<String> matchMerkleRoot(BlockHeader blockHeader) {
        List<PseudoTransaction> availablePseudoTransactions = storage
                .getPseudoTransactionGrouped(blockHeader.getTimeStamp());

        List<String> transactionHashes = new ArrayList<>();
        List<String> bothHashes = new ArrayList<>();

        for (int index = 0; index < availablePseudoTransactions.size(); index++) {
            PseudoTransaction pseudoTransaction = availablePseudoTransactions.get(index);

            String mappedHashes = BlockchainUtils.mapHashed(pseudoTransaction, blockHeader.getIndex(),
                    blockHeader.getTimeStamp(), index);

            String[] splitHashes = mappedHashes.split("_");
            transactionHashes.add(splitHashes[1]);
            bothHashes.add(mappedHashes);
        }

        String markleRoot = BlockchainUtils.markleRootGenerator(transactionHashes);
        int totalSize = transactionHashes.size();

        log.info("Tx:{}", transactionHashes);

        if (totalSize >= blockHeader.getNumberOfTransactions()) {
            while (!markleRoot.equals(blockHeader.getMarkleRoot()) && !transactionHashes.isEmpty()) {
                log.info("MR:{}", markleRoot);
                transactionHashes.remove(totalSize - 1); // Remove the last transaction hash
                bothHashes.remove(totalSize - 1);

                totalSize--;
                markleRoot = BlockchainUtils.markleRootGenerator(transactionHashes);
            }
        } else {
            //This could not be possible
            throw new MiningException(MiningErrorCode.OPERATION_FAILED);
        }

        if(markleRoot.equals(blockHeader.getMarkleRoot())) {
            return bothHashes;
        }

        return new ArrayList<>();
    }

    private void updateMempoolTransactions(List<String> pseudoTransactions, TransactionStatus status) {
        for(String pseudoHash : pseudoTransactions) {
            PseudoTransaction pseudoTransaction = storage.getMempoolTransaction(pseudoHash);

            if(pseudoTransaction != null) {
                pseudoTransaction.setStatus(status);

                storage.addMempool(pseudoTransaction.getPseudoHash(), pseudoTransaction);
            }
        }
    }
}
