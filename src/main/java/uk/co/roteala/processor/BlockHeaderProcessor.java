package uk.co.roteala.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.http.websocket.WebsocketOutbound;
import uk.co.roteala.api.ApiStateChain;
import uk.co.roteala.common.*;
import uk.co.roteala.common.events.MessageActions;
import uk.co.roteala.common.events.MessageTypes;
import uk.co.roteala.common.events.MessageWrapper;
import uk.co.roteala.common.monetary.AmountDTO;
import uk.co.roteala.common.monetary.Fund;
import uk.co.roteala.common.monetary.MoveFund;
import uk.co.roteala.configs.WebSocketConfig;
import uk.co.roteala.exceptions.MiningException;
import uk.co.roteala.exceptions.errorcodes.MiningErrorCode;
import uk.co.roteala.services.WebSocketServices;
import uk.co.roteala.storage.StorageServices;
import uk.co.roteala.utils.BlockchainUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Process block headers
 * */
@Slf4j
@Component
@AllArgsConstructor
@NoArgsConstructor
public class BlockHeaderProcessor {

    @Autowired
    private WebSocketServices webSocketServices;

    private BlockHeader blockHeader;

    @Autowired
    private List<Connection> connectionStorage;
    private Connection connection;

    @Autowired
    private List<WebsocketOutbound> websocketOutbounds;

    @Autowired
    private StorageServices storage;

    @Autowired
    private MoveFund moveFund;

    /**
     * Process new mined block
     * */
    public void processMinedBlock() {
        ChainState state = storage.getStateTrie();

        Block prevBlock = state.getLastBlockIndex() <= 0 ? state.getGenesisBlock()
                : storage.getBlockByIndex(String.valueOf(state.getLastBlockIndex()));

        try {
            if(this.blockHeader == null) {
                log.info("Block header could not be null!");
                throw new MiningException(MiningErrorCode.MINED_BLOCK_EMPTY);
            }

            //Match the proposed header previous hash with the existing previous hash
            if(!Objects.equals(prevBlock.getHash(), this.blockHeader.getPreviousHash())){
                log.info("Could not match with a previous hash:{} with:{}!", prevBlock.getHash(),
                        this.blockHeader.getPreviousHash());
                throw new MiningException(MiningErrorCode.PREVIOUS_HASH);
            }

            if(Objects.equals(blockHeader.getMarkleRoot(),
                    "0000000000000000000000000000000000000000000000000000000000000000")){
                processEmptyBlock();
            } else {
                processTransactionalBlock();
            }
            log.info("New block:{} validated!", blockHeader.getHash());
        } catch (Exception e) {
            log.error("Error while processing newly mined block:{}", e.getMessage());
        }
    }

    /**
     * Process confirmations for each block
     * If confirmations >= threshold then append the block and update the state
     * */
    public void processVerifiedMinedBlock() {
        try {
            Block pseudoBlock = this.storage.getPseudoBlockByHash(this.blockHeader.getHash());
            Block block = this.storage.getBlockByIndex(String.valueOf(this.blockHeader.getIndex()));

            log.info("Blocks:{}", pseudoBlock);

            if(pseudoBlock == null) {
                throw new MiningException(MiningErrorCode.MINED_BLOCK_EMPTY);
            }

            final int numberOfConfirmations = pseudoBlock.getConfirmations() + 1;
            int threshold = 2;//TODO: Use the current number of nodes connected

            if(numberOfConfirmations >= threshold) {
                if(block == null) {
                    pseudoBlock.setConfirmations(numberOfConfirmations);
                    createBlockAndExecuteFund(pseudoBlock);
                } else {
                    block.setConfirmations(numberOfConfirmations);
                    storage.addBlock(String.valueOf(block.getHeader().getIndex()), block, true);
                }
            } else {
                pseudoBlock.setConfirmations(numberOfConfirmations);
                this.storage.addBlockMempool(pseudoBlock.getHash(), pseudoBlock);
                log.info("Updated number of confirmations:{} for block:{}", numberOfConfirmations,
                        pseudoBlock.getHash());
            }
        } catch (Exception e) {
            log.error("Error while processing verified request:{}", e);
        }
    }

