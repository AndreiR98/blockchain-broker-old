package uk.co.roteala.api;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.*;
import uk.co.roteala.common.monetary.Coin;
import uk.co.roteala.common.monetary.CoinConverter;

@Data
@Builder
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ApiStateChain {
    @JsonSerialize(converter = CoinConverter.class)
    private Coin networkFees;
    private Integer lastBlockIndex;
}
