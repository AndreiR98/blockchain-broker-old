package uk.co.roteala.api.account;

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
public class AccountRequest {
    @Schema(description = "Account address", type = "String", example = "1L8pa8DQyuVaHCgR2UN71Mzy3KnNX68Y1a", required = true)
    @NotNull(message = "The field is mandatory")
    private String address;
}