    /**
     * Send to the node state chain block+ transaction that failed during node validation
     * */
    public void processRequest() {
        try {
            Block block = this.storage.getBlockByIndex(String.valueOf(this.blockHeader.getIndex()));

            if (block.getTransactions().isEmpty()) {
                //Only send empty block
                MessageWrapper blockWrapper = new MessageWrapper();
                blockWrapper.setContent(block);
                blockWrapper.setVerified(true);
                blockWrapper.setType(MessageTypes.BLOCK);
                blockWrapper.setAction(MessageActions.APPEND);

                log.info("Sending:{}", blockWrapper.getType());
                this.connection
                        .outbound().sendObject(Mono.just(blockWrapper.serialize()))
                        .then().subscribe();
            } else {
                //Remove the transaction list and send the block without transaction, nodes will re-construct it
                Block blockWithoutTransactionList = block;
                blockWithoutTransactionList.setTransactions(new ArrayList<>());

                MessageWrapper blockWrapper = new MessageWrapper();
                blockWrapper.setContent(blockWithoutTransactionList);
                blockWrapper.setVerified(true);
                blockWrapper.setType(MessageTypes.BLOCK);
                blockWrapper.setAction(MessageActions.APPEND);

                Flux.fromIterable(block.getTransactions())
                        .flatMap(transactionHash -> {
                            Transaction transaction = this.storage.getTransactionByKey(transactionHash);

                            MessageWrapper transactionWrapper = new MessageWrapper();
                            transactionWrapper.setAction(MessageActions.APPEND);
                            transactionWrapper.setType(MessageTypes.TRANSACTION);
                            transactionWrapper.setVerified(true);
                            transactionWrapper.setContent(transaction);

                            return Mono.just(transactionWrapper);
                        }).mergeWith(Mono.just(blockWrapper))
                        .delayElements(Duration.ofMillis(150))
                        .doOnNext(messageWrapper -> {
                            log.info("Sending:{}", messageWrapper.getType());

                            this.connection
                                    .outbound().sendObject(Mono.just(messageWrapper.serialize()))
                                    .then().subscribe();
                        })
                        .then()
                        .subscribe();
            }
        } catch (Exception e){}
    }


    /**
     * SUB-PROCESS MODULES
     * */



