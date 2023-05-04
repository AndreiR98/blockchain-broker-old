package uk.co.roteala.broker;

import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksDBException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.SerializationUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.DisposableServer;
import reactor.netty.tcp.TcpServer;
import uk.co.roteala.common.*;
import uk.co.roteala.common.events.Event;
import uk.co.roteala.common.monetary.Coin;
import uk.co.roteala.net.Peer;
import uk.co.roteala.processor.EventTransformer;
import uk.co.roteala.storage.StorageCreatorComponent;
import uk.co.roteala.utils.GlacierUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

@Slf4j
@Configuration
public class Broker {

    @Autowired
    private StorageCreatorComponent storages;

    @Bean
    public void serverConfig() throws RocksDBException, NoSuchAlgorithmException {
        //Genesis
        setGenesis();

        TcpServer.create()
                .doOnConnection(doOnConnectionHandler())
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handle(((inbound, outbound) -> {

                    inbound.receive().asByteArray()
                            .flatMap(e -> {
                                Event event = (Event) SerializationUtils.deserialize(e);
                                return Mono.just(event);
                            })
                            .doOnNext(event -> {
                                log.info("Data:{}", event);
                            })
                            .flatMap(event -> {
                                log.info("Transform event:{}", event);
                                List<BaseEmptyModel> data = event.flatTransform(eventTransformer());

                                for(BaseEmptyModel model : data) {
                                    outbound.sendByteArray(Flux.just(SerializationUtils.serialize(model)));
                                }

                                return Flux.never();
                            })
                            .subscribe();

                    // Keep the connection alive
                    return Flux.never();
                }))
                .port(7331)
                .doOnBound(server -> {
                    log.info("Server started on address:{} and port:{}", server.address(), server.port());
                })
                .doOnUnbound(server -> {
                    log.info("Server stopped!");
                })
                .bindNow();
    }
    public EventTransformer eventTransformer() {
        return new EventTransformer(storages);
    }

    public InetSocketAddress parseIpAddressInet(String address) {
        String addressFirst = address.substring(1);
                //.split(":")[0];
        String addressIp = addressFirst.split(":")[0];
        Integer port = Integer.valueOf(addressFirst.split(":")[1]);
        return new InetSocketAddress(addressIp, port);
    }

    public Consumer<Connection> doOnConnectionHandler(){
        return connection -> {
            log.info("INNET_SOCKET:{}", parseIpAddressInet(connection.address().toString()));
            String ipAddress = GlacierUtils.parseIpAddress(connection.address().toString());
            try {
                Peer peerDeserialized = (Peer) storages.peers().get(ipAddress.getBytes());
                if(peerDeserialized == null) {
                    Peer peer = new Peer();
                    peer.setActive(true);
                    peer.setAddress(ipAddress);
                    peer.setLastTimeSeen(System.currentTimeMillis());

                    storages.peers().add(peer.getAddress().getBytes(), peer);
                    log.info("New peer added:{}", peer);
                } else {
                    peerDeserialized.setLastTimeSeen(System.currentTimeMillis());
                    peerDeserialized.setActive(true);

                    storages.peers().add(peerDeserialized.getAddress().getBytes(), peerDeserialized);
                    log.info("Peer updated:{}", peerDeserialized);
                }
            } catch (Exception e) {
                log.error("Error while retrieving peer!{}", e);
            }

        };
    }

