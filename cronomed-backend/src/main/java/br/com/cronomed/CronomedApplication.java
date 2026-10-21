package br.com.cronomed;

import br.com.cronomed.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class CronomedApplication {

    public static void main(String[] args) {
        SpringApplication.run(CronomedApplication.class, args);
    }
}
