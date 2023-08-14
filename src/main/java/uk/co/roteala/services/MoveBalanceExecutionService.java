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
        Coin totalFees = fees.getFees().add(fees.getNetworkFees());

        //if true move the actual balance
        if(fund.isProcessed()) {
            targetAccount.setInboundAmount(targetAccount.getInboundAmount().add(amount));
            sourceAccount.setOutboundAmount(sourceAccount.getOutboundAmount().add(amount.add(totalFees)));

            log.info("Source balance:{}", sourceAccount.getBalance().add(amount));

            targetAccount.setBalance(targetAccount.getBalance().add(amount));
            sourceAccount.setBalance(sourceAccount.getBalance().subtract(amount.add(totalFees)));
        } else {
            targetAccount.setNonce(targetAccount.getNonce());
            sourceAccount.setNonce(sourceAccount.getNonce() + 1);

            targetAccount.setInboundAmount(targetAccount.getInboundAmount().add(amount));
            sourceAccount.setOutboundAmount(sourceAccount.getOutboundAmount().subtract(amount.add(totalFees)));
        }

        storageServices.updateAccount(targetAccount);
        storageServices.updateAccount(sourceAccount);
    }

    @Override
    public void executeRewardFund(Fund fund){}

    @Override
    public void reverseFunding(Fund fund) {
    }
}
