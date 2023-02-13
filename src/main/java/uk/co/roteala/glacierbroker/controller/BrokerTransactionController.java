package uk.co.roteala.glacierbroker.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import uk.co.roteala.common.TransactionBaseModel;
import uk.co.roteala.glacierbroker.services.BrokerServices;

@Slf4j
@Controller
@RequestMapping("/transaction")
public class BrokerTransactionController {

    @Autowired
    private BrokerServices services;

    @PostMapping("/create")
    public void createRnadomTransactions(){
        for(int i = 0; i <= 100000; i++){
            //Insert one million transactions
            TransactionBaseModel transaction = new TransactionBaseModel();
        }
    }
}
