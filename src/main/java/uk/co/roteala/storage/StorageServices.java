package uk.co.roteala.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.bouncycastle.util.StoreException;
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


import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class StorageServices {
    @Autowired
    private StorageInterface storages;

    public void addMempool(String key, BaseModel transaction){
        final byte[] serializedKey = key.getBytes();
        final byte[] serializedTransaction = SerializationUtils.serialize(transaction);

        RocksDB.loadLibrary();

        StorageHandlers storage = storages.getMempool();

        try {
            storage.getDatabase().put(storage.getHandlers().get(1), new WriteOptions().setSync(true), serializedKey, serializedTransaction);
            storage.getDatabase().flush(new FlushOptions().setWaitForFlush(true), storage.getHandlers().get(1));
        } catch (Exception e) {
            log.error("Could not add mempool transaction:{}", e.getMessage());
        }
    }

    public PseudoTransaction getMempoolTransaction(String key) {
        final byte[] serializedKey;

        RocksDB.loadLibrary();

        PseudoTransaction transaction = null;

        StorageHandlers storage = storages.getMempool();

        if(key != null) {
            serializedKey = key.getBytes();

            try {
                if(storage.getDatabase().get(storage.getHandlers().get(1), serializedKey) == null) {
                    throw new TransactionException(StorageErrorCode.TRANSACTION_NOT_FOUND);
                }

                transaction = SerializationUtils.deserialize(
                        storage.getDatabase().get(storage.getHandlers().get(1), serializedKey));

            } catch (Exception e){
                log.error("Could not add mempool transaction:{}", e.getMessage());
            }
        }

        return transaction;
    }

    public List<PseudoTransaction> getPseudoTransactionGrouped(long timeWindow) {
        List<PseudoTransaction> returnTransactions = new ArrayList<>();

        RocksDB.loadLibrary();

        try {
            StorageHandlers handlers = storages.getMempool();

            List<PseudoTransaction> withPriority = new ArrayList<>();
            List<PseudoTransaction> withoutPriority = new ArrayList<>();

            final int maxSize = 1024 * 124;


            RocksIterator iterator = handlers.getDatabase()
                    .newIterator(handlers.getHandlers().get(1));

            for(iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                PseudoTransaction transaction = SerializationUtils.deserialize(iterator.value());

                if((transaction != null)
                        && transaction.getStatus() != TransactionStatus.PROCESSED
                        && transaction.getStatus() != TransactionStatus.SUCCESS
                        && transaction.getTimeStamp() <= timeWindow){

                    BigDecimal feesPercentage = transaction.getFees().getFees().getValue()
                            .divide(transaction.getValue().getValue(), 6, RoundingMode.HALF_UP);

                    if(feesPercentage.compareTo(new BigDecimal("1.55")) > 0) {
                        withPriority.add(transaction);
                    } else {
                        withoutPriority.add(transaction);
                    }
                }
            }

            //Order the non-priority by time
            withoutPriority.sort(Comparator.comparingLong(PseudoTransaction::getTimeStamp).reversed());
            withPriority.sort(Comparator.comparing(t -> t.getFees().getFees().getValue()));//Compare them by fees in ascending order

            //Add all priority transactions first
            if(!withPriority.isEmpty()){
                returnTransactions.addAll(withPriority);
            }

            int transactionBytesSize = SerializationUtils.serialize((Serializable) returnTransactions).length;

            int index = 0;
            while (index < withoutPriority.size() && transactionBytesSize < maxSize) {
                PseudoTransaction transaction = withoutPriority.get(index);
                returnTransactions.add(transaction);

                // Update transactionBytesSize with the size of the newly added transaction
                transactionBytesSize += SerializationUtils.serialize((Serializable) transaction).length;

                index++;
            }
            log.info("Pseudo:{}", returnTransactions);
        } catch (Exception e) {
            log.info("Eror:{}", e.getMessage());
        }

        //Return 1MB list of transactions
        return returnTransactions;
    }

    public void deleteMempoolTransaction(String key) {
        final byte[] serializedKey = key.getBytes();

        RocksDB.loadLibrary();

        StorageHandlers storage = storages.getMempool();

        try {
            storage.getDatabase()
                    .delete(storage.getHandlers().get(1), serializedKey);
        } catch (Exception e) {
            log.error("Error:{}", e.getMessage());
        }
    }

    /**
     *
     * Query all transaction that are VALIDATED and in the time window provided by the block
     *
     *
     * @return PseudoTransaction
     * */
    public List<PseudoTransaction> getMinableTransactions(long endTime, long startTime){
        RocksDB.loadLibrary();

        List<PseudoTransaction> pseudoTransactions = new ArrayList<>();

        try {
            StorageHandlers handlers = storages.getMempool();

            RocksIterator iterator = handlers.getDatabase()
                    .newIterator(handlers.getHandlers().get(1));

            for(iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                PseudoTransaction transaction = SerializationUtils.deserialize(iterator.value());

                //Gets them by time window and order them by fees and value
                if((transaction != null)
                        && (transaction.getStatus() == TransactionStatus.VALIDATED)
                        && (transaction.getTimeStamp() <= endTime && transaction.getTimeStamp() > startTime)){
                    pseudoTransactions.add(transaction);
                }
            }
        } catch (Exception e) {
            throw new StorageException(StorageErrorCode.MEMPOOL_NOT_FOUND);
        }

        return pseudoTransactions;
    }

    public void deleteMempoolBlocksAtIndex(Integer index) {
        RocksDB.loadLibrary();

        List<Block> pseudoBlocks = getPseudoBlocks();

        StorageHandlers storage = storages.getMempool();

        try {
            List<Block> filteredBlocks = pseudoBlocks.stream()
                    .filter(block -> block.getHeader().getIndex().equals(index))
                    .collect(Collectors.toList());

            for (Block filteredBlock : filteredBlocks) {
                final byte[] serializedKey = filteredBlock.getHash().getBytes();
                storage.getDatabase().delete(storage.getHandlers().get(2), serializedKey);
            }
        } catch (Exception e) {
            log.error("Could not delete any blocks:{}", e.getMessage());
        }
    }

    public void addTransaction(String key, Transaction data)  {
        final byte[] serializedKey = key.getBytes();
        final byte[] serializedData = SerializationUtils.serialize(data);

        RocksDB.loadLibrary();

        StorageHandlers storage = storages.getStorageData();

        try {
            storage.getDatabase().put(storage.getHandlers().get(1), serializedKey, serializedData);
            storage.getDatabase().flush(new FlushOptions().setWaitForFlush(true), storage.getHandlers().get(1));
        } catch (Exception e) {
            log.error("Could not add transaction:{}", e.getMessage());
        }
    }

    public Block getBlockByIndex(String strIndex) {
        final byte[] serializedKey = strIndex.getBytes();

        Block block = null;

        RocksDB.loadLibrary();

        try {

            if(!BlockchainUtils.isInteger(strIndex)){
                throw new NumberFormatException();
            }

            StorageHandlers handlers = storages.getStorageData();

            if(handlers.getDatabase().get(handlers.getHandlers().get(2), serializedKey) == null) {
                throw new BlockException(StorageErrorCode.BLOCK_NOT_FOUND);
            }

            block = SerializationUtils.deserialize(handlers.getDatabase().get(handlers.getHandlers().get(2), serializedKey));

        }catch (Exception e) {
            log.error("Could not get blocks by index:{}", e.getMessage());
        }

        return block;
    }

    public Block getPseudoBlockByHash(String hash) {
        final byte[] serializedKey = hash.getBytes();

        Block block = null;

        RocksDB.loadLibrary();

        try {

            StorageHandlers handlers = storages.getMempool();

            if(handlers.getDatabase().get(handlers.getHandlers().get(2), serializedKey) == null) {
                block = null;
            }

            block = SerializationUtils.deserialize(handlers.getDatabase().get(handlers.getHandlers().get(2), serializedKey));

        }catch (Exception e) {
            log.error("Could not get pseudo block by hash:{}", e.getMessage());
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

                if(iterator.value() == null) {
                    throw new BlockException(StorageErrorCode.BLOCK_NOT_FOUND);
                }

                if(Objects.equals(blockInStorage.getHash(), hash)) {
                    block = (Block) SerializationUtils.deserialize(iterator.value());
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Could not get block by hash:{}", e.getMessage());
        }

        return block;
    }

    public Transaction getTransactionByKey(String key) {
        final byte[] serializedKey;

        RocksDB.loadLibrary();

        Transaction transaction = null;

        StorageHandlers storage = storages.getStorageData();

        try {
            if(key == null) {
                throw new TransactionException(StorageErrorCode.TRANSACTION_NOT_FOUND);
            }

            serializedKey = key.getBytes();

            if(storage.getDatabase().get(storage.getHandlers().get(1), serializedKey) == null) {
                throw new TransactionException(StorageErrorCode.TRANSACTION_NOT_FOUND);
            }

            transaction = SerializationUtils.deserialize(storage.getDatabase().get(storage.getHandlers().get(1), serializedKey));

        } catch (Exception e) {
            log.error("Could not get transaction:{}", e.getMessage());
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

            storage.getDatabase().flush(new FlushOptions().setWaitForFlush(true), storage.getHandlers().get(2));
        } catch (Exception e) {
            log.error("Error:{}", e.getMessage());
        }
    }

    public void addBlockMempool(String key, Block block) {
        final byte[] serializedKey = key.getBytes();
        final byte[] serializedData = SerializationUtils.serialize(block);

        RocksDB.loadLibrary();

        StorageHandlers storage = storages.getMempool();

        try {
            //storage.getDatabase().put(storage.getHandlers().get(2), serializedKey, serializedData);

            storage.getDatabase().put(storage.getHandlers().get(2), serializedKey, serializedData);
            storage.getDatabase().flush(new FlushOptions().setWaitForFlush(true));


        } catch (Exception e) {
            log.error("Error adding new block:{}", e.getMessage());
        }
    }

    public void addPeer(Peer peer) {
        //final byte[] serializedKey = (peer.getAddress() + peer.getPort()).getBytes();
        final byte[] serializedPeer = SerializationUtils.serialize(peer);

        try {
            RocksDB storage = storages.getPeers();

            final byte[] serializedKey = peer.getAddress().getBytes();

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
            StorageHandlers handlers = storages.getMempool();

            RocksIterator iterator = handlers.getDatabase()
                    .newIterator(handlers.getHandlers().get(1));

            for(iterator.seekToFirst(); iterator.isValid(); iterator.next()) {

                if(iterator.value() == null) {
                    throw new TransactionException(TransactionErrorCode.TRANSACTION_NOT_FOUND);
                }

                PseudoTransaction transaction = SerializationUtils.deserialize(iterator.value());

                if(transaction != null && transaction.getStatus() == TransactionStatus.VALIDATED) {
                    pseudoTransactions.add(transaction);
                }
            }
        } catch (Exception e) {
            log.error("Could not get pseudo transactions:{}", e.getMessage());
        }

        return pseudoTransactions;
    }

    public List<Block> getPseudoBlocks() {
        List<Block> pseudoBlocks = new ArrayList<>();

        try {
            StorageHandlers handlers = storages.getMempool();

            RocksIterator iterator = handlers.getDatabase()
                    .newIterator(handlers.getHandlers().get(2));

            for(iterator.seekToFirst(); iterator.isValid(); iterator.next()) {

                if(iterator.value() == null) {
                    throw new StorageException(StorageErrorCode.BLOCK_NOT_FOUND);
                }

                Block block = SerializationUtils.deserialize(iterator.value());

                if(block != null) {
                    pseudoBlocks.add(block);
                }
            }
        } catch (Exception e) {
            log.error("Could not get in memory blocks:{}", e.getMessage());
        }

        return pseudoBlocks;
    }

    public ChainState getStateTrie() {
        final byte[] key = "stateChain".getBytes();

        ChainState state = null;

        RocksDB storage = storages.getStateTrie();

        try {

            if(storage.get(key) == null) {
                throw new StorageException(StorageErrorCode.STATE_NOT_FOUND);
            }

            state = SerializationUtils.deserialize(storage.get(key));

        } catch (Exception e){
            log.error("Could not get state trie:{}", e.getMessage());
        }

        return state;
    }

    public void updateStateTrie(ChainState newState) {
        final byte[] key = "stateChain".getBytes();

        RocksDB storage = storages.getStateTrie();

        try {
            if(newState == null) {
                throw new StorageException(StorageErrorCode.STATE_NOT_FOUND);
            }

            storage.put("stateChain".getBytes(), SerializationUtils.serialize(newState));
            storage.flush(new FlushOptions().setWaitForFlush(true));
        } catch (Exception e){
            log.info("Could not get state trie:{}", e);
        }
    }

    public void addStateTrie(ChainState state, List<AccountModel> accounts) {
        final byte[] key = "stateChain".getBytes();

        RocksDB storage = storages.getStateTrie();

        try {
            storage.put(key, SerializationUtils.serialize(state));
            storage.flush(new FlushOptions().setWaitForFlush(true));

            for(AccountModel accountModel : accounts) {
                storage.put(accountModel.getAddress().getBytes(), SerializationUtils.serialize(accountModel));
                storage.flush(new FlushOptions().setWaitForFlush(true));
            }
        } catch (Exception e){
            log.error("Could not add state chain:{}", e.getMessage());
        }
    }
    public void updateAccount(AccountModel account) {
        RocksDB storage = storages.getStateTrie();

        try {
            storage.put(new WriteOptions().setSync(true), account.getAddress().getBytes(), SerializationUtils.serialize(account));
            storage.flush(new FlushOptions().setWaitForFlush(true));
        } catch (Exception e) {
            log.error("Could not update account:{}", e.getMessage());
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
            log.error("Error while retrieving account:{}", e.getMessage());
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
