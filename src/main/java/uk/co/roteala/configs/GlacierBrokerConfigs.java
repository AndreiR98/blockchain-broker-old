package uk.co.roteala.configs;


import lombok.Getter;
import lombok.Setter;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
public class GlacierBrokerConfigs {
    private static final String STORAGE_PATH = "C:/Glacier/peers";

    private String storagePath = STORAGE_PATH;

    private static final String NAME = "peers";

    private String name = NAME;
}
