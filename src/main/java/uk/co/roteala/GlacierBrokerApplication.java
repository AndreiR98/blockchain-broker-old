package uk.co.roteala;

import org.rocksdb.RocksDBException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.io.IOException;

@SpringBootApplication
@CrossOrigin
//@EnableResourceServer
@PropertySource("classpath:i18n/errors.properties")
public class GlacierBrokerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GlacierBrokerApplication.class, args);
    }

}