    /**
     * This method will create a block from an existing pseudoBlock and append it to the chain while executing transacitons
     * */
    private void createBlockAndExecuteFund(Block pseudoBlock) {
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            Block block = new Block();
            block.setConfirmations(pseudoBlock.getConfirmations());
            block.setHeader(this.blockHeader);
            block.setStatus(BlockStatus.MINED);
            block.setForkHash("0000000000000000000000000000000000000000000000000000000000000000");

            List<String> transactionHashes = new ArrayList<>();

            //Process the transactional block create and execute transactions
            if(!pseudoBlock.getTransactions().isEmpty()) {
                int index = 0;
                for (String pseudoHash : pseudoBlock.getTransactions()) {
                    PseudoTransaction pseudoTransaction = storage.getMempoolTransaction(pseudoHash);

                    Transaction transaction = BlockchainUtils
                            .mapPsuedoTransactionToTransaction(pseudoTransaction, this.blockHeader, index);

                    transactionHashes.add(transaction.getHash());

                    index++;

                    AccountModel sourceAccount = storage.getAccountByAddress(transaction.getFrom());

                    Fund fund = Fund.builder()
                            .sourceAccount(sourceAccount)
                            .isProcessed(true)
                            .targetAccountAddress(transaction.getTo())
                            .amount(AmountDTO.builder()
                                    .rawAmount(transaction.getValue())
                                    .fees(transaction.getFees())
                                    .build())
                            .build();

                    moveFund.execute(fund);

                    storage.addTransaction(transaction.getHash(), transaction);
                }

                updateMempoolTransactions(pseudoBlock.getTransactions(), TransactionStatus.SUCCESS);

                block.setTransactions(transactionHashes);
                block.setNumberOfBits(SerializationUtils.serialize(block).length);
            } else {
                block.setTransactions(pseudoBlock.getTransactions());//Empty
                block.setNumberOfBits(pseudoBlock.getNumberOfBits());
            }

            Fund rewardFund = Fund.builder()
                    .sourceAccount(null)
                    .targetAccountAddress(this.blockHeader.getMinerAddress())
                    .isProcessed(true)
                    .amount(AmountDTO.builder()
                            .rawAmount(this.blockHeader.getReward())
                            .build())
                    .build();

            moveFund.executeRewardFund(rewardFund);

            ChainState state = this.storage.getStateTrie();
            state.setLastBlockIndex(state.getLastBlockIndex() + 1);

            storage.addBlock(String.valueOf(block.getHeader().getIndex()), block, true);
            log.info("New block added to the chain:{}", block);

            storage.updateStateTrie(state);
            log.info("State updated with latest index:{}", state.getLastBlockIndex());

            MessageWrapper messageWrapper = new MessageWrapper();
            messageWrapper.setVerified(true);
            messageWrapper.setType(MessageTypes.BLOCKHEADER);
            messageWrapper.setAction(MessageActions.APPEND_MINED_BLOCK);
            messageWrapper.setContent(this.blockHeader);


            //Broadcast confirmation order to the nodes
            for(Connection conn : this.connectionStorage) {
                conn.outbound().sendObject(Mono.just(messageWrapper.serialize()))
                        .then().subscribe();
            }

            ApiStateChain apiStateChain = new ApiStateChain();
            apiStateChain.setNetworkFees(state.getNetworkFees());
            apiStateChain.setLastBlockIndex(state.getLastBlockIndex());

            String apiStateString = objectMapper.writeValueAsString(apiStateChain);

            //Update API
//            for(WebsocketOutbound websocketOutbound : this.websocketOutbounds) {
//                websocketOutbound.sendString(Mono.just(apiStateString))
//                        .then().subscribe();
//            }

            this.webSocketServices.broadcastToAll(apiStateString);

            this.storage.deleteMempoolBlocksAtIndex(blockHeader.getIndex());
            log.info("Deleted all memory blocks for index:{}", blockHeader.getIndex());
        }catch (Exception e) {
            log.error("Error while creating new block and executing fund:{}", e);
        }
    }

    /**
     * Process the incoming empty block, without any transactions
     * */
    private void processEmptyBlock() {
        try {
            Block block = new Block();
            block.setHeader(this.blockHeader);
            block.setConfirmations(1);
            block.setTransactions(new ArrayList<>());
            block.setForkHash("0000000000000000000000000000000000000000000000000000000000000000");
            block.setStatus(BlockStatus.PENDING);
            block.setNumberOfBits(SerializationUtils.serialize(block).length);

            this.storage.addBlockMempool(block.getHash(), block);

            log.info("Empty block added to memory!{}", this.storage.getPseudoBlockByHash(this.blockHeader.getHash()));

            //Ask nodes to confirm
            for(Connection conn : this.connectionStorage) {
                MessageWrapper connectionWrapper = new MessageWrapper();
                connectionWrapper.setContent(this.blockHeader);
                connectionWrapper.setVerified(true);
                connectionWrapper.setAction(MessageActions.VERIFY);
                connectionWrapper.setType(MessageTypes.BLOCKHEADER);

                conn.outbound().sendObject(Mono.just(connectionWrapper.serialize()))
                        .then().subscribe();
            }
        } catch (Exception e) {
            MessageWrapper discard = new MessageWrapper();
            discard.setContent(this.blockHeader);
            discard.setType(MessageTypes.BLOCKHEADER);
            discard.setVerified(true);
            discard.setAction(MessageActions.DISCARD);

            for(Connection conn : this.connectionStorage) {
                conn.outbound()
                        .sendObject(Mono.just(discard.serialize()))
                        .then().subscribe();
            }

            updateMempoolTransactions(matchMerkleRoot(), TransactionStatus.VALIDATED);

            log.error("Error while processing empty block:{}", e.getMessage());
        }

    }

    /**
     * Process block with transactions
     * */
    private void processTransactionalBlock() {
        try {
            //Return the list of pseudoHashes that matched the markleRoot
            List<String> bothHashes = matchMerkleRoot();

            if(bothHashes.isEmpty()) {
                log.error("Could not match markle root!");
                throw new MiningException(MiningErrorCode.PSEUDO_MATCH);
            }

            Block block = new Block();
            block.setHeader(this.blockHeader);
            block.setConfirmations(1);
            block.setTransactions(bothHashes);
            block.setForkHash("0000000000000000000000000000000000000000000000000000000000000000");


            //if((this.connectionStorage.size() - 1) > 0) {
                block.setStatus(BlockStatus.PENDING);
                block.setNumberOfBits(SerializationUtils.serialize(block).length);

                this.storage.addBlockMempool(block.getHash(), block);
                log.info("Transactional Block added to memory!{}", block.getHash());
            //} else {
                //block.setStatus(BlockStatus.MINED);
                //block.setNumberOfBits(SerializationUtils.serialize(block).length);

                //createBlockAndExecuteFund(block);
                //log.info("Block executed!");
            //}
            //Ask nodes to confirm
            for(Connection conn : this.connectionStorage) {
                MessageWrapper connectionWrapper = new MessageWrapper();
                connectionWrapper.setContent(this.blockHeader);
                connectionWrapper.setVerified(true);
                connectionWrapper.setAction(MessageActions.VERIFY);
                connectionWrapper.setType(MessageTypes.BLOCKHEADER);

                conn.outbound().sendObject(Mono.just(connectionWrapper.serialize()))
                        .then().subscribe();
            }
        } catch (Exception e) {
            MessageWrapper discard = new MessageWrapper();
            discard.setContent(this.blockHeader);
            discard.setType(MessageTypes.BLOCKHEADER);
            discard.setVerified(true);
            discard.setAction(MessageActions.DISCARD);

            for(Connection conn : this.connectionStorage) {
                conn.outbound()
                        .sendObject(Mono.just(discard.serialize()))
                        .then().subscribe();
            }

            updateMempoolTransactions(matchMerkleRoot(), TransactionStatus.VALIDATED);

            log.error("Error while processing transactional block:{}", e.getMessage());
        }
    }

    private List<String> matchMerkleRoot() {
        List<PseudoTransaction> availablePseudoTransactions = this.storage
                .getPseudoTransactionGrouped(this.blockHeader.getTimeStamp());

        List<String> transactionHashes = new ArrayList<>();
        List<String> pseudoHashes = new ArrayList<>();

        for (int index = 0; index < availablePseudoTransactions.size(); index++) {
            PseudoTransaction pseudoTransaction = availablePseudoTransactions.get(index);

            Transaction transaction = BlockchainUtils
                    .mapPsuedoTransactionToTransaction(pseudoTransaction, this.blockHeader, index);

            transactionHashes.add(transaction.getHash());
            pseudoHashes.add(pseudoTransaction.getPseudoHash());
        }

        String markleRoot = BlockchainUtils.markleRootGenerator(transactionHashes);
        int totalSize = transactionHashes.size();

        try {
            if (totalSize >= this.blockHeader.getNumberOfTransactions()) {
                while (!markleRoot.equals(this.blockHeader.getMarkleRoot()) && !transactionHashes.isEmpty()) {
                    //log.info("MR:{}", markleRoot);
                    transactionHashes.remove(totalSize - 1); // Remove the last transaction hash
                    pseudoHashes.remove(totalSize - 1);

                    totalSize--;
                    markleRoot = BlockchainUtils.markleRootGenerator(transactionHashes);
                }
            } else {
                //This could not be possible
                throw new MiningException(MiningErrorCode.OPERATION_FAILED);
            }

            if(markleRoot.equals(this.blockHeader.getMarkleRoot())) {
                return pseudoHashes;
            }
        } catch (Exception e) {
            log.error("Error:{}", e.getMessage());
        }


        return new ArrayList<>();
    }
    private void updateMempoolTransactions(List<String> mempoolHashes, TransactionStatus status){
        for(String pseudoHash : mempoolHashes) {
            PseudoTransaction pseudoTransaction = storage.getMempoolTransaction(pseudoHash);

            if(pseudoTransaction != null) {
                pseudoTransaction.setStatus(status);

                storage.addMempool(pseudoTransaction.getPseudoHash(), pseudoTransaction);
            }
        }
    }
}
