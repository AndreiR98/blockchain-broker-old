package uk.co.roteala.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import uk.co.roteala.api.ApiError;
import uk.co.roteala.api.transaction.*;
import uk.co.roteala.common.Transaction;
import uk.co.roteala.services.TransactionServices;

import javax.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/transaction")
@AllArgsConstructor
@Tag(name = "Blockchain Transaction Operations", description = "The API to fetch info regarding transaction")
public class TransactionController {

    final TransactionServices transactionServices;

//    /**
//     * EXPLORER API
//     * */

//
//    public List<String> getTransactionsByAddress(){}
//
//    public List<String> getTransactionsByBlockId(){}
//
//
//    public List<String> getTransactionsByStatus(){}
//
//    public List<Transaction> getTransactionBatchByHashes(){}

    @Operation(summary = "Get transaction from storage")
    @RequestBody(content = @Content(mediaType = "application/json", schema = @Schema(implementation = PseudoTransactionRequest.class)), required = true)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transaction retrieved successfully", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = PseudoTransactionResponse.class))}),
            @ApiResponse(responseCode = "404", description = "Invalid transaction data", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiError.class))}),
            @ApiResponse(responseCode = "400", description = "BadRequest", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiError.class))})})
    @PostMapping("/hash")
    @ResponseStatus(HttpStatus.OK)
    public TransactionResponse getTransactionsByHash(@Valid @org.springframework.web.bind.annotation.RequestBody TransactionRequest transactionRequest){
        return this.transactionServices.getTransactionByHash(transactionRequest);
    }

    /**
     * Receive and validates pseudoTransaction then sends it to nodes for mining
     * */
    @Operation(summary = "Get pseudo transaction from the wallet interface, validate it and then send it to the nodes")
    @RequestBody(content = @Content(mediaType = "application/json", schema = @Schema(implementation = PseudoTransactionRequest.class)), required = true)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transaction validated and added to mempool", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = PseudoTransactionResponse.class))}),
            @ApiResponse(responseCode = "404", description = "Invalid transaction data", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiError.class))}),
            @ApiResponse(responseCode = "400", description = "BadRequest", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiError.class))})})
    @PostMapping("/send-transaction")
    @ResponseStatus(HttpStatus.OK)
    public PseudoTransactionResponse sendTransaction(@Valid @org.springframework.web.bind.annotation.RequestBody PseudoTransactionRequest transactionRequest){
        log.info("Received new transaction:{}", transactionRequest);
        return this.transactionServices.sendTransaction(transactionRequest);
    }

    /**
     * Get pseudo transaction by key
     * */
    @Operation(summary = "Get pseudo transaction from mempool")
    @RequestBody(content = @Content(mediaType = "application/json", schema = @Schema(implementation = PseudoTransactionRequest.class)), required = true)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "PseudoTransaction retrieved form mempool", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = PseudoTransactionResponse.class))}),
            @ApiResponse(responseCode = "404", description = "Invalid transaction data", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiError.class))}),
            @ApiResponse(responseCode = "400", description = "BadRequest", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiError.class))})})
    @PostMapping("/pseudotransaction")
    @ResponseStatus(HttpStatus.OK)
    public PseudoTransactionResponse getPseudoTransactionByKey(@Valid @org.springframework.web.bind.annotation.RequestBody PseudoTransactionByKeyRequest transactionRequest){
        return this.transactionServices.getPseudoTransactionByKey(transactionRequest);
    }

    /**
     * Get batch of pseudo transactions
     * */
}
