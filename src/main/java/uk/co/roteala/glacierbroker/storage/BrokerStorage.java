package uk.co.roteala.glacierbroker.storage;


import lombok.extern.slf4j.Slf4j;
import org.rocksdb.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.SerializationUtils;
import uk.co.roteala.common.*;
import uk.co.roteala.glacierbroker.config.GlacierBrokerConfigs;
import uk.co.roteala.glacierbroker.models.ChainPayload;
import uk.co.roteala.utils.GlacierUtils;

import java.math.BigDecimal;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;

@Slf4j
@Configuration
public class BrokerStorage {

    @Autowired
    private GlacierBrokerConfigs configs;

    private RocksDB storage;

    private RocksDB peers;

    @Bean
    public void initStorage() throws RocksDBException, NoSuchAlgorithmException {
        final long writeBufferSize = 24 * 1024 * 1024;
        Options options = new Options();
        options.setCreateIfMissing(true);
        options.setWriteBufferSize(writeBufferSize);
        options.setAllowConcurrentMemtableWrite(true);
        options.setAllowMmapReads(true);
        options.setAllowMmapWrites(true);
        options.setAtomicFlush(true);


        log.info("Open storage at path:{}", configs.getStoragePath());

        storage = RocksDB.open(options, configs.getStoragePath()+"/chain");

        peers = RocksDB.open(options, configs.getStoragePath()+"/peers");
    }

    public Chain getChain() throws RocksDBException {

        return (Chain) SerializationUtils.deserialize(this.storage.get(SerializationUtils.serialize("CHAIN")));
    }

    public void setChain(Chain chain) throws RocksDBException {
        final byte[] serializedChain = SerializationUtils.serialize(chain);

        final byte[] serializedKey = SerializationUtils.serialize("CHAIN");

        this.storage.put(serializedKey, serializedChain);
        this.storage.flush(new FlushOptions().setWaitForFlush(true));
    }

    /**
     * @param account
     *
     * Add new account to blockchain
     * */
    public void addAccount(AccountModel account) throws RocksDBException {
        final byte[] serializedKey = SerializationUtils.serialize(account.getAddress().getHexEncoded());

        final byte[] serializedAccount = SerializationUtils.serialize(account);

        this.storage.put(serializedKey, serializedAccount);
        this.storage.flush(new FlushOptions().setWaitForFlush(true));
    }

    /**
     * Retrieve the account information from blockchain
     * */
    public AccountModel getAccount(AddressBaseModel address) throws RocksDBException {
        final byte[] serializedKey = SerializationUtils.serialize(address.getHexEncoded());

        AccountModel account = new AccountModel();

        try {
            //Check if account exists in ledger
            if(this.storage.get(serializedKey) != null) {
                account = ((AccountModel) SerializationUtils.deserialize(this.storage.get(serializedKey)));
            }
        } catch (Exception e){
            log.info("Account matching the address:{} doesn't exist", address.getHexEncoded());
        }

        return account;
    }

    /***/
    public BaseBlockModel getBlock(Integer blockNumber) {
        final byte[] serializedKey = SerializationUtils.serialize(blockNumber);

        BaseBlockModel blockModel = new BaseBlockModel();

        try{
            if(this.storage.get(serializedKey) != null) {
                blockModel = ((BaseBlockModel) SerializationUtils.deserialize(this.storage.get(serializedKey)));
            }
        }catch (Exception e){
            log.info("Block does not exists!");
        }

        return blockModel;
    }

    /**
     * Retrieve transaction informations
     * Transaction are found by hash and block number
     * TO DO: Add unique transaciton index for global search in mapper
     * */
    public TransactionBaseModel getTransaction(String txHash) {
        final byte[] serializedKey = SerializationUtils.serialize(txHash);

        TransactionBaseModel transaction = new TransactionBaseModel();

        try {
            if(this.storage.get(serializedKey) != null) {
                transaction = ((TransactionBaseModel) SerializationUtils.deserialize(this.storage.get(serializedKey)));
            }
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }

        ColumnFamilyDescriptor cd = new ColumnFamilyDescriptor();

        return transaction;
    }

    /**
     * Create add new transaction to blockchain
     * */
    public void createTransaction(TransactionBaseModel transaction) throws RocksDBException {
        final byte[] serializedKey = SerializationUtils.serialize(transaction.getHash());

        final byte[] serializedTransaction = SerializationUtils.serialize(transaction);

        this.storage.put(serializedKey, serializedTransaction);
        this.storage.flush(new FlushOptions().setWaitForFlush(true));
    }

}
