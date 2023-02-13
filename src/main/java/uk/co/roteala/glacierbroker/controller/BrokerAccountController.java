package uk.co.roteala.glacierbroker.controller;


import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksDBException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import uk.co.roteala.common.*;
import uk.co.roteala.glacierbroker.models.ChainPayload;
import uk.co.roteala.glacierbroker.services.BrokerServices;

import java.util.List;


@Slf4j
@Controller
@RequestMapping("/account")
public class BrokerAccountController {
    @Autowired
    private BrokerServices services;

    @GetMapping("/{hex}")
    public ResponseEntity<AccountModel> getChain(@PathVariable String hex) throws RocksDBException {

        AddressBaseModel address = new AddressBaseModel();
        address.setHexEncoded(hex);

        log.info("Hex:{}",hex);

        HttpHeaders headers = new HttpHeaders();

        return new ResponseEntity<>(services.getAccountDetails(address), headers, HttpStatus.CREATED);
    }


}
