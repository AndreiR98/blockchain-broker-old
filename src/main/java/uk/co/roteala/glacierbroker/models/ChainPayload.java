package uk.co.roteala.glacierbroker.models;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.co.roteala.common.BaseCoinModel;
import uk.co.roteala.common.ChainType;
import uk.co.roteala.common.GenesisAccount;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class ChainPayload {
    private String name;
    private String coinName;
    private String coinNickName;
    private BigDecimal maxAmount;
    private Integer decimalPoints;
    private ChainType type;
    private List<GenesisAccount> accounts;
    //private CoinPayload coin;
}
