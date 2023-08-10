package uk.co.roteala.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.buffer.Unpooled;
import io.netty.util.concurrent.BlockingOperationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import uk.co.roteala.api.ResultStatus;
import uk.co.roteala.api.transaction.*;
import uk.co.roteala.common.*;
import uk.co.roteala.common.events.*;
import uk.co.roteala.common.monetary.AmountDTO;
import uk.co.roteala.common.monetary.Coin;
import uk.co.roteala.common.monetary.Fund;
import uk.co.roteala.common.monetary.MoveFund;
import uk.co.roteala.exceptions.TransactionException;
import uk.co.roteala.exceptions.errorcodes.TransactionErrorCode;
import uk.co.roteala.handlers.TransmissionHandler;
import uk.co.roteala.processor.MessageProcessor;
import uk.co.roteala.processor.Processor;
import uk.co.roteala.storage.StorageServices;
import uk.co.roteala.utils.BlockchainUtils;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@Validated
@RequiredArgsConstructor
public class TransactionServices {
    @Autowired
    private List<Connection> connectionStorage;

    @Autowired
    private MoveFund moveBalanceExecutionService;

    private final StorageServices storage;

    public PseudoTransactionResponse sendTransaction(@Valid PseudoTransactionRequest transactionRequest) {
        PseudoTransactionResponse response = new PseudoTransactionResponse();

        PseudoTransaction pseudoTransaction = mapRequest(transactionRequest);

        boolean isValidated = false;

        try {
            //Check the transaction signature is valid
            AccountModel senderAccount = storage.getAccountByAddress(pseudoTransaction.getFrom());

            BigDecimal senderBalance = senderAccount.getBalance().getValue();
            BigDecimal amount = pseudoTransaction.getValue().getValue();
            BigDecimal fees = pseudoTransaction.getFees()
                    .getNetworkFees().value
                    .add(pseudoTransaction.getFees().getFees().value);

            int comparator = senderBalance.compareTo(amount.add(fees));

            if(pseudoTransaction.verifySignatureWithRecovery() && (comparator > 0)){
                pseudoTransaction.setStatus(TransactionStatus.VALIDATED);
                isValidated = true;
            } else if (!pseudoTransaction.verifySignatureWithRecovery()) {
                throw new TransactionException(TransactionErrorCode.TRANSACTION_IDENTITY);
            } else if(comparator < 0) {
                throw new TransactionException(TransactionErrorCode.AMOUNT_GREATER_ACCOUNT, amount.add(fees), senderBalance);
            }

            //Create fund object and execution service
            Fund fund = new Fund();
            fund.setProcessed(false);
            fund.setSourceAccount(senderAccount);
            fund.setTargetAccountAddress(pseudoTransaction.getTo());
            fund.setAmount(AmountDTO.builder()
                            .fees(pseudoTransaction.getFees())
                            .rawAmount(pseudoTransaction.getValue())
                    .build());

            if(isValidated) {
                storage.addMempool(transactionRequest.getPseudoHash(), pseudoTransaction);

                moveBalanceExecutionService.execute(fund);

                //Broadcast the transaction to other nodes
                MessageWrapper messageWrapper = MessageWrapper.builder()
                        .type(MessageTypes.MEMPOOL)
                        .verified(true)
                        .action(MessageActions.APPEND)
                        .content(pseudoTransaction)
                        .build();

                Flux.fromIterable(connectionStorage)
                                .doOnNext(connection -> connection.outbound()
                                        .sendObject(Mono.just(messageWrapper.serialize()))
                                        .then().subscribe()).then().subscribe();

                log.info("New transaction added:{}", pseudoTransaction.getPseudoHash());

                response.setResult(ResultStatus.SUCCESS);
            }

        } catch (TransactionException e) {
            return PseudoTransactionResponse.builder()
                    .result(ResultStatus.ERROR)
                    .message(e.getMessage()).build();
        }

        return response;
    }

    private PseudoTransaction mapRequest(PseudoTransactionRequest request) {
        return PseudoTransaction.builder()
                .from(request.getFrom())
                .nonce(request.getNonce())
                .pseudoHash(request.getPseudoHash())
                .to(request.getTo())
                .fees(request.getFees())
                .pubKeyHash(request.getPubKeyHash())
                .value(request.getValue())
                .signature(request.getSignature())
                .status(TransactionStatus.valueOfCode(Integer.valueOf(request.getStatus())))
                .version(request.getVersion())
                .timeStamp(request.getTimeStamp())
                .build();
    }
}
