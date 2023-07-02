package uk.co.roteala.storage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDB;

import java.util.List;

@AllArgsConstructor
public class StorageHandlers {

    @Getter
    private RocksDB database;
    @Getter
    private List<ColumnFamilyHandle> handlers;
}
