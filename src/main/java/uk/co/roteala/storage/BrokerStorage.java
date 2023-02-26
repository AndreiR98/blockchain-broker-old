package uk.co.roteala.storage;


import lombok.extern.slf4j.Slf4j;
import org.rocksdb.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;
import uk.co.roteala.common.*;
import uk.co.roteala.configs.GlacierBrokerConfigs;
import uk.co.roteala.net.Peer;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class BrokerStorage {

    private GlacierBrokerConfigs configs = new GlacierBrokerConfigs();

    private RocksDB storage;

    @Bean
    public void start() throws RocksDBException {
        Options options = new Options();
        options.setCreateIfMissing(true);

        log.info("System:{}", System.getProperty("os.name"));
        log.info("Test:{}", configs.getStoragePath());

        if(System.getProperty("os.name").contains("linux")){
            this.storage = RocksDB.open(options, System.getProperty("user.home")+"/."+configs.getName());
        } else {
            storage = RocksDB.open(options, configs.getStoragePath());
        }

        log.info("System:{}", System.getProperty("os.name"));


        log.info("Storage open!");
    }

    public void addPeer(Peer peer) throws RocksDBException {
        final byte[] serializedKey = SerializationUtils.serialize(peer.getAddress());

        final byte[] serializedObject = SerializationUtils.serialize(peer);
        log.info("Peer added!");
        storage.put(serializedKey, serializedObject);
        storage.flush(new FlushOptions().setWaitForFlush(true));
    }

    public byte[] getPeer(String key) throws RocksDBException {
        final byte[] serializedByteKey = SerializationUtils.serialize(key);

        return storage.get(serializedByteKey);
    }

    public void deletePeer(byte[] key) throws RocksDBException {
        this.storage.delete(key);
    }

    public List<Peer> getPeers(@Nullable boolean random) {
        List<Peer> peers = new ArrayList<>();

        RocksIterator iterator = storage.newIterator();

        for(iterator.seekToFirst(); iterator.isValid(); iterator.next()) {

            Peer peer = (Peer) SerializationUtils.deserialize(iterator.value());

            peers.add(peer);
        }

        log.info("Peers:{}", peers);

        //Return random 50 peers
        if(random){
            Collections.shuffle(peers);

            if(peers.size() <= 50){
                return peers;
            } else {
                return peers.subList(0, 50);
            }
        } else {
            return peers;
        }


    }

}
