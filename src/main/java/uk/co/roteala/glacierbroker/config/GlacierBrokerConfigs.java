package uk.co.roteala.glacierbroker.config;


import lombok.Getter;
import lombok.Setter;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
public class GlacierBrokerConfigs {
    private static final String STORAGE_PATH = "C:/Glacier";

    private String storagePath = STORAGE_PATH;
}
