package uk.co.roteala.client;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class TModel {
    private BigDecimal amount;
}
