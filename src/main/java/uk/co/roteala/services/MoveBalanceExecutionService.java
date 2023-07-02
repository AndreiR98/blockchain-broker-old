package uk.co.roteala.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.co.roteala.common.AccountModel;
import uk.co.roteala.common.monetary.Coin;
import uk.co.roteala.common.monetary.Fund;
import uk.co.roteala.common.monetary.MoveFund;
import uk.co.roteala.storage.StorageServices;

@Slf4j
@RequiredArgsConstructor
public class MoveBalanceExecutionService implements MoveFund {

    private final StorageServices storageServices;


    /**
     * Update balances for both target and source account
     * */
    @Override
    public void execute(Fund fund) {
        AccountModel targetAccount = fund.getTargetAccount();
        AccountModel sourceAccount = fund.getSourceAccount();

        Coin amount = fund.getAmount();

        //Set new values;
        targetAccount.setInboundAmount(targetAccount.getInboundAmount().plus(amount));
        sourceAccount.setOutboundAmount(sourceAccount.getOutboundAmount().plus(amount));

        storageServices.updateAccount(targetAccount);
        storageServices.updateAccount(sourceAccount);
    }
}
