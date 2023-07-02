package uk.co.roteala.api.transaction;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import uk.co.roteala.common.SignatureModel;
import uk.co.roteala.common.TransactionStatus;
import uk.co.roteala.common.monetary.Coin;
import uk.co.roteala.common.monetary.CoinConverter;

import javax.validation.constraints.NotNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class PseudoTransactionRequest {
    @Schema(description = "Sender address", type = "String", example = "1L8pa8DQyuVaHCgR2UN71Mzy3KnNX68Y1a", required = true)
    @NotNull(message = "The field is mandatory")
    private String from;

    @Schema(description = "Transaction index in wallet", type = "Integer", example = "1", required = true)
    private Integer nonce;

    @NotNull(message = "The field is mandatory")
    @Schema(description = "Transaction pseudo hash", type = "String", example = "81a85ed916f6e356b92a39c268b6f069c6c1e1fcfc46b646f9e5249f0002b9d8", required = true)
    private String pseudoHash;

    @NotNull(message = "The field is mandatory")
    @Schema(description = "Sender public key hash", type = "String", example = "786a33e9e10673747d9021c8478e1e5572c6fcd346e79f6b1184aeeb8e7b3f20", required = true)
    private String pubKeyHash;

    @NotNull(message = "The field is mandatory")
    @Schema(description = "Transaction Signature", type = "SignatureModel", example = "{\n" +
            "        \"r\": \"ef05c205d267249ffa70b830b69f6fbbf26198f9e82d6adf4c0d49ea6303fa82\",\n" +
            "        \"s\": \"f653a822c42053a23a1dff8e5e203ecedd59e22aefa7a4cf1a0908bf10aa6a1b\"\n" +
            "    }", required = true)
    private SignatureModel signature;

    @NotNull(message = "The field is mandatory")
    @Schema(description = "Transaction status", type = "TransactionStatus", example = "PENDING", required = true)
    private TransactionStatus status;

    @NotNull(message = "The field is mandatory")
    @Schema(description = "Transaction timestamp", type = "long", example = "1688143810", required = true)
    private long timeStamp;

    @NotNull(message = "The field is mandatory")
    @Schema(description = "Transaction destination address", type = "String", example = "1L8pa8DQyuVaHCgR2UN71Mzy3KnNX68Y1a", required = true)
    private String to;

    @NotNull(message = "The field is mandatory")
    @Schema(description = "Transaction amount/value", type = "Coin", example = "122", required = true)
    @JsonSerialize(converter = CoinConverter.class)
    private Coin value;

    @NotNull(message = "The field is mandatory")
    @Schema(description = "Transaction version", type = "Integer", example = "16", required = true)
    private Integer version;
}
