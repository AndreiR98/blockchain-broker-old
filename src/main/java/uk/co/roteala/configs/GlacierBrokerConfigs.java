package uk.co.roteala.configs;


import lombok.Getter;
import lombok.Setter;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
public class GlacierBrokerConfigs {
    private static final String ROOT_WINDOWS = "C:/Glacier";

    private static final String ROOT_LINUX = "user.home";

    private String rootWindows = ROOT_WINDOWS;

    private String rootLinux = ROOT_LINUX;

    private static final String PEERS_PATH = "/peers";

    private String peersPath = PEERS_PATH;
}
