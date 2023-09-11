package uk.co.roteala.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import uk.co.roteala.api.ResultStatus;
import uk.co.roteala.api.account.AccountRequest;
import uk.co.roteala.api.account.AccountResponse;
import uk.co.roteala.common.AccountModel;
import uk.co.roteala.exceptions.AccountException;
import uk.co.roteala.exceptions.errorcodes.AccountErrorCode;
import uk.co.roteala.storage.StorageServices;
import uk.co.roteala.utils.BlockchainUtils;

import java.util.List;


@Slf4j
@Service
@Validated
@RequiredArgsConstructor
public class AccountServices {
    private final StorageServices storage;

    public AccountResponse getAccount(AccountRequest request) {
        AccountResponse response = new AccountResponse();

        try {
            if(BlockchainUtils.validBlockAddress(request.getAddress())) {
                throw new AccountException(AccountErrorCode.ADDRESS_INVALID);
            }

            AccountModel account = storage.getAccountByAddress(request.getAddress());



            response.setAddress(account.getAddress());
            response.setBalance(account.getBalance());
            response.setInboundAmount(account.getInboundAmount());
            response.setOutboundAmount(account.getOutboundAmount());
            response.setNonce(account.getNonce());

            response.setResult(ResultStatus.SUCCESS);

        } catch (Exception e) {
            response.setResult(ResultStatus.ERROR);
            response.setMessage(e.getMessage());
        }

        return response;
    }
}
