package uk.co.roteala.storage;

import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

public interface StorageInterface {
    StorageHandlers getStorageData();

    RocksDB getPeers() throws RocksDBException;

    RocksDB getStateTrie();
}
