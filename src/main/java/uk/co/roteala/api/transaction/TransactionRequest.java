package uk.co.roteala.api.transaction;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class TransactionRequest {
    @NotNull(message = "The field is mandatory")
    @Schema(description = "Transaction hash", type = "String", example = "81a85ed916f6e356b92a39c268b6f069c6c1e1fcfc46b646f9e5249f0002b9d8", required = true)
    private String transactionHash;
}
