package uk.co.roteala.api.transaction;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.co.roteala.api.ResultStatus;
import uk.co.roteala.common.PseudoTransaction;
import uk.co.roteala.common.TransactionStatus;
import uk.co.roteala.common.monetary.Coin;
import uk.co.roteala.common.monetary.CoinConverter;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PseudoTransactionResponse {
//    private String pseudoHash;
//    private String from;
//    private String to;
//    @JsonSerialize(converter = CoinConverter.class)
//    private Coin fees;
//    private Integer version;
//    @JsonSerialize(converter = CoinConverter.class)
//    private Coin value;
//    private Integer nonce;
//    private long timeStamp;
//    private String pubKeyHash;
//    private TransactionStatus transactionStatus;
    private PseudoTransaction pseudoTransaction;
    private String message;
    private ResultStatus result;
}
