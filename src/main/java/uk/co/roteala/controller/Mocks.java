package uk.co.roteala.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.SerializationUtils;
import org.springframework.web.bind.annotation.*;
import uk.co.roteala.client.TModel;
import uk.co.roteala.common.*;
import uk.co.roteala.storage.StorageCreatorComponent;
import uk.co.roteala.utils.GlacierUtils;

import java.math.BigDecimal;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
@RequestMapping("/blockchain")
public class Mocks {

    @Autowired
    private StorageCreatorComponent storages;

    @GetMapping("/account")
    @CrossOrigin
    public ResponseEntity<TModel> getChain() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {

       TModel model = new TModel();

       model.setAmount(new BigDecimal("1.0000"));

        HttpHeaders headers = new HttpHeaders();

        return new ResponseEntity<>(model, headers, HttpStatus.OK);
    }

    @GetMapping("/transaction/{hash}")
    public ResponseEntity<TransactionBaseModel> getTransaction(@PathVariable(value = "hash") String txHash) throws RocksDBException {
        TransactionBaseModel transaction = new TransactionBaseModel();

        if(txHash != null) {
            byte[] txKey = txHash.getBytes();

            BaseEmptyModel serializedTx = this.storages.tx().get(txKey);

            if(serializedTx != null) {
                transaction = (TransactionBaseModel) serializedTx;
            }
        }

        HttpHeaders headers = new HttpHeaders();

        return new ResponseEntity<>(transaction, headers, HttpStatus.OK);
    }

    @GetMapping("/block/{id}")
    public ResponseEntity<BaseBlockModel> getTransaction(@PathVariable(value = "id") Integer blockId) throws RocksDBException {
        BaseBlockModel block = new BaseBlockModel();

        if(blockId != null) {
            byte[] txKey = blockId.toString().getBytes();

            BaseEmptyModel serializedTx = this.storages.blocks().get(txKey);

            if(serializedTx != null) {
                block = (BaseBlockModel) serializedTx;
            }
        }

        HttpHeaders headers = new HttpHeaders();

        return new ResponseEntity<>(block, headers, HttpStatus.OK);
    }

    @GetMapping("/blockchain")
    public ResponseEntity<List<Integer>> getBlocksIds() throws RocksDBException {
        List<Integer> ids = new ArrayList<>();

        RocksIterator iterator = this.storages.blocks().getRaw().newIterator();

        for(iterator.seekToFirst(); iterator.isValid(); iterator.next()){
            final Integer blockId = (Integer) SerializationUtils.deserialize(iterator.key());
            ids.add(blockId);
        }

        HttpHeaders headers = new HttpHeaders();

        return new ResponseEntity<>(ids, headers, HttpStatus.OK);
    }
}
