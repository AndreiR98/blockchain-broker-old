package uk.co.roteala.storage;

import lombok.extern.slf4j.Slf4j;
import org.rocksdb.DbPath;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import uk.co.roteala.configs.GlacierBrokerConfigs;
import uk.co.roteala.utils.GlacierUtils;

import java.nio.file.Path;
import java.util.List;

@Lazy
@Slf4j
@Component
public class StorageCreatorComponentFactory{
    private GlacierBrokerConfigs configs = new GlacierBrokerConfigs();

    private RocksDB peers;

    private RocksDB tx;

    private RocksDB blocks;

    public StorageCreatorComponentFactory() throws RocksDBException {
        this.peers = initPeersStorage();
        this.blocks = initBlocksStorage();
        this.tx = initTxStorage();
    }

    public RocksDB getTxStorage(){
        return this.tx;
    }

    public RocksDB getBlocksStorage() {
        return this.blocks;
    }

    public RocksDB getStorage() {
        return this.peers;
    }

    private RocksDB initTxStorage() throws RocksDBException {
        log.info("System:{}", GlacierUtils.getSystem());
        try{
            Options options = new Options();
            options.setCreateIfMissing(true);

            String path = null;

            //Implement system check
            if(GlacierUtils.getSystem()){
                //use windows path
                path = configs.getRootLinux()+configs.getTxPath();
            } else {
                path = configs.getRootLinux()+configs.getTxPath();
            }
            final DbPath pathDb = new DbPath(Path.of(path), 1L);

            options.setDbLogDir(path+"/logs");
            options.setDbPaths(List.of(pathDb));

            log.info("Open storage at:{}",path);

            return RocksDB.open(options, path);
        }catch (Exception e) {
            log.error("Unable to open storage at:");
            throw new RocksDBException("");
        }
    }

    private RocksDB initBlocksStorage() throws RocksDBException {
        try{
            Options options = new Options();
            options.setCreateIfMissing(true);

            String path = null;



            //Implement system check
            if(GlacierUtils.getSystem()){
                //use windows path
                path = configs.getRootWindows()+configs.getBlockPath();
            } else {
                path = configs.getRootLinux()+configs.getBlockPath();
            }
            final DbPath pathDb = new DbPath(Path.of(path), 1L);

            options.setDbLogDir(path+"/logs");
            options.setDbPaths(List.of(pathDb));

            log.info("Open storage at:{}",path);

            return RocksDB.open(options, path);
        }catch (Exception e) {
            log.error("Unable to open storage at:");
            throw new RocksDBException("");
        }
    }

    private RocksDB initPeersStorage() throws RocksDBException {
        try{
            Options options = new Options();
            options.setCreateIfMissing(true);

            String path = null;

            //Implement system check
            if(GlacierUtils.getSystem()){
                //use windows path
                path = configs.getRootWindows()+configs.getPeersPath();
            } else {
                path = configs.getRootLinux()+configs.getPeersPath();
            }
            final DbPath pathDb = new DbPath(Path.of(path), 1L);

            options.setDbLogDir(path+"/logs");
            options.setDbPaths(List.of(pathDb));

            log.info("Open storage at:{}",path);

            return RocksDB.open(options, path);
        }catch (Exception e) {
            log.error("Unable to open storage at:");
            throw new RocksDBException("");
        }
    }
}
