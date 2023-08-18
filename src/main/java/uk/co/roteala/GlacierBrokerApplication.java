package uk.co.roteala;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;

import java.io.IOException;

@SpringBootApplication
@CrossOrigin
@EnableWebSocketMessageBroker
@PropertySource("classpath:i18n/errors.properties")
public class GlacierBrokerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GlacierBrokerApplication.class, args);
    }

}
