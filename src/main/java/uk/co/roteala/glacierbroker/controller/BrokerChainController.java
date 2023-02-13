package uk.co.roteala.glacierbroker.controller;

import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksDBException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import uk.co.roteala.common.Chain;
import uk.co.roteala.common.GenesisAccount;
import uk.co.roteala.glacierbroker.models.ChainPayload;
import uk.co.roteala.glacierbroker.services.BrokerServices;
import uk.co.roteala.utils.GlacierUtils;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.List;

@Slf4j
@Controller
@RequestMapping("/chain")
public class BrokerChainController {
    @Autowired
    private BrokerServices services;

    @PostMapping("/genesis")
    public void createGenesisAccounts(@RequestBody ChainPayload payload) throws RocksDBException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, NoSuchProviderException {
        //log.info("Chain:{}", GlacierUtils.generateRandomSha256(32));
        if(!services.chainExistent()){
            //Create new chain if non existant
            services.initChain(payload);
        }else{
            log.error("CHAIN ALREADY EXISTS!");
        }
    }

    @GetMapping("/chain")
    public ResponseEntity<Chain> getChain() throws RocksDBException {


        HttpHeaders headers = new HttpHeaders();

        return new ResponseEntity<>(services.getChain(), headers, HttpStatus.CREATED);
    }
}
