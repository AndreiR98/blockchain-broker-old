package uk.co.roteala.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import uk.co.roteala.api.ResultStatus;
import uk.co.roteala.api.account.AccountRequest;
import uk.co.roteala.api.account.AccountResponse;
import uk.co.roteala.common.AccountModel;
import uk.co.roteala.storage.StorageServices;

@Slf4j
@Service
@Validated
@RequiredArgsConstructor
public class AccountServices {
    private final StorageServices storage;

    public AccountResponse getAccount(AccountRequest request) {
        AccountResponse response = new AccountResponse();

        AccountModel account = storage.getAccountByAddress(request.getAddress());
        //TODO:Check integrity of address, format and checksum

        response.setAddress(account.getAddress());
        response.setBalance(account.getBalance());
        response.setInboundAmount(account.getInboundAmount());
        response.setOutboundAmount(account.getOutboundAmount());

        response.setResult(ResultStatus.SUCCESS);

        return response;
    }
}
