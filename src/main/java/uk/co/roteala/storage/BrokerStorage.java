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
import java.util.*;

@Slf4j
@Component
public class BrokerStorage {

    private GlacierBrokerConfigs configs = new GlacierBrokerConfigs();

    private RocksDB storage;


    public void start() throws RocksDBException {
        Options options = new Options();
        options.setCreateIfMissing(true);

        if(System.getProperty("os.name").contains("linux")){
            this.storage = RocksDB.open(options, System.getProperty("user.home")+"/."+configs.getName());
        } else {
            storage = RocksDB.open(options, configs.getStoragePath());
        }

        log.info("Storage open!");
    }

    public void addPeer(Peer peer) throws RocksDBException {
        //try {
            final byte[] serializedKey = SerializationUtils.serialize(peer.getAddress());

            final byte[] serializedObject = SerializationUtils.serialize(peer);
            log.info("Peer added!");
            storage.put(serializedKey, serializedObject);
            storage.flush(new FlushOptions().setWaitForFlush(true));
//        }catch (Exception e) {
//            log.error("Error:{}", e.getMessage());
//            log.error("Failing adding new peer!");
//        }

    }

    public byte[] getPeer(String key) throws RocksDBException {
        byte[] peerSerialized = null;
        try {
            final byte[] serializedByteKey = SerializationUtils.serialize(key);

            peerSerialized = storage.get(serializedByteKey);
        } catch (Exception e) {
            log.error("Failing retrieving:{}", key);
        }
        return peerSerialized;
    }

    public void deletePeer(byte[] key) throws RocksDBException {
        this.storage.delete(key);
    }

    public Set<String> getPeers(@Nullable boolean random) {
        List<String> peers = new ArrayList<>();

        RocksIterator iterator = storage.newIterator();

        for(iterator.seekToFirst(); iterator.isValid(); iterator.next()) {

            Peer peer = (Peer) SerializationUtils.deserialize(iterator.value());

            peers.add(peer.getAddress());
        }



        //Return random 50 peers
        if(random){
            Collections.shuffle(peers);

            if(peers.size() <= 51){
                return new HashSet<>(peers);
            } else {
                return new HashSet<>(peers.subList(0, 51));
            }
        } else {
            return new HashSet<>(peers);
        }


    }

}
