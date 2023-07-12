package uk.co.roteala.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import uk.co.roteala.api.ResultStatus;
import uk.co.roteala.api.transaction.*;
import uk.co.roteala.common.AccountModel;
import uk.co.roteala.common.PseudoTransaction;
import uk.co.roteala.common.Transaction;
import uk.co.roteala.common.TransactionStatus;
import uk.co.roteala.common.events.MempoolTransaction;
import uk.co.roteala.common.events.Message;
import uk.co.roteala.common.monetary.Coin;
import uk.co.roteala.common.monetary.Fund;
import uk.co.roteala.common.monetary.MoveFund;
import uk.co.roteala.exceptions.TransactionException;
import uk.co.roteala.exceptions.errorcodes.TransactionErrorCode;
import uk.co.roteala.handlers.TransmissionHandler;
import uk.co.roteala.storage.StorageServices;

import javax.validation.Valid;
import java.math.BigDecimal;

@Slf4j
@Service
@Validated
@RequiredArgsConstructor
public class TransactionServices {
    @Autowired
    private TransmissionHandler transmissionHandler;

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

            if(senderAccount == null) {
                throw new RuntimeException("sss");
            }

            BigDecimal senderBalance = senderAccount.getBalance().getValue();
            BigDecimal amount = pseudoTransaction.getValue().getValue();

            if(pseudoTransaction.verifySignatureWithRecovery() && (senderBalance.compareTo(amount) > 0)){
                pseudoTransaction.setStatus(TransactionStatus.VALIDATED);
                isValidated = true;
            } else if (!pseudoTransaction.verifySignatureWithRecovery()) {
                throw new TransactionException(TransactionErrorCode.TRANSACTION_IDENTITY);
            } else if(senderBalance.compareTo(amount) < 0) {
                log.info("Transaction amount {}, is greater than sender's balance {}", amount, senderBalance);
                throw new TransactionException(TransactionErrorCode.AMOUNT_GREATER_ACCOUNT, amount, senderBalance);
            }

            //Create fund object and execution service
            Fund fund = new Fund();
            fund.setProcessed(false);
            fund.setSourceAccount(senderAccount);
            fund.setTargetAccountAddress(pseudoTransaction.getTo());
            fund.setAmount(pseudoTransaction.getValue());

            if(isValidated) {
                storage.addMempool(transactionRequest.getPseudoHash(), pseudoTransaction);

                moveBalanceExecutionService.execute(fund);

                //Broadcast the transaction to other nodes
                Message pseudoTransactionMessage = new MempoolTransaction(pseudoTransaction);

                transmissionHandler.sendPseudoTransaction(pseudoTransactionMessage);

                response.setTransaction(pseudoTransaction);
                response.setResult(ResultStatus.SUCCESS);
            }

        } catch (TransactionException e) {
            return PseudoTransactionResponse.builder()
                    .result(ResultStatus.ERROR)
                    .message(e.getMessage()).build();
        }

        return response;
    }

    public PseudoTransactionResponse getPseudoTransactionByKey(@Valid PseudoTransactionByKeyRequest transactionRequest) {
        PseudoTransactionResponse response = new PseudoTransactionResponse();

        if(transactionRequest.getPseudoHash() == null) {
            throw new RuntimeException();
        }

        PseudoTransaction transaction = storage.getMempoolTransaction(transactionRequest.getPseudoHash());

        if(transaction == null) {
            throw new RuntimeException();
        } else {
            response.setTransaction(transaction);
            response.setResult(ResultStatus.SUCCESS);
        }

        return response;
    }

    public TransactionResponse getTransactionByHash(@Valid TransactionRequest transactionRequest) {
        return null;
    }

    private PseudoTransaction mapRequest(PseudoTransactionRequest request) {
        return PseudoTransaction.builder()
                .from(request.getFrom())
                .nonce(request.getNonce())
                .pseudoHash(request.getPseudoHash())
                .to(request.getTo())
                .pubKeyHash(request.getPubKeyHash())
                .value(request.getValue())
                .signature(request.getSignature())
                .status(TransactionStatus.valueOfCode(request.getStatus()))
                .version(request.getVersion())
                .timeStamp(request.getTimeStamp())
                .build();
    }
}
