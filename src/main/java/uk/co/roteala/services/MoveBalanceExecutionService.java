package uk.co.roteala.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.co.roteala.common.AccountModel;
import uk.co.roteala.common.Fees;
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
        AccountModel sourceAccount = fund.getSourceAccount();

        AccountModel targetAccount = storageServices.getAccountByAddress(fund.getTargetAccountAddress());


        Coin amount = fund.getAmount().getRawAmount();
        Fees fees = fund.getAmount().getFees();
        Coin totalFees = fees.getFees().plus(fees.getNetworkFees());

        //if true move the actual balance
        if(fund.isProcessed()) {
            targetAccount.setInboundAmount(targetAccount.getInboundAmount().plus(amount));
            sourceAccount.setOutboundAmount(sourceAccount.getOutboundAmount().minus(amount.add(totalFees)));

            targetAccount.setBalance(targetAccount.getBalance().plus(amount));
            sourceAccount.setBalance(sourceAccount.getBalance().min(amount.add(totalFees)));
        } else {
            targetAccount.setNonce(targetAccount.getNonce());
            sourceAccount.setNonce(sourceAccount.getNonce() + 1);

            targetAccount.setInboundAmount(targetAccount.getInboundAmount().plus(amount));
            sourceAccount.setOutboundAmount(sourceAccount.getOutboundAmount().min(amount.add(totalFees)));
        }

        storageServices.updateAccount(targetAccount);
        storageServices.updateAccount(sourceAccount);
    }
}
