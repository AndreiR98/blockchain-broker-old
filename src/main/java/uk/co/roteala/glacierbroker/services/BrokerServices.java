package uk.co.roteala.glacierbroker.services;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksDBException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.co.roteala.common.*;
import uk.co.roteala.glacierbroker.storage.BrokerStorage;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j
@Service
@RequiredArgsConstructor
public class BrokerServices {

    @Autowired
    private BrokerStorage storage;


    /**
     * Create new account
     * TO DO:Retrieve the payload as byte[] decrypte then create account
     * */
    public void setNewAccount(String hexEncodedAddress, String password) throws RocksDBException {
        AddressBaseModel address = new AddressBaseModel();
        address.setHexEncoded(hexEncodedAddress);

        AccountModel account = new AccountModel();
        account.setTimeStamp(String.valueOf(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)));
        account.setBalance(new BigDecimal("0.0000"));
        account.setAddress(address);
        account.setPassword(password);

        storage.addAccount(account);
    }

    /**
     * Retrieve account based on the address
     * */
    public AccountModel getAccountDetails(AddressBaseModel address) throws RocksDBException {
        return storage.getAccount(address);
    }

    /**
     * Retrieve transaction details
     * */
    public TransactionBaseModel getTransactionDetails(String txHash){
        return storage.getTransaction(txHash);
    }

    public void sendNewTransaction(){}

    public BaseBlockModel getBlockDetails(Integer index){
        return storage.getBlock(index);
    }
}
