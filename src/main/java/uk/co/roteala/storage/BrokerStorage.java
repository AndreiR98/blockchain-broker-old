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
import uk.co.roteala.utils.GlacierUtils;

import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Slf4j
@Component
public class BrokerStorage {

    private GlacierBrokerConfigs configs = new GlacierBrokerConfigs();

    private RocksDB storage;


    public void start() throws RocksDBException {
        try{
            Options options = new Options();
            options.setCreateIfMissing(true);

            String path = null;

            if(GlacierUtils.getSystem()){
                path = configs.getRootWindows()+configs.getPeersPath();
            } else {
                path = configs.getRootLinux()+configs.getPeersPath();
            }

            final DbPath pathDb = new DbPath(Path.of(path), 1L);

            log.info("P:{}", path);

            options.setDbLogDir(path+"/logs");
            options.setDbPaths(List.of(pathDb));

            this.storage = RocksDB.open(options, path);

            log.info("Storage open!");
        }catch (Exception e) {
            log.error("Unable to open storage:");
            throw new RocksDBException("");
        }
    }

    public void addPeer(Peer peer){
        try {
            final byte[] serializedKey = SerializationUtils.serialize(peer.getAddress());

            final byte[] serializedObject = SerializationUtils.serialize(peer);
            this.storage.put(serializedKey, serializedObject);
            this.storage.flush(new FlushOptions().setWaitForFlush(true));
        }catch (Exception e) {
            log.error("Failing adding new peer!");
        }
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

    public void updatePeerStatus(String key) {
        final byte[] serializedKey = SerializationUtils.serialize(key);

        try {
            final byte[] peerSerialized = this.storage.get(serializedKey);

            if(peerSerialized != null) {
                Peer peer = (Peer) SerializationUtils.deserialize(peerSerialized);

                peer.setActive(false);

                addPeer(peer);
            }
        }catch (Exception e) {
            log.error("Peer status cannot be updated!");
        }
    }

    public Set<String> getPeers(@Nullable boolean random) {
        List<String> peers = new ArrayList<>();

        RocksIterator iterator = storage.newIterator();

        for(iterator.seekToFirst(); iterator.isValid(); iterator.next()) {

            Peer peer = (Peer) SerializationUtils.deserialize(iterator.value());

            if(peer.isActive()){
                peers.add(peer.getAddress());
            }
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
