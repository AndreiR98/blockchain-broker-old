package uk.co.roteala.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.co.roteala.configs.GlacierBrokerConfigs;


import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class Storage {

    private final GlacierBrokerConfigs configs = new GlacierBrokerConfigs();

    @Bean
    public StorageData setUpStorages() throws RocksDBException {
        return new StorageData(initStorageData(), initPeers(), initMempool(), initStateTrie());
    }

    private RocksDB initStateTrie() throws RocksDBException {
        if(configs.getStateTriePath().mkdirs()) log.info("Creating state storage directory:{}",
                configs.getStateTriePath().getAbsolutePath());

        try {
            Options options = new Options();
            options.setCreateIfMissing(true);
            options.setDbLogDir(configs.getStateTriePath().getAbsolutePath());
            log.info("Open storage at:{}", configs.getStateTriePath().getAbsolutePath());

            return RocksDB.open(options, configs.getStateTriePath().getAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to create storage for mempool");
            throw new RocksDBException("Exception:" + e);
        }
    }

    private RocksDB initPeers() throws RocksDBException {
        if(configs.getPeersPath().mkdirs()) log.info("Creating peers storage directory:{}",
                configs.getPeersPath().getAbsolutePath());

        try {
            Options options = new Options();
            options.setCreateIfMissing(true);
            options.setDbLogDir(configs.getPeersPathLogs().getAbsolutePath());
            log.info("Open storage at:{}", configs.getPeersPath().getAbsolutePath());

            return RocksDB.open(options, configs.getPeersPath().getAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to create storage for peers");
            throw new RocksDBException("Exception:" + e);
        }
    }

    private RocksDB initMempool() {
        if(configs.getMempoolPath().mkdirs()) log.info("Creating mempool storage directory:{}",
                configs.getMempoolPath().getAbsolutePath());

        RocksDB.loadLibrary();

        try {
            Options options = new Options();
            options.setCreateIfMissing(true);
            options.setDbLogDir(configs.getMempoolLogsPath().getAbsolutePath());
            log.info("Open storage at:{}", configs.getMempoolLogsPath().getAbsolutePath());

            return RocksDB.open(options, configs.getMempoolPath().getAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private StorageHandlers initStorageData() throws RocksDBException {
        if(configs.getBlocksPath().mkdirs()) log.info("Creating blocks&transaction storage directory:{}",
                configs.getBlocksPath().getAbsolutePath());

        RocksDB.loadLibrary();

        try {
            DBOptions dbOptions = new DBOptions();
            dbOptions.setCreateIfMissing(true);
            dbOptions.setDbLogDir(configs.getBlocksPathLogs().getAbsolutePath());
            dbOptions.setAllowConcurrentMemtableWrite(true);
            dbOptions.setCreateMissingColumnFamilies(true);
            log.info("Open storage at:{}", configs.getBlocksPath().getAbsolutePath());

            ColumnFamilyOptions columnFamilyOptions = new ColumnFamilyOptions();
            columnFamilyOptions.setCompressionType(CompressionType.SNAPPY_COMPRESSION); // Set compression type
            columnFamilyOptions.enableBlobGarbageCollection();

            final List<ColumnFamilyDescriptor> cfDescriptors = new ArrayList<>();
            cfDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, columnFamilyOptions));
            cfDescriptors.add(new ColumnFamilyDescriptor("transactions".getBytes(StandardCharsets.UTF_8), columnFamilyOptions));
            cfDescriptors.add(new ColumnFamilyDescriptor("blocks".getBytes(StandardCharsets.UTF_8), columnFamilyOptions));

            List<ColumnFamilyHandle> cfHandles = new ArrayList<>();

            return new StorageHandlers(RocksDB.open(
                    dbOptions, configs.getBlocksPath().getAbsolutePath(), cfDescriptors, cfHandles),
                    cfHandles);
        } catch (Exception e) {
            log.error("Failed to create storage for blocks&transactions");
            throw new RocksDBException("Exception:" + e);
        }
    }
}
