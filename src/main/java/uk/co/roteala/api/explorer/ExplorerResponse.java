package uk.co.roteala.api.explorer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.co.roteala.api.ResultStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
//@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExplorerResponse {
    private String blockHash;
    private String accountAddress;
    private String transactionHash;
    private Integer blockIndex;
    private ResultStatus status;
}
