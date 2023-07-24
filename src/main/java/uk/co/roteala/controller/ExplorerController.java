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
import uk.co.roteala.api.account.AccountRequest;
import uk.co.roteala.api.account.AccountResponse;
import uk.co.roteala.api.block.BlockRequest;
import uk.co.roteala.api.block.BlockResponse;
import uk.co.roteala.api.explorer.ExplorerRequest;
import uk.co.roteala.api.explorer.ExplorerResponse;
import uk.co.roteala.api.transaction.PseudoTransactionRequest;
import uk.co.roteala.api.transaction.PseudoTransactionResponse;
import uk.co.roteala.api.transaction.TransactionRequest;
import uk.co.roteala.api.transaction.TransactionResponse;
import uk.co.roteala.common.BaseModel;
import uk.co.roteala.services.ExplorerServices;
import uk.co.roteala.utils.BlockchainUtils;

import javax.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/explorer")
@AllArgsConstructor
@Tag(name = "Blockchain Transaction Operations", description = "The API to fetch info regarding transaction")
public class ExplorerController {
    private final ExplorerServices explorerServices;

    @Operation(summary = "Get data from storage")
    @RequestBody(content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExplorerRequest.class)), required = true)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Data retrieved successfully", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = ExplorerResponse.class))}),
            @ApiResponse(responseCode = "404", description = "Invalid  data", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiError.class))}),
            @ApiResponse(responseCode = "400", description = "BadRequest", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiError.class))})})
    @PostMapping("/")
    @ResponseStatus(HttpStatus.OK)
    public ExplorerResponse processExplorerRequest(@Valid @org.springframework.web.bind.annotation.RequestBody ExplorerRequest explorerRequest){
        return this.explorerServices.processExplorerRequest(explorerRequest);
    }

    @Operation(summary = "Get transaction from storage")
    @RequestBody(content = @Content(mediaType = "application/json", schema = @Schema(implementation = TransactionRequest.class)), required = true)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transaction retrieved successfully", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = TransactionResponse.class))}),
            @ApiResponse(responseCode = "404", description = "Invalid transaction data", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiError.class))}),
            @ApiResponse(responseCode = "400", description = "BadRequest", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiError.class))})})
    @GetMapping("/transaction/{hash}")
    @ResponseStatus(HttpStatus.OK)
    public TransactionResponse getTransactionsByHash(@Valid @PathVariable String hash){
        TransactionRequest request = new TransactionRequest();
        request.setTransactionHash(hash);
        return this.explorerServices.getTransactionByHash(request);
    }

    @Operation(summary = "Get transaction from storage")
    @RequestBody(content = @Content(mediaType = "application/json", schema = @Schema(implementation = TransactionRequest.class)), required = true)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transaction retrieved successfully", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = TransactionResponse.class))}),
            @ApiResponse(responseCode = "404", description = "Invalid transaction data", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiError.class))}),
            @ApiResponse(responseCode = "400", description = "BadRequest", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiError.class))})})
    @PostMapping("/transaction")
    @ResponseStatus(HttpStatus.OK)
    public TransactionResponse getTransactionsByHashPost(@Valid @org.springframework.web.bind.annotation.RequestBody TransactionRequest request){
        return this.explorerServices.getTransactionByHash(request);
    }

    @Operation(summary = "Get block from storage")
    @RequestBody(content = @Content(mediaType = "application/json", schema = @Schema(implementation = BlockRequest.class)), required = true)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Block retrieved successfully", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = BlockResponse.class))}),
            @ApiResponse(responseCode = "404", description = "Invalid block data", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiError.class))}),
            @ApiResponse(responseCode = "400", description = "BadRequest", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiError.class))})})
    @PostMapping("/block")
    @ResponseStatus(HttpStatus.OK)
    public BlockResponse getBlockByIndex(@Valid @org.springframework.web.bind.annotation.RequestBody BlockRequest blockRequest){
        return this.explorerServices.getBlock(blockRequest);
    }

    @Operation(summary = "Get block from storage")
    @RequestBody(content = @Content(mediaType = "application/json", schema = @Schema(implementation = BlockRequest.class)), required = true)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Block retrieved successfully", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = BlockResponse.class))}),
            @ApiResponse(responseCode = "404", description = "Invalid block data", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiError.class))}),
            @ApiResponse(responseCode = "400", description = "BadRequest", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiError.class))})})
    @GetMapping("/block/{hash}")
    @ResponseStatus(HttpStatus.OK)
    public BlockResponse getBlockByHash(@Valid @PathVariable String hash){
        BlockRequest blockRequest = new BlockRequest();
        blockRequest.setIndex(hash);

        return this.explorerServices.getBlock(blockRequest);
    }

    @Operation(summary = "Get account from storage")
    @RequestBody(content = @Content(mediaType = "application/json", schema = @Schema(implementation = PseudoTransactionRequest.class)), required = true)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account retrieved successfully", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = PseudoTransactionResponse.class))}),
            @ApiResponse(responseCode = "404", description = "Invalid account data", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiError.class))}),
            @ApiResponse(responseCode = "400", description = "BadRequest", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiError.class))})})
    @PostMapping("/address")
    @ResponseStatus(HttpStatus.OK)
    public AccountResponse getAccount(@Valid @org.springframework.web.bind.annotation.RequestBody AccountRequest accountRequest, @PathVariable String hash){
        return this.explorerServices.getAccount(accountRequest);
    }

    @Operation(summary = "Get account from storage")
    @RequestBody(content = @Content(mediaType = "application/json", schema = @Schema(implementation = PseudoTransactionRequest.class)), required = true)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account retrieved successfully", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = PseudoTransactionResponse.class))}),
            @ApiResponse(responseCode = "404", description = "Invalid account data", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiError.class))}),
            @ApiResponse(responseCode = "400", description = "BadRequest", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiError.class))})})
    @GetMapping("/address/{address}")
    @ResponseStatus(HttpStatus.OK)
    public AccountResponse getAccountByAddress(@Valid @PathVariable String address){
        AccountRequest accountRequest = new AccountRequest();
        accountRequest.setAddress(address);

        return this.explorerServices.getAccount(accountRequest);
    }

    @Operation(summary = "Add data into the blockchain for testing")
    //@RequestBody(content = @Content(mediaType = "application/json", schema = @Schema(implementation = PseudoTransactionRequest.class)), required = true)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account retrieved successfully", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = PseudoTransactionResponse.class))}),
            @ApiResponse(responseCode = "404", description = "Invalid account data", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiError.class))}),
            @ApiResponse(responseCode = "400", description = "BadRequest", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiError.class))})})
    @PostMapping("/test")
    @ResponseStatus(HttpStatus.OK)
    public List<BaseModel> addData(){
        return this.explorerServices.addMultipleData();
    }
}
