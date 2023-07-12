package uk.co.roteala.storage;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

@Data
@RequiredArgsConstructor
public class StorageData implements StorageInterface {
    private final StorageHandlers storageData;

    private final RocksDB storagePeers;

    private final RocksDB mempool;

    private final RocksDB stateTrie;



    @Override
    public StorageHandlers getStorageData() {
        return storageData;
    }

    @Override
    public RocksDB getPeers() {
        return storagePeers;
    }

    @Override
    public RocksDB getStateTrie() {
        return stateTrie;
    }

    @Override
    public RocksDB getMempool() {
        return mempool;
    }
}
