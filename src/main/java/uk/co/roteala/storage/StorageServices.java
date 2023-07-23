package uk.co.roteala.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.rocksdb.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import uk.co.roteala.common.*;
import uk.co.roteala.common.Transaction;
import uk.co.roteala.common.monetary.Coin;
import uk.co.roteala.exceptions.BlockException;
import uk.co.roteala.exceptions.StorageException;
import uk.co.roteala.exceptions.TransactionException;
import uk.co.roteala.exceptions.errorcodes.StorageErrorCode;
import uk.co.roteala.exceptions.errorcodes.TransactionErrorCode;
import uk.co.roteala.net.Peer;
import uk.co.roteala.utils.BlockchainUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

@Slf4j
@Service
public class StorageServices {
    @Autowired
    private StorageInterface storages;

    public void addMempool(String key, BaseModel transaction){
        final byte[] serializedKey = key.getBytes();
        final byte[] serializedTransaction = SerializationUtils.serialize(transaction);

        RocksDB.loadLibrary();

        RocksDB storage = storages.getMempool();

        try {
            storage.put(new WriteOptions().setSync(true), serializedKey, serializedTransaction);
            storage.flush(new FlushOptions().setWaitForFlush(true));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public PseudoTransaction getMempoolTransaction(String key) {
        final byte[] serializedKey = key.getBytes();

        RocksDB.loadLibrary();

        RocksDB storage = storages.getMempool();

        PseudoTransaction transaction = null;

        try {
            transaction = SerializationUtils.deserialize(storage.get(serializedKey));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return transaction;
    }

    public void addTransaction(String key, Transaction data)  {
        final byte[] serializedKey = key.getBytes();
        final byte[] serializedData = SerializationUtils.serialize(data);

        RocksDB.loadLibrary();

        StorageHandlers storage = storages.getStorageData();

        try {
            storage.getDatabase().put(storage.getHandlers().get(1), serializedKey, serializedData);
            storage.getDatabase().flush(new FlushOptions().setWaitForFlush(true));
        } catch (Exception e) {
            throw new TransactionException(TransactionErrorCode.TRANSACTION_FAILED);
        }
    }

    public Block getBlockByIndex(String strIndex) {
        Block block = null;
        RocksDB.loadLibrary();
        try {

            if(!BlockchainUtils.isInteger(strIndex)){
                throw new NumberFormatException();
            }

            final byte[] serializedKey = strIndex.getBytes();

            StorageHandlers handlers = storages.getStorageData();

             block = SerializationUtils.deserialize(
                    handlers.getDatabase().get(handlers.getHandlers().get(2), serializedKey));



            if(block == null) {
                throw new BlockException(StorageErrorCode.BLOCK_NOT_FOUND);
            }

        }catch (Exception e) {
            throw new BlockException(StorageErrorCode.BLOCK_NOT_FOUND);
        }

        return block;
    }

    public Block getBlockByHash(String hash) {
        Block block = null;

        try {
            RocksDB.loadLibrary();

            StorageHandlers handlers = storages.getStorageData();

            RocksIterator iterator = handlers.getDatabase().newIterator(handlers.getHandlers().get(2));

            for(iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                Block blockInStorage = SerializationUtils.deserialize(iterator.value());

                if(Objects.equals(blockInStorage.getHash(), hash)) {
                    block = (Block) SerializationUtils.deserialize(iterator.value());
                    break;
                }
            }
        } catch (Exception e) {
            throw new BlockException(StorageErrorCode.BLOCK_NOT_FOUND);
        }

        return block;
    }

    public Transaction getTransactionByKey(String key) {
        final byte[] serializedKey;

        RocksDB.loadLibrary();

        Transaction transaction = null;

        StorageHandlers storage = storages.getStorageData();

        if(key != null) {
            serializedKey = key.getBytes();

            try {
                transaction = (Transaction) SerializationUtils.deserialize(
                        storage.getDatabase().get(storage.getHandlers().get(1), serializedKey));

                if(transaction == null) {
                    log.error("Failed to retrieve transaction with hash:{}", key);
                    throw new TransactionException(StorageErrorCode.TRANSACTION_NOT_FOUND);
                }
            } catch (Exception e){
                new TransactionException(StorageErrorCode.TRANSACTION_NOT_FOUND);
            }
        }

        return transaction;
    }

    public void addBlock(String key, Block block, boolean toAppend) {
        final byte[] serializedKey = key.getBytes();
        final byte[] serializedData = SerializationUtils.serialize(block);

        RocksDB.loadLibrary();

        StorageHandlers storage = storages.getStorageData();

        try {
            storage.getDatabase().put(storage.getHandlers().get(2), serializedKey, serializedData);

            if(toAppend) {
                storage.getDatabase().put(storage.getHandlers().get(2), new WriteOptions().setSync(true), serializedKey, serializedData);
                storage.getDatabase().flush(new FlushOptions().setWaitForFlush(true));
            }

        } catch (Exception e) {
            throw new StorageException(StorageErrorCode.STORAGE_FAILED);
        }
    }

    public void addPeer(byte[] serializedKey, Peer peer) {
        //final byte[] serializedKey = (peer.getAddress() + peer.getPort()).getBytes();
        final byte[] serializedPeer = SerializationUtils.serialize(peer);

        try {
            RocksDB storage = storages.getPeers();

            if(serializedKey == null) {
                log.error("Failed to add, key:{}", peer.getAddress());
            }

            if(serializedPeer == null) {
                log.error("Failed to add, peer:{}", peer);
                new RocksDBException("Failed due to peer issue");
            }

            storage.put(serializedKey, serializedPeer);
            storage.flush(new FlushOptions().setWaitForFlush(true));
        } catch (RocksDBException e) {
            log.error("Failed to store peer"+ serializedPeer + e);
            //throw new RocksDBException("Error");
        }
    }

    public Peer getPeer(byte[] key) throws RocksDBException {
        if(key != null) {
            final byte[] serializedData = storages.getPeers().get(key);

            if(serializedData != null) {
                return (Peer) SerializationUtils.deserialize(serializedData);
            }
        }

        return null;
    }

    public List<Peer> getPeers() {
        List<Peer> peers = new ArrayList<>();

        try{
            RocksIterator iterator = storages.getPeers().newIterator();

            for (iterator.seekToFirst(); iterator.isValid(); iterator.next()){
                Peer peer = (Peer) SerializationUtils.deserialize(iterator.value());

                if(peer.isActive()) {
                    peers.add(peer);
                }
            }
        } catch (Exception e){
            //
        }

        Collections.shuffle(peers);

        return peers;
    }

    public List<PseudoTransaction> getPseudoTransactions() {
        List<PseudoTransaction> pseudoTransactions = new ArrayList<>();

        try {
            RocksIterator iterator = storages.getMempool().newIterator();

            for(iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                PseudoTransaction transaction = SerializationUtils.deserialize(iterator.value());

                if(transaction != null) {
                    pseudoTransactions.add(transaction);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return pseudoTransactions;
    }

    public ChainState getStateTrie() {
        final byte[] key = "stateChain".getBytes();

        ChainState state = null;

        RocksDB storage = storages.getStateTrie();

        try {
            state = (ChainState) SerializationUtils.deserialize(storage.get(key));

            if(state == null) {
                new Exception("Failed to retrieve state chain!");
            }
        } catch (Exception e){
            new Exception("Storage failed to retrieve state chain:"+ e);
        }

        return state;
    }

    public void updateStateTrie(ChainState newState) {
        final byte[] key = "stateChain".getBytes();

        ChainState state = null;

        RocksDB storage = storages.getStateTrie();

        try {
            state = (ChainState) SerializationUtils.deserialize(storage.get(key));

            if(state == null) {
                throw new StorageException(StorageErrorCode.STATE_NOT_FOUND);
            }

            state.setTarget(newState.getTarget());
            state.setLastBlockIndex(newState.getLastBlockIndex());
            storage.put("stateChain".getBytes(), SerializationUtils.serialize(state));
            storage.flush(new FlushOptions().setWaitForFlush(true));
        } catch (Exception e){
            log.info("Error:{}", e);
            throw new StorageException(StorageErrorCode.STATE_NOT_FOUND);
        }
    }

    public void addStateTrie(ChainState state, List<AccountModel> accounts) {
        final byte[] key = "stateChain".getBytes();

        RocksDB storage = storages.getStateTrie();

        try {
            storage.put(key, SerializationUtils.serialize(state));
            storage.flush(new FlushOptions().setWaitForFlush(true));

            accounts.forEach(accountModel -> {
                try {
                    storage.put(accountModel.getAddress().getBytes(), SerializationUtils.serialize(accountModel));
                    storage.flush(new FlushOptions().setWaitForFlush(true));
                } catch (RocksDBException e) {
                    throw new RuntimeException(e);
                }
            });

            //storage.flush(new FlushOptions().setWaitForFlush(true));
        } catch (Exception e){
            new Exception("Storage failed to retrieve state chain:"+ e);
        }
    }
    public void updateAccount(AccountModel account) {
        RocksDB storage = storages.getStateTrie();

        try {
            storage.put(new WriteOptions().setSync(true), account.getAddress().getBytes(), SerializationUtils.serialize(account));
            storage.flush(new FlushOptions().setWaitForFlush(true));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * is needCreate is true, create the account
     * */
    public AccountModel getAccountByAddress(String address) {
        RocksDB storage = storages.getStateTrie();

        AccountModel account = new AccountModel();

        try {
            if(storage.get(address.getBytes()) == null) {
                account = account.empty(address);
            } else {
                account = SerializationUtils.deserialize(storage.get(address.getBytes()));
            }
        } catch (RocksDBException e){
            throw new RuntimeException("Storage failed to retrieve state chain:"+ e);
        }

        return account;
    }

    public AccountModel addNewAccount(String address) {
        RocksDB storage = storages.getStateTrie();

        AccountModel receiverAccount = new AccountModel();

            receiverAccount.setAddress(address);
            receiverAccount.setBalance(Coin.ZERO);
            receiverAccount.setNonce(0);
            receiverAccount.setInboundAmount(Coin.ZERO);
            receiverAccount.setOutboundAmount(Coin.ZERO);

            try {
                storage.put(new WriteOptions().setSync(true), receiverAccount.getAddress().getBytes(), SerializationUtils.serialize(receiverAccount));
                storage.flush(new FlushOptions().setWaitForFlush(true));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }


        return receiverAccount;
    }
}
