package uk.co.roteala.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.FlushOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.SerializationUtils;
import uk.co.roteala.common.BaseEmptyModel;
import uk.co.roteala.net.Peer;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageServices {
    @Autowired
    private RocksDB storage;

    public RocksDB getRaw(){
        return storage;
    }

    public void add(byte[] key, BaseEmptyModel data) throws RocksDBException {
        final byte[] serializedData = SerializationUtils.serialize(data);
        storage.put(key, serializedData);
        storage.flush(new FlushOptions().setWaitForFlush(true));
    }

    public BaseEmptyModel get(byte[] key) throws RocksDBException {
        if(key != null) {
            final byte[] serializedData = storage.get(key);

            if(serializedData != null) {
                return (BaseEmptyModel) SerializationUtils.deserialize(serializedData);
            }
        }

        return null;
    }

    public void delete(byte[] key) throws RocksDBException {
        if(key != null) {
            storage.delete(key);
        }
    }

    public Long count() throws RocksDBException {
        return Long.parseLong(storage.getProperty("rocksdb.estimate-num-keys"));
    }

    public List<Peer> getPeers() {
        List<Peer> peers = new ArrayList<>();

        try{
            RocksIterator iterator = storage.newIterator();

            for (iterator.seekToFirst(); iterator.isValid(); iterator.next()){
                Peer peer = (Peer) SerializationUtils.deserialize(iterator.value());

                if(peer.isActive()) {
                    peers.add(peer);
                }
            }
        } catch (Exception e){
            //
        }

        Collections.shuffle(peers);

        return peers;
    }
}
