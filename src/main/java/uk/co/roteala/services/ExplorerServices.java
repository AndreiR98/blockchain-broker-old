package uk.co.roteala.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Mono;
import reactor.netty.http.websocket.WebsocketOutbound;
import uk.co.roteala.api.ResultStatus;
import uk.co.roteala.api.account.AccountRequest;
import uk.co.roteala.api.account.AccountResponse;
import uk.co.roteala.api.block.BlockRequest;
import uk.co.roteala.api.block.BlockResponse;
import uk.co.roteala.api.explorer.ExplorerRequest;
import uk.co.roteala.api.explorer.ExplorerResponse;
import uk.co.roteala.api.transaction.TransactionRequest;
import uk.co.roteala.api.transaction.TransactionResponse;
import uk.co.roteala.common.*;
import uk.co.roteala.common.monetary.Coin;
import uk.co.roteala.exceptions.BlockException;
import uk.co.roteala.exceptions.TransactionException;
import uk.co.roteala.exceptions.errorcodes.StorageErrorCode;
import uk.co.roteala.exceptions.errorcodes.TransactionErrorCode;
import uk.co.roteala.handlers.WebSocketRouterHandler;
import uk.co.roteala.security.ECKey;
import uk.co.roteala.security.utils.HashingService;
import uk.co.roteala.storage.StorageServices;
import uk.co.roteala.utils.BlockchainUtils;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static uk.co.roteala.security.utils.HashingService.bytesToHexString;

@Slf4j
@Service
@Validated
@RequiredArgsConstructor
public class ExplorerServices {
    private final StorageServices storage;

    private final WebSocketRouterHandler webSocketHandler;

    @Autowired
    private List<WebsocketOutbound> websocketOutbounds;

    public ExplorerResponse processExplorerRequest(@Valid ExplorerRequest explorerRequest){
        ExplorerResponse response = new ExplorerResponse();

        log.info("Requested:{}", explorerRequest);

        if(BlockchainUtils.validAddress(explorerRequest.getDataHash())) {
            response.setStatus(ResultStatus.SUCCESS);
            response.setAccountAddress(explorerRequest.getDataHash());
        } else if (BlockchainUtils.validTransactionHash(explorerRequest.getDataHash())) {
            if(storage.getTransactionByKey(explorerRequest.getDataHash()) != null) {
                response.setStatus(ResultStatus.SUCCESS);
                response.setTransactionHash(explorerRequest.getDataHash());
            }
        } else if (BlockchainUtils.validBlockAddress(explorerRequest.getDataHash())) {
            if(storage.getBlockByHash(explorerRequest.getDataHash()) != null) {
                Block block = storage.getBlockByHash(explorerRequest.getDataHash());

                response.setStatus(ResultStatus.SUCCESS);
                response.setBlockIndex(block.getIndex());
            }
        } else if (BlockchainUtils.isInteger(explorerRequest.getDataHash())){
            if(storage.getBlockByIndex(explorerRequest.getDataHash()) != null) {
                response.setStatus(ResultStatus.SUCCESS);
                response.setBlockIndex(Integer.parseInt(explorerRequest.getDataHash()));
            }
        } else {
            response.setStatus(ResultStatus.ERROR);
        }

        return response;
    }

    public TransactionResponse getTransactionByHash(@Valid TransactionRequest transactionRequest){
        TransactionResponse response = new TransactionResponse();

        try {
            Transaction transaction = storage.getTransactionByKey(transactionRequest.getTransactionHash());

            if(transaction == null) {
                throw new TransactionException(TransactionErrorCode.TRANSACTION_NOT_FOUND);
            }

            response.setHash(transaction.getHash());
            response.setPseudoHash(transaction.getPseudoHash());
            response.setBlockNumber(transaction.getBlockNumber());
            response.setFrom(transaction.getFrom());
            response.setTo(transaction.getTo());
            response.setValue(transaction.getValue());
            response.setVersion(transaction.getVersion());
            response.setTransactionIndex(transaction.getTransactionIndex());
            response.setFees(transaction.getFees());
            response.setNonce(transaction.getNonce());
            response.setTimeStamp(transaction.getTimeStamp());
            response.setConfirmations(transaction.getConfirmations());
            response.setBlockTime(transaction.getBlockTime());
            response.setPseudoHash(transaction.getPubKeyHash());
            response.setTransactionStatus(transaction.getStatus());

            response.setResult(ResultStatus.SUCCESS);

            return response;
        } catch (Exception e) {
            return TransactionResponse.builder()
                    .result(ResultStatus.ERROR)
                    .message(e.getMessage()).build();
        }
    }

