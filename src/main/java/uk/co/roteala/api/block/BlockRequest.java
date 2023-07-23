package uk.co.roteala.api.block;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
//@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class BlockRequest {
    @NotNull(message = "The field is mandatory")
    @Schema(description = "Index", type = "String", example = "33", required = true)
    private String index;
}
