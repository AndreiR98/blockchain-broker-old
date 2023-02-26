package uk.co.roteala;

import org.rocksdb.RocksDBException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import uk.co.roteala.server.Server;

import java.io.IOException;

@SpringBootApplication
public class GlacierBrokerApplication {

    public static void main(String[] args) throws RocksDBException, IOException {
        SpringApplication.run(GlacierBrokerApplication.class, args);

        //Server server = new Server();

        //server.startServer();
    }

}