    private void setGenesis() throws NoSuchAlgorithmException, RocksDBException {
        List<UTXO> in = new ArrayList<>();
        List<UTXO> out = new ArrayList<>();

        final String memo = "17/March/2023 - Now I am become death, the destroyer of worlds";
        final String txID = GlacierUtils.bytesToHex(memo.getBytes());

        UTXO input = new UTXO();
        input.setCoinbase(true);
        input.setTxid(txID);

        in.add(input);

        UTXO output = new UTXO();
        output.setAddress("1Lxu7ASy3QhmEPPkLaBSmSx2VkBNzTbkur");
        //output.setPkscript("OP_DUP OP_HASH160 e7b1f5e83db7437c06510a686a2f5bdf248283a4 OP_EQUALVERIFY OP_CHECKSIG");
        output.setValue(Coin.valueOf(33));
        output.setSpent(false);
        output.setSpender(null);

        out.add(output);

        TransactionBaseModel tx = new TransactionBaseModel();
        tx.setBlockHash("1d809807f051f19959dbedcc50b3c5016fcca68eb876cfca92d4dae454d1170e");
        tx.setBlockNumber(0);
        tx.setTransactionIndex(0);
        tx.setIn(in);
        tx.setFees(Coin.valueOf(0L));
        tx.setOut(out);
        tx.setTimeStamp(1679014272);
        tx.setStatus(TransactionStatus.SUCCESS);
        tx.setHash(tx.hashHeader());
        tx.setVersion(0x1);



        BaseBlockModel block = new BaseBlockModel();
        block.setVersion(0x1);
        block.setMarkleRoot(tx.getHash());
        block.setTimeStamp(1679014272);
        block.setNonce(new BigInteger("387f8648", 16));
        block.setPreviousHash("0000000000000000000000000000000000000000000000000000000000000000");


        block.setIndex(0);

        block.setMiner("Roti");
        block.setTransactions(List.of(tx.getHash()));
        block.setConfirmations(1);
        block.setStatus(BlockStatus.MINED);
        block.setDifficulty(new BigInteger("1"));

        final byte[] nb = SerializationUtils.serialize(block);

        block.setNumberOfBits(nb.length);
        block.setHash(block.hashHeader());


        //Chain state
//        ChainState chain = new ChainState();
//        chain.setNonce();
//        chain.setMinerKey(null);
//        chain.setTarget("00000000ffff0000000000000000000000000000000000000000000000000000");


        final byte[] txKey = tx.getHash().getBytes();
        final byte[] blockKey = block.getIndex().toString().getBytes();

        if(this.storages.blocks().get(blockKey) == null) {
            this.storages.blocks().add(blockKey, block);
        }

        if(this.storages.tx().get(txKey) == null){
            this.storages.tx().add(txKey, tx);
        }
    }

    @Bean
    public void miner() throws NoSuchAlgorithmException {
        //Mine the nonce for the first block
        BigInteger target = new BigInteger("00000000ffff0000000000000000000000000000000000000000000000000000", 16);

        boolean mining = true;
        BaseBlockModel block = new BaseBlockModel();
        block.setMiner("Roti");
        block.setPreviousHash("1d809807f051f19959dbedcc50b3c5016fcca68eb876cfca92d4dae454d1170e");
        block.setIndex(1);
        block.setDifficulty(target);
        block.setConfirmations(1);
        block.setVersion(0x1);

        List<UTXO> in = new ArrayList<>();
        List<UTXO> out = new ArrayList<>();

        UTXO input = new UTXO();
        input.setCoinbase(true);
        input.setTxid(GlacierUtils.generateRandomSha256(32));
        //input.setSigscript(null);

        in.add(input);

        UTXO output = new UTXO();
        output.setAddress("1Lxu7ASy3QhmEPPkLaBSmSx2VkBNzTbkur");
        //output.setPkscript("OP_DUP OP_HASH160 e7b1f5e83db7437c06510a686a2f5bdf248283a4 OP_EQUALVERIFY OP_CHECKSIG");
        output.setValue(Coin.valueOf(33));
        output.setSpent(false);
        output.setSpender(null);

        out.add(output);

        TransactionBaseModel tx = new TransactionBaseModel();
        tx.setVersion(0x1);
        tx.setStatus(TransactionStatus.PENDING);
        tx.setOut(out);
        tx.setIn(in);
        tx.setBlockNumber(1);
        tx.setBlockTime(System.currentTimeMillis());
        tx.setTransactionIndex(0);
        tx.setFees(Coin.valueOf(10));
        tx.setConfirmations(1);

        if(target.compareTo(new BigInteger(block.getPreviousHash(), 16)) < 0) {
            log.info("True:{}", target.compareTo(new BigInteger(block.getPreviousHash(), 16)));
        } else {
            log.info("False:{}", new BigInteger(block.getPreviousHash(), 16));
        }


//        while(mining) {
//            BigInteger nonce = new BigInteger(32, new Random());
//            block.setNonce(nonce);
//            block.setTimeStamp(System.currentTimeMillis());
//            block.setHash(block.hashHeader());
//            tx.setBlockHash(block.getHash());
//            tx.setHash(tx.hashHeader());
//            block.setTransactions(List.of(tx.getHash()));
//
//            BigInteger h = new BigInteger(block.getHash(), 16);
//
//            log.info("Hash:{}", block.getHash());
//
//            if(h.compareTo(target) < 0) {
//                mining = false;
//            }
//        }

        log.info("Block:{}", block);

    }
}
