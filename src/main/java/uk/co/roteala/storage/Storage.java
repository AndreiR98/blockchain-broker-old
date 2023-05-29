package uk.co.roteala.storage;

import lombok.extern.slf4j.Slf4j;
import org.rocksdb.DbPath;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import uk.co.roteala.configs.GlacierBrokerConfigs;
import uk.co.roteala.utils.GlacierUtils;

import java.nio.file.Path;
import java.util.List;

@Slf4j
@Component
public class Storage {

    private final GlacierBrokerConfigs configs = new GlacierBrokerConfigs();

    @Bean
    public RocksDB startStorage() throws RocksDBException {
        if(configs.getPeersPath().mkdirs()) log.info("Creating peers directory:{}", configs.getPeersPath().getAbsolutePath());
        try{
            Options options = new Options();
            options.setCreateIfMissing(true);

            final DbPath pathDb = new DbPath(configs.getPeersPath().toPath(), 1L);

//            options.setDbLogDir(configs.getPeersPath().getAbsolutePath()+"/logs");
//            options.setDbPaths(List.of(pathDb));

            log.info("Open storage at:{}", configs.getPeersPath().getAbsolutePath());

            return RocksDB.open(options, configs.getPeersPath().getAbsolutePath());
        }catch (Exception e) {
            log.error("Unable to open storage at:{}",configs.getPeersPath().getAbsolutePath());
            throw new RocksDBException("Exception:" + e);
        }
    }
}
