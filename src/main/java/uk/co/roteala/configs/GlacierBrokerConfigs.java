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

    private static final boolean DEFAULT_MODE = true;

    private boolean netWorkMode = DEFAULT_MODE;

    private static final Integer DEFAULT_PORT = 7331;

    private Integer defaultPort = DEFAULT_PORT;

    private static final String LOGS = "/logs";

    private static final String PEERS_PATH = "/roteala/crawler/peers/";

    private File peersPath = new File(Paths.get(ROOT_WINDOWS, PEERS_PATH).toString());

    private File peersPathLogs = new File(Paths.get(ROOT_WINDOWS, PEERS_PATH, LOGS).toString());

    private static final String BLOCKS_PATH = "/roteala/crawler/data/";

    private File blocksPath = new File(Paths.get(ROOT_WINDOWS, BLOCKS_PATH).toString());

    private File blocksPathLogs = new File(Paths.get(ROOT_WINDOWS, BLOCKS_PATH, LOGS).toString());

    private static final String STATE_TRIE = "/roteala/crawler/state/";

    private File stateTriePath = new File(Paths.get(ROOT_WINDOWS, STATE_TRIE).toString());

    private File stateTrieLogsPath = new File(Paths.get(ROOT_WINDOWS, STATE_TRIE, LOGS).toString());

    private static final String MEMPOOL_PATH = "/roteala/crawler/mempool/";

    private File mempoolPath = new File(Paths.get(ROOT_WINDOWS, MEMPOOL_PATH).toString());

    private File mempoolLogsPath = new File(Paths.get(ROOT_WINDOWS, MEMPOOL_PATH, LOGS).toString());
}
