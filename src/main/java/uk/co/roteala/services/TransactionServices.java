package uk.co.roteala.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import uk.co.roteala.api.ResultStatus;
import uk.co.roteala.api.transaction.PseudoTransactionRequest;
import uk.co.roteala.api.transaction.PseudoTransactionResponse;
import uk.co.roteala.common.AccountModel;
import uk.co.roteala.common.PseudoTransaction;
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
        PseudoTransaction pseudoTransaction = mapRequest(transactionRequest);

        boolean isValidated = false;

        try {
            //Check the transaction signature is valid
            isValidated = pseudoTransaction.verifySignatureWithRecovery();

            AccountModel senderAccount = storage.getAccountByAddress(pseudoTransaction.getFrom());

            if(senderAccount == null) {
                throw new RuntimeException("sss");
            }

            BigDecimal senderBalance = senderAccount.getBalance().getValue();
            BigDecimal amount = pseudoTransaction.getValue().getValue();

            if(senderBalance.compareTo(amount) < 0) {
                throw new TransactionException(TransactionErrorCode.AMOUNT_GREATER_ACCOUNT, senderBalance, amount);
            }

            AccountModel receiverAccount = storage.getAccountByAddress(pseudoTransaction.getTo());

            if(receiverAccount == null) {

                receiverAccount = storage.addNewAccount(pseudoTransaction.getTo());

                log.info("Create new account:{}", receiverAccount.getAddress());
            }

            //Create fund object and execution service
            Fund fund = new Fund();
            fund.setProcessed(false);
            fund.setSourceAccount(senderAccount);
            fund.setTargetAccount(receiverAccount);
            fund.setAmount(pseudoTransaction.getValue());

            if(isValidated) {
                moveBalanceExecutionService.execute(fund);
            }

            //TODO: Create Balances object store boths accounts with new values, imutable, then execute the fund movement
            //Create Bean Autotwire MoveBalance execution service

            //Verify if account has enough funds
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        log.info("Validated:{}", isValidated);

        PseudoTransactionResponse response = new PseudoTransactionResponse();
        response.setResult(ResultStatus.SUCCESS);

        return response;
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
                .status(request.getStatus())
                .version(request.getVersion())
                .timeStamp(request.getTimeStamp())
                .build();
    }
}