    public BlockResponse getBlock(@Valid BlockRequest blockRequest){
        BlockResponse response = new BlockResponse();

        try {
            Block block = null;
            if(BlockchainUtils.isInteger(blockRequest.getIndex())){
                block = storage.getBlockByIndex(blockRequest.getIndex());
            } else {
                block = storage.getBlockByHash(blockRequest.getIndex());
            }

            if(block == null) {
                throw new BlockException(StorageErrorCode.BLOCK_NOT_FOUND);
            }

            response.setBlockHash(block.getHash());
            response.setBlock(block);
            response.setResult(ResultStatus.SUCCESS);

            Block finalBlock = block;

            ObjectMapper objectMapper = new ObjectMapper();
            String blockJSON = objectMapper.writeValueAsString(block);

            websocketOutbounds.forEach(connection -> {
                connection.sendString(Mono.just(blockJSON))
                        .then()
                        .subscribe();
            });

            return response;
        } catch (Exception e) {
            return BlockResponse.builder()
                    .result(ResultStatus.ERROR)
                    .message(e.getMessage()).build();
        }
    }

    public AccountResponse getAccount(@Valid AccountRequest accountRequest){
        return null;
    }

    public List<BaseModel> addMultipleData() {
        Random r = new Random();

        List<BaseModel> dataList = new ArrayList<>();

        int numberOfTx = r.nextInt(5);

        ECKey key = new ECKey();

        String miner = key.getPublicKey().toAddress();

        ChainState state = storage.getStateTrie();

        Block previousBlock = storage.getBlockByIndex(state.getLastBlockIndex().toString());

        long timeStamp = System.currentTimeMillis();

        List<String> tx = new ArrayList<>();

        SignatureModel signature = new SignatureModel();
        signature.setR("abc123");
        signature.setS("bcd123");

        for (int i = 0; i < numberOfTx; i++){
            Transaction transaction = new Transaction();
            transaction.setConfirmations(1);
            transaction.setStatus(TransactionStatus.PROCESSED);
            transaction.setNonce(2);
            transaction.setVersion(0x16);
            transaction.setBlockTime(timeStamp);
            transaction.setPubKeyHash(bytesToHexString(HashingService.doubleSHA256(key.getPublicKey().encode())));
            transaction.setFrom("1L8pa8DQyuVaHCgR2UN71Mzy3KnNX68Y1a");
            transaction.setTo("19mdW9Nrcy5F5dX1gEe4caytrSpHuM2Vsw");
            transaction.setSignature(signature);
            transaction.setValue(Coin.ZERO);
            transaction.setFees(Coin.ZERO);
            transaction.setTransactionIndex(i);
            transaction.setBlockNumber(state.getLastBlockIndex() + 1);
            transaction.setPseudoHash("0000000000000000000000000000000000000000000000000000000000000000");
            transaction.setHash(transaction.computeHash());

            storage.addTransaction(transaction.getHash(), transaction);

            dataList.add(transaction);

            tx.add(transaction.getHash());
        }

        String markleRoot = BlockchainUtils.markleRootGenerator(tx);

        //Prepare block
        Block newBlock = new Block();
        newBlock.setForkHash("0000000000000000000000000000000000000000000000000000000000000000");
        newBlock.setStatus(BlockStatus.MINED);
        newBlock.setPreviousHash(previousBlock.getHash());
        newBlock.setVersion(0x16);
        newBlock.setIndex(previousBlock.getIndex() + 1);
        newBlock.setMiner(miner);
        newBlock.setTimeStamp(timeStamp);
        newBlock.setDifficulty(state.getTarget());
        newBlock.setReward(Coin.ZERO);
        newBlock.setNonce("fff");
        newBlock.setTransactions(tx);
        newBlock.setMarkleRoot(markleRoot);
        newBlock.setConfirmations(1);
        newBlock.setNumberOfBits(SerializationUtils.serialize(newBlock).length);
        newBlock.setHash(newBlock.computeHash());

        storage.addBlock(newBlock.getIndex().toString(), newBlock, true);


        dataList.add(newBlock);

        state.setLastBlockIndex(newBlock.getIndex());
        storage.updateStateTrie(state);
        //webSocketHandler.broadcastMessage(state.getLastBlockIndex().toString());
        log.info("Index:{}", state.getLastBlockIndex());
        return dataList;
    }
}
