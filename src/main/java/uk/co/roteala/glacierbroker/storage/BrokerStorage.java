package uk.co.roteala.glacierbroker.storage;


import lombok.extern.slf4j.Slf4j;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.SerializationUtils;
import uk.co.roteala.common.*;
import uk.co.roteala.glacierbroker.config.GlacierBrokerConfigs;
import uk.co.roteala.glacierbroker.models.ChainPayload;
import uk.co.roteala.utils.GlacierUtils;

import java.math.BigDecimal;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;

@Slf4j
@Configuration
public class BrokerStorage {

    @Autowired
    private GlacierBrokerConfigs configs;

    private RocksDB storage;

    @Bean
    public void initStorage() throws RocksDBException, NoSuchAlgorithmException {
        Options options = new Options();
        options.setCreateIfMissing(true);
        //options.setSkipStatsUpdateOnDbOpen(true);

        log.info("Open storage at path:{}", configs.getStoragePath());

        storage = RocksDB.open(options, configs.getStoragePath());
    }

    /**
     * Create the chain if non existant
     * */
    public void initChain(ChainPayload chainPayload) throws NoSuchAlgorithmException, RocksDBException {
        //TO DO: Modify the uniquness
        StringBuilder builder = new StringBuilder();
        builder.append(chainPayload.getBuilder());
        builder.append(chainPayload.getName());
        builder.append(chainPayload.getType().getCode());
        builder.append(LocalTime.now().toString());

        String deterministicKey = GlacierUtils.generateSHA256Digest(SerializationUtils.serialize(builder), 32);

        final byte[] serializedDeterministicKey = SerializationUtils.serialize(deterministicKey);

        if(storage.get(serializedDeterministicKey) == null) {
            //Generate new chain
            Chain chain = new Chain();
            AccountModel genesisAccount = new AccountModel();
            AddressBaseModel genesisAddress = new AddressBaseModel();

            //Check is privateKey empty
            if(chainPayload.getPrivateKey() == null || chainPayload.getPrivateKey().length() < 64){
                genesisAddress.setHexEncoded(GlacierUtils.generateECPrivateKey());
            } else {
                genesisAddress.setHexEncoded(GlacierUtils.generatePublicAddress(chainPayload.getPrivateKey()));
            }

            genesisAccount.setPassword(chainPayload.getPassword());
            genesisAccount.setBalance(new BigDecimal(GlacierUtils.generateDecimalPoints(chainPayload.getCoin().getDecimalPoints())));
            genesisAccount.setTimeStamp(String.valueOf(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)));
            genesisAccount.setAddress(genesisAddress);

            chain.setChainType(ChainType.BROKERS);
            chain.setStatus(ChainStatus.ACTIVE);
            chain.setName(chainPayload.getName());
            chain.setUniqueId(deterministicKey);
            chain.setCoin(chainPayload.getCoin());
        }
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

        return transaction;
    }

    /**
     * Create add new transaction to blockchain
     * */
    public void createTransaction(TransactionBaseModel transaction) throws RocksDBException {
        final byte[] serializedKey = SerializationUtils.serialize(transaction.getHash());

        final byte[] serializedTransaction = SerializationUtils.serialize(transaction);

        this.storage.put(serializedKey, serializedTransaction);
    }

}
