package uk.co.roteala.processor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.springframework.lang.Nullable;
import org.springframework.util.SerializationUtils;
import uk.co.roteala.common.BaseBlockModel;
import uk.co.roteala.common.BaseEmptyModel;
import uk.co.roteala.common.TransactionBaseModel;
import uk.co.roteala.common.events.*;
import uk.co.roteala.common.events.enums.ActionTypes;
import uk.co.roteala.common.events.enums.SubjectTypes;
import uk.co.roteala.common.events.transformer.ScriptTransformer;
import uk.co.roteala.common.events.transformer.ScriptTransformerSupplier;
import uk.co.roteala.net.Peer;
import uk.co.roteala.storage.StorageServices;

import java.util.*;


@Slf4j
@RequiredArgsConstructor
public class EventTransformer implements ScriptTransformerSupplier<Event, List<BaseEmptyModel>> {

    private final StorageServices storages;

    @Override
    public ScriptTransformer<Event, List<BaseEmptyModel>> get() {
        return new Transformer();
    }

    @RequiredArgsConstructor
    private class Transformer implements ScriptTransformer<Event, List<BaseEmptyModel>> {

        @Override
        public void init() {
            //No need yet
        }

        @Override
        public List<BaseEmptyModel> transform(Event event) {

            final ActionTypes action = event.getAction();
            final SubjectTypes subject = event.getSubject();
            final String extra = event.getExtra();

            List<BaseEmptyModel> data = new ArrayList<>();

            if(action == ActionTypes.FETCH) {
                if(subject == SubjectTypes.PEER){
                    //fetch 50 peers and sent it
                    try {
                        Set<String> peersSet  = getPeers(true);
                        List<Peer> peerList = new ArrayList<>();

                        peersSet.forEach(peer -> {
                            Peer p = new Peer();
                            p.setAddress(peer);
                            p.setActive(true);
                            p.setLastTimeSeen(System.currentTimeMillis());
                            data.add(p);
                        });

                    } catch (RocksDBException ex) {
                        throw new RuntimeException(ex);
                    }
                }

                if (subject == SubjectTypes.TRANSACTION && Objects.equals(extra, "genesis")) {
                    //send genesis
                    final byte[] txKey = "24e3eaae9e36792df7f2487fd7158ef7878d6f36966b5286c982cc71274d3ba2".getBytes();

                    try {
                        TransactionBaseModel tx = (TransactionBaseModel) storages.get(txKey);
                        data.add(tx);
                    } catch (RocksDBException e) {
                        throw new RuntimeException(e);
                    }

                }

                if (subject == SubjectTypes.BLOCK && Objects.equals(extra, "genesis")) {
                    //send genesis
                    final byte[] blockKey = "0".getBytes();

                    try {
                        BaseBlockModel block = (BaseBlockModel) storages.get(blockKey);
                        data.add(block);
                    } catch (RocksDBException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            log.info("Response:{}", data);
            return data;
        }

        @Override
        public void close() {
            //No need
        }
    }

    private Set<String> getPeers(@Nullable boolean random) throws RocksDBException {
        List<String> peers = new ArrayList<>();

        RocksIterator iterator = storages
                .getRaw()
                .newIterator();

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
