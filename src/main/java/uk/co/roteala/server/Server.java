package uk.co.roteala.server;

import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksDBException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.SerializationUtils;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpServer;
import uk.co.roteala.common.*;
import uk.co.roteala.common.monetary.Coin;
import uk.co.roteala.configs.GlacierBrokerConfigs;
import uk.co.roteala.handlers.TransmissionHandler;
import uk.co.roteala.net.Peer;
import uk.co.roteala.storage.StorageServices;
import uk.co.roteala.utils.GlacierUtils;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Consumer;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class Server {

    private final StorageServices storage;

    private final GlacierBrokerConfigs configs;

    @Bean
    public void serverConfig() {

        TcpServer.create()
                .doOnConnection(doOnConnectionHandler())
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handle(transmissionHandler())
                .port(7331)
                .doOnBound(server -> {
                    log.info("Server started on address:{} and port:{}", server.address(), server.port());
                })
                .doOnUnbound(server -> {
                    log.info("Server stopped!");
                })
                .bindNow()
                .onDispose()
                .block();
    }

    @Bean
    public TransmissionHandler transmissionHandler() {
        return new TransmissionHandler(storage);
    }

    public Consumer<Connection> doOnConnectionHandler(){
        return connection -> {
            AddressBaseModel addressModel = GlacierUtils.formatAddress(connection.address().toString());
            try {
                String address = addressModel.getAddress();

                Peer peer;

                if(storage.get(address.getBytes()) != null) {
                    peer = (Peer) storage.get(address.getBytes());
                    peer.setActive(true);
                } else {
                    peer = new Peer();
                    peer.setActive(true);
                    //peer.setPort(addressModel.getPort());
                    peer.setAddress(addressModel.getAddress());

                    log.info("Peer updated:{}", peer);

                    if(configs.isNetWorkMode()){
                        peer.setPort(addressModel.getPort());
                        log.info("New peer added:{} with key:{}", peer, address);
                    } else {
                        log.info("New peer added:{} with key:{}", peer, address);
                        peer.setPort(configs.getDefaultPort());
                    }
                }
                storage.add(address.getBytes(), peer);

                connection.onDispose()
                        .doFinally(signalType -> onDisconnect(connection));
            } catch (Exception e) {
                log.error("Error while retrieving peer!{}", e);
            }
        };
    }

    private void onDisconnect(Connection connection){
        //AddressBaseModel addressBaseModel = GlacierUtils.formatAddress(connection.address().toString());

        try {
            String addressWithPort = connection.address().toString();

            if(storage.get(addressWithPort.getBytes()) != null) {
                Peer peer = (Peer) storage.get(addressWithPort.getBytes());
                peer.setLastTimeSeen(System.currentTimeMillis());
                peer.setActive(false);

                storage.add(addressWithPort.getBytes(), peer);
                log.info("Peer disconnected");
            }

        }catch (Exception e) {
            //
        }

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

        if(storage.get(blockKey) == null) {
            storage.add(blockKey, block);
        }

        if(storage.get(txKey) == null){
            storage.add(txKey, tx);
        }
    }
}
