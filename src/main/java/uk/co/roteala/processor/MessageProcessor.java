package uk.co.roteala.processor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.netty.Connection;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import uk.co.roteala.common.Block;
import uk.co.roteala.common.ChainState;
import uk.co.roteala.common.PseudoTransaction;
import uk.co.roteala.common.Transaction;
import uk.co.roteala.common.events.*;
import uk.co.roteala.exceptions.MiningException;
import uk.co.roteala.exceptions.errorcodes.MiningErrorCode;
import uk.co.roteala.storage.StorageServices;
import uk.co.roteala.utils.BlockchainUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Component
public class MessageProcessor implements Processor {
    @Autowired
    private StorageServices storage;

    @Autowired
    private List<Connection> connectionStorage;

    //Pass the processor in handler
    public void forwardMessage(NettyInbound inbound, NettyOutbound outbound) {
        inbound.receive().retain()
                .map(this::mapToMessage)
                .doOnNext(message -> {
                    inbound.withConnection(message::setConnection);

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
        MessageTypes messageTypes = message.getMessageType();

        log.info("Received:{}", message);

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
            default:
                // Code to handle cases when type does not match any of the above
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
    private void processStateChainMessage(Message message){}

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


                ChainState state = storage.getStateTrie();

                Block previousBlock = storage.getBlockByIndex(String.valueOf(state.getLastBlockIndex()));
                //Block nextBlock = storage.getBlockByIndex(String.valueOf(state.getLastBlockIndex() + 2));



                try {
                    if(minedBlock == null) {
                        log.info("Block message is empty!");
                        throw new MiningException(MiningErrorCode.MINED_BLOCK_EMPTY);
                    }

                    //Match the hash with the previous and or the previous ones in case of queue
                    if((!Objects.equals(previousBlock.getHash(), minedBlock.getPreviousHash()))
                            || !(matchWithPrevious(minedBlock.getPreviousHash()))){
                        log.info("Could not match with a previous hash!");
                        throw new MiningException(MiningErrorCode.PREVIOUS_HASH);
                    }

                    if(!matchPseudoWithTransaction(minedBlock)) {
                        log.error("Could not match proposed hash with pseudoHash!");
                        throw new MiningException(MiningErrorCode.PSEUDO_MATCH);
                    }

                    //If all the verification have passed add the block to the mempool
                    storage.addBlockMempool(minedBlock.getHash(), minedBlock);
                } catch (Exception e) {
                    throw new MiningException(MiningErrorCode.OPERATION_FAILED);
                }
                break;
                /**
                 * When a psuedo block is confirmed then move funds, if block already processed and exists in the store
                 * then only update confirmations
                 * */
            case VERIFIED_MINED_BLOCK:
                if(minedBlock != null) {
                    final String blockHash = minedBlock.getHash();

                    Block inStorageBlock = storage.getPseudoBlockByHash(blockHash);

                    final int confirmations = inStorageBlock.getConfirmations() + 1;

                    if(inStorageBlock != null) {
                        if(confirmations > 0) {
                            //Add the block to blockchain
                            //Create transactions and map the psuedo
                            Block block = inStorageBlock;
                            List<String> originalHashes = new ArrayList<>();


                            inStorageBlock.getTransactions()
                                    .forEach(mapHashed -> {
                                        int i = 0;

                                        final String hash = mapHashed.split("_")[0];
                                        final String transactionHash = mapHashed.split("_")[1];

                                        final PseudoTransaction pseudoTransaction = storage.getMempoolTransaction(hash);

                                        final Transaction transaction = BlockchainUtils
                                                .mapPsuedoTransactionToTransaction(pseudoTransaction, inStorageBlock, i);

                                        if(Objects.equals(transactionHash, transaction.getHash())){
                                            originalHashes.add(transaction.getHash());
                                        }

                                        //Add transaction to blockhcain
                                        storage.addTransaction(transaction.getHash(), transaction);
                                        storage.deleteMempoolTransaction(pseudoTransaction.getPseudoHash());

                                        i++;
                                    });

                            block.setTransactions(originalHashes);
                            block.setHash(block.computeHash());

                            //Delete the block from mempool
                            storage.deleteMempoolBlocksAtIndex(block.getIndex());

                            //Broadcast the block to everyone
                        }
                    } else {
                        Block block = storage.getBlockByHash(blockHash);

                        if(block != null) {
                            block.setConfirmations(block.getConfirmations() + 1);

                            storage.addBlock(String.valueOf(block.getIndex()), block, true);

                            //Send to all for confirmations
                        }
                    }
                }
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
    private void processPeersMessage(Message message){}

    private List<Block> isIndexMined(Integer index) {
        return null;
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

            return possiblePreviousHash.add(hash);
        } else {
            return true;
        }
    }

    /**
     * Loop every pseudoTransaction inside the proposed block, check if exists in mempool, if so create a transaciton from it
     * then match the hash with the one proposed
     * */
    private boolean matchPseudoWithTransaction(Block block) {
        List<String> computedHashes = new ArrayList<>();

        block.getTransactions().forEach(hash -> {
            final String[] splitHash = hash.split("_");
            String pseudoHash = splitHash[0];

            final PseudoTransaction pseudoTransaction = storage.getMempoolTransaction(pseudoHash);

            if (pseudoTransaction != null) {
                computedHashes.add(BlockchainUtils
                        .mapPsuedoTransactionToTransaction(pseudoTransaction, block, computedHashes.size()).computeHash());
            }
        });

        List<String> transactionHashes = block.getTransactions().stream()
                .map(hash -> hash.split("_")[1])
                .collect(Collectors.toList());

        return computedHashes.equals(transactionHashes);
    }

}
