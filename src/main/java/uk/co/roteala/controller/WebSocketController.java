package uk.co.roteala.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import uk.co.roteala.api.ApiError;
import uk.co.roteala.api.ApiStateChain;
import uk.co.roteala.api.explorer.ExplorerRequest;
import uk.co.roteala.api.explorer.ExplorerResponse;
import uk.co.roteala.common.ChainState;
import uk.co.roteala.storage.StorageServices;

import javax.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/websocket")
@AllArgsConstructor
public class WebSocketController {

    @Autowired
    private StorageServices services;

    @Operation(summary = "Get data from storage")
    @RequestBody(content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExplorerRequest.class)), required = true)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Data retrieved successfully", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = ExplorerResponse.class))}),
            @ApiResponse(responseCode = "404", description = "Invalid  data", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiError.class))}),
            @ApiResponse(responseCode = "400", description = "BadRequest", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiError.class))})})
    @GetMapping("/latest")
    @ResponseStatus(HttpStatus.OK)
    public String getLatestChanges(){
        ApiStateChain apiStateChain = new ApiStateChain();

        ChainState state = this.services.getStateTrie();

        apiStateChain.setLastBlockIndex(state.getLastBlockIndex());
        apiStateChain.setNetworkFees(state.getNetworkFees());

        ObjectMapper mapper = new ObjectMapper();

        String json = "";

        try {
            json = mapper.writeValueAsString(apiStateChain);
        } catch (Exception e){}

        return json;
    }
}
