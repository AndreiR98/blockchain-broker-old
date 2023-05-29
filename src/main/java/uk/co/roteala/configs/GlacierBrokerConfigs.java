package uk.co.roteala.configs;


import lombok.Getter;
import lombok.Setter;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.nio.file.Paths;

@Getter
@Setter
@Configuration
public class GlacierBrokerConfigs {
    private static final String ROOT_WINDOWS = System.getenv("APPDATA");

    private static final String ROOT_LINUX = System.getProperty("user.home");

    private String rootWindows = ROOT_WINDOWS;

    private String rootLinux = ROOT_LINUX;

    private static final String PEERS_PATH = "/roteala/crawler/peers/";

    private File peersPath = new File(Paths.get(ROOT_LINUX, PEERS_PATH).toString());

    private static final boolean DEFAULT_MODE = true;

    private boolean netWorkMode = DEFAULT_MODE;

    private static final Integer DEFAULT_PORT = 7331;

    private Integer defaultPort = DEFAULT_PORT;
}
