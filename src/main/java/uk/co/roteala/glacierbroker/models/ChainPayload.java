package uk.co.roteala.glacierbroker.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.co.roteala.common.BaseCoinModel;
import uk.co.roteala.common.ChainType;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChainPayload {
    private String name;
    private ChainType type;
    private String builder;
    private BaseCoinModel coin;
    private String password;
    private String privateKey;
}
