package uk.co.roteala.glacierbroker.services;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksDBException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.SerializationUtils;
import uk.co.roteala.common.*;
import uk.co.roteala.glacierbroker.models.ChainPayload;
import uk.co.roteala.glacierbroker.storage.BrokerStorage;
import uk.co.roteala.utils.GlacierUtils;

import java.math.BigDecimal;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * Check the storage if chain exists
     * */
    public boolean chainExistent() throws RocksDBException {
        return storage.getChain() != null;
    }

    public Chain getChain() throws RocksDBException {
        return storage.getChain();
    }

    /**
     * Initialize a new chain
     * */
    public void initChain(ChainPayload chainPayload) throws NoSuchAlgorithmException, RocksDBException, InvalidAlgorithmParameterException, NoSuchProviderException {
        Chain chain = new Chain();

        final BigDecimal[] totalValue = {new BigDecimal("0.0000")};

        chain.setName(chainPayload.getName());
        chain.setUniqueId(GlacierUtils.generateRandomSha256(32));
        chain.setStatus(ChainStatus.ACTIVE);
        chain.setChainType(ChainType.BROKERS);

        BaseCoinModel coin = new BaseCoinModel();
//        coin.setName(chainPayload.getCoin().getName());
//        coin.setDecimalPoints(chainPayload.getCoin().getDecimalPoints());
//        coin.setMaxAmount(chainPayload.getCoin().getMaxAmount());
//        coin.setNickName(chainPayload.getCoin().getNickName());

        coin.setNickName(chainPayload.getCoinNickName());
        coin.setMaxAmount(chainPayload.getMaxAmount());
        coin.setDecimalPoints(chainPayload.getDecimalPoints());
        coin.setName(chainPayload.getCoinName());

        chain.setCoin(coin);

        List<String> accountsAddress = new ArrayList<>();

        chainPayload.getAccounts().forEach(genesisAccount -> {
            totalValue[0] = totalValue[0].add(genesisAccount.getValue());
        });

        if(!totalValue[0].equals(chainPayload.getMaxAmount())) {
            log.info("Amounts in accounts are not matching the total maximum amount!");
            return;
        }

        //Genesis transaction list
        List<TransactionBaseModel> transactions = new ArrayList<>();

        for (GenesisAccount accounts : chainPayload.getAccounts()){
            AccountModel account = GlacierUtils.generateGenesisAccount(accounts.getValue());
            String address = account.getAddress().getHexEncoded();

            accountsAddress.add(address);

            //Add the account to the blockchain
            storage.addAccount(account);

            //Generate genesis transaction then compute genesis block
//            TransactionBaseModel transaction = GlacierUtils.generateGenesisTransactions(account);
//
//            transactions.add(transaction);
        }

//        BaseBlockModel genesisBlock = new BaseBlockModel();
//        genesisBlock.setVersion("0x01");
//        genesisBlock.setChainWork(chain.getUniqueId());
//        genesisBlock.setTimeStamp(String.valueOf(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)));
//        genesisBlock.setConfirmations(1);
//        genesisBlock.setHash();
//        genesisBlock.setHeight(0);
//        genesisBlock.setSize(3);
//        genesisBlock.setMiner();
//        genesisBlock.setDifficulty();
//        genesisBlock.setTotalDifficulty();
//        genesisBlock.setMarkleRoot();
//        genesisBlock.setNonce();
//        genesisBlock.setPreviousHash();

        chain.setAccounts(accountsAddress);
        chain.setGenesis(accountsAddress);

        storage.setChain(chain);
    }
}
