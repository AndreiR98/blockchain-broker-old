package uk.co.roteala.storage;

import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksDBException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Component
public class StorageCreatorComponent implements StoragesCreatorComponent {

    @Autowired
    private StorageCreatorComponentFactory storageCreatorComponentFactory;
    @Override
    public StorageComponent mempool() throws RocksDBException {
        return null;
    }

    @Override
    public StorageComponent wallet() throws RocksDBException {
        return null;
    }

    @Override
    public StorageComponent tx() throws RocksDBException {
        return new StorageComponent(storageCreatorComponentFactory.getTxStorage());

    }

    @Override
    public StorageComponent blocks() throws RocksDBException {
        return new StorageComponent(storageCreatorComponentFactory.getBlocksStorage());
    }

    @Override
    public StorageComponent peers(){
        return new StorageComponent(storageCreatorComponentFactory.getStorage());
    }

    @Override
    public StorageComponent chainState() throws RocksDBException {
        return null;
    }
}
