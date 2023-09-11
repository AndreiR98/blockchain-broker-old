package uk.co.roteala.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.Unpooled;
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
import uk.co.roteala.api.mempool.MempoolBlocksResponse;
import uk.co.roteala.api.transaction.PseudoTransactionResponse;
import uk.co.roteala.api.transaction.TransactionRequest;
import uk.co.roteala.api.transaction.TransactionResponse;
import uk.co.roteala.common.*;
import uk.co.roteala.common.monetary.Coin;
import uk.co.roteala.exceptions.BlockException;
import uk.co.roteala.exceptions.StorageException;
import uk.co.roteala.exceptions.TransactionException;
import uk.co.roteala.exceptions.errorcodes.StorageErrorCode;
import uk.co.roteala.exceptions.errorcodes.TransactionErrorCode;
import uk.co.roteala.handlers.WebSocketRouterHandler;
import uk.co.roteala.security.ECKey;
import uk.co.roteala.security.utils.HashingService;
import uk.co.roteala.storage.StorageServices;
import uk.co.roteala.utils.BlockchainUtils;

import javax.validation.Valid;
import java.math.BigInteger;
import java.util.*;

import static uk.co.roteala.security.utils.HashingService.bytesToHexString;

@Slf4j
@Service
@Validated
@RequiredArgsConstructor
public class ExplorerServices {
    private final StorageServices storage;

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
                response.setBlockIndex(block.getHeader().getIndex());
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
            response.setPubKeyHash(transaction.getPubKeyHash());
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

            Coin totalValue = Coin.ZERO;
            Coin totalFees = Coin.ZERO;

            for(String hash : block.getTransactions()) {
                Transaction transaction = storage.getTransactionByKey(hash);

                totalValue = totalValue.add(transaction.getValue());
                totalFees = totalFees.add(transaction.getFees().getFees().add(transaction.getFees().getNetworkFees()));
            }



            if(block == null) {
                throw new BlockException(StorageErrorCode.BLOCK_NOT_FOUND);
            }

            response.setBlockHash(block.getHash());
            response.setBlock(block);
            response.setTotalValue(totalValue);
            response.setTotalFees(totalFees);
            response.setTotalTransactions(block.getTransactions().size());
            response.setResult(ResultStatus.SUCCESS);

            return response;
        } catch (Exception e) {
            return BlockResponse.builder()
                    .result(ResultStatus.ERROR)
                    .message(e.getMessage()).build();
        }
    }

    public AccountResponse getAccount(@Valid AccountRequest accountRequest){
        AccountResponse response = new AccountResponse();

        try {
            AccountModel account = storage.getAccountByAddress(accountRequest.getAddress());

            Map<String, List<String>> transactionByUser = storage.getTransactionsByUser(account.getAddress());

            response.setAddress(account.getAddress());
            response.setBalance(account.getBalance());
            response.setInboundAmount(account.getInboundAmount());
            response.setOutboundAmount(account.getOutboundAmount());
            response.setTransactions(transactionByUser);

            response.setResult(ResultStatus.SUCCESS);
        } catch (Exception e) {
            response.setResult(ResultStatus.ERROR);
        }

        return response;
    }

    public MempoolBlocksResponse getMempoolBlocksGrouped(){
        MempoolBlocksResponse response = new MempoolBlocksResponse();

        try {
            final List<Block> pseudoBlocks = storage.getPseudoBlocks();

            Map<Integer, List<Block>> mapByIndex = new HashMap<>();

            pseudoBlocks.forEach(block -> {
                int index = block.getHeader().getIndex();
                mapByIndex.computeIfAbsent(index, k -> new ArrayList<>())
                        .add(block);
            });

            response.setBlocksMap(mapByIndex);
            response.setResult(ResultStatus.SUCCESS);
        } catch (Exception e) {
            response.setResult(ResultStatus.ERROR);
            response.setMessage(e.getMessage());
            throw new StorageException(StorageErrorCode.MEMPOOL_FAILED);
        }

        return response;
    }

    public BlockResponse getMempoolBlock(@Valid BlockRequest blockRequest){
        BlockResponse response = new BlockResponse();

        try {
            Block block = null;
            block = storage.getPseudoBlockByHash(blockRequest.getIndex());


            if(block == null) {
                throw new BlockException(StorageErrorCode.BLOCK_NOT_FOUND);
            }

            response.setBlockHash(block.getHash());
            response.setBlock(block);
            response.setTotalTransactions(block.getTransactions().size());
            response.setResult(ResultStatus.SUCCESS);

            return response;
        } catch (Exception e) {
            return BlockResponse.builder()
                    .result(ResultStatus.ERROR)
                    .message(e.getMessage()).build();
        }
    }

    public PseudoTransactionResponse getPseudoTransaction(@Valid TransactionRequest transactionRequest) {
        PseudoTransactionResponse response = new PseudoTransactionResponse();

        try {
            PseudoTransaction transaction = storage.getMempoolTransaction(transactionRequest.getTransactionHash());

            if(transaction == null) {
                throw new TransactionException(TransactionErrorCode.TRANSACTION_NOT_FOUND);
            }

//            response.setPseudoHash(transaction.getPseudoHash());
//            response.setFrom(transaction.getFrom());
//            response.setTo(transaction.getTo());
//            response.setValue(transaction.getValue());
//            response.setVersion(transaction.getVersion());
//            response.setNonce(transaction.getNonce());
//            response.setTransactionStatus(transaction.getStatus());
            response.setPseudoTransaction(transaction);

            response.setResult(ResultStatus.SUCCESS);

            return response;
        } catch (Exception e) {
            return PseudoTransactionResponse.builder()
                    .result(ResultStatus.ERROR)
                    .message(e.getMessage()).build();
        }
    }
}
